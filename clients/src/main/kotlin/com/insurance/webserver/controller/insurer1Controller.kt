package com.insurance.webserver.controller

import com.insurance.flows.PolicyFlow
import com.insurance.flows.ProductFlow
import com.insurance.schema.PolicySchemav1
import com.insurance.schema.ProductProposalSchemaV1
import com.insurance.schema.ProductSchemaV1
import com.insurance.states.Policy
import com.insurance.states.Product
import com.insurance.states.ProductProposal
import com.insurance.webserver.config.NodeRPCConnection
import com.insurance.webserver.utils.InputPolicy
import com.insurance.webserver.utils.InputProduct
import com.insurance.webserver.utils.ManualClaim
import com.insurance.webserver.utils.ProductProposalInput
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.*

/**
 * Define your API endpoints here.
 */
@RestController("insurer1Controller")
@RequestMapping("/insurer1") // The paths for HTTP requests are relative to this base path.
class insurer1Controller(@Qualifier("insurer1Connection") rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @GetMapping(value = ["/templateendpoint"], produces = ["text/plain"])
    private fun templateendpoint(): String {
        return "Define an endpoint here."
    }

    @PostMapping("/proposal")
    private fun createProposal(@RequestBody proposal: ProductProposalInput) : String {
        if (proposal.rainCriteria.isEmpty()) throw IllegalArgumentException("Rain criteria is not mentioned in proposal")
        if (proposal.droughtCriteria.isEmpty()) throw IllegalArgumentException("Drought criteria is not mentioned in proposal")
        println("Rain Criteria: ${proposal.rainCriteria}")
        println("Drought Criteria: ${proposal.droughtCriteria}")

        return proxy.startFlowDynamic(ProductFlow.Propose::class.java,
                proposal.proposalId,
                proposal.providerId,
                proposal.forCrop,
                proposal.premiumAmountPerHector,
                proposal.insuredAmountPerHector,
                proposal.productDocHash,
                proposal.expiryDate,
                proposal.rainCriteria,
                proposal.droughtCriteria).returnValue.get().toString()
    }

    @GetMapping("/proposal/{proposalId}")
    private fun getProposal(@PathVariable("proposalId") proposalId: String) : List<ProductProposal> {

        var result = builder {
            val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
            val idCriteria = ProductProposalSchemaV1.PersistentProductProposalSchema::proposalId.equal(proposalId)

            val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
            val criteria = generalCriteria.and(customCriteria)
            proxy.vaultQueryBy<ProductProposal>(criteria)
        }

        return result.states.map { it.state.data }
    }

    @PostMapping("/product")
    private fun addProduct(@RequestBody productDetails: InputProduct) : String {
        return proxy.startFlowDynamic(ProductFlow.Create::class.java,
                                        productDetails.proposalId,
                                        productDetails.productId).returnValue.get().toString()
    }

    @GetMapping("/products")
    private fun getProducts() : List<Product> {
        var products = proxy.vaultQueryByCriteria(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), Product::class.java).states.map { it.state.data }
        println(products)
        return products
    }

    @GetMapping("/products/{productId}")
    private fun getProductById(@PathVariable("productId") productId: Int) : List<Product> {
        var result = builder {
            val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val idCriteria = ProductSchemaV1.PersistentProductSchema::productId.equal(productId)
            val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
            val criteria = generalCriteria.and(customCriteria)
            proxy.vaultQueryBy<Product>(criteria)
        }

        return result.states.map { it.state.data }
    }

    @PostMapping("/policy")
    private fun createPolicy(@RequestBody policy: InputPolicy) : String {
        return proxy.startFlowDynamic(PolicyFlow.Create::class.java,
                policy.policyId,
                policy.farmerId,
                policy.productId,
                policy.latitude,
                policy.longitude,
                policy.areaInHector).returnValue.get().toString()
    }

    @PostMapping("/policy/manualClaim")
    private fun processManualClaim(@RequestBody manualClaim: ManualClaim) : String {
        return proxy.startFlowDynamic(PolicyFlow.ManualClaim::class.java,
                manualClaim.policyId,
                manualClaim.cropDamagePercentage,
                manualClaim.reasonOfDamage).returnValue.get().toString()
    }


    @GetMapping("/policy/{policyId}")
    private fun getPolicy(@PathVariable("policyId") policyId: String) : List<Policy> {
        var result = builder {
            val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val idCriteria = PolicySchemav1.PersistentPolicySchema::policyId.equal(policyId)
            val customCriteria = QueryCriteria.VaultCustomQueryCriteria(idCriteria)
            val criteria = generalCriteria.and(customCriteria)
            proxy.vaultQueryBy<Policy>(criteria)
        }

        println(result.states)
        return result.states.map { it.state.data }
    }

    @GetMapping("/policies")
    private fun getPolicies() : List<Policy> {
        return proxy.vaultQueryByCriteria(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), Policy::class.java).states.map { it.state.data }
    }
}