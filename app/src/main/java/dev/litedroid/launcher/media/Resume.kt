package dev.litedroid.launcher.media

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * "Kaldığın yerden devam et" kartına basınca içeriği mümkün olduğunca doğrudan açar.
 *
 * Google TV'nin kendi "izlemeye devam et" verisi yalnızca sistem uygulamalarına açık. Bu yüzden
 * LiteDroid, uygulamaların medya oturumlarından gördüğü bilgilerle sırayla şunları dener:
 * 1. Oturum hâlâ açıksa (uygulama arkada duruyorsa): oynatıcı ekranına dön ve oynat.
 * 2. Uygulamaya özel derin bağlantılar: YouTube videosu (kaldığı saniyeden), Netflix bölümü,
 *    Prime Video ve Disney+ içerik kimlikleri. Kimlik; içerik id'sinde, adresinde ya da oturumun
 *    diğer metin alanlarında aranır.
 * 3. Uygulamanın bu içerik için verdiği "oynatıcıya dön" bağlantısı (LiteDroid açık kaldığı sürece).
 * 4. Oturumda geçen herhangi bir web adresi.
 * Hiçbiri olmazsa çağıran taraf uygulamayı açar.
 */
object Resume {

    private const val TAG = "LiteDroidMedia"

    private val YOUTUBE = setOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.google.android.youtube.tvkids",
        "com.google.android.youtube.tvunplugged",
    )
    private val NETFLIX = setOf("com.netflix.ninja", "com.netflix.mediaclient")
    private val PRIME = setOf(
        "com.amazon.amazonvideo.livingroom",
        "com.amazon.avod.thirdpartyclient",
        "com.amazon.avod",
    )
    private val DISNEY = setOf("com.disney.disneyplus")

    private val YT_ID = Regex("[A-Za-z0-9_-]{11}")
    private val YT_IN_TEXT = Regex("(?:v=|youtu\\.be/|/watch/|/shorts/)([A-Za-z0-9_-]{11})")
    private val NETFLIX_URL = Regex("netflix\\.com/(?:[a-z-]+/)?(?:watch|title)/(\\d{5,10})")
    private val NETFLIX_ID = Regex("(?<![\\d.])([78]\\d{7})(?!\\d)")
    private val PRIME_GTI = Regex("amzn1\\.dv\\.gti\\.[0-9a-fA-F-]{36}")
    private val PRIME_ASIN = Regex("\\b(B0[0-9A-Z]{8})\\b")
    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val WEB_URL = Regex("https?://[^\\s|,\"']+")

    fun resume(context: Context, item: PlayedItem): Boolean {
        NowPlayingStore.liveController(item)?.let { controller ->
            val pi = controller.sessionActivity
            if (pi != null && send(context, pi)) {
                runCatching { controller.transportControls.play() }
                Log.i(TAG, "resume ${item.packageName}: live session")
                return true
            }
        }
        if (start(context, specificLinks(item), "deep link")) return true
        NowPlayingStore.sessionActivity(item.key)?.let {
            if (send(context, it)) {
                Log.i(TAG, "resume ${item.packageName}: session activity")
                return true
            }
        }
        return start(context, genericLinks(item), "web link")
    }

    private fun start(context: Context, intents: List<Intent>, what: String): Boolean {
        intents.forEach { intent ->
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                Log.i(TAG, "resume ${intent.`package`}: $what ${intent.data}")
                return true
            }
        }
        return false
    }

    private fun send(context: Context, pi: PendingIntent): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 34) {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(context, 0, null, null, null, null, options.toBundle())
        } else {
            pi.send()
        }
        true
    }.getOrDefault(false)

    /** Kimliğin aranacağı tüm metin: id, adres, diğer alanlar. */
    private fun haystack(item: PlayedItem): String =
        listOfNotNull(item.mediaId, item.mediaUri, item.hints).joinToString(" | ")

    private fun specificLinks(item: PlayedItem): List<Intent> {
        val pkg = item.packageName
        val text = haystack(item)
        val seconds = item.position / 1000
        val out = ArrayList<Intent>()

        when (pkg) {
            in YOUTUBE -> {
                val id = item.mediaId?.takeIf { YT_ID.matches(it) }
                    ?: YT_IN_TEXT.find(text)?.groupValues?.get(1)
                if (id != null) {
                    val t = if (seconds > 5) "&t=${seconds}s" else ""
                    out += view("https://www.youtube.com/watch?v=$id$t", pkg)
                }
            }
            in NETFLIX -> {
                val id = NETFLIX_URL.find(text)?.groupValues?.get(1)
                    ?: item.mediaId?.takeIf { it.length in 5..10 && it.all(Char::isDigit) }
                    ?: NETFLIX_ID.find(text)?.groupValues?.get(1)
                if (id != null) {
                    out += view("http://www.netflix.com/watch/$id", pkg).putExtra("source", "30")
                    out += view("nflx://www.netflix.com/watch/$id", pkg).putExtra("source", "30")
                }
            }
            in PRIME -> {
                PRIME_GTI.find(text)?.value?.let { gti ->
                    out += view("https://app.primevideo.com/detail?gti=$gti", pkg)
                    out += view("https://watch.amazon.com/detail?gti=$gti", pkg)
                }
                PRIME_ASIN.find(text)?.groupValues?.get(1)?.let { asin ->
                    out += view("https://www.primevideo.com/detail/$asin", pkg)
                    out += view("https://watch.amazon.com/detail?asin=$asin", pkg)
                }
            }
            in DISNEY -> {
                UUID.find(text)?.value?.let { id ->
                    out += view("https://www.disneyplus.com/video/$id", pkg)
                }
            }
        }
        return out
    }

    private fun genericLinks(item: PlayedItem): List<Intent> {
        val out = ArrayList<Intent>()
        item.mediaUri?.takeIf { it.contains("://") }?.let { out += view(it, item.packageName) }
        WEB_URL.findAll(item.hints.orEmpty()).take(3).forEach { out += view(it.value, item.packageName) }
        return out
    }

    private fun view(uri: String, pkg: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
