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
    /** Bugün ilerleyen saatlerde beklenen yağış: başlama saati ve WMO kodu (yoksa null). */
    val alertHour: Int? = null,
    val alertCode: Int? = null,
) {
    /** Sabah uyarısı metni: "15:00 civarı yağmur bekleniyor, şemsiyeni al." */
    val alertText: String?
        get() {
            val h = alertHour ?: return null
            val c = alertCode ?: return null
            val time = "%02d:00".format(h)
            return when (c) {
                in 71..77, 85, 86 -> tx("Bugün $time civarı kar bekleniyor. Sıkı giyin ❄️", "Snow expected around $time today. Dress warm ❄️")
                in 95..99 -> tx("Bugün $time civarı fırtına bekleniyor. Dikkatli ol ⛈️", "A storm is expected around $time today. Take care ⛈️")
                else -> tx("Bugün $time civarı yağmur bekleniyor. Şemsiyeni al ☔", "Rain expected around $time today. Take an umbrella ☔")
            }
        }

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
        Weather(
            o.getString("city"), o.getInt("temp"), o.getInt("code"), o.getBoolean("day"), o.getLong("at"),
            o.optInt("ah", -1).takeIf { it >= 0 }, o.optInt("ac", -1).takeIf { it >= 0 },
        )
    }.getOrNull()

    suspend fun fetch(cityOverride: String): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val (city, lat, lon) = if (cityOverride.isNotBlank()) geocode(cityOverride) else locateByIp()
            val full = JSONObject(
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day&hourly=weather_code,precipitation_probability&forecast_days=1&timezone=auto"
                )
            )
            val alert = runCatching { precipitationAlert(full) }.getOrNull()
            val json = full.getJSONObject("current")
            val weather = Weather(
                city = city,
                temperature = Math.round(json.getDouble("temperature_2m")).toInt(),
                code = json.optInt("weather_code", -1),
                isDay = json.optInt("is_day", 1) == 1,
                fetchedAt = System.currentTimeMillis(),
                alertHour = alert?.first,
                alertCode = alert?.second,
            )
            prefs.edit().putString(
                KEY,
                JSONObject()
                    .put("city", weather.city)
                    .put("temp", weather.temperature)
                    .put("code", weather.code)
                    .put("day", weather.isDay)
                    .put("at", weather.fetchedAt)
                    .put("ah", weather.alertHour ?: -1)
                    .put("ac", weather.alertCode ?: -1)
                    .toString()
            ).apply()
            weather
        }.getOrNull()
    }

    /**
     * Saatlik tahminde şu andan gün sonuna kadar ilk yağış saati (yağmur, kar, fırtına; olasılık %50+).
     * Saatler şehrin yerel saatidir ("2026-10-26T15:00").
     */
    private fun precipitationAlert(full: JSONObject): Pair<Int, Int>? {
        val hourly = full.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val codes = hourly.getJSONArray("weather_code")
        val probs = hourly.optJSONArray("precipitation_probability")
        val nowHour = full.optJSONObject("current")?.optString("time")?.substringAfter('T')?.take(2)?.toIntOrNull() ?: 0
        for (i in 0 until times.length()) {
            val h = times.getString(i).substringAfter('T').take(2).toIntOrNull() ?: continue
            if (h < nowHour) continue
            val c = codes.optInt(i, 0)
            val wet = c in 51..67 || c in 71..77 || c in 80..86 || c in 95..99
            val p = probs?.optInt(i, 100) ?: 100
            if (wet && p >= 50) return h to c
        }
        return null
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
