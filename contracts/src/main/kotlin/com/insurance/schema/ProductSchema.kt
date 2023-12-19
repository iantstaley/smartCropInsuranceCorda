package com.insurance.schema

import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object ProductSchema

@CordaSerializable
object ProductSchemaV1 : MappedSchema(ProductSchema.javaClass,1, listOf(PersistentProductSchema::class.java)) {

    @Entity
    @Table(name = "Products")
    class PersistentProductSchema(
            @Column
            val productId:String,
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
            val createdDate: LocalDate = LocalDate.now(),
            @Column
            val expiryDate: LocalDate,
            @Column
            val isActive: Boolean
            ) : PersistentState(){
        constructor():this("",0, "","",0,0,"",LocalDate.now(),LocalDate.now(), false)
    }
}