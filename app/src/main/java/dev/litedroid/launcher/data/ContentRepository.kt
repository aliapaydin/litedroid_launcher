package dev.litedroid.launcher.data

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bir uygulamanın Android TV'ye yayınladığı içerik: bölüm, film, video... */
data class ContentItem(
    val key: String,
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val imageUri: String?,
    val intentUri: String?,
    /** 0..1 arası izlenme oranı (biliniyorsa). */
    val progress: Float?,
    val lastEngagement: Long,
    val isWatchNext: Boolean,
)

enum class ContentAccess { UNKNOWN, GRANTED, DENIED }

data class ContentState(
    /** "İzlemeye devam et" satırı: uygulamaların sisteme bildirdiği yarım kalan içerikler. */
    val watchNext: List<ContentItem> = emptyList(),
    /** Uygulama kanalları: her uygulamanın ana ekrana önerdiği içerikler. */
    val preview: List<ContentItem> = emptyList(),
    val access: ContentAccess = ContentAccess.UNKNOWN,
) {
    val total: Int get() = watchNext.size + preview.size
}

/**
 * Android TV'nin ortak içerik deposunu (TvProvider) okur.
 *
 * Bu veriye erişim cihaza ve izne bağlıdır: izin yoksa [ContentAccess.DENIED] döner ve
 * arayüz "Son kullanılanlar"a düşer. Erişim olsa bile her uygulama veri yayınlamaz.
 */
class ContentRepository(context: Context) {

    private val resolver = context.contentResolver

    suspend fun load(): ContentState = withContext(Dispatchers.IO) {
        try {
            val watchNext = query(TvContract.WatchNextPrograms.CONTENT_URI, isWatchNext = true)
                .sortedByDescending { it.lastEngagement }
                .distinctBy { it.packageName + "|" + it.title + "|" + it.subtitle }
                .take(12)
            val preview = runCatching { query(TvContract.PreviewPrograms.CONTENT_URI, isWatchNext = false) }
                .getOrDefault(emptyList())
                .groupBy { it.packageName }
                .flatMap { (_, items) -> items.take(8) }
            ContentState(watchNext, preview, ContentAccess.GRANTED)
        } catch (e: Exception) {
            ContentState(access = ContentAccess.DENIED)
        }
    }

    private fun query(uri: Uri, isWatchNext: Boolean): List<ContentItem> {
        val out = mutableListOf<ContentItem>()
        resolver.query(uri, null, null, null, null)?.use { c ->
            val iId = c.getColumnIndex("_id")
            val iPkg = c.getColumnIndex("package_name")
            val iTitle = c.getColumnIndex("title")
            val iEpisode = c.getColumnIndex("episode_title")
            val iPoster = c.getColumnIndex("poster_art_uri")
            val iThumb = c.getColumnIndex("thumbnail_uri")
            val iIntent = c.getColumnIndex("intent_uri")
            val iPos = c.getColumnIndex("last_playback_position_millis")
            val iDur = c.getColumnIndex("duration_millis")
            val iEngaged = c.getColumnIndex("last_engagement_time_utc_millis")
            val iBrowsable = c.getColumnIndex("browsable")

            while (c.moveToNext() && out.size < 300) {
                if (iBrowsable >= 0 && !c.isNull(iBrowsable) && c.getInt(iBrowsable) == 0) continue
                val pkg = c.str(iPkg) ?: continue
                val title = c.str(iTitle) ?: continue
                val pos = c.lng(iPos)
                val dur = c.lng(iDur)
                val progress = if (pos != null && dur != null && dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null
                out += ContentItem(
                    key = (if (isWatchNext) "w" else "p") + (c.lng(iId) ?: out.size.toLong()),
                    packageName = pkg,
                    title = title,
                    subtitle = c.str(iEpisode),
                    imageUri = c.str(iThumb) ?: c.str(iPoster),
                    intentUri = c.str(iIntent),
                    progress = progress,
                    lastEngagement = c.lng(iEngaged) ?: 0L,
                    isWatchNext = isWatchNext,
                )
            }
        }
        return out
    }

    private fun Cursor.str(i: Int): String? =
        if (i >= 0 && !isNull(i)) getString(i)?.takeIf { it.isNotBlank() } else null

    private fun Cursor.lng(i: Int): Long? = if (i >= 0 && !isNull(i)) getLong(i) else null
}
