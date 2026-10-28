package dev.litedroid.launcher.games

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.isSelectKey

/** LiteDroid'in içinde gelen, kumandayla oynanan küçük oyunlar. */
enum class BuiltInGame(
    private val tr: String,
    private val en: String,
    private val trDesc: String,
    private val enDesc: String,
    val top: Color,
    val bottom: Color,
    /** Oyun değil araç: Oyunlar sekmesinde ayrı "Araçlar" rafında durur. */
    val isTool: Boolean = false,
) {
    STORY("Masal Makinesi", "Story Machine", "Yapay zekâ yazsın, çizsin, anlatsın", "AI writes, draws and tells it", Color(0xFFFFC75A), Color(0xFF3A2A86)),
    QUIZ("Bilgi Yarışması", "Quiz Night", "Telefonlarla katıl, soruları ilk sen bil", "Join with phones, answer first", Color(0xFFFF5C8A), Color(0xFF5B3FE0)),
    DRAW("Çiz-Bil", "Draw & Guess", "Telefonda çiz, TV'de herkes tahmin etsin", "Draw on your phone, everyone guesses on TV", Color(0xFF3BE0C0), Color(0xFF1F4FD0)),
    SNAKE("Yılan", "Snake", "Elmaları topla, kendine çarpma", "Eat apples, don't bite yourself", Color(0xFF3BE07F), Color(0xFF0B6B3E)),
    BOXES("Kutu İtme", "Box Push", "Kutuları hedeflere it", "Push the boxes onto targets", Color(0xFFFFB13D), Color(0xFF9A4A0C)),
    RACER("Mini Yarış", "Mini Racer", "Şerit değiştir, araçlara çarpma", "Switch lanes, dodge the traffic", Color(0xFFFF5C6E), Color(0xFF8E1430)),
    SOS("SOS", "SOS", "S-O-S diz, puanı kap", "Line up S-O-S to score", Color(0xFF4D9BFF), Color(0xFF16408F)),
    TICTACTOE("XOX", "Tic-Tac-Toe", "Üçünü yan yana getir", "Get three in a row", Color(0xFFB06BFF), Color(0xFF4A1D9E)),
    SUDOKU("Sudoku", "Sudoku", "Her satır, sütun ve kutuda 1-9", "1-9 in every row, column and box", Color(0xFF2ED3C8), Color(0xFF0C5E66)),
    MINES("Mayın Tarlası", "Minesweeper", "Mayınlara basmadan tarlayı temizle", "Clear the field without hitting a mine", Color(0xFFFF8A3D), Color(0xFF8A2E12)),

    // ---- Araçlar ----
    BROWSER("Tarayıcı", "Browser", "Hafif tarayıcı, kendi indiricisiyle", "A light browser with its own downloader", Color(0xFF3BA7FF), Color(0xFF123A8A), isTool = true),
    CALC("Hesap Makinesi", "Calculator", "Kumandayla ya da rakam tuşlarıyla hesapla", "Calculate with the remote or number keys", Color(0xFF4D9BFF), Color(0xFF1B3A7A), isTool = true),
    SPEED("Hız Testi", "Speed Test", "İndirme, yükleme ve ping", "Download, upload and ping", Color(0xFF2ED3C8), Color(0xFF0C4F66), isTool = true),
    SHOTS("Ekran Görüntüleri", "Screenshots", "Her uygulamada çek, telefona al", "Capture any app, get it on your phone", Color(0xFFFF7AB6), Color(0xFF6B1F55), isTool = true),
    SYSINFO("Sistem Özellikleri", "System Info", "Donanım, ekran, ağ ve izinler", "Hardware, display, network and permissions", Color(0xFF9AA6C8), Color(0xFF2B3350), isTool = true),
    AVATAR("Avatar Düzenleyici", "Avatar Maker", "Kendi avatarını yap, profil resmi yap", "Make your avatar, use it as profile picture", Color(0xFFFFB13D), Color(0xFF8E3A9E), isTool = true);

    val title: String get() = tx(tr, en)
    val description: String get() = tx(trDesc, enDesc)
    val hint: String
        get() = when (this) {
            SNAKE -> tx("Yön tuşları: dön · Orta tuş: başla", "Arrows: turn · Center: start")
            BOXES -> tx("Yön tuşları: yürü/it · Orta tuş: bölümü yeniden başlat", "Arrows: walk/push · Center: restart level")
            RACER -> tx("Sol/Sağ: şerit · Orta tuş: başla", "Left/Right: lane · Center: start")
            SOS -> tx("Yön tuşları: gez · Orta tuş: S ya da O koy", "Arrows: move · Center: place S or O")
            TICTACTOE -> tx("Yön tuşları: gez · Orta tuş: X koy", "Arrows: move · Center: place X")
            SUDOKU -> tx("Orta tuş ya da rakam tuşları: sayı yaz · Sağ kenar: sonraki bulmaca", "Center or number keys: enter digit · Right edge: next puzzle")
            MINES -> tx("Yön tuşları: gez · Tamam: aç · Basılı tut: bayrak", "Arrows: move · OK: open · Hold: flag")
            QUIZ -> tx("Telefonlarla QR kodu okut · Tamam: devam", "Scan the QR code with phones · OK: continue")
            DRAW -> tx("Telefonlarla QR kodu okut · Tamam: turu geç", "Scan the QR code with phones · OK: skip the round")
            STORY -> tx("Tamam: duraklat · ▶ sonraki · ◀ önceki", "OK: pause · ▶ next · ◀ previous")
            CALC -> tx("Yön tuşları: gez · Tamam: bas · Rakam tuşları: yaz", "Arrows: move · OK: press · Number keys: type")
            BROWSER -> tx("Yön tuşları: imleç · Tamam: tıkla", "Arrows: pointer · OK: click")
            SPEED -> tx("Tamam: testi başlat", "OK: start the test")
            SHOTS -> tx("Yüzen menüde 📸 Görüntü · Tamam: aç", "📸 Capture in the floating menu · OK: open")
            SYSINFO -> tx("Yön tuşları: kartlar arasında gez", "Arrows: move between cards")
            AVATAR -> tx("◀ ▶ parçayı değiştir · Tamam: sonraki", "◀ ▶ change the part · OK: next")
        }
}

