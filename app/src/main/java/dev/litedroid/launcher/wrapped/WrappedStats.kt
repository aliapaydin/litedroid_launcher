package dev.litedroid.launcher.wrapped

import android.content.Context
import android.os.SystemClock
import java.time.LocalDate

/**
 * LiteDroid'in kendi bölümlerinde olanların günlük sayaçları (Wrapped için).
 * Android bunları bilemez: kaç oyun oynandı, kaç masal dinlendi, uyku zamanlayıcısı kaç kez kapattı…
 * Sayaçlar gün gün tutulur ("2026-10-27|game.SNAKE" → 3); gün bitince [WrappedEngine] onları
 * o günün kaydına taşır ve buradan siler. İçerik (soru metni, site adresi) asla tutulmaz, yalnızca sayılar.
 */
object WrappedStats {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("litedroid_stats", Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toString()

    /** Bir olayı say: [key] örneğin "game.SNAKE", "story.new", "claude.ask". */
    fun count(ctx: Context, key: String, by: Int = 1) {
        runCatching {
            val p = prefs(ctx)
            val k = "${today()}|c|$key"
            p.edit().putInt(k, p.getInt(k, 0) + by).apply()
        }
    }

    /** Süre ekle (ms): "saver" gibi. */
    fun addTime(ctx: Context, key: String, ms: Long) {
        if (ms <= 0) return
        runCatching {
            val p = prefs(ctx)
            val k = "${today()}|t|$key"
            p.edit().putLong(k, p.getLong(k, 0L) + ms).apply()
        }
    }

    /** Kırılan rekor: o günün en iyisi saklanır. */
    fun record(ctx: Context, id: String, value: Int) {
        runCatching { prefs(ctx).edit().putInt("${today()}|r|$id", value).apply() }
    }

    /** Bu hafta yapılan Canlı Tablo resimleri (dosya kimlikleri, en fazla 12). */
    fun painting(ctx: Context, id: String) {
        runCatching {
            val p = prefs(ctx)
            val k = "${today()}|p"
            val list = p.getString(k, "").orEmpty().split('\n').filter { it.isNotBlank() }
            if (id in list) return
            p.edit().putString(k, (list + id).takeLast(12).joinToString("\n")).apply()
        }
    }

    // ---- Süre ölçen yardımcılar (ekran koruyucu) ----
    private val started = HashMap<String, Long>()

    @Synchronized
    fun begin(key: String) {
        if (key !in started) started[key] = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun end(ctx: Context, key: String) {
        val s = started.remove(key) ?: return
        addTime(ctx, key, SystemClock.elapsedRealtime() - s)
    }

    /** O günün sayaçları (gün kaydına taşınırken). */
    internal class Day(
        val counters: Map<String, Int>,
        val times: Map<String, Long>,
        val records: Map<String, Int>,
        val paintings: List<String>,
    ) {
        val isEmpty get() = counters.isEmpty() && times.isEmpty() && records.isEmpty() && paintings.isEmpty()
    }

    internal fun read(ctx: Context, date: String): Day {
        val all = prefs(ctx).all
        val c = HashMap<String, Int>()
        val t = HashMap<String, Long>()
        val r = HashMap<String, Int>()
        var p = emptyList<String>()
        for ((k, v) in all) {
            if (!k.startsWith("$date|")) continue
            val rest = k.removePrefix("$date|")
            when {
                rest.startsWith("c|") -> (v as? Int)?.let { c[rest.removePrefix("c|")] = it }
                rest.startsWith("t|") -> (v as? Long)?.let { t[rest.removePrefix("t|")] = it }
                rest.startsWith("r|") -> (v as? Int)?.let { r[rest.removePrefix("r|")] = it }
                rest == "p" -> p = (v as? String).orEmpty().split('\n').filter { it.isNotBlank() }
            }
        }
        return Day(c, t, r, p)
    }

    /** Gün kaydına taşınan günlerin ve 3 haftadan eski artıkların sayaçlarını siler. */
    internal fun clear(ctx: Context, date: String) {
        val p = prefs(ctx)
        val old = LocalDate.now().minusDays(21).toString()
        val e = p.edit()
        for (k in p.all.keys) {
            val d = k.substringBefore('|')
            if (d == date || d < old) e.remove(k)
        }
        e.apply()
    }
}
