package dev.litedroid.launcher.canvas

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.data.CanvasCities
import dev.litedroid.launcher.data.GeminiMedia
import dev.litedroid.launcher.data.LauncherSettings
import dev.litedroid.launcher.data.SaverScene
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.data.Weather
import dev.litedroid.launcher.data.WeatherRepository
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.ScreensaverContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Resmedilebilecek bir yer: adı ve yapay zekâya giden manzara tarifi (İngilizce). */
data class Place(val tr: String, val en: String, val lat: Double, val lon: Double, val scene: String) {
    val name get() = tx(tr, en)
    val key get() = en.lowercase().filter { it.isLetterOrDigit() }
}

val WORLD_PLACES = listOf(
    Place("İstanbul", "Istanbul", 41.01, 28.98, "the Bosphorus with the Galata Tower, domed mosques with slender minarets and ferries crossing the water"),
    Place("Kapadokya", "Cappadocia", 38.64, 34.83, "the fairy chimneys and soft valleys of Cappadocia with drifting hot air balloons"),
    Place("Paris", "Paris", 48.86, 2.35, "the Seine with the Eiffel Tower, stone bridges and Parisian rooftops"),
    Place("Kyoto", "Kyoto", 35.01, 135.77, "a quiet Kyoto lane with wooden machiya houses, lanterns and a five-storey pagoda"),
    Place("New York", "New York", 40.71, -74.0, "the Manhattan skyline seen from the Brooklyn Bridge"),
    Place("Venedik", "Venice", 45.44, 12.33, "the Grand Canal of Venice with gondolas and old palazzos"),
    Place("Reykjavik", "Reykjavik", 64.15, -21.94, "colorful wooden houses of Reykjavik by the sea under snowy mountains"),
    Place("Santorini", "Santorini", 36.39, 25.46, "white cliffside houses and blue domes of Santorini above the Aegean sea"),
    Place("Rio de Janeiro", "Rio de Janeiro", -22.91, -43.2, "Sugarloaf Mountain rising over Guanabara Bay and Copacabana"),
    Place("Banff", "Banff", 51.18, -115.57, "a turquoise mountain lake surrounded by pine forest and the Rocky Mountains"),
    Place("Marakeş", "Marrakesh", 31.63, -7.99, "a Marrakesh square with the Koutoubia minaret and the Atlas mountains beyond"),
    Place("Sidney", "Sydney", -33.87, 151.21, "the Sydney Opera House and Harbour Bridge across the harbour"),
    Place("Prag", "Prague", 50.09, 14.42, "the Charles Bridge and the gothic spires of Prague's old town"),
    Place("Londra", "London", 51.5, -0.12, "the Thames with Big Ben, Westminster and red buses on the bridge"),
    Place("Kahire", "Cairo", 29.98, 31.13, "the pyramids of Giza at the edge of the desert"),
    Place("Edinburg", "Edinburgh", 55.95, -3.19, "Edinburgh Castle on its rock above the old town"),
)

