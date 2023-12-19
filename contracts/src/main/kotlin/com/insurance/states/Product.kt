package com.insurance.states

import com.insurance.contracts.ProductContract
import com.insurance.schema.ProductSchemaV1
import net.corda.core.contracts.BelongsToContract

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.lang.IllegalArgumentException
import java.time.LocalDate

@BelongsToContract(ProductContract::class)
data class Product(val productId:String,
                   val providerId: Int,
                   val providerName: CordaX500Name,
                   val forCrop: String,
                   val premiumAmountPerHector: Int,
                   val insuredAmountPerHector: Int,
                   val productDocHash: String,
                   val createdDate: LocalDate = LocalDate.now(),
                   val expiryDate: LocalDate,
                   val weatherCriteria: WeatherCriteria,
                   val isActive: Boolean,
                   override val participants: List<AbstractParty>,
                   override val linearId: UniqueIdentifier = UniqueIdentifier(productId)
                   ) : LinearState, QueryableState{

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return if (schema is ProductSchemaV1) {
            ProductSchemaV1.PersistentProductSchema(this.productId,
                                                    this.providerId,
                                                    this.providerName.toString(),
                                                    this.forCrop,
                                                    this.premiumAmountPerHector,
                                                    this.insuredAmountPerHector,
                                                    this.productDocHash,
                                                    this.createdDate,
                                                    this.expiryDate,
                                                    this.isActive)
        } else{
            throw IllegalArgumentException("Invalid ClaimDataState Schema")
        }
    }


    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ProductSchemaV1)
}