enum class Dir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    fun isOpposite(other: Dir) = dx == -other.dx && dy == -other.dy
}

private fun Key.toDir(): Dir? = when (this) {
    Key.DirectionUp -> Dir.UP
    Key.DirectionDown -> Dir.DOWN
    Key.DirectionLeft -> Dir.LEFT
    Key.DirectionRight -> Dir.RIGHT
    else -> null
}

private fun Key.toDigit(): Int? = when (this) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/**
 * Oyun yüzeyi: odağı alır, yön/orta/rakam tuşlarını oyuna iletir. Geri tuşu dışarı bırakılır
 * (oyundan çıkmak için).
 */
@Composable
fun GameSurface(
    modifier: Modifier = Modifier,
    onDir: (Dir) -> Unit,
    onSelect: () -> Unit,
    onDigit: ((Int) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        modifier
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                val dir = e.key.toDir()
                val digit = e.key.toDigit()
                if (e.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent dir != null || e.key.isSelectKey || (digit != null && onDigit != null)
                }
                when {
                    dir != null -> { onDir(dir); true }
                    e.key.isSelectKey -> {
                        if (e.nativeKeyEvent.repeatCount == 0) onSelect()
                        true
                    }
                    digit != null && onDigit != null -> { onDigit(digit); true }
                    else -> false
                }
            }
            .focusable(),
        content = content,
    )
}

