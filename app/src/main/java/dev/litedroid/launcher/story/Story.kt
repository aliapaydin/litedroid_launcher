package dev.litedroid.launcher.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.litedroid.launcher.data.GeminiMedia
import dev.litedroid.launcher.data.Pcm
import dev.litedroid.launcher.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Sayfanın havası: müzik ve ışık buna göre değişir. */
enum class Mood { CALM, HAPPY, MYSTERY, ADVENTURE, SLEEPY;
    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: CALM
    }
}

/** Sayfadaki bir satır: anlatıcı ya da bir karakter konuşur. */
data class StoryLine(val speaker: String, val text: String)

data class StoryPage(val id: String, val lines: List<StoryLine>, val imagePrompt: String, val mood: Mood)

data class StoryCharacter(val name: String, val look: String, val voice: String, val style: String)

data class StoryChoice(val question: String, val a: String, val b: String)

data class Story(
    val id: String,
    val title: String,
    val artStyle: String,
    val narratorVoice: String,
    val characters: List<StoryCharacter>,
    val intro: List<StoryPage>,
    val choice: StoryChoice?,
    val branchA: List<StoryPage>,
    val branchB: List<StoryPage>,
    val createdAt: Long,
) {
    /** Seçime göre sayfa sırası. */
    fun pages(choseA: Boolean?): List<StoryPage> = intro + when (choseA) {
        null -> emptyList()
        true -> branchA
        false -> branchB
    }

    fun character(name: String) = characters.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("art_style", artStyle).put("narrator_voice", narratorVoice)
        .put("created", createdAt)
        .put("characters", JSONArray().apply { characters.forEach { put(JSONObject().put("name", it.name).put("look", it.look).put("voice", it.voice).put("style", it.style)) } })
        .put("intro", pagesJson(intro))
        .put("choice", choice?.let { JSONObject().put("question", it.question).put("a", it.a).put("b", it.b) })
        .put("branch_a", pagesJson(branchA))
        .put("branch_b", pagesJson(branchB))

    companion object {
        const val NARRATOR = "Anlatıcı"

        private fun pagesJson(pages: List<StoryPage>) = JSONArray().apply {
            pages.forEach { p ->
                put(JSONObject().put("id", p.id).put("image_prompt", p.imagePrompt).put("mood", p.mood.name.lowercase())
                    .put("lines", JSONArray().apply { p.lines.forEach { put(JSONObject().put("speaker", it.speaker).put("text", it.text)) } }))
            }
        }

        /**
         * Hem yapay zekânın yazdığı hem kitaplıkta saklanan masalı okur. Tanınmayan konuşmacılar
         * anlatıcıya verilir, boş sayfalar atılır.
         */
        fun fromJson(j: JSONObject, id: String, narratorVoice: String, voices: Set<String>): Story {
            val chars = (j.optJSONArray("characters") ?: JSONArray()).let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { c ->
                    val name = c.optString("name").trim()
                    if (name.isBlank()) null
                    else StoryCharacter(
                        name = name,
                        look = c.optString("look"),
                        voice = c.optString("voice").takeIf { it in voices && it != narratorVoice } ?: "",
                        style = c.optString("style").ifBlank { "expressive, playful" },
                    )
                }.take(4)
            }.let { list ->
                // Sesi olmayan ya da aynı sesi alan karakterlere boştaki sesler dağıtılır
                val pool = (voices - narratorVoice).toMutableList()
                val used = HashSet<String>()
                list.map { c ->
                    val v = c.voice.takeIf { it.isNotBlank() && used.add(it) } ?: pool.firstOrNull { it !in used }?.also { used += it } ?: narratorVoice
                    c.copy(voice = v)
                }
            }
            fun pages(key: String, prefix: String): List<StoryPage> {
                val arr = j.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapIndexedNotNull { i, p ->
                    val la = p.optJSONArray("lines") ?: JSONArray()
                    val lines = (0 until la.length()).mapNotNull { la.optJSONObject(it) }.mapNotNull { l ->
                        val text = l.optString("text").trim()
                        if (text.isBlank()) null
                        else {
                            val sp = l.optString("speaker").trim()
                            val known = chars.firstOrNull { it.name.equals(sp, ignoreCase = true) }?.name
                            StoryLine(known ?: NARRATOR, text)
                        }
                    }
                    if (lines.isEmpty()) null
                    else StoryPage(p.optString("id").ifBlank { "$prefix$i" }, lines, p.optString("image_prompt"), Mood.of(p.optString("mood")))
                }
            }
            val choice = j.optJSONObject("choice")?.let {
                val q = it.optString("question").trim()
                val a = it.optString("a").trim()
                val b = it.optString("b").trim()
                if (q.isBlank() || a.isBlank() || b.isBlank()) null else StoryChoice(q, a, b)
            }
            val a = pages("branch_a", "a")
            val b = pages("branch_b", "b")
            return Story(
                id = id,
                title = j.optString("title").ifBlank { tx("Bir varmış bir yokmuş", "Once upon a time") },
                artStyle = j.optString("art_style").ifBlank { "soft watercolor children's picture book, warm cozy light" },
                narratorVoice = j.optString("narrator_voice").ifBlank { narratorVoice },
                characters = chars,
                intro = pages("intro", "i"),
                // Dallardan biri boş geldiyse seçim sorulmaz, dolu olan dal kullanılır
                choice = if (a.isNotEmpty() && b.isNotEmpty()) choice else null,
                branchA = a.ifEmpty { b },
                branchB = b.ifEmpty { a },
                createdAt = j.optLong("created", System.currentTimeMillis()),
            )
        }
    }
}

