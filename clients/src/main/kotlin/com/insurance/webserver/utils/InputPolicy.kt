package com.insurance.webserver.utils

data class InputPolicy(val policyId: String,
                       val farmerId: String,
                       val productId: String,
                       val latitude: Double,
                       val longitude: Double,
                       val areaInHector: Double)