/** Ekranda gösterilen tablo ve altındaki bilgi. */
class Painting(
    val image: ImageBitmap,
    val city: String,
    val utcOffsetSec: Int,
    val weatherLabel: String,
    val temperature: Int?,
    val style: String,
    val id: String,
) {
    fun localTime(): String =
        LocalDateTime.now(ZoneOffset.UTC).plusSeconds(utcOffsetSec.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
}

/**
 * Canlı Tablo ressamı: sıradaki şehrin şu anki saatine ve havasına göre bir tablo hazırlar.
 * Aynı şehir + gün dilimi + hava + mevsim + stil için resim bir kez çizilir ve saklanır; sonraki
 * gösterimler ücretsizdir. Gemini anahtarı yoksa yalnızca saklanan tablolar gösterilir.
 */
class CanvasPainter(private val context: Context, private val settings: LauncherSettings) {

    private val dir = File(context.filesDir, "canvas").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("litedroid_canvas", Context.MODE_PRIVATE)
    private val key = settings.geminiKey.trim()

    private class Conditions(val city: String, val scene: String, val placeKey: String, val code: Int, val isDay: Boolean, val temp: Int?, val utcOffsetSec: Int, val lat: Double)

    /** Açılışta hemen gösterilecek son tablo. */
    suspend fun latestCached(): Painting? = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".jpg") }?.maxByOrNull { it.lastModified() }?.let { load(it) }
    }

    suspend fun next(): Painting? = withContext(Dispatchers.IO) {
        val c = nextConditions() ?: return@withContext latestCached()
        val local = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(c.utcOffsetSec.toLong())
        val bucket = dayPart(local.hour)
        val wclass = weatherClass(c.code)
        val season = season(local.monthValue, c.lat)
        val id = "${c.placeKey}_${bucket.first}_${wclass.first}_${season}_${settings.canvasStyle.name.lowercase()}"
        val file = File(dir, "$id.jpg")
        if (!file.exists()) {
            if (key.isEmpty()) throw IllegalStateException(tx("Canlı Tablo için Gemini API anahtarı gerekli (Ayarlar → Claude'a sor).", "Living Painting needs a Gemini API key (Settings → Ask Claude)."))
            val prompt = "${settings.canvasStyle.prompt}. ${c.scene}, ${bucket.second}, ${wclass.second}, in $season. " +
                "Wide 16:9 landscape composition, atmospheric, serene and beautiful, museum quality. No text, no letters, no signature, no watermark, no frame."
            Log.i(TAG, "painting $id")
            file.writeBytes(GeminiMedia.image(context, key, prompt))
            prune()
        } else {
            file.setLastModified(System.currentTimeMillis())
        }
        File(dir, "$id.json").writeText(
            JSONObject().put("city", c.city).put("offset", c.utcOffsetSec).put("code", c.code).put("day", c.isDay)
                .put("temp", c.temp ?: JSONObject.NULL).toString()
        )
        load(file)
    }

    private fun load(file: File): Painting? {
        val bmp = BitmapFactory.decodeFile(file.path) ?: return null
        val meta = runCatching { JSONObject(File(file.path.removeSuffix(".jpg") + ".json").readText()) }.getOrNull()
        val code = meta?.optInt("code", 0) ?: 0
        val day = meta?.optBoolean("day", true) ?: true
        return Painting(
            image = bmp.asImageBitmap(),
            city = meta?.optString("city").orEmpty(),
            utcOffsetSec = meta?.optInt("offset", currentOffset()) ?: currentOffset(),
            weatherLabel = Weather("", 0, code, day, 0).description,
            temperature = meta?.takeIf { !it.isNull("temp") }?.optInt("temp"),
            style = settings.canvasStyle.label,
            id = file.nameWithoutExtension,
        )
    }

    private fun currentOffset() = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000

    /** Sıradaki yer: ayara göre ev şehri, dünya şehirleri ya da ikisi dönüşümlü. */
    private suspend fun nextConditions(): Conditions? {
        val turn = prefs.getInt("turn", 0)
        prefs.edit().putInt("turn", turn + 1).apply()
        val home = settings.canvasCities == CanvasCities.HOME || (settings.canvasCities == CanvasCities.MIXED && turn % 2 == 0)
        if (home) homeConditions()?.let { return it }
        val worldTurn = prefs.getInt("world", 0)
        prefs.edit().putInt("world", worldTurn + 1).apply()
        val place = WORLD_PLACES[worldTurn % WORLD_PLACES.size]
        return runCatching { worldConditions(place) }.onFailure { Log.w(TAG, "weather ${place.en}: ${it.message}") }.getOrNull()
    }

    private suspend fun homeConditions(): Conditions? {
        val repo = WeatherRepository(context)
        val w = repo.cached()?.takeIf { System.currentTimeMillis() - it.fetchedAt < 45 * 60_000L }
            ?: repo.fetch(settings.weatherCity) ?: repo.cached() ?: return null
        if (w.city.isBlank()) return null
        val lat = if (I18n.locale.language == "tr") 39.0 else 45.0
        return Conditions(
            city = w.city,
            scene = "a beautiful, characteristic and recognizable view of the city of ${w.city}",
            placeKey = "home" + w.city.lowercase().filter { it.isLetterOrDigit() },
            code = w.code, isDay = w.isDay, temp = w.temperature, utcOffsetSec = currentOffset(), lat = lat,
        )
    }

    private fun worldConditions(p: Place): Conditions {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}&current=temperature_2m,weather_code,is_day&timezone=auto"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "LiteDroid-Launcher")
        }
        val json = try {
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        val cur = json.getJSONObject("current")
        return Conditions(
            city = p.name, scene = p.scene, placeKey = p.key,
            code = cur.optInt("weather_code", 0), isDay = cur.optInt("is_day", 1) == 1,
            temp = Math.round(cur.optDouble("temperature_2m", 0.0)).toInt(),
            utcOffsetSec = json.optInt("utc_offset_seconds", 0), lat = p.lat,
        )
    }

    private fun dayPart(h: Int): Pair<String, String> = when (h) {
        in 5..6 -> "dawn" to "at dawn with the first pink light on the horizon"
        in 7..10 -> "morning" to "in fresh, clear morning light"
        in 11..14 -> "noon" to "at midday in bright daylight"
        in 15..17 -> "afternoon" to "in warm afternoon light with long shadows"
        in 18..19 -> "sunset" to "at sunset, golden hour, glowing orange and pink sky"
        in 20..21 -> "dusk" to "at blue hour dusk as the city lights turn on"
        else -> "night" to "at night under the moon and stars, warm glowing windows and lights"
    }

    private fun weatherClass(code: Int): Pair<String, String> = when (code) {
        0 -> "clear" to "under a clear sky"
        1, 2 -> "partly" to "with a few soft drifting clouds"
        3 -> "cloudy" to "under a heavy overcast sky"
        45, 48 -> "fog" to "wrapped in thick mist and fog"
        in 51..67, in 80..82 -> "rain" to "in gentle falling rain with wet, reflective surfaces"
        in 71..77, 85, 86 -> "snow" to "with snow falling and everything covered in fresh snow"
        in 95..99 -> "storm" to "during a dramatic thunderstorm with distant lightning"
        else -> "clear" to "under a clear sky"
    }

    private fun season(month: Int, lat: Double): String {
        val m = if (lat < 0) (month + 5) % 12 + 1 else month
        return when (m) {
            12, 1, 2 -> "winter"
            3, 4, 5 -> "spring"
            6, 7, 8 -> "summer"
            else -> "autumn"
        }
    }

    private fun prune() {
        dir.listFiles { f -> f.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() }?.drop(80)?.forEach {
            it.delete()
            File(it.path.removeSuffix(".jpg") + ".json").delete()
        }
    }

    companion object {
        const val TAG = "LiteDroidCanvas"
    }
}

