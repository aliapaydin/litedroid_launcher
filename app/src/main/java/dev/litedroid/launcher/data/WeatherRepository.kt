package dev.litedroid.launcher.data

import android.content.Context
import dev.litedroid.launcher.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Weather(
    val city: String,
    val temperature: Int,
    /** WMO hava kodu (0 açık, 1-3 parçalı bulutlu, 61 yağmur, 71 kar, 95 fırtına...). */
    val code: Int,
    val isDay: Boolean,
    val fetchedAt: Long,
) {
    val description: String
        get() = when (code) {
            0 -> if (isDay) tx("Güneşli", "Sunny") else tx("Açık", "Clear")
            1, 2 -> tx("Parçalı bulutlu", "Partly cloudy")
            3 -> tx("Bulutlu", "Cloudy")
            45, 48 -> tx("Sisli", "Foggy")
            in 51..57 -> tx("Çisenti", "Drizzle")
            in 61..67, in 80..82 -> tx("Yağmurlu", "Rainy")
            in 71..77, 85, 86 -> tx("Karlı", "Snowy")
            in 95..99 -> tx("Fırtınalı", "Stormy")
            else -> tx("Hava durumu", "Weather")
        }
}

/**
 * Hava durumu: Open-Meteo (anahtar gerektirmez, ücretsiz).
 * Konum: ayarlarda şehir yazılıysa Open-Meteo'nun şehir aramasından, boşsa IP adresinden (ipwho.is).
 * Son sonuç saklanır; açılışta önce o gösterilir.
 */
class WeatherRepository(context: Context) {

    private val prefs = context.getSharedPreferences("litedroid_weather", Context.MODE_PRIVATE)

    fun cached(): Weather? = runCatching {
        val raw = prefs.getString(KEY, null) ?: return null
        val o = JSONObject(raw)
        Weather(o.getString("city"), o.getInt("temp"), o.getInt("code"), o.getBoolean("day"), o.getLong("at"))
    }.getOrNull()

    suspend fun fetch(cityOverride: String): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val (city, lat, lon) = if (cityOverride.isNotBlank()) geocode(cityOverride) else locateByIp()
            val json = JSONObject(
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day&timezone=auto"
                )
            ).getJSONObject("current")
            val weather = Weather(
                city = city,
                temperature = Math.round(json.getDouble("temperature_2m")).toInt(),
                code = json.optInt("weather_code", -1),
                isDay = json.optInt("is_day", 1) == 1,
                fetchedAt = System.currentTimeMillis(),
            )
            prefs.edit().putString(
                KEY,
                JSONObject()
                    .put("city", weather.city)
                    .put("temp", weather.temperature)
                    .put("code", weather.code)
                    .put("day", weather.isDay)
                    .put("at", weather.fetchedAt)
                    .toString()
            ).apply()
            weather
        }.getOrNull()
    }

    private fun geocode(name: String): Triple<String, Double, Double> {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val results = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=tr&format=json"))
            .getJSONArray("results")
        val r = results.getJSONObject(0)
        return Triple(r.optString("name", name), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    private fun locateByIp(): Triple<String, Double, Double> {
        val o = JSONObject(get("https://ipwho.is/?fields=success,city,latitude,longitude"))
        check(o.optBoolean("success", true)) { "konum bulunamadı" }
        return Triple(o.optString("city", ""), o.getDouble("latitude"), o.getDouble("longitude"))
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        conn.setRequestProperty("User-Agent", "LiteDroid-Launcher")
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val KEY = "last"
    }
}