/** Kullanıcının masal için yaptığı seçimler. */
data class StoryRequest(
    val hero: String,
    val place: String,
    val theme: String,
    val introPages: Int,
    val branchPages: Int,
    val narratorVoice: String,
    val english: Boolean,
)

/** Masalı Gemini'ye yazdırır. */
object StoryWriter {

    /** Karakterlere verilebilecek sesler ve tarifleri (yapay zekâ seçer). */
    val VOICES = linkedMapOf(
        "Leda" to "youthful, childlike girl",
        "Puck" to "upbeat, playful boy",
        "Fenrir" to "excitable, energetic",
        "Laomedeia" to "upbeat, bubbly",
        "Achird" to "friendly, kind",
        "Gacrux" to "mature, wise elder",
        "Algenib" to "gravelly, deep, big creature",
        "Charon" to "calm, deep, knowledgeable",
        "Enceladus" to "breathy, mysterious",
        "Zubenelgenubi" to "casual, funny",
        "Autonoe" to "bright, sparkling",
        "Umbriel" to "easy-going, gentle giant",
    )

    private val TAGS = "<short pause>, <long pause>, <whispers>, <laugh>, <giggle>, <sigh>, <gasp>, <yawn>"

    suspend fun write(key: String, model: String, req: StoryRequest): Story {
        val lang = if (req.english) "English" else "Türkçe"
        val system = """
            Sen çocuklar için yatmadan önce okunacak, sıcak, yaratıcı ve yaşa uygun (4-10 yaş) interaktif masallar yazan usta bir masalcısın.
            Masal dili: $lang. Kısa, akıcı, sesli okunmaya uygun cümleler kur; tekerlemeler, ses yansımaları ve küçük espriler kullan.
            Korku, şiddet, ölüm, yaralanma yok; "kötü" karakterler bile sevimli ve sonunda iyiye döner. Masal huzurlu, uykuya hazırlayan bir kapanışla biter.
            Yalnızca geçerli JSON döndür, başka hiçbir şey yazma.
        """.trimIndent()
        val voices = VOICES.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val prompt = """
            Bu seçimlere göre bir masal yaz:
            - Kahraman: ${req.hero}
            - Yer: ${req.place}
            - Konu / değer: ${req.theme}

            Yapı:
            - "intro": tam ${req.introPages} sayfa. Son intro sayfası heyecanlı bir yol ayrımına gelir.
            - "choice": çocuğa sorulacak soru ve iki seçenek. "question" anlatıcının çocuğa doğrudan sorduğu tek cümle; "a" ve "b" en çok 5 kelimelik seçenekler.
            - "branch_a" ve "branch_b": her biri tam ${req.branchPages} sayfa; ikisi de farklı ama mutlu bir sonla biter. Her dalın son sayfası "mood": "sleepy" olur ve çocuğa iyi geceler dileyerek kapanır.

            Her sayfa:
            - "lines": 2-5 satır, sayfa başına toplam 45-85 kelime. Her satırda "speaker" ("${Story.NARRATOR}" ya da bir karakter adı) ve "text".
            - Bir sayfada anlatıcı dışında EN ÇOK BİR karakter konuşur. Karakter repliklerini anlatıcı satırına koyma, ayrı satır yap.
            - Metinde ara sıra (sayfa başına en çok 2) şu ses etiketlerini kullanabilirsin: $TAGS. Başka etiket, parantez, emoji ya da sahne yönergesi KULLANMA.
            - "image_prompt": İngilizce, sahnenin somut tarifi (kim, ne yapıyor, nerede, ışık, renkler). Resimde yazı olmayacak.
            - "mood": calm | happy | mystery | adventure | sleepy

            Ayrıca:
            - "title": masalın adı ($lang).
            - "art_style": İngilizce, tüm resimlerde aynı kalacak tek cümlelik çizim stili (ör. soft watercolor picture book, warm golden light).
            - "characters": en çok 3 karakter. Her biri: "name" (masaldaki adı), "look" (İngilizce, her resimde aynı kalacak görünüş tarifi), "voice" (aşağıdaki listeden, karaktere uyan; her karaktere farklı ses), "style" (İngilizce, nasıl konuştuğu, ör. "high-pitched, giggly and excited").
            Sesler:
            $voices

            JSON biçimi:
            {"title":"","art_style":"","characters":[{"name":"","look":"","voice":"","style":""}],
             "intro":[{"lines":[{"speaker":"","text":""}],"image_prompt":"","mood":""}],
             "choice":{"question":"","a":"","b":""},
             "branch_a":[...], "branch_b":[...]}
        """.trimIndent()
        val json = GeminiMedia.json(key, model, system, prompt)
        val id = "s" + System.currentTimeMillis()
        val story = Story.fromJson(json, id, req.narratorVoice, VOICES.keys)
        if (story.intro.isEmpty()) throw GeminiMedia.ApiException(500, tx("Masal eksik geldi, tekrar dene", "The story came back incomplete, try again"))
        return story
    }
}

