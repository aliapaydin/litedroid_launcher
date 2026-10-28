package dev.litedroid.launcher.data

import android.content.Context
import dev.litedroid.launcher.tools.AvatarSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class ProfileKind { ADULT, CHILD }

/**
 * Bir LiteDroid profili. Her profilin kendi ayarları, favorileri, oyun rekorları, masalları,
 * izleme geçmişi ve Wrapped'i vardır ([Profiles.scoped] ile ayrı dosyalarda tutulur).
 */
data class Profile(
    val id: String,
    val name: String,
    val kind: ProfileKind = ProfileKind.ADULT,
    /** PIN'in özeti (boşsa PIN yok). PIN'in kendisi saklanmaz. */
    val pinHash: String = "",
    /** Avatar Düzenleyici'den seçilen avatar; yoksa [emoji] ve [color]. */
    val avatar: AvatarSpec? = null,
    val emoji: String = "🙂",
    val color: Int = 0xFF6B4DFF.toInt(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isChild get() = kind == ProfileKind.CHILD
    val hasPin get() = pinHash.isNotEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("kind", kind.name).put("pin", pinHash)
        .put("avatar", avatar?.toJson()).put("emoji", emoji).put("color", color).put("at", createdAt)

    companion object {
        fun from(o: JSONObject) = Profile(
            id = o.getString("id"),
            name = o.optString("name").ifBlank { "Profil" },
            kind = runCatching { ProfileKind.valueOf(o.optString("kind")) }.getOrDefault(ProfileKind.ADULT),
            pinHash = o.optString("pin"),
            avatar = o.optJSONObject("avatar")?.let { runCatching { AvatarSpec.from(it) }.getOrNull() },
            emoji = o.optString("emoji").ifBlank { "🙂" },
            color = o.optInt("color", 0xFF6B4DFF.toInt()),
            createdAt = o.optLong("at"),
        )
    }
}

/**
 * Profiller (en fazla [MAX]). Etkin profil uygulama genelinde tektir; ayar ve veri dosyaları
 * [scoped] ile etkin profile göre seçilir. İlk profil ("main") eski kurulumun dosyalarını aynen kullanır,
 * böylece güncellemede hiçbir şey taşınmadan kaybolmadan kalır.
 */
object Profiles {
    const val MAIN = "main"
    const val MAX = 5
    private const val PREFS = "litedroid_profiles"

    private val _list = MutableStateFlow<List<Profile>>(emptyList())
    val list: StateFlow<List<Profile>> = _list
    private val _active = MutableStateFlow(MAIN)
    val active: StateFlow<String> = _active

    /** Etkin profil kimliği (yüklenmemişse "main"). */
    val activeId: String get() = _active.value
    val activeProfile: Profile? get() = _list.value.firstOrNull { it.id == _active.value }

    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** İlk çağrıda yüklenir; profil yoksa mevcut kurulumdan "main" profili oluşturulur (taşıma). */
    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val p = prefs(ctx)
        val stored = runCatching {
            val arr = JSONArray(p.getString("list", "[]"))
            (0 until arr.length()).map { Profile.from(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        val list = stored.ifEmpty {
            // 3.0 öncesi kurulum: bugünkü ad ve avatar ilk yetişkin profili olur
            val legacy = ctx.applicationContext.getSharedPreferences("litedroid", Context.MODE_PRIVATE)
            val name = legacy.getString("user_name", null)?.takeIf { it.isNotBlank() } ?: "Ali"
            val av = ctx.applicationContext.getSharedPreferences("litedroid_avatars", Context.MODE_PRIVATE)
            val selected = av.getString("selected", null)
            val spec = runCatching {
                val arr = JSONArray(av.getString("list", "[]"))
                (0 until arr.length()).map { AvatarSpec.from(arr.getJSONObject(it)) }.firstOrNull { it.id == selected }
            }.getOrNull()
            listOf(Profile(MAIN, name, ProfileKind.ADULT, avatar = spec, emoji = "😎", color = 0xFF6B4DFF.toInt(), createdAt = 0L))
        }
        _list.value = list
        _active.value = p.getString("active", null)?.takeIf { id -> list.any { it.id == id } } ?: list.first().id
        loaded = true
        if (stored.isEmpty()) persist(ctx)
    }

    private fun persist(ctx: Context) {
        prefs(ctx).edit()
            .putString("list", JSONArray().apply { _list.value.forEach { put(it.toJson()) } }.toString())
            .putString("active", _active.value)
            .apply()
    }

    fun get(id: String): Profile? = _list.value.firstOrNull { it.id == id }

    /**
     * Dosya adı etkin profile göre: "main" profili eski adı kullanır, diğerleri "<ad>__p_<id>".
     * [forProfile] verilirse o profilin dosyası (ör. ebeveyn denetiminde çocuğun verisi).
     */
    fun scoped(base: String, forProfile: String = activeId): String =
        if (forProfile == MAIN) base else "${base}__p_$forProfile"

    fun setActive(ctx: Context, id: String) {
        load(ctx)
        if (_list.value.none { it.id == id }) return
        if (_active.value != id) ProfileTimeline.switched(ctx, id)
        _active.value = id
        persist(ctx)
    }

    @Synchronized
    fun add(ctx: Context, name: String, kind: ProfileKind, emoji: String, color: Int): Profile? {
        load(ctx)
        if (_list.value.size >= MAX) return null
        val p = Profile("p" + System.currentTimeMillis().toString(36), name.trim().take(16).ifBlank { "Profil" }, kind, emoji = emoji, color = color)
        _list.value = _list.value + p
        persist(ctx)
        return p
    }

    @Synchronized
    fun update(ctx: Context, id: String, change: (Profile) -> Profile) {
        load(ctx)
        _list.value = _list.value.map { if (it.id == id) change(it) else it }
        persist(ctx)
    }

    /** Son yetişkin profili silinemez. Silinen profilin verileri de temizlenir. */
    @Synchronized
    fun remove(ctx: Context, id: String): Boolean {
        load(ctx)
        val target = get(id) ?: return false
        if (id == MAIN) return false
        if (!target.isChild && _list.value.count { !it.isChild } <= 1) return false
        _list.value = _list.value.filter { it.id != id }
        if (_active.value == id) _active.value = _list.value.first().id
        persist(ctx)
        ProfileData.erase(ctx, id)
        return true
    }

    fun setPin(ctx: Context, id: String, pin: String?) =
        update(ctx, id) { it.copy(pinHash = if (pin.isNullOrEmpty()) "" else hash(id, pin)) }

    fun checkPin(p: Profile, pin: String): Boolean = !p.hasPin || p.pinHash == hash(p.id, pin)

    private fun hash(id: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("litedroid:$id:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    /** Etkin profil Avatar Düzenleyici'de "profil resmi yap" dediğinde. */
    fun setAvatar(ctx: Context, spec: AvatarSpec?) = update(ctx, activeId) { it.copy(avatar = spec) }

    val adults: List<Profile> get() = _list.value.filter { !it.isChild }
    val children: List<Profile> get() = _list.value.filter { it.isChild }
}

/** Profil başına tutulan dosyalar: profil silinince temizlenir. */
object ProfileData {
    /** Profile göre ayrılan SharedPreferences dosyaları. */
    val PREFS = listOf(
        "litedroid", "litedroid_avatars", "litedroid_games", "litedroid_story", "litedroid_browser",
        "litedroid_bell", "litedroid_stats", "litedroid_media",
    )

    fun erase(ctx: Context, id: String) {
        if (id == Profiles.MAIN) return
        PREFS.forEach { base ->
            val name = Profiles.scoped(base, id)
            runCatching { ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
            runCatching { java.io.File(ctx.filesDir.parentFile, "shared_prefs/$name.xml").delete() }
        }
        runCatching { java.io.File(ctx.filesDir, "wrapped/$id").deleteRecursively() }
        runCatching { java.io.File(ctx.filesDir, Profiles.scoped("stories", id)).deleteRecursively() }
    }
}

/**
 * Hangi profil ne zaman açıktı: Wrapped ve kullanım raporu, cihazın uygulama sürelerini
 * o anda açık olan profile yazar. Son 30 günün geçişleri tutulur.
 */
object ProfileTimeline {
    private const val PREFS = "litedroid_profile_timeline"

    fun switched(ctx: Context, id: String) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val arr = runCatching { JSONArray(p.getString("log", "[]")) }.getOrDefault(JSONArray())
        val keep = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (now - o.optLong("t") < 30L * 24 * 3600_000) keep.put(o)
        }
        keep.put(JSONObject().put("t", now).put("p", id))
        p.edit().putString("log", keep.toString()).apply()
    }

    /**
     * [from, to) aralığında [id] profilinin açık olduğu dilimler. Kayıt öncesi (3.0'dan önce) her şey
     * "main" profiline aittir.
     */
    fun intervals(ctx: Context, id: String, from: Long, to: Long): List<LongRange> {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(p.getString("log", "[]")) }.getOrDefault(JSONArray())
        val events = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optLong("t") to it.optString("p") }.sortedBy { it.first }
        val out = ArrayList<LongRange>()
        var cur = events.lastOrNull { it.first <= from }?.second ?: Profiles.MAIN
        var start = from
        for ((t, pid) in events) {
            if (t <= from) continue
            if (t >= to) break
            if (cur == id && t > start) out += start until t
            cur = pid
            start = t
        }
        if (cur == id && to > start) out += start until to
        return out
    }
}
