package com.insurance.contracts

import com.insurance.states.Product
import com.insurance.states.ProductProposal
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class ProductContract: Contract{
    companion object {
        const val ID: String = "com.insurance.contracts.ProductContract"
    }

    override fun verify(tx: LedgerTransaction) {
        requireThat { "Transaction must contain at least one product command" using (tx.commandsOfType<Commands>().isNotEmpty()) }

        val command = tx.commandsOfType<Commands>().first()

        when (command.value) {
            is Commands.Create -> {
                requireThat { "Transaction should have only one input state" using (tx.inputs.size == 1) }
                requireThat { "Transaction must have single Product Proposal as a Input state" using (tx.inputsOfType<ProductProposal>().size == 1) }
                requireThat { "Transaction should contain only one output state" using (tx.outputs.size == 1) }
                requireThat { "Transaction should have only on product state" using (tx.outputsOfType<Product>().size == 1) }

                val product = tx.outputsOfType<Product>().single()

                requireThat { "Created product must be active" using (product.isActive) }
                requireThat { "Insured amount per hector must be greater than premium amount" using (product.insuredAmountPerHector > product.premiumAmountPerHector) }
                requireThat { "Product expiry date should be greater than start date" using (product.expiryDate > product.createdDate)}

                requireThat { "Govt and Insurance provider must be required signer in Product creation" using (product.participants.map { it.owningKey }.containsAll(command.signers)) }
            }

            is Commands.Propose -> {
                requireThat { "Transaction should not have only one input state" using (tx.inputs.size == 1) }
                requireThat { "Transaction should contain only one output state" using (tx.outputs.size == 1) }
                val proposal = tx.outputsOfType<ProductProposal>().single()
                requireThat { "Insured amount per hector must be greater than premium amount" using (proposal.insuredAmountPerHector > proposal.premiumAmountPerHector) }
                requireThat { "Insurance provider must be required signer in proposal creation" using (proposal.participants.map { it.owningKey }.containsAll(command.signers)) }
            }
        }
    }

    interface Commands:CommandData{
        class Create : TypeOnlyCommandData(), Commands
        class Propose : TypeOnlyCommandData(), Commands
    }
}