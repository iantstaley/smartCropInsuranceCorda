package com.insurance.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object ProductProposalSchema

@CordaSerializable
object ProductProposalSchemaV1 : MappedSchema(ProductProposalSchema.javaClass,1, listOf(PersistentProductProposalSchema::class.java)) {

    @Entity
    @Table(name = "Products")
    class PersistentProductProposalSchema(
            @Column
            val proposalId:String,
            @Column
            val providerId: Int,
            @Column
            val providerName: String,
            @Column
            val forCrop: String,
            @Column
            val premiumAmountPerHector: Int,
            @Column
            val insuredAmountPerHector: Int,
            @Column
            val productDocHash: String,
            @Column
            val expiryDate: LocalDate
            ) : PersistentState(){
        constructor():this("",0, "","",0,0,"",LocalDate.now())
    }
}