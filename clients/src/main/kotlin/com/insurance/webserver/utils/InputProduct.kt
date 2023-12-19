package com.insurance.webserver.utils

import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.util.*

@CordaSerializable
data class InputProduct(val proposalId:String, val productId: String)