/** En iyi skorlar (oyun başına tek sayı). */
object GameScores {
    /** Rekorlar profile özel. */
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped("litedroid_games"), Context.MODE_PRIVATE)
    fun best(ctx: Context, id: String): Int = prefs(ctx).getInt(id, 0)

    /** Yeni rekor ise kaydeder ve true döner. [lowerIsBetter] hamle sayısı gibi değerler için. */
    fun submit(ctx: Context, id: String, value: Int, lowerIsBetter: Boolean = false): Boolean {
        val old = best(ctx, id)
        val better = if (lowerIsBetter) (old == 0 || value < old) else value > old
        if (better) {
            prefs(ctx).edit().putInt(id, value).apply()
            dev.litedroid.launcher.wrapped.WrappedStats.record(ctx, id, value)
        }
        return better
    }
}

/** Oyunun üstündeki küçük bilgi kutucukları (Skor, En iyi...). */
@Composable
fun GameStats(vararg items: Pair<String, String>, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(label.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Oyun tahtasının üstünde yarı saydam mesaj (Başla / Oyun bitti / Tebrikler). */
@Composable
fun BoxScope.GameMessage(title: String, subtitle: String, accent: Color) {
    Column(
        Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF07080F).copy(alpha = 0.86f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(horizontal = 30.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
    }
}

/** Oyun kartlarındaki küçük çizim simgesi. */
@Composable
fun GameGlyph(game: BuiltInGame, size: Dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val s = w / 10f
        when (game) {
            BuiltInGame.SNAKE -> {
                val p = Path().apply {
                    moveTo(s * 1.5f, s * 7.5f)
                    cubicTo(s * 3f, s * 3f, s * 5f, s * 9f, s * 6.5f, s * 4.5f)
                    lineTo(s * 8.2f, s * 3.2f)
                }
                drawPath(p, color, style = Stroke(width = s * 1.5f, cap = StrokeCap.Round))
                drawCircle(Color(0xFFFF4D5E), radius = s * 1.1f, center = Offset(s * 8.6f, s * 7.6f))
            }
            BuiltInGame.BOXES -> {
                drawRoundRect(color, Offset(s * 2f, s * 2f), Size(s * 6f, s * 6f), CornerRadius(s), style = Stroke(s * 1.2f))
                drawLine(color, Offset(s * 2.5f, s * 2.5f), Offset(s * 7.5f, s * 7.5f), s)
                drawLine(color, Offset(s * 7.5f, s * 2.5f), Offset(s * 2.5f, s * 7.5f), s)
            }
            BuiltInGame.RACER -> {
                drawRoundRect(color, Offset(s * 3f, s * 1.2f), Size(s * 4f, s * 7.6f), CornerRadius(s * 1.4f))
                drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(s * 3.7f, s * 2.6f), Size(s * 2.6f, s * 1.8f), CornerRadius(s * 0.5f))
                drawRect(color, Offset(s * 2.2f, s * 2f), Size(s * 0.8f, s * 1.6f))
                drawRect(color, Offset(s * 7f, s * 2f), Size(s * 0.8f, s * 1.6f))
                drawRect(color, Offset(s * 2.2f, s * 6.4f), Size(s * 0.8f, s * 1.6f))
                drawRect(color, Offset(s * 7f, s * 6.4f), Size(s * 0.8f, s * 1.6f))
            }
            BuiltInGame.SOS -> {
                // S · O · S üç daire, ortadan geçen çizgi
                drawCircle(color, s * 1.4f, Offset(s * 2f, s * 5f), style = Stroke(s * 0.9f))
                drawCircle(color, s * 1.4f, Offset(s * 5f, s * 5f))
                drawCircle(color, s * 1.4f, Offset(s * 8f, s * 5f), style = Stroke(s * 0.9f))
                drawLine(color, Offset(s * 0.6f, s * 5f), Offset(s * 9.4f, s * 5f), s * 0.6f, cap = StrokeCap.Round)
            }
            BuiltInGame.TICTACTOE -> {
                for (i in 1..2) {
                    drawLine(color.copy(alpha = 0.6f), Offset(s * (1 + i * 8f / 3f), s), Offset(s * (1 + i * 8f / 3f), s * 9f), s * 0.6f)
                    drawLine(color.copy(alpha = 0.6f), Offset(s, s * (1 + i * 8f / 3f)), Offset(s * 9f, s * (1 + i * 8f / 3f)), s * 0.6f)
                }
                drawLine(color, Offset(s * 1.6f, s * 1.6f), Offset(s * 3.2f, s * 3.2f), s * 0.8f, cap = StrokeCap.Round)
                drawLine(color, Offset(s * 3.2f, s * 1.6f), Offset(s * 1.6f, s * 3.2f), s * 0.8f, cap = StrokeCap.Round)
                drawCircle(color, s * 0.9f, Offset(s * 5f, s * 5f), style = Stroke(s * 0.7f))
            }
            BuiltInGame.QUIZ -> {
                // Konuşma balonu ve soru işareti
                drawRoundRect(color, Offset(s * 1f, s * 1.5f), Size(s * 8f, s * 5.6f), CornerRadius(s * 1.6f), style = Stroke(s * 0.9f))
                drawPath(Path().apply { moveTo(s * 3f, s * 7f); lineTo(s * 2.6f, s * 9f); lineTo(s * 5f, s * 7f) }, color)
                drawArc(color, 200f, 230f, false, Offset(s * 3.8f, s * 2.4f), Size(s * 2.4f, s * 2.2f), style = Stroke(s * 0.8f, cap = StrokeCap.Round))
                drawLine(color, Offset(s * 5f, s * 4.6f), Offset(s * 5f, s * 5.1f), s * 0.8f, cap = StrokeCap.Round)
                drawCircle(color, s * 0.45f, Offset(s * 5f, s * 6f))
            }
            BuiltInGame.DRAW -> {
                // Eğik kalem ve arkasında dalgalı çizgi
                drawPath(Path().apply {
                    moveTo(s * 1f, s * 8.6f)
                    cubicTo(s * 2.4f, s * 6.4f, s * 3.6f, s * 9.6f, s * 5f, s * 7.6f)
                }, color.copy(alpha = 0.7f), style = Stroke(s * 0.7f, cap = StrokeCap.Round))
                drawLine(color, Offset(s * 5.2f, s * 6.8f), Offset(s * 8.6f, s * 1.6f), s * 1.6f, cap = StrokeCap.Round)
                drawPath(Path().apply { moveTo(s * 4.4f, s * 6.2f); lineTo(s * 6f, s * 7.3f); lineTo(s * 4.3f, s * 8.4f); close() }, color)
            }
            BuiltInGame.STORY -> {
                // Hilal ve yıldız
                val moon = Path().apply { addOval(Rect(s * 1.2f, s * 1.4f, s * 7.6f, s * 7.8f)) }
                val cut = Path().apply { addOval(Rect(s * 3.4f, s * 0.4f, s * 9.4f, s * 6.4f)) }
                drawPath(Path().apply { op(moon, cut, PathOperation.Difference) }, color)
                drawCircle(color, s * 0.7f, Offset(s * 7.6f, s * 7.4f))
                drawCircle(color.copy(alpha = 0.7f), s * 0.45f, Offset(s * 8.6f, s * 4.6f))
            }
            BuiltInGame.BROWSER -> {
                drawCircle(color, s * 4f, Offset(s * 5f, s * 5f), style = Stroke(s * 0.8f))
                drawOval(color, Offset(s * 3f, s * 1f), Size(s * 4f, s * 8f), style = Stroke(s * 0.7f))
                drawLine(color, Offset(s * 1f, s * 5f), Offset(s * 9f, s * 5f), s * 0.7f)
                drawLine(color, Offset(s * 1.7f, s * 3f), Offset(s * 8.3f, s * 3f), s * 0.5f)
                drawLine(color, Offset(s * 1.7f, s * 7f), Offset(s * 8.3f, s * 7f), s * 0.5f)
            }
            BuiltInGame.CALC -> {
                drawRoundRect(color, Offset(s * 2f, s * 1f), Size(s * 6f, s * 8f), CornerRadius(s), style = Stroke(s * 0.8f))
                drawRect(color, Offset(s * 3f, s * 2f), Size(s * 4f, s * 1.6f))
                for (r in 0..2) for (c in 0..2) drawCircle(color, s * 0.45f, Offset(s * (3.3f + c * 1.7f), s * (5f + r * 1.3f)))
            }
            BuiltInGame.SPEED -> {
                drawArc(color, 160f, 220f, false, Offset(s * 1f, s * 1.5f), Size(s * 8f, s * 8f), style = Stroke(s * 0.9f, cap = StrokeCap.Round))
                drawLine(color, Offset(s * 5f, s * 5.5f), Offset(s * 7.4f, s * 3.4f), s * 0.9f, cap = StrokeCap.Round)
                drawCircle(color, s * 0.8f, Offset(s * 5f, s * 5.5f))
            }
            BuiltInGame.SHOTS -> {
                val l = s * 2.4f
                listOf(Offset(s, s) to Offset(1f, 1f), Offset(s * 9f, s) to Offset(-1f, 1f), Offset(s, s * 9f) to Offset(1f, -1f), Offset(s * 9f, s * 9f) to Offset(-1f, -1f)).forEach { (p, d) ->
                    drawLine(color, p, Offset(p.x + d.x * l, p.y), s * 0.8f, cap = StrokeCap.Round)
                    drawLine(color, p, Offset(p.x, p.y + d.y * l), s * 0.8f, cap = StrokeCap.Round)
                }
                drawCircle(color, s * 1.6f, Offset(s * 5f, s * 5f))
            }
            BuiltInGame.SYSINFO -> {
                drawRoundRect(color, Offset(s * 2.5f, s * 2.5f), Size(s * 5f, s * 5f), CornerRadius(s * 0.8f), style = Stroke(s * 0.8f))
                for (i in 0..2) {
                    val p = s * (3.5f + i * 1.5f)
                    drawLine(color, Offset(p, s * 0.8f), Offset(p, s * 2.5f), s * 0.6f)
                    drawLine(color, Offset(p, s * 7.5f), Offset(p, s * 9.2f), s * 0.6f)
                    drawLine(color, Offset(s * 0.8f, p), Offset(s * 2.5f, p), s * 0.6f)
                    drawLine(color, Offset(s * 7.5f, p), Offset(s * 9.2f, p), s * 0.6f)
                }
            }
            BuiltInGame.AVATAR -> {
                drawCircle(color, s * 2.2f, Offset(s * 5f, s * 3.6f))
                drawArc(color, 180f, 180f, true, Offset(s * 1.6f, s * 6.4f), Size(s * 6.8f, s * 5.6f))
            }
            BuiltInGame.MINES -> {
                // Mayın: daire, dikenler ve parıltı
                val c = Offset(s * 5f, s * 5.2f)
                for (a in 0 until 8) {
                    val ang = a * Math.PI / 4
                    drawLine(color, c, Offset(c.x + (kotlin.math.cos(ang) * s * 3.6f).toFloat(), c.y + (kotlin.math.sin(ang) * s * 3.6f).toFloat()), s * 0.8f, cap = StrokeCap.Round)
                }
                drawCircle(color, s * 2.6f, c)
                drawCircle(Color.Black.copy(alpha = 0.35f), s * 0.8f, Offset(c.x - s * 0.9f, c.y - s * 0.9f))
            }
            BuiltInGame.SUDOKU -> {
                drawRoundRect(color, Offset(s, s), Size(s * 8f, s * 8f), CornerRadius(s), style = Stroke(s * 0.8f))
                for (i in 1..2) {
                    drawLine(color, Offset(s * (1 + i * 8f / 3f), s), Offset(s * (1 + i * 8f / 3f), s * 9f), s * 0.5f)
                    drawLine(color, Offset(s, s * (1 + i * 8f / 3f)), Offset(s * 9f, s * (1 + i * 8f / 3f)), s * 0.5f)
                }
                drawCircle(color, s * 0.7f, Offset(s * 5f, s * 5f))
            }
        }
    }
}

/** Tam ekran oyun sayfası: başlık, açıklama, tuş ipucu ve oyunun kendisi. Geri tuşu çıkar. */
@Composable
fun GameScreen(game: BuiltInGame, modifier: Modifier = Modifier, onExit: () -> Unit = {}) {
    // Wrapped için: hangi oyun/araç kaç kez açıldı
    val statsCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(game) { dev.litedroid.launcher.wrapped.WrappedStats.count(statsCtx, (if (game.isTool) "tool." else "game.") + game.name) }
    // Bilgi yarışması kendi tam ekran düzenini kullanır
    if (game == BuiltInGame.QUIZ) {
        dev.litedroid.launcher.quiz.QuizScreen(modifier)
        return
    }
    // Çiz-Bil de
    if (game == BuiltInGame.DRAW) {
        dev.litedroid.launcher.draw.DrawScreen(modifier)
        return
    }
    // Tarayıcı tüm ekranı kullanır
    if (game == BuiltInGame.BROWSER) {
        dev.litedroid.launcher.tools.BrowserScreen(modifier, onExit)
        return
    }
    if (game.isTool) {
        ToolScreen(game, modifier)
        return
    }
    // Masal Makinesi de kendi tam ekranını kullanır
    if (game == BuiltInGame.STORY) {
        dev.litedroid.launcher.story.StoryScreen(modifier, onExit)
        return
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C0E1C), Color(0xFF04050A))))
    ) {
        // Oyunun renginde yumuşak bir ışık
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(game.top.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                    radius = size.maxDimension * 0.5f,
                ),
                radius = size.maxDimension * 0.5f,
                center = Offset(size.width * 0.15f, size.height * 0.1f),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(game.top, game.bottom))),
                    contentAlignment = Alignment.Center,
                ) { GameGlyph(game, 28.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                    Text(game.description, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(game.hint, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(tx("Geri: oyundan çık", "Back: leave the game"), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (game) {
                    BuiltInGame.SNAKE -> SnakeGame(game)
                    BuiltInGame.BOXES -> BoxPushGame(game)
                    BuiltInGame.RACER -> RacerGame(game)
                    BuiltInGame.SOS -> SosGame(game)
                    BuiltInGame.TICTACTOE -> TicTacToeGame(game)
                    BuiltInGame.SUDOKU -> SudokuGame(game)
                    BuiltInGame.MINES -> MinesweeperGame(game)
                    else -> Unit
                }
            }
        }
    }
}

/** Araç sayfası: oyunlarla aynı çerçeve (başlık, ipucu), içinde aracın kendisi. */
@Composable
private fun ToolScreen(tool: BuiltInGame, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C0E1C), Color(0xFF04050A))))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(tool.top.copy(alpha = 0.2f), Color.Transparent), center = Offset(size.width * 0.12f, size.height * 0.08f), radius = size.maxDimension * 0.5f),
                radius = size.maxDimension * 0.5f,
                center = Offset(size.width * 0.12f, size.height * 0.08f),
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(tool.top, tool.bottom))),
                    contentAlignment = Alignment.Center,
                ) { GameGlyph(tool, 26.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tool.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(tool.description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(tool.hint, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(tx("Geri: çık", "Back: leave"), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tool) {
                    BuiltInGame.CALC -> dev.litedroid.launcher.tools.CalculatorTool(tool.top)
                    BuiltInGame.SPEED -> dev.litedroid.launcher.tools.SpeedTestTool(tool.top)
                    BuiltInGame.SHOTS -> dev.litedroid.launcher.tools.ScreenshotsTool(tool.top)
                    BuiltInGame.SYSINFO -> dev.litedroid.launcher.tools.SystemInfoTool(tool.top)
                    BuiltInGame.AVATAR -> dev.litedroid.launcher.tools.AvatarEditorTool(tool.top)
                    else -> Unit
                }
            }
        }
    }
}
