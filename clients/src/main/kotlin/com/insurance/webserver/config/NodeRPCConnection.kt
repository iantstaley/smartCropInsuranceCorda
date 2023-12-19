package com.insurance.webserver.config

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy


open class NodeRPCConnection(
        private val host: String,
        private val username: String,
        private val password: String,
        private val rpcPort: Int): AutoCloseable {

    lateinit var rpcConnection: CordaRPCConnection
        private set
    lateinit var proxy: CordaRPCOps
        private set

    @PostConstruct
    fun initialiseNodeRPCConnection() {
            val rpcAddress = NetworkHostAndPort(host, rpcPort)
            val rpcClient = CordaRPCClient(rpcAddress)
            val rpcConnection = rpcClient.start(username, password)
            proxy = rpcConnection.proxy
    }

    @PreDestroy
    override fun close() {
        rpcConnection.notifyServerAndClose()
    }
}