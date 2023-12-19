package com.insurance.flows

import co.paralleluniverse.fibers.Suspendable
import com.insurance.contracts.PolicyContract
import com.insurance.oracles.WeatherOracle
import com.insurance.schema.PolicySchemav1
import com.insurance.schema.ProductSchemaV1
import com.insurance.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import java.time.Instant
import java.time.LocalDate
import java.lang.IllegalArgumentException
import java.util.function.Predicate

object PolicyFlow{

    @StartableByRPC
    @InitiatingFlow
    class Create(val policyId: String,
                 val farmerId: String,
                 val productId: String,
                 val latitude: Double,
                 val longitude: Double,
                 val areaInHector: Double) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val product = getProduct(productId)?:throw IllegalArgumentException("Given Product ID does not exist")
            val policy = createPolicy(product = product.state.data)

            val createCommand = Command(PolicyContract.Commands.Create(), product.state.data.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary).
                            addOutputState(policy, PolicyContract.ID).
                            addCommand(createCommand).
                            addReferenceState(product.referenced())

            val partiallySignedTx = serviceHub.signInitialTransaction(txBuilder)
            txBuilder.verify(serviceHub)

            val counterParties = (policy.participants - ourIdentity).map { it.nameOrNull()}.map { serviceHub.networkMapCache.getPeerByLegalName(it?:throw java.lang.IllegalArgumentException("Party not found")) }
            val counterPartySessions = counterParties.map { initiateFlow(it?:throw IllegalArgumentException("unknown party for flow session")) }
            val fullySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTx, counterPartySessions))

            return  subFlow(FinalityFlow(fullySignedTransaction, counterPartySessions)).id.toString()
        }

        @Suspendable
        fun getProduct(productId: String) : StateAndRef<Product>? {
            val genericQueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val result = builder {
                val idCriteria = ProductSchemaV1.PersistentProductSchema::productId.equal(productId)
                val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
                val criteria = genericQueryCriteria.and(customCriteria)
                serviceHub.vaultService.queryBy<Product>(criteria)
            }


            return result.states.singleOrNull()
        }

        @Suspendable
        fun createPolicy(product: Product) : Policy {
            return Policy( policyId =policyId,
                    farmerId = farmerId,
                    productId = productId,
                    providerName = product.providerName,
                    forCrop = product.forCrop,
                    latitude = latitude,
                    longitude = longitude,
                    areaInHector = areaInHector,
                    totalPremium = areaInHector * product.premiumAmountPerHector,
                    insuredAmount = areaInHector * product.insuredAmountPerHector,
                    startDate = LocalDate.now(),
                    expiryDate = product.expiryDate,
                    settlementPaidAmountTotal = 0.0,
                    lastSettlementPaidDate = LocalDate.now(),
                    totalPercentage = 0.0,
                    status = PolicyStatus.CREATED,
                    participants = product.participants,
                    nextActivityTime = Instant.now().plusSeconds(15),
                    autoClaimDetails = mutableListOf(AutoClaimDetails(Instant.now(),0, 0, 0, 0.0)),
                    manualClaimDetails = null)
        }


        @InitiatedBy(Create::class)
        class CreateResponder(val counterPartySession: FlowSession): FlowLogic<Unit>() {

            @Suspendable
            override fun call() {
                val signTransactionFlow : SignTransactionFlow = object : SignTransactionFlow(counterPartySession) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat{

                        requireThat { "Transaction must have only one referenced state" using (stx.tx.references.size == 1) }

                        val referencedProduct = stx.tx.references.single()
                        val product = serviceHub.validatedTransactions.getTransaction(referencedProduct.txhash)?.toLedgerTransaction(serviceHub)?.outputsOfType<Product>()?:throw IllegalArgumentException("Referenced state doen not have product state")
                        requireThat { "Referenced state must have product state" using (product.isNotEmpty()) }

                        requireThat { "Our Identity must be participants list" using (stx.tx.outputsOfType<Policy>().single().participants.contains(ourIdentity)) }
                        requireThat { "Only one Policy should be created" using (stx.tx.outputsOfType<Policy>().size == 1) }

                    }
                }

                val idOfSignedTx = subFlow(signTransactionFlow).id

                subFlow(ReceiveFinalityFlow(counterPartySession,idOfSignedTx))
            }
        }
    }

    @InitiatingFlow
    @StartableByRPC
    @SchedulableFlow
    class RunAutoClaim(val stateRef: StateRef) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            println("I am invoked by schedulable at: ${LocalDate.now()}" )

            val currentPolicy = serviceHub.toStateAndRef<Policy>(stateRef)

            if (currentPolicy.state.data.providerName != ourIdentity.name) return

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val oracle = oracle()
            val weatherOracleService = serviceHub.cordaService(WeatherOracle::class.java)

            val currentPolicyState = currentPolicy.state.data

            val (repetitiveRainDays, repetitiveDroughtDays) = weatherOracleService.getWeatherReport(currentPolicyState.latitude.toString(), currentPolicyState.longitude.toString(), currentPolicyState.startDate.toString(), currentPolicyState.expiryDate.toString())

            var referencedProduct = getProduct(currentPolicyState.productId)?:throw IllegalArgumentException("Referenced product is not found")
            val outputPolicy = outputPolicy(currentPolicyState, referencedProduct.state.data, repetitiveRainDays, repetitiveDroughtDays)
            val autoClaimCommand = Command(PolicyContract.Commands.AutoClaim(currentPolicyState.latitude.toString(), currentPolicyState.longitude.toString(), currentPolicyState.startDate.toString(), currentPolicyState.expiryDate.toString(), repetitiveRainDays, repetitiveDroughtDays), (currentPolicyState.participants + oracle ).map { it.owningKey })

            val txBuilder = TransactionBuilder(notary).
                    addInputState(currentPolicy).
                    addOutputState(outputPolicy,PolicyContract.ID).
                    addReferenceState(referencedProduct.referenced()).
                    addCommand(autoClaimCommand)

            txBuilder.verify(serviceHub)

            val signedTransaction = serviceHub.signInitialTransaction(txBuilder)
            val counterParties = (currentPolicyState.participants - ourIdentity).map { it.nameOrNull()}.map { serviceHub.networkMapCache.getPeerByLegalName(it?:throw java.lang.IllegalArgumentException("Party not found")) }.map { it?:throw IllegalArgumentException("Unknown party") }
            val counterPartySessions = counterParties.map { initiateFlow(it?:throw IllegalArgumentException("unknown party for flow session")) }
            val partiallySignedTransaction = getParticipantsSignatures(signedTransaction, counterParties.single())
            val fullySignedTransaction = getOracleSignature(partiallySignedTransaction, oracle)

            subFlow(FinalityFlow(fullySignedTransaction, counterPartySessions))
        }

        @Suspendable
        private fun getParticipantsSignatures(signedTransaction: SignedTransaction, party: Party) : SignedTransaction {
            val signature = subFlow(CollectSignatureFlow(signedTransaction, initiateFlow(party), party.owningKey)).single()
            return signedTransaction.withAdditionalSignature(signature)
        }

        private fun oracle() : Party = serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name.parse("O=Oracle,L=Pune,C=IN"))?:throw IllegalArgumentException("Oracle party not found")

        @Suspendable
        private fun getOracleSignature(transaction: SignedTransaction, oracle: Party) :SignedTransaction {
            println("Getting oracle signature")
            val filteredTransaction = transaction.buildFilteredTransaction(Predicate {
                when (it) {
                    is Command<*> -> oracle.owningKey in it.signers && it.value is PolicyContract.Commands.AutoClaim
                    else -> false
                }
            })

            val signature = subFlow(CollectOracleSignatureFlow(filteredTransaction, oracle))
            return transaction.withAdditionalSignature(signature)
        }

        private fun outputPolicy(inputPolicy: Policy, product: Product, repetitiveRainDays: Int, repetitiveDroughtDays: Int) : Policy {

            var autoClaimDetails = mutableListOf<AutoClaimDetails>()
            autoClaimDetails.addAll(inputPolicy.autoClaimDetails)
            val percentage = getPercentage(product, repetitiveRainDays, repetitiveDroughtDays)
            return if (percentage > 0) {
                autoClaimDetails.add(AutoClaimDetails(Instant.now(),repetitiveRainDays, repetitiveDroughtDays, percentage, (inputPolicy.insuredAmount*percentage)/100))
                inputPolicy.copy(settlementPaidAmountTotal = inputPolicy.settlementPaidAmountTotal + (inputPolicy.insuredAmount*percentage)/100,
                        lastSettlementPaidDate = LocalDate.now(),
                        nextActivityTime = Instant.now().plusSeconds(15),
                        autoClaimDetails = autoClaimDetails,
                        totalPercentage = inputPolicy.totalPercentage + percentage)
            } else {
                autoClaimDetails.add(AutoClaimDetails(Instant.now(),repetitiveRainDays, repetitiveDroughtDays, percentage, 0.0))
                inputPolicy.copy(nextActivityTime = Instant.now().plusSeconds(15),
                        autoClaimDetails = autoClaimDetails)
            }
        }

        private fun getPercentage(product: Product, repetitiveRainDays: Int, repetitiveDroughtDays: Int) : Int{

            var percentage = 0
            for (i in repetitiveRainDays downTo 1) {
                if (product.weatherCriteria.rainyDaysConditions.containsKey(i)) {
                    percentage += product.weatherCriteria.rainyDaysConditions.getValue(i)
                    break
                }
            }

            for (i in repetitiveDroughtDays downTo 1) {
                if (product.weatherCriteria.droughtDayConditions.containsKey(i)) {
                    percentage += product.weatherCriteria.droughtDayConditions.getValue(i)
                    break
                }
            }

            return percentage
        }

        private fun getProduct(productId: String) : StateAndRef<Product>? {
            return builder {
                val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                val idCriteria = ProductSchemaV1.PersistentProductSchema::productId.equal(productId)
                val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
                val criteria = generalCriteria.and(customCriteria)
                serviceHub.vaultService.queryBy<Product>(criteria)
            }.states.map { it }.singleOrNull()
        }
    }

    @InitiatedBy(RunAutoClaim::class)
    class RunAutoClaimResponder(val counterPartySession: FlowSession) : FlowLogic<Unit>(){

        @Suspendable
        override fun call() {
            val signTransactionFlow : SignTransactionFlow = object : SignTransactionFlow(counterPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat{
                    println("RunAutoClaimResponder")
                    requireThat { "Transaction must have only one referenced state" using (stx.tx.references.size == 1) }

                    val referencedProduct = stx.tx.references.single()
                    val product = serviceHub.validatedTransactions.getTransaction(referencedProduct.txhash)?.toLedgerTransaction(serviceHub)?.outputsOfType<Product>()?:throw IllegalArgumentException("Referenced state doen not have product state")
                    requireThat { "Referenced state must have product state" using (product.isNotEmpty()) }

                    requireThat { "Our Identity must be participants list" using (stx.tx.outputsOfType<Policy>().single().participants.contains(ourIdentity)) }
                    requireThat { "Only one Policy should be created" using (stx.tx.outputsOfType<Policy>().size == 1) }
                }
            }

            val idOfSignedTx = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(counterPartySession,idOfSignedTx))
        }
    }

    @InitiatingFlow
    class CollectOracleSignatureFlow(private val filteredTransaction: FilteredTransaction, private val oracle: Party) : FlowLogic<TransactionSignature>() {

        @Suspendable
        override fun call(): TransactionSignature {
            println("Inside CollectOracleSignatureFlow")
            return initiateFlow(oracle).sendAndReceive<TransactionSignature>(filteredTransaction).unwrap { it}
        }
    }


    @InitiatedBy(CollectOracleSignatureFlow::class)
    class CollectOracleSignatureResponderFlow(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {

        private companion object {
            val log = loggerFor<CollectOracleSignatureResponderFlow>()
        }

        @Suspendable
        override fun call() {
            val filteredTransaction = otherPartySession.receive<FilteredTransaction>().unwrap { it }
            val valid = filteredTransaction.checkWithFun { element : Any ->
                when {
                    element is Command<*> && element.value is PolicyContract.Commands.AutoClaim ->{
                        val command = element.value as PolicyContract.Commands.AutoClaim
                        println("Command: $command")
                        (ourIdentity.owningKey in element.signers).also {
                            validateWeatherInfo(command)
                        }
                    }
                    else -> {
                        log.info("Transaction: ${filteredTransaction.id} is invalid")
                        false
                    }
                }
            }

            if (valid) {
                log.info("Transaction: ${filteredTransaction.id} is valid, signing with oracle key")
                otherPartySession.send(serviceHub.createSignature(filteredTransaction, ourIdentity.owningKey))
            } else {
                log.error("Transaction: ${filteredTransaction.id} is invalid")
                throw FlowException("Transaction: ${filteredTransaction.id} is invalid")
            }
        }

        private fun validateWeatherInfo(autoClaim: PolicyContract.Commands.AutoClaim) = try {
                serviceHub.cordaService(WeatherOracle::class.java).verifyWeatherReport(autoClaim.latitude,
                        autoClaim.longitude, autoClaim.startDate, autoClaim.endDate, autoClaim.repetitiveRainDays, autoClaim.repetitiveDraughtDays)
            } catch ( e: IllegalArgumentException) {
                throw FlowException(e.message)
            }
        }

    @StartableByRPC
    @InitiatingFlow
    class ManualClaim(val policyId: String,  val cropDamagePercentage: Double, val reasonOfDamage: String) :FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            val policyStateAndRef = getPolicy(policyId)?:throw FlowException("Policy Id does not exist in system")
            val inputPolicy = policyStateAndRef.state.data
            val referencedProduct = getProduct(inputPolicy.productId)?: throw FlowException("Referenced Product does not exist")
            val outputPolicy = getOutputPolicy(inputPolicy)
            val manualClaimCommand = Command(PolicyContract.Commands.ManualClaim(), inputPolicy.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary()).
                    addReferenceState(referencedProduct.referenced()).
                    addInputState(policyStateAndRef).
                    addOutputState(outputPolicy, PolicyContract.ID).
                    addCommand(manualClaimCommand)

            txBuilder.verify(serviceHub)

            val partiallySignedTransaction = serviceHub.signInitialTransaction(txBuilder)

            val counterParties = (inputPolicy.participants - ourIdentity).map { it.nameOrNull()}.map { serviceHub.networkMapCache.getPeerByLegalName(it?:throw java.lang.IllegalArgumentException("Party not found")) }.map { it?:throw IllegalArgumentException("Unknown party") }
            val counterPartySessions = counterParties.map { initiateFlow(it?:throw IllegalArgumentException("unknown party for flow session")) }

            val fullySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction,counterPartySessions))

            return subFlow(FinalityFlow(fullySignedTransaction, counterPartySessions)).id.toString()
        }

        private fun notary() = serviceHub.networkMapCache.notaryIdentities.first()
        private fun oracle() = serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name.parse("O=Oracle,L=Pune,C=IN"))?:throw IllegalArgumentException("Oracle party not found")
        private fun getProduct(productId: String) : StateAndRef<Product>? {
            return builder {
                val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                val idCriteria = ProductSchemaV1.PersistentProductSchema::productId.equal(productId)
                val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
                val criteria = generalCriteria.and(customCriteria)
                serviceHub.vaultService.queryBy<Product>(criteria)
            }.states.map { it }.singleOrNull()
        }

        private fun getPolicy(policyId: String) : StateAndRef<Policy>? {
            return builder {
                val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                val idCriteria = PolicySchemav1.PersistentPolicySchema::policyId.equal(policyId)
                val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
                val criteria = generalCriteria.and(customCriteria)
                serviceHub.vaultService.queryBy<Policy>(criteria)
            }.states.map { it }.singleOrNull()
        }

        private fun getOutputPolicy(inputPolicy: Policy) =
            inputPolicy.copy(manualClaimDetails = ManualClaimDetails(Instant.now(), cropDamagePercentage, (inputPolicy.insuredAmount * cropDamagePercentage)/100, reasonOfDamage),
                    totalPercentage = inputPolicy.totalPercentage + cropDamagePercentage,
                    settlementPaidAmountTotal = inputPolicy.settlementPaidAmountTotal + ((inputPolicy.insuredAmount * cropDamagePercentage)/100),
                    lastSettlementPaidDate = LocalDate.now())

    }


    @InitiatedBy(ManualClaim::class)
    class ManualClaimResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val signTransactionFlow : SignTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    requireThat { "Transaction must have only one referenced state" using (stx.tx.references.size == 1) }

                    val referencedProduct = stx.tx.references.single()
                    val product = serviceHub.validatedTransactions.getTransaction(referencedProduct.txhash)?.toLedgerTransaction(serviceHub)?.outputsOfType<Product>()?:throw IllegalArgumentException("Referenced state doen not have product state")
                    requireThat { "Referenced state must have product state" using (product.isNotEmpty()) }

                    requireThat { "Our Identity must be participants list" using (stx.tx.outputsOfType<Policy>().single().participants.contains(ourIdentity)) }
                    requireThat { "Only one Policy should be created" using (stx.tx.outputsOfType<Policy>().size == 1) }
                }
            }

            val signedTransactionId = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(otherPartySession,signedTransactionId))
        }
    }
}