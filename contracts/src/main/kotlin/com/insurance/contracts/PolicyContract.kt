package com.insurance.contracts

import com.insurance.states.Policy
import com.insurance.states.PolicyStatus
import com.insurance.states.Product
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class PolicyContract: Contract{
    companion object {
        const val ID: String = "com.insurance.contracts.PolicyContract"
    }

    override fun verify(tx: LedgerTransaction) {

        requireThat { "Transaction must contain at least one command" using (tx.commandsOfType<Commands>().isNotEmpty()) }

        val command = tx.commandsOfType<Commands>().first()

        when (command.value) {

            is Commands.Create -> {
                requireThat { "Create Policy must have single referenced product" using (tx.referenceInputRefsOfType<Product>().size == 1) }
                requireThat { "Create Policy must have only one output state" using (tx.outputStates.size == 1) }
                requireThat { "Create Policy must have only one Policy output state" using (tx.outputsOfType<Policy>().size == 1) }

                val product = tx.referenceInputRefsOfType<Product>().first().state.data
                val policy = tx.outputsOfType<Policy>().first()

                requireThat { "Referenced product must be active" using (product.isActive) }
//                requireThat { "Initial policy status must be CREATED" using (policy.status == PolicyStatus.CREATED) }
                requireThat { "Policy total premium amount must be correct" using (policy.totalPremium == policy.areaInHector * product.premiumAmountPerHector) }
                requireThat { "Policy insured amount must be correct" using (policy.insuredAmount == policy.areaInHector * product.insuredAmountPerHector) }
                requireThat { "Insured amount must be greater than total premium paid" using (policy.insuredAmount > policy.totalPremium ) }
                requireThat { "settlementPaidAmountTotal must be 0 while creating new policy" using (policy.settlementPaidAmountTotal == 0.0)}
                requireThat { "Total percentage must be 0 while creating the new policy" using (policy.totalPercentage == 0.0)}
                requireThat { "New policy must have CREATED status" using (policy.status == PolicyStatus.CREATED)}

                requireThat { "Govt regulator and Insurance provider should be the required signer" using ( policy.participants.map { it.owningKey }.containsAll(command.signers)) }
//                requireThat { "Govt regulator and Insurance provider should be the required signer" using (command.signers.toSet() == setOf(policy.participants.map { it.owningKey })) }
            }

            is Commands.AutoClaim -> {
                requireThat { "Auto claim Policy must have single referenced product" using (tx.referenceInputRefsOfType<Product>().size == 1) }
                requireThat { "Policy Auto Claim must have only one output state" using (tx.outputStates.size == 1) }
                requireThat { "Auto Claim Policy must have only one Policy output state" using (tx.outputsOfType<Policy>().size == 1) }
                requireThat { "Auto claim policy must have single input policy" }

                val product = tx.referenceInputRefsOfType<Product>().first().state.data
                val inputPolicy = tx.inputsOfType<Policy>().single()
                val policy = tx.outputsOfType<Policy>().first()

                requireThat { "Referenced product and policy product must be same" using (product.productId == policy.productId) }
                requireThat { "Provider of Referenced product and policy product must be same" using (product.providerName == policy.providerName) }

                val autoClaimDetails = tx.commandsOfType<Commands.AutoClaim>().single().value
                val percentage = getPercentage(product, autoClaimDetails.repetitiveRainDays, autoClaimDetails.repetitiveDraughtDays)
                requireThat { "Calculated percentage and tx percentage must be same" using (policy.autoClaimDetails.last().percentage == percentage) }

                val txTotalPercentage = policy.totalPercentage
                val lastPercentage = inputPolicy.totalPercentage
                requireThat { "Output policy total percentage must be equal to (last percentage + current percentage)" using (txTotalPercentage == lastPercentage + percentage) }
                requireThat { "Total percentage should be less than or equal to 100" using (txTotalPercentage <= 100) }
                requireThat { "Settlement amount should be correct" using (policy.settlementPaidAmountTotal == ((inputPolicy.insuredAmount * txTotalPercentage/100))) }

                 requireThat { "There should be total 3 signers" using (command.signers.size == 3) }
            }

            is Commands.ManualClaim -> {
                requireThat { "Auto claim Policy must have single referenced product" using (tx.referenceInputRefsOfType<Product>().size == 1) }
                requireThat { "Policy Auto Claim must have only one output state" using (tx.outputStates.size == 1) }
                requireThat { "Auto Claim Policy must have only one Policy output state" using (tx.outputsOfType<Policy>().size == 1) }
                requireThat { "Auto claim policy must have single input policy" }

                val product = tx.referenceInputRefsOfType<Product>().first().state.data
                val inputPolicy = tx.inputsOfType<Policy>().single()
                val policy = tx.outputsOfType<Policy>().first()

                requireThat { "Referenced product and policy product must be same" using (product.productId == policy.productId) }
                requireThat { "Provider of Referenced product and policy product must be same" using (product.providerName == policy.providerName) }

                val txTotalPercentage = policy.totalPercentage
                val lastPercentage = inputPolicy.totalPercentage
                requireThat { "Output policy total percentage must be equal to (last percentage + manual claim percentage)" using (txTotalPercentage == lastPercentage + policy.manualClaimDetails!!.cropDamagePercentage) }
                requireThat { "Total percentage should be less than or equal to 100" using (txTotalPercentage <= 100) }
                requireThat { "Settlement amount should be correct" using (policy.settlementPaidAmountTotal == ((inputPolicy.insuredAmount * txTotalPercentage/100))) }

                requireThat { "Govt regulator and Insurance provider should be the required signer" using ( policy.participants.map { it.owningKey }.containsAll(command.signers)) }
            }

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

    interface Commands:CommandData{
        class Create : TypeOnlyCommandData(), Commands
        data class AutoClaim(val latitude: String, val longitude: String, val startDate: String, val endDate: String, val repetitiveRainDays: Int, val repetitiveDraughtDays: Int) : TypeOnlyCommandData(), Commands
        class ManualClaim : TypeOnlyCommandData(), Commands
    }
}