package com.insurance.states

import com.insurance.contracts.ProductContract
import com.insurance.schema.ProductProposalSchemaV1
import com.insurance.schema.ProductSchemaV1
import net.corda.core.contracts.BelongsToContract

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.lang.IllegalArgumentException
import java.time.LocalDate

@BelongsToContract(ProductContract::class)
data class ProductProposal(val proposalId:String,
                           val providerId: Int,
                           val providerName: CordaX500Name,
                           val forCrop: String,
                           val premiumAmountPerHector: Int,
                           val insuredAmountPerHector: Int,
                           val productDocHash: String,
                           val expiryDate: LocalDate,
                           val weatherCriteria: WeatherCriteria,
                           override val participants: List<AbstractParty>,
                           override val linearId: UniqueIdentifier = UniqueIdentifier(proposalId)
                   ): LinearState, QueryableState{

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return if (schema is ProductProposalSchemaV1) {
            ProductProposalSchemaV1.PersistentProductProposalSchema(this.proposalId,
                                                    this.providerId,
                                                    this.providerName.toString(),
                                                    this.forCrop,
                                                    this.premiumAmountPerHector,
                                                    this.insuredAmountPerHector,
                                                    this.productDocHash,
                                                    this.expiryDate)
        } else{
            throw IllegalArgumentException("Invalid ClaimDataState Schema")
        }
    }


    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ProductProposalSchemaV1)
}

@CordaSerializable
data class WeatherCriteria(val rainyDaysConditions: Map<Int, Int>, val droughtDayConditions: Map<Int,Int>)