/**
 * Masalların, resimlerinin ve seslerinin saklandığı kitaplık. Bir kez üretilen her şey diske yazılır:
 * aynı masal yeniden dinlenince hiçbir istek atılmaz, ücret çıkmaz. En yeni 15 masal tutulur.
 */
class StoryLibrary(context: Context) {
    private val root = File(context.filesDir, "stories").apply { mkdirs() }

    fun dir(story: Story) = File(root, story.id).apply { mkdirs() }

    fun save(story: Story) {
        File(dir(story), "story.json").writeText(story.toJson().toString())
        prune()
    }

    fun list(narratorVoice: String): List<Story> =
        (root.listFiles()?.toList() ?: emptyList())
            .mapNotNull { d -> runCatching { Story.fromJson(JSONObject(File(d, "story.json").readText()), d.name, narratorVoice, StoryWriter.VOICES.keys) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    fun delete(story: Story) {
        dir(story).deleteRecursively()
    }

    private fun prune() {
        root.listFiles()?.sortedByDescending { it.lastModified() }?.drop(15)?.forEach { it.deleteRecursively() }
    }

    fun imageFile(story: Story, pageId: String) = File(dir(story), "$pageId.jpg")
    fun audioFile(story: Story, pageId: String) = File(dir(story), "$pageId.wav")

    suspend fun loadImage(story: Story, pageId: String): Bitmap? = withContext(Dispatchers.IO) {
        val f = imageFile(story, pageId)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        // TV'de 1280 genişlik yeterli; belleği korur
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    suspend fun loadAudio(story: Story, pageId: String): Pcm? = withContext(Dispatchers.IO) {
        val f = audioFile(story, pageId)
        if (!f.exists()) null else runCatching { Pcm.from(f.readBytes(), null) }.getOrNull()
    }
}
