package com.insurance.webserver.utils

import com.insurance.states.WeatherCriteria
import java.time.LocalDate

data class ProductProposalInput(val proposalId: String,
                                val providerId: Int,
                                val forCrop: String,
                                val premiumAmountPerHector: Int,
                                val insuredAmountPerHector: Int,
                                val productDocHash: String,
                                val expiryDate: LocalDate,
                                val rainCriteria: Map<Int, Int>,
                                val droughtCriteria: Map<Int, Int>)