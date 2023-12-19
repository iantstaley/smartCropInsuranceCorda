package com.insurance.flows

import co.paralleluniverse.fibers.Suspendable
import com.insurance.contracts.ProductContract
import com.insurance.oracles.WeatherOracle
import com.insurance.schema.ProductProposalSchemaV1
import com.insurance.states.Product
import com.insurance.states.ProductProposal
import com.insurance.states.WeatherCriteria
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.LocalDate
import java.lang.IllegalArgumentException
//import kotlin.IllegalArgumentException

object ProductFlow {

    @StartableByRPC
    @InitiatingFlow
    class Create(val proposalId: String, val productId: String) : FlowLogic<String>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): String {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val proposal = getProposal(proposalId)?:throw IllegalArgumentException("Proposal not found for proposal Id: $proposalId")
            val insuranceProviderParty = serviceHub.networkMapCache.getPeerByLegalName(proposal.state.data.providerName)?:throw IllegalArgumentException("Insurance Provider party not found ${proposal.state.data.providerName}")

            val product = makeProduct(proposal.state.data, insuranceProviderParty)
            val command = Command(ProductContract.Commands.Create(), listOf(ourIdentity.owningKey, insuranceProviderParty.owningKey))

            val txBuilder = TransactionBuilder(notary).
                            addInputState(proposal).
                            addOutputState(product, ProductContract.ID).
                            addCommand(command)

            txBuilder.verify(serviceHub)

            val partiallySignedTx = serviceHub.signInitialTransaction(txBuilder)

            val counterPartySession = initiateFlow(insuranceProviderParty)

            val fullySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTx, listOf(counterPartySession)))

            return subFlow(FinalityFlow(fullySignedTransaction, listOf(counterPartySession))).id.toString()
        }


        @Suspendable
        fun getProposal(proposalId: String) : StateAndRef<ProductProposal>? {
            val genericQueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val result = builder {
                val idCriteria = ProductProposalSchemaV1.PersistentProductProposalSchema::proposalId.equal(proposalId)

                val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
                val criteria = genericQueryCriteria.and(customCriteria)

                serviceHub.vaultService.queryBy<ProductProposal>(criteria)
            }

            return result.states.firstOrNull()

        }

        @Suspendable
        fun makeProduct(proposal: ProductProposal, insuranceProviderParty: Party) : Product {
            return Product( productId = productId,
                            providerId = proposal.providerId,
                            providerName = insuranceProviderParty.name,
                            forCrop = proposal.forCrop,
                            premiumAmountPerHector = proposal.premiumAmountPerHector,
                            insuredAmountPerHector = proposal.insuredAmountPerHector,
                            productDocHash = proposal.productDocHash,
                            createdDate = LocalDate.now(),
                            expiryDate = proposal.expiryDate,
                            isActive = true,
                            weatherCriteria = proposal.weatherCriteria,
                            participants = listOf(insuranceProviderParty, ourIdentity))
        }
    }


    @InitiatedBy(Create::class)
    class CreateResponder(val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            val signTransactionFlow : SignTransactionFlow = object : SignTransactionFlow(counterPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {

                    requireThat { "Output must contain Product state" using (stx.tx.outputsOfType<Product>().size == 1) }

                    val product = stx.tx.outputsOfType<Product>().single()
                    val regulatoryAuthority = serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name.parse("O=Govt,L=Mumbai,C=IN"))?:throw IllegalArgumentException("Govt Party Not Found")

                    requireThat { "Regulatory Authority must be sender" using (regulatoryAuthority == counterPartySession.counterparty) }
                    requireThat { "Our identity and counter party must be in participants list" using (product.participants.containsAll(listOf(ourIdentity, counterPartySession.counterparty))) }
                }
            }

            val signedTxId = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(counterPartySession, signedTxId))
        }
    }


    @InitiatingFlow
    @StartableByRPC
    class Propose(val proposalId: String,
                  val providerId: Int,
                  val forCrop: String,
                  val primiumAmountPerHector: Int,
                  val insuredAmountPerHector: Int,
                  val productDocHash: String,
                  val expiryDate: LocalDate,
                  val rainCriteria: Map<Int, Int>,
                  val droughtCriteria: Map<Int, Int>): FlowLogic<String>() {

        @Suspendable
        override fun call(): String {

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val regulatoryAuthority = serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name.parse("O=Govt,L=Mumbai,C=IN"))?:throw IllegalArgumentException("Govt Party Not Found")
            val proposal = makeProposal(regulatoryAuthority)

            val command = Command(ProductContract.Commands.Propose(), listOf(ourIdentity.owningKey))
            val txBuilder = TransactionBuilder(notary).
                            addOutputState(proposal, ProductContract.ID).
                            addCommand(command)

            txBuilder.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(txBuilder)
            return subFlow(FinalityFlow(signedTx, initiateFlow(regulatoryAuthority))).id.toString()
        }

        @Suspendable
        fun makeProposal(regulatoryAuthority: Party) : ProductProposal{
                return ProductProposal(proposalId,
                                       providerId,
                                       ourIdentity.name,
                                       forCrop,
                                       primiumAmountPerHector,
                                       insuredAmountPerHector,
                                       productDocHash,
                                       expiryDate,
                                       WeatherCriteria(rainCriteria, droughtCriteria),
                                       listOf(ourIdentity, regulatoryAuthority))
        }
    }


    @InitiatedBy(Propose::class)
    class ProposeResponder(val counterPartySession: FlowSession):FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            subFlow(ReceiveFinalityFlow(counterPartySession))
        }
    }
}