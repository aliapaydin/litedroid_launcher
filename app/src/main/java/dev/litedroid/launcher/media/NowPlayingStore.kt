package dev.litedroid.launcher.media

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bir uygulamada oynatılmış içerik (dizi bölümü, video, şarkı...). */
data class PlayedItem(
    val key: String,
    val packageName: String,
    val title: String,
    val subtitle: String?,
    /** Kapak görseli: dosya yolu ("/...") ya da URI. */
    val art: String?,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
    /** Oturumun bildirdiği içerik kimliği (YouTube video id'si, Netflix başlık numarası...). */
    val mediaId: String? = null,
    /** Oturumun bildirdiği içerik adresi, varsa. */
    val mediaUri: String? = null,
    /**
     * Oturumun metin alanlarının (açıklama, ekstra bilgiler...) birleşimi. Bazı uygulamalar içerik
     * kimliğini standart alan yerine buralara koyar; derin bağlantı ararken taranır.
     */
    val hints: String? = null,
    /**
     * Uygulama oturum açtı ama ne oynattığını söylemedi (ör. Netflix). Kart uygulamanın kendisini
     * temsil eder; [title] uygulamanın adıdır.
     */
    val anonymous: Boolean = false,
) {
    val progress: Float?
        get() = if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null
}

/**
 * Uygulamaların medya oturumlarından son izlenenleri tutar. [MediaListenerService] yazar, launcher okur.
 * Kayıtlar kalıcıdır: uygulama kapansa da kalır.
 *
 * Her içerik tek kayıttır (aynı şey iki kez görünmez). Kimlik sırası: içerik kimliği (YouTube video id'si,
 * Prime "gti"si...) > gerçek başlık > uygulamanın kendisi. Bir uygulamadan en fazla [PER_APP] içerik tutulur.
 */
object NowPlayingStore {

    private const val PREFS = "litedroid_media"
    private const val KEY = "history"
    private const val MAX = 12
    const val PER_APP = 3
    private const val TAG = "LiteDroidMedia"

    /** Başlığı olmayan içerik bu kadar oynatılmadıysa fragman/önizleme sayılır, listeye girmez. */
    private const val MIN_UNTITLED_PLAY_MS = 12_000L

    /** Müzik/podcast uygulamaları: "izlemeye devam et" listesine girmez. */
    val MUSIC_PACKAGES = setOf(
        "com.spotify.tv.android",
        "com.spotify.music",
        "com.google.android.youtube.tvmusic",
        "com.google.android.apps.youtube.music",
        "com.apple.android.music",
        "deezer.android.tv",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.aspiro.tidal",
        "com.turkcell.gncplay",
        "com.amazon.mp3",
        "tunein.player",
        "com.google.android.apps.podcasts",
        "com.pocketcasts.android",
        "com.qobuz.music",
    )

    fun isAudioApp(context: Context, pkg: String): Boolean {
        if (pkg in MUSIC_PACKAGES) return true
        val category = runCatching { context.packageManager.getApplicationInfo(pkg, 0).category }
            .getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
        return category == ApplicationInfo.CATEGORY_AUDIO
    }

    // Süreç yaşadığı sürece tutulan canlı bilgiler (diske yazılamazlar)
    private val sessionActivities = HashMap<String, PendingIntent>()
    private val liveControllers = HashMap<String, MediaController>()
    /** Oturum boyunca son görülen içerik kimliği: bazı uygulamalar (Prime) kimliği sonradan boş gönderiyor. */
    private val sessionIds = HashMap<String, String>()
    private val playingSince = HashMap<String, Long>()
    private val playedMs = HashMap<String, Long>()

    fun keyOf(pkg: String, title: String): String = (pkg + "|" + title).hashCode().toUInt().toString()

    fun sessionActivity(key: String): PendingIntent? = synchronized(this) { sessionActivities[key] }

