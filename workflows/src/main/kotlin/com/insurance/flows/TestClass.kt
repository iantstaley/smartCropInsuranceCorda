package com.insurance.flows

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request

fun main(args: Array<String>) {

    val client = OkHttpClient()
    val mapper = ObjectMapper()

    try {
        val url = Request.Builder().url("http://api.worldweatheronline.com/premium/v1/past-weather.ashx?q=18.5204,73.8567&date=2020-01-01&tp=24&enddate=2020-01-10&format=json&key=51ac31893d6f486b85860801201301").build()
        val rootNode = mapper.readTree(client.newCall(url).execute().body()?.let { it.string() })

        var codes = mutableListOf<Int>()
        rootNode.path("data").path("weather").forEach {
            codes.add(it.path("hourly")[0].path("weatherCode").asInt())
        }

        var repetitiveRainDaysCounter = 0
        var repetitiveDraughtDaysCounter = 0
        var repetitiveRainDays = mutableListOf<Int>()
        var repetitiveDraughtDays = mutableListOf<Int>()

        codes.forEach {
            if (it > 299) {
                repetitiveRainDaysCounter++
            } else {
                if (repetitiveRainDaysCounter > 4) repetitiveRainDays.add(repetitiveRainDaysCounter)
                repetitiveRainDaysCounter = 0
            }

            if ( it < 150) {
                repetitiveDraughtDaysCounter++
            } else {
                if (repetitiveDraughtDaysCounter > 4) repetitiveDraughtDays.add(repetitiveDraughtDaysCounter)
                repetitiveDraughtDaysCounter = 0
            }
        }

        if (repetitiveDraughtDaysCounter > 4) repetitiveDraughtDays.add(repetitiveDraughtDaysCounter)

        if (repetitiveRainDaysCounter > 4) repetitiveRainDays.add(repetitiveRainDaysCounter)


        println("repetitiveRainDays: ${repetitiveRainDays} and repetitiveDraughtDays: ${repetitiveDraughtDays}")

    }catch (e : Exception) {
        throw IllegalArgumentException("Error while getting the weather data")
    }

}