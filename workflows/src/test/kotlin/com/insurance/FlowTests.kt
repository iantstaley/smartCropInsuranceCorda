//package com.insurance
//
//import com.insurance.flows.Responder
//import net.corda.testing.node.MockNetwork
//import net.corda.testing.node.MockNetworkParameters
//import net.corda.testing.node.TestCordapp
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//
//class FlowTests {
//    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
//        TestCordapp.findCordapp("com.insurance.contracts"),
//        TestCordapp.findCordapp("com.insurance.flows")
//    )))
//    private val a = network.createNode()
//    private val b = network.createNode()
//
//    init {
//        listOf(a, b).forEach {
//            it.registerInitiatedFlow(Responder::class.java)
//        }
//    }
//
//    @Before
//    fun setup() = network.runNetwork()
//
//    @After
//    fun tearDown() = network.stopNodes()
//
//    @Test
//    fun `dummy test`() {
//
//    }
//}