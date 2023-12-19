package com.insurance.schema

import com.insurance.states.PolicyStatus
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object PolicySchema

@CordaSerializable
object PolicySchemav1: MappedSchema(PolicySchema::class.java,1, listOf(PersistentPolicySchema::class.java)) {

    @Entity
    @Table(name = "Policies")
    class PersistentPolicySchema(
            @Column
            val policyId: String,
            @Column
            val farmerId: String,
            @Column
            val productId: String,
            @Column
            val providerName: String,
            @Column
            val forCrop: String,
            @Column
            val latitude: Double,
            @Column
            val longitude: Double,
            @Column
            val areaInHector: Double,
            @Column
            val totalPremium: Double,
            @Column
            val insuredAmount: Double,
            @Column
            val startDate: LocalDate = LocalDate.now(),
            @Column
            val expiryDate: LocalDate,
            @Column
            val settlementPaidAmountTotal: Double,
            @Column
            val lastSettlementPaidDate: LocalDate,
            @Column
            val status: PolicyStatus
            ) : PersistentState() {
        constructor() : this("",
                "",
                "",
                "",
                "",
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                LocalDate.now(),
                LocalDate.now(),
                0.0,
                LocalDate.now(),
                PolicyStatus.CREATED)
    }
}