    /** Aynı içerik için hâlâ açık bir oturum varsa onu döndürür. */
    fun liveController(item: PlayedItem): MediaController? = synchronized(this) {
        val c = liveControllers[item.packageName] ?: return@synchronized null
        when {
            item.mediaId != null -> c.takeIf { sessionIds[item.packageName] == item.mediaId }
            item.anonymous -> c
            else -> c.takeIf {
                val m = c.metadata
                (m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)) == item.title
            }
        }
    }

    fun forget(controller: MediaController) {
        synchronized(this) {
            val pkg = controller.packageName
            if (liveControllers[pkg] === controller) {
                liveControllers.remove(pkg)
                sessionIds.remove(pkg)
                playingSince.remove(pkg)
                playedMs.remove(pkg)
            }
        }
    }

    fun isAccessEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun listenerComponent(context: Context) = ComponentName(context, MediaListenerService::class.java)

    fun load(context: Context): List<PlayedItem> {
        val raw = context.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped(PREFS), Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlayedItem(
                    key = o.getString("key"),
                    packageName = o.getString("pkg"),
                    title = o.getString("title"),
                    subtitle = o.optString("subtitle").takeIf { it.isNotBlank() },
                    art = o.optString("art").takeIf { it.isNotBlank() },
                    position = o.optLong("pos"),
                    duration = o.optLong("dur"),
                    updatedAt = o.optLong("at"),
                    mediaId = o.optString("mid").takeIf { it.isNotBlank() },
                    mediaUri = o.optString("uri").takeIf { it.isNotBlank() },
                    hints = o.optString("hints").takeIf { it.isNotBlank() },
                    anonymous = o.optBoolean("anon", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped(PREFS), Context.MODE_PRIVATE).edit().remove(KEY).apply()
        File(context.filesDir, "art").deleteRecursively()
    }

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun norm(s: String?): String = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    /**
     * Gerçek içerik başlığı. Uygulamanın kendi adı ("PrimeVideo" gibi) ya da yazar alanının kopyası
     * başlık sayılmaz.
     */
    private fun realTitle(meta: MediaMetadata?, appLabel: String): String? {
        meta ?: return null
        val author = meta.getString(MediaMetadata.METADATA_KEY_AUTHOR)
        return listOf(
            meta.getString(MediaMetadata.METADATA_KEY_TITLE),
            meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
        ).firstOrNull { t ->
            !t.isNullOrBlank() && norm(t) != norm(appLabel) && norm(t) != norm(author) && norm(t).length > 1
        }
    }

    private fun positionOf(state: PlaybackState?): Long {
        if (state == null) return 0
        var position = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        return position.coerceAtLeast(0)
    }

    fun save(context: Context, controller: MediaController) {
        val pkg = controller.packageName ?: return
        if (pkg == context.packageName) return
        if (isAudioApp(context, pkg)) return

        val meta = controller.metadata
        val state = controller.playbackState
        val stateCode = state?.state ?: PlaybackState.STATE_NONE
        val label = appLabel(context, pkg)
        val title = realTitle(meta, label)
        val now = SystemClock.elapsedRealtime()

        // Oturum boyunca oynatma süresi ve son içerik kimliği
        val (contentId, played) = synchronized(this) {
            liveControllers[pkg] = controller
            meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }?.let { sessionIds[pkg] = it }
            if (stateCode == PlaybackState.STATE_PLAYING || stateCode == PlaybackState.STATE_BUFFERING) {
                playingSince.getOrPut(pkg) { now }
            } else {
                playingSince.remove(pkg)?.let { start -> playedMs[pkg] = (playedMs[pkg] ?: 0L) + (now - start) }
            }
            val sum = (playedMs[pkg] ?: 0L) + (playingSince[pkg]?.let { now - it } ?: 0L)
            sessionIds[pkg] to sum
        }

        val hints = meta?.let { collectHints(controller, it) }
        Log.i(
            TAG,
            "pkg=$pkg state=$stateCode title=$title id=$contentId played=${played}ms " +
                "pos=${positionOf(state)} keys=[${runCatching { meta?.keySet()?.joinToString() }.getOrNull()}] hints=$hints",
        )

        // Başlıksız içerik ancak gerçekten izlendiyse (fragman değilse) kaydedilir
        if (title == null && played < MIN_UNTITLED_PLAY_MS) return
        if (title != null && stateCode !in TRACKED_STATES) return

        val key = when {
            contentId != null -> keyOf(pkg, "id:$contentId")
            title != null -> keyOf(pkg, title)
            else -> keyOf(pkg, "__session__")
        }
        synchronized(this) { controller.sessionActivity?.let { sessionActivities[key] = it } }

        val list = load(context).toMutableList()
        val existing = list.firstOrNull { it.key == key }
        val rawDuration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val duration = if (rawDuration > 0) rawDuration else existing?.duration ?: 0L
        // Durdurulunca bazı uygulamalar konumu 0 bildirir: o zaman son bilinen konumu koru
        val position = positionOf(state).takeIf { it > 0 } ?: existing?.position ?: 0L

        // Sonuna kadar izlenen (son %3) uzun içerik listeden düşer
        if (duration > 120_000 && position >= duration * 97 / 100) {
            if (existing != null) {
                list.remove(existing)
                existing.art?.takeIf { it.startsWith("/") }?.let { File(it).delete() }
                write(context, list)
            }
            return
        }

        // Kapak: YouTube'da video kimliğinden gerçek küçük resim; diğerlerinde oturumun kapağı.
        // Uygulamalar oynatma başında çoğu zaman kendi logosunu gönderip sonra gerçek kapağa geçer,
        // bu yüzden kapak değiştikçe dosya yenilenir (yeni ad = önbellekte eski resim kalmaz).
        val oldArt = existing?.art?.takeIf { !it.startsWith("/") || File(it).exists() }
        val signature = meta?.let { artSignature(it) }
        val art = youtubeThumbnail(pkg, contentId, hints ?: existing?.hints)
            ?: run {
                if (meta == null || signature == null) return@run oldArt
                if (oldArt != null && synchronized(this) { artSignatures[key] } == signature) return@run oldArt
                saveArt(context, key, meta, signature)?.also { fresh ->
                    if (fresh != oldArt) oldArt?.takeIf { it.startsWith("/") }?.let { File(it).delete() }
                } ?: oldArt
            }
        synchronized(this) { signature?.let { artSignatures[key] = it } }
        val subtitle = meta?.let {
            it.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: it.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { a -> title != null && norm(a) != norm(label) }
                ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { title != null }
        }?.takeIf { it.isNotBlank() } ?: existing?.subtitle

        list.removeAll { it.key == key }
        list.add(
            0,
            PlayedItem(
                key = key,
                packageName = pkg,
                title = title ?: existing?.title ?: label,
                subtitle = subtitle,
                art = art,
                position = position,
                duration = duration,
                updatedAt = System.currentTimeMillis(),
                mediaId = contentId ?: existing?.mediaId,
                mediaUri = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)?.takeIf { it.isNotBlank() } ?: existing?.mediaUri,
                hints = hints ?: existing?.hints,
                anonymous = title == null && existing?.anonymous != false,
            ),
        )

        // Uygulama başına en fazla PER_APP, toplamda en fazla MAX içerik
        val perApp = HashMap<String, Int>()
        val keep = ArrayList<PlayedItem>()
        list.forEach { item ->
            val n = perApp.getOrDefault(item.packageName, 0)
            if (n < PER_APP && keep.size < MAX) {
                keep += item
                perApp[item.packageName] = n + 1
            } else {
                item.art?.takeIf { it.startsWith("/") }?.let { File(it).delete() }
            }
        }
        write(context, keep)
    }

    /** Metadata'daki tüm metin alanları + açıklama ekstraları + oturum ekstraları, tek satırda. */
    @Suppress("DEPRECATION")
    private fun collectHints(controller: MediaController, meta: MediaMetadata): String? {
        val parts = LinkedHashSet<String>()
        runCatching {
            meta.keySet().forEach { k ->
                runCatching { meta.getText(k) }.getOrNull()?.toString()?.takeIf { it.isNotBlank() }?.let { parts += "$k=$it" }
            }
        }
        runCatching {
            meta.description?.mediaUri?.let { parts += "descUri=$it" }
            meta.description?.extras?.let { b -> b.keySet().forEach { k -> b.get(k)?.let { v -> parts += "$k=$v" } } }
        }
        runCatching {
            controller.extras?.let { b -> b.keySet().forEach { k -> b.get(k)?.let { v -> parts += "$k=$v" } } }
        }
        return parts.joinToString(" | ").take(1500).takeIf { it.isNotBlank() }
    }

    private val artSignatures = HashMap<String, String>()

    private val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.google.android.youtube.tvkids",
        "com.google.android.youtube.tvunplugged",
    )
    private val YT_ID = Regex("[A-Za-z0-9_-]{11}")
    private val YT_IN_TEXT = Regex("(?:v=|youtu\\.be/|/watch/|/shorts/|/vi/)([A-Za-z0-9_-]{11})")

    /** YouTube videosunun gerçek küçük resmi (kimlik biliniyorsa). */
    private fun youtubeThumbnail(pkg: String, id: String?, hints: String?): String? {
        if (pkg !in YOUTUBE_PACKAGES) return null
        val vid = id?.takeIf { YT_ID.matches(it) } ?: hints?.let { YT_IN_TEXT.find(it)?.groupValues?.get(1) } ?: return null
        return "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
    }

    /** Kapağın değişip değişmediğini anlamak için ucuz bir imza. */
    private fun artSignature(meta: MediaMetadata): String? {
        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null) {
            // Köşe ve orta piksellerden küçük bir parmak izi
            val w = bitmap.width
            val h = bitmap.height
            val probe = runCatching {
                listOf(bitmap.getPixel(w / 2, h / 2), bitmap.getPixel(w / 4, h / 4), bitmap.getPixel(3 * w / 4, 3 * h / 4))
                    .joinToString("") { Integer.toHexString(it) }
            }.getOrDefault("")
            return "${w}x${h}:$probe"
        }
        return meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
    }

    private fun saveArt(context: Context, key: String, meta: MediaMetadata, signature: String): String? {
        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val uri = meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        if (bitmap == null) return uri
        return runCatching {
            val dir = File(context.filesDir, "art").apply { mkdirs() }
            val file = File(dir, "${key}_${signature.hashCode().toUInt()}.jpg")
            val scaled = if (bitmap.width > 640) {
                Bitmap.createScaledBitmap(bitmap, 640, (bitmap.height * 640f / bitmap.width).toInt().coerceAtLeast(1), true)
            } else bitmap
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull() ?: uri
    }

    private fun write(context: Context, items: List<PlayedItem>) {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(
                JSONObject()
                    .put("key", p.key)
                    .put("pkg", p.packageName)
                    .put("title", p.title)
                    .put("subtitle", p.subtitle ?: "")
                    .put("art", p.art ?: "")
                    .put("pos", p.position)
                    .put("dur", p.duration)
                    .put("at", p.updatedAt)
                    .put("mid", p.mediaId ?: "")
                    .put("uri", p.mediaUri ?: "")
                    .put("hints", p.hints ?: "")
                    .put("anon", p.anonymous)
            )
        }
        context.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped(PREFS), Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private val TRACKED_STATES = setOf(
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_BUFFERING,
    )
}
