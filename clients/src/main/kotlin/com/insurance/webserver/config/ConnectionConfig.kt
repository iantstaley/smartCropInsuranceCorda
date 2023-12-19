package com.insurance.webserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
open class ConnectionConfig {

    @Bean
    open fun govtConnection() : NodeRPCConnection {
        return NodeRPCConnection("localhost", "user1", "test", 10006)
    }

    @Bean
    open fun insurer1Connection() : NodeRPCConnection {
        return NodeRPCConnection("localhost", "user1", "test", 10009)
    }

}