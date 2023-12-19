package com.insurance.states

import com.insurance.contracts.PolicyContract
import com.insurance.schema.PolicySchemav1
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.time.Instant
import java.time.LocalDate

@BelongsToContract(PolicyContract::class)
data class Policy(val policyId: String,
                  val farmerId: String,
                  val productId: String,
                  val providerName: CordaX500Name,
                  val forCrop: String,
                  val latitude: Double,
                  val longitude: Double,
                  val areaInHector: Double,
                  val totalPremium: Double,
                  val insuredAmount: Double,
                  val startDate: LocalDate = LocalDate.now(),
                  val expiryDate: LocalDate,
                  val settlementPaidAmountTotal: Double,
                  val lastSettlementPaidDate: LocalDate,
                  val totalPercentage: Double,
                  val status: PolicyStatus,
                  val autoClaimDetails: MutableList<AutoClaimDetails>,
                  val manualClaimDetails: ManualClaimDetails?,
                  val nextActivityTime: Instant,
                  override val linearId: UniqueIdentifier = UniqueIdentifier(policyId),
                  override val participants: List<AbstractParty> = listOf()) : LinearState, QueryableState, SchedulableState{

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
       println("Next scheduled activity: ${this.nextActivityTime}")
        return ScheduledActivity(flowLogicRefFactory.create("com.insurance.flows.PolicyFlow\$RunAutoClaim", thisStateRef), nextActivityTime)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return if (schema is PolicySchemav1) {
            PolicySchemav1.PersistentPolicySchema(  policyId = policyId,
                                                    farmerId = farmerId,
                                                    productId = productId,
                                                    providerName = providerName.toString(),
                                                    forCrop = forCrop,
                                                    latitude = latitude,
                                                    longitude = longitude,
                                                    areaInHector = areaInHector,
                                                    totalPremium = totalPremium,
                                                    insuredAmount = insuredAmount,
                                                    startDate = startDate,
                                                    expiryDate = expiryDate,
                                                    lastSettlementPaidDate = lastSettlementPaidDate,
                                                    settlementPaidAmountTotal = settlementPaidAmountTotal,
                                                    status = status)
        } else {
            throw IllegalArgumentException("Policy Schema not found")
        }

    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(PolicySchemav1)
}

@CordaSerializable
data class AutoClaimDetails(val dateTime: Instant, val repetitiveRainDays: Int, val repetitiveDraughtDays: Int, val percentage: Int, val paidAmount:Double)

@CordaSerializable
data class ManualClaimDetails(val dateTime: Instant, val cropDamagePercentage: Double, val paidAmount: Double, val reasonOfDamage: String)

@CordaSerializable
enum class PolicyStatus{
    CREATED,
    AUTOCLAIM,
    MANUALCLAIM,
    EXPIRED
}