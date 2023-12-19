package com.insurance.oracles

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import okhttp3.OkHttpClient
import okhttp3.Request
import net.corda.core.utilities.loggerFor

@CordaService
class WeatherOracle(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken(){

    private val client = OkHttpClient()
    private val mapper = ObjectMapper()

    init {
        println("I am in WeatherOracle service.")
    }

    private companion object {
        val log = loggerFor<WeatherOracle>()
    }

    fun getWeatherReport(latitude: String, longitude: String, startDate: String, endDate: String) : Pair<Int, Int>{
        println("Weather report for latitude: $latitude, longitude: $longitude, startDate: $startDate, endDate: $endDate")
        log.info("Weather report for latitude: $latitude, longitude: $longitude, startDate: $startDate, endDate: $endDate")
        val url = buildUrl(latitude, longitude, startDate, endDate)
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

        return Pair(repetitiveRainDays.max()?:0, repetitiveDraughtDays.max()?:0)
    }

    private fun buildUrl(latitude: String, longitude: String, startDate: String, endDate: String) =
            Request.Builder().url("http://api.worldweatheronline.com/premium/v1/past-weather.ashx?q=$latitude,$longitude&date=$startDate&tp=24&enddate=$endDate&format=json&key=51ac31893d6f486b85860801201301").build()

    fun verifyWeatherReport(latitude: String, longitude: String, startDate: String, endDate: String, repetitiveRainDays: Int, repetitiveDraughtDays: Int) =
            Pair(repetitiveRainDays, repetitiveDraughtDays) == getWeatherReport(latitude, longitude, startDate, endDate)

}