/**
 * Canlı Tablo ekran koruyucusu: tablo yavaşça kayar, değişirken 3 saniyede birbirine karışır.
 * Altta şehir, o şehrin şu anki saati ve havası; sağ üstte saat. Tablo yoksa gece sahnesi.
 */
@Composable
fun LivingCanvas(userName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context).load() }
    val painter = remember { CanvasPainter(context.applicationContext, settings) }
    var shown by remember { mutableStateOf<Painting?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val minutes = settings.canvasMinutes.coerceIn(2, 60)
    LaunchedEffect(Unit) {
        shown = painter.latestCached()
        loaded = true
        while (true) {
            try {
                painter.next()?.let { shown = it }
                status = null
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(CanvasPainter.TAG, "next failed: ${t.message}")
                status = if (t is IllegalStateException) t.message else GeminiMedia.describe(t)
            }
            delay(minutes * 60_000L)
        }
    }
    // Saat her saniye
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val p = shown
        if (p == null) {
            // Henüz tablo yok: klasik gece sahnesi
            if (loaded) ScreensaverContent(SaverScene.NIGHT, userName)
        } else {
            Crossfade(p, animationSpec = tween(3_000), label = "painting") { painting ->
                val pan = remember(painting.id) { Animatable(0f) }
                LaunchedEffect(painting.id) { pan.animateTo(1f, tween(minutes * 60_000, easing = LinearEasing)) }
                val dir = if (painting.id.hashCode() % 2 == 0) 1f else -1f
                Image(
                    painting.image, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val v = pan.value
                        scaleX = 1.12f - 0.08f * v
                        scaleY = scaleX
                        translationX = dir * (v - 0.5f) * 40.dp.toPx()
                    },
                )
            }
            // Yazılar okunsun diye alt ve üst kararma
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
            // Ekran yanmasına karşı bilgi blokları çok yavaş yer değiştirir
            val drift = ((tick / 60) % 6).let { (it - 3) * 6 }
            val now = remember(tick) { LocalDateTime.now() }
            val cityTime = remember(tick, p) { p.localTime() }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 34.dp).offset(x = drift.dp)) {
                Text("🎨 " + tx("CANLI TABLO", "LIVING PAINTING"), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(p.city, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                val parts = listOfNotNull(
                    tx("Şu an ", "Now ") + cityTime,
                    p.weatherLabel,
                    p.temperature?.let { "$it°" },
                )
                Text(parts.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                Text(tx("Yapay zekâ ile resmedildi · ", "Painted by AI · ") + p.style, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                status?.let { Text(it, color = Color(0xFFFFC2B5), fontSize = 11.sp) }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 48.dp).offset(x = (-drift).dp), horizontalAlignment = Alignment.End) {
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
                Text(now.format(DateTimeFormatter.ofPattern(tx("d MMMM EEEE", "EEEE, MMMM d"), I18n.locale)), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            }
        }
    }
}
