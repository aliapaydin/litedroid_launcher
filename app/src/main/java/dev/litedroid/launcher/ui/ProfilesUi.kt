package dev.litedroid.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.Profile
import dev.litedroid.launcher.data.ProfileKind
import dev.litedroid.launcher.data.PinGuard
import dev.litedroid.launcher.data.PinKind
import dev.litedroid.launcher.data.Profiles
import dev.litedroid.launcher.tools.ProfileFace
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PickerBg = Brush.verticalGradient(listOf(Color(0xFF15102E), Color(0xFF0A0A18), Color(0xFF05050B)))
private val Gold = Color(0xFFFFD35C)

val PROFILE_EMOJIS = listOf("😎", "🦊", "🐼", "🦁", "🐸", "🦄", "🐙", "🐯", "🐨", "🐵", "🦖", "🐱", "🐶", "👾", "🤖", "🌟")
val PROFILE_COLORS = listOf(0xFF6B4DFF, 0xFFFF5C8A, 0xFF2ED3C8, 0xFFFFB13D, 0xFF3BE07F, 0xFF4D9BFF, 0xFFB06BFF, 0xFFFF7A45).map { it.toInt() }

// ---------------------------------------------------------------------------
// Kim İzliyor?
// ---------------------------------------------------------------------------

/**
 * "Kim İzliyor?": profiller büyük yuvarlaklar halinde. PIN'li profilde slot makinesi PIN seçici açılır.
 * [forced] açılışta / TV uyanınca: Geri ekranı kapatamaz. Elle açıldıysa Geri eski profile döner.
 * [onManage] "Profilleri yönet" (yetişkin doğrulamasından sonra çağrılır).
 */
@Composable
fun WhoIsWatchingScreen(
    forced: Boolean,
    onPick: (Profile) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val profiles by Profiles.list.collectAsState()
    val activeId by Profiles.active.collectAsState()
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var lockedFor by remember { mutableStateOf<Profile?>(null) }
    var gateThen by remember { mutableStateOf<Profile?>(null) }
    var adultGate by remember { mutableStateOf(false) }
    val parentalVersion by dev.litedroid.launcher.data.Parental.version.collectAsState()
    val requesters = remember(profiles.size) { List(profiles.size + 2) { FocusRequester() } }
    val manageFocus = remember { FocusRequester() }
    var restore by remember { mutableIntStateOf(0) }
    LaunchedEffect(restore, profiles.size) {
        delay(120)
        val i = profiles.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        runCatching { requesters[i].requestFocus() }
    }
    BackHandler(enabled = pinFor == null && lockedFor == null && gateThen == null && !adultGate && !forced) { onClose() }

    Box(modifier.fillMaxSize().background(PickerBg)) {
        PickerGlow()
        Column(Modifier.fillMaxSize().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tx("Kim İzliyor?", "Who's watching?"), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.Top) {
                profiles.forEachIndexed { i, p ->
                    key(p.id) {
                        // Kilitli çocuk profili (süre doldu, saat dışı, ebeveyn kapattı) seçilemez
                        val locked = parentalVersion >= 0 && dev.litedroid.launcher.data.Parental.lockReason(ctx, p.id) != null
                        ProfileTile(p, Modifier.focusRequester(requesters[i]), locked) {
                            when {
                                locked -> lockedFor = p
                                p.hasPin -> pinFor = p
                                // Çocuk sınırları varken PIN'siz yetişkin profiline başka bir yetişkinin PIN'iyle girilir
                                !p.isChild && dev.litedroid.launcher.data.Parental.anyLimits(ctx) -> gateThen = p
                                else -> onPick(p)
                            }
                        }
                    }
                }
                if (profiles.size < Profiles.MAX) {
                    AddTile(Modifier.focusRequester(requesters[profiles.size])) { adultGate = true }
                }
            }
            Spacer(Modifier.weight(1f))
            ManagePill(tx("Profilleri yönet", "Manage profiles"), Modifier.focusRequester(manageFocus)) { adultGate = true }
        }

        if (pinFor != null) {
            val p = pinFor!!
            PinDialog(
                title = if (p.pinKind == PinKind.ARROWS) tx("${p.name} için yön kodu", "Arrow code for ${p.name}") else tx("${p.name} için PIN", "PIN for ${p.name}"),
                subtitle = null,
                face = p,
                kind = p.pinKind,
                guardKey = p.id,
                check = { Profiles.checkPin(p, it) },
                onSuccess = {
                    pinFor = null
                    onPick(p)
                },
                onCancel = {
                    pinFor = null
                    restore++
                },
            )
        }
        gateThen?.let { p ->
            AdultGate(
                onOk = {
                    gateThen = null
                    onPick(p)
                },
                onCancel = {
                    gateThen = null
                    restore++
                },
            )
        }
        lockedFor?.let { p ->
            LockedChildDialog(p) {
                lockedFor = null
                restore++
            }
        }
        if (adultGate) {
            AdultGate(
                onOk = {
                    adultGate = false
                    onManage()
                },
                onCancel = {
                    adultGate = false
                    restore++
                },
            )
        }
    }
}

@Composable
private fun PickerGlow() {
    val t by rememberInfiniteTransition(label = "glow").animateFloat(0f, 6.2832f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun blob(c: Color, x: Float, y: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), androidx.compose.ui.geometry.Offset(x, y), r), r, androidx.compose.ui.geometry.Offset(x, y))
        blob(Color(0xFF6B4DFF), w * (0.25f + 0.08f * kotlin.math.cos(t)), h * 0.3f, w * 0.45f)
        blob(Color(0xFFFF5C8A), w * (0.75f + 0.08f * kotlin.math.sin(t)), h * 0.7f, w * 0.4f)
    }
}

@Composable
private fun ProfileTile(p: Profile, modifier: Modifier, locked: Boolean = false, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "s")
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "r")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
        Box(
            modifier
                .size(150.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .onFocusChanged { focused = it.isFocused }
                .onRemoteSelect(onSelect)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            if (focused) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
                    drawCircle(Brush.sweepGradient(listOf(Gold, Color(0xFFFF6FB5), Color(0xFF8E7BFF), Gold)), radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(5.dp.toPx()))
                }
            }
            Box(Modifier.graphicsLayer { alpha = if (locked) 0.45f else 1f }) { ProfileFace(p, 128.dp) }
            if (locked) {
                Text("⏳", fontSize = 40.sp)
            }
            if (p.hasPin) {
                Text("🔒", fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp)
                    .clip(CircleShape).background(Color(0xFF14121F)).padding(5.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(p.name, color = if (focused) Color.White else Color.White.copy(alpha = 0.7f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (locked) {
            Text(tx("Şimdilik kilitli", "Locked for now"), color = Color(0xFFFFB4A8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (p.isChild) {
            Text(tx("ÇOCUK", "KID"), color = Color(0xFF14121F), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color(0xFF7CFFCB)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun AddTile(modifier: Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
        Box(
            modifier
                .size(128.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .onFocusChanged { focused = it.isFocused }
                .onRemoteSelect(onSelect)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = if (focused) Color(0xFF14121F) else Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(22.dp))
        Text(tx("Profil ekle", "Add profile"), color = Color.White.copy(alpha = if (focused) 1f else 0.6f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ManagePill(label: String, modifier: Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        "✏️  $label", color = if (focused) Color(0xFF14121F) else Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onSelect)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

/** Profile girişte altta çıkan karşılama kutusu. */
@Composable
fun BoxScope.WelcomeToast(profile: Profile?) {
    AnimatedVisibility(
        visible = profile != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
    ) {
        var last by remember { mutableStateOf(profile) }
        if (profile != null) last = profile
        val p = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xEE14121F))
                .border(1.5.dp, Brush.linearGradient(listOf(Gold, Color(0xFFFF6FB5), Color(0xFF8E7BFF))), RoundedCornerShape(50))
                .padding(start = 8.dp, end = 26.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileFace(p, 46.dp)
            Spacer(Modifier.width(14.dp))
            Text(tx("Hoş geldin, ${p.name} 👋", "Welcome, ${p.name} 👋"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ---------------------------------------------------------------------------
// PIN: rakam tuş takımı ya da yön kodu
// ---------------------------------------------------------------------------

/** Tuş takımı: 1-9, altta sil · 0 · vazgeç. */
private val PadKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕")

/** Türe göre ekranda yazan kısa yardım. */
fun pinHint(kind: PinKind): String = when (kind) {
    PinKind.DIGITS -> tx("Yön tuşlarıyla rakamı seç, Tamam ile yaz", "Pick a digit with the arrows, OK to type it")
    PinKind.ARROWS -> tx("Kodunu yön tuşlarıyla tuşla · Tamam: son adımı sil", "Press your code on the arrows · OK: undo last step")
}

/**
 * Şifre girişi. İki tür:
 * - [PinKind.DIGITS]: klasik tuş takımı; yön tuşları imleci gezdirir, Tamam seçili rakamı gizli yazar.
 * - [PinKind.ARROWS]: yön kodu; her yön tuşu bir haneyi doldurur. Hangi yöne basıldığı hiçbir yerde görünmez,
 *   yalnızca hanenin dolduğu belli olur. Tamam son adımı siler.
 * Dördüncü hanede Tamam beklenmeden denetlenir: doğruysa [onSuccess], yanlışsa sallanır ve sıfırlanır.
 * [guardKey] verilirse (giriş kapıları) art arda 10 yanlıştan sonra 30 dk giriş kapanır; yeni şifre
 * belirlerken verilmez.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String?,
    face: Profile?,
    check: (String) -> Boolean,
    onSuccess: (String) -> Unit,
    onCancel: () -> Unit,
    kind: PinKind = PinKind.DIGITS,
    guardKey: String? = null,
) {
    val ctx = LocalContext.current
    val entered = remember { mutableStateListOf<Char>() }
    // Başlangıçta ortadaki 5
    var cursor by remember { mutableIntStateOf(4) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var blockedMs by remember { mutableLongStateOf(guardKey?.let { PinGuard.blockedFor(ctx, it) } ?: 0L) }
    val shake = remember { Animatable(0f) }
    val bump = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { fr.requestFocus() }
    }
    // Engel sürerken kalan süre saniye saniye
    LaunchedEffect(blockedMs > 0) {
        while (blockedMs > 0 && guardKey != null) {
            delay(1_000)
            blockedMs = PinGuard.blockedFor(ctx, guardKey)
        }
    }
    BackHandler(onBack = onCancel)

    fun type(c: Char) {
        if (done || blockedMs > 0 || entered.size >= 4) return
        error = null
        entered.add(c)
        scope.launch {
            bump.snapTo(1.25f)
            bump.animateTo(1f, tween(140))
        }
        if (entered.size == 4) {
            val value = entered.joinToString("")
            if (check(value)) {
                done = true
                guardKey?.let { PinGuard.success(ctx, it) }
                onSuccess(value)
            } else {
                val locked = guardKey != null && PinGuard.fail(ctx, guardKey)
                if (locked) {
                    blockedMs = PinGuard.BLOCK_MS
                    error = null
                } else {
                    val left = guardKey?.let { PinGuard.MAX_FAILS - PinGuard.fails(ctx, it) }
                    val base = if (kind == PinKind.ARROWS) tx("Kod yanlış, tekrar dene", "Wrong code, try again") else tx("PIN yanlış, tekrar dene", "Wrong PIN, try again")
                    error = if (left != null && left <= 5) base + tx(" · $left deneme kaldı", " · $left tries left") else base
                }
                scope.launch {
                    for (x in listOf(16f, -14f, 10f, -6f, 3f, 0f)) shake.animateTo(x, tween(40))
                    entered.clear()
                }
            }
        }
    }

    fun undo() {
        if (entered.isNotEmpty() && entered.size < 4) entered.removeAt(entered.lastIndex)
        error = null
    }

    fun press(i: Int) {
        when (val k = PadKeys[i]) {
            "⌫" -> undo()
            "✕" -> onCancel()
            else -> type(k[0])
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back) return@onPreviewKeyEvent false
                val down = e.type == KeyEventType.KeyDown
                if (blockedMs > 0) return@onPreviewKeyEvent true
                if (kind == PinKind.ARROWS) {
                    // Yön kodu: basılı tutmak tekrar saymaz, her basış bir hane
                    val first = down && e.nativeKeyEvent.repeatCount == 0
                    when {
                        e.key.isSelectKey -> {
                            if (first) armed = true
                            if (!down && armed) {
                                armed = false
                                undo()
                            }
                        }
                        !first -> Unit
                        e.key == Key.DirectionUp -> type('U')
                        e.key == Key.DirectionDown -> type('D')
                        e.key == Key.DirectionLeft -> type('L')
                        e.key == Key.DirectionRight -> type('R')
                        e.key == Key.Backspace || e.key == Key.Delete -> undo()
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = when (e.key) {
                    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3
                    Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7
                    Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9
                    else -> null
                }
                when {
                    e.key.isSelectKey -> {
                        // Önceki ekrandan kalan bırakma sayılmaz: basılıp bırakılınca yazar
                        if (down && e.nativeKeyEvent.repeatCount == 0) armed = true
                        if (!down && armed) {
                            armed = false
                            press(cursor)
                        }
                    }
                    !down -> Unit
                    digit != null -> type('0' + digit)
                    e.key == Key.Backspace || e.key == Key.Delete -> undo()
                    e.key == Key.DirectionUp -> cursor = (cursor + 9) % 12
                    e.key == Key.DirectionDown -> cursor = (cursor + 3) % 12
                    e.key == Key.DirectionLeft -> cursor = cursor - cursor % 3 + (cursor % 3 + 2) % 3
                    e.key == Key.DirectionRight -> cursor = cursor - cursor % 3 + (cursor % 3 + 1) % 3
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFF16141F))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
                .padding(horizontal = 36.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (face != null) {
                ProfileFace(face, 56.dp)
                Spacer(Modifier.height(10.dp))
            }
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle ?: pinHint(kind), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            if (blockedMs > 0) {
                // Çok fazla yanlış: süre dolana kadar giriş kapalı
                val sec = (blockedMs + 999) / 1000
                Text("🔒", fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text(tx("Çok fazla yanlış deneme", "Too many wrong tries"), color = Color(0xFFFF8A9A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    tx("%d:%02d sonra tekrar dene".format(sec / 60, sec % 60), "Try again in %d:%02d".format(sec / 60, sec % 60)),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                return@Column
            }
            // Dört gizli hane
            Row(Modifier.offset(x = shake.value.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (i in 0..3) {
                    val filled = i < entered.size
                    val next = i == entered.size
                    Box(
                        Modifier
                            .size(width = 46.dp, height = 54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = if (next) 0.35f else 0.10f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            val sc = if (i == entered.lastIndex) bump.value else 1f
                            Box(Modifier.size(12.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(Color.White))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(error ?: " ", color = Color(0xFFFF8A9A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (kind == PinKind.ARROWS) {
                // Sabit bir kumanda simgesi: hiçbir yön yanmaz, yalnızca ne yapılacağını anlatır
                Box(
                    Modifier.size(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val c = Color.White.copy(alpha = 0.22f)
                    Text("▲", color = c, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
                    Text("▼", color = c, fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
                    Text("◀", color = c, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
                    Text("▶", color = c, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
                }
                return@Column
            }
            // Tuş takımı: tek odak, imleç yalnızca biraz daha açık zemin ve ince çerçeve
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (r in 0..3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0..2) {
                            val i = r * 3 + c
                            val on = i == cursor
                            val label = PadKeys[i]
                            Box(
                                Modifier
                                    .size(width = 76.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = if (on) 0.16f else 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = if (on) 0.38f else 0f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = Color.White.copy(alpha = if (label[0].isDigit()) 0.85f else 0.5f),
                                    fontSize = if (label[0].isDigit()) 24.sp else 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Yetişkinler farklı şifre türü kullanıyorsa önce tür seçilir (hangisi seçilirse onun şifresi sorulur). */
@Composable
private fun PinKindChooser(onPick: (PinKind) -> Unit, onCancel: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { fr.requestFocus() }
    }
    BackHandler(onBack = onCancel)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFF16141F))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp)).padding(horizontal = 36.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tx("Yetişkin doğrulaması", "Adult check"), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(tx("Hangi yöntemle gireceksin?", "How do you want to enter?"), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(PinKind.DIGITS to ("🔢" to tx("Rakam PIN", "Digit PIN")), PinKind.ARROWS to ("🕹️" to tx("Yön kodu", "Arrow code"))).forEachIndexed { i, (k, v) ->
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        (if (i == 0) Modifier.focusRequester(fr) else Modifier)
                            .size(width = 150.dp, height = 110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = if (focused) 0.16f else 0.05f))
                            .border(1.dp, Color.White.copy(alpha = if (focused) 0.38f else 0.08f), RoundedCornerShape(20.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .onRemoteSelect({ onPick(k) })
                            .focusable(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(v.first, fontSize = 30.sp)
                        Text(v.second, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Yetişkin doğrulaması: PIN'i olan bir yetişkin varsa onun şifresi sorulur (herhangi bir yetişkininki kabul).
 * Yetişkinlerin şifre türleri farklıysa önce tür seçilir. Hiçbir yetişkinin şifresi yoksa hemen geçer.
 * 10 yanlışta 30 dk kapanır (tüm yetişkinler için tek sayaç).
 */
@Composable
fun AdultGate(onOk: () -> Unit, onCancel: () -> Unit) {
    val adults = Profiles.adults.filter { it.hasPin }
    if (adults.isEmpty()) {
        LaunchedEffect(Unit) { onOk() }
        return
    }
    val kinds = adults.map { it.pinKind }.distinct()
    var kind by remember { mutableStateOf(kinds.singleOrNull()) }
    val k = kind
    if (k == null) {
        PinKindChooser(onPick = { kind = it }, onCancel = onCancel)
        return
    }
    PinDialog(
        title = if (k == PinKind.ARROWS) tx("Yetişkin yön kodu", "Adult arrow code") else tx("Yetişkin PIN'i", "Adult PIN"),
        subtitle = null,
        face = null,
        check = { pin -> adults.any { it.pinKind == k && Profiles.checkPin(it, pin) } },
        onSuccess = { onOk() },
        onCancel = { if (kinds.size > 1) kind = null else onCancel() },
        kind = k,
        guardKey = PinGuard.ADULTS,
    )
}

// ---------------------------------------------------------------------------
// Profilleri yönet
// ---------------------------------------------------------------------------

/**
 * Profilleri yönet: ekle (yetişkin/çocuk), ad, simge, renk, PIN, tür ve silme.
 * En fazla [Profiles.MAX] profil; son yetişkin silinemez ve çocuğa çevrilemez.
 */
@Composable
fun ProfileManagerScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val profiles by Profiles.list.collectAsState()
    val activeId by Profiles.active.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<String?>(null) }
    var pinStep by remember { mutableStateOf<Pair<String, String?>?>(null) }
    /** Belirlenen yeni şifrenin türü (PIN'i değiştir: mevcut tür; Giriş yöntemi: diğer tür). */
    var pinStepKind by remember { mutableStateOf(PinKind.DIGITS) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val listFocus = remember { FocusRequester() }
    val editFocus = remember { FocusRequester() }

    LaunchedEffect(editing, naming, pinStep) {
        if (naming != null || pinStep != null) return@LaunchedEffect
        delay(120)
        runCatching { if (editing != null) editFocus.requestFocus() else listFocus.requestFocus() }
    }
    BackHandler(enabled = naming == null && pinStep == null) {
        if (editing != null) {
            editing = null
            confirmDelete = false
            message = null
        } else onClose()
    }

    Box(modifier.fillMaxSize().background(PickerBg)) {
        val current = profiles.firstOrNull { it.id == editing }
        if (current == null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 80.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "head") {
                    Column {
                        Text(tx("Profiller", "Profiles"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            tx("En fazla ${Profiles.MAX} profil. Her profilin kendi ayarları, favorileri, oyun rekorları, masalları ve Wrapped'i olur.",
                                "Up to ${Profiles.MAX} profiles. Each has its own settings, favorites, game records, stories and Wrapped."),
                            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(profiles, key = { it.id }) { p ->
                    val first = p.id == profiles.first().id
                    ManagerRow(
                        face = p, title = p.name + if (p.id == activeId) tx("  · şu an açık", "  · current") else "",
                        subtitle = listOfNotNull(
                            if (p.isChild) tx("Çocuk", "Kid") else tx("Yetişkin", "Adult"),
                            when {
                                !p.hasPin -> tx("PIN yok", "No PIN")
                                p.pinKind == PinKind.ARROWS -> tx("Yön kodu var", "Arrow code set")
                                else -> tx("PIN var", "PIN set")
                            },
                        ).joinToString(" · "),
                        modifier = if (first) Modifier.focusRequester(listFocus) else Modifier,
                    ) { editing = p.id }
                }
                if (profiles.size < Profiles.MAX) {
                    item(key = "add_adult") {
                        ManagerRow(null, tx("＋  Yetişkin ekle", "＋  Add adult"), tx("Kendi ayarları ve isterse PIN'i olan bir profil", "A profile with its own settings and optional PIN")) {
                            Profiles.add(ctx, tx("Yeni profil", "New profile"), ProfileKind.ADULT, PROFILE_EMOJIS[profiles.size % PROFILE_EMOJIS.size], PROFILE_COLORS[profiles.size % PROFILE_COLORS.size])
                                ?.let { editing = it.id; naming = it.id }
                        }
                    }
                    item(key = "add_child") {
                        ManagerRow(null, tx("＋  Çocuk ekle", "＋  Add kid"), tx("Ebeveyn denetimiyle süre ve uygulama sınırı konabilir", "Can have time and app limits with parental controls")) {
                            Profiles.add(ctx, tx("Çocuk", "Kid"), ProfileKind.CHILD, PROFILE_EMOJIS[(profiles.size + 5) % PROFILE_EMOJIS.size], PROFILE_COLORS[(profiles.size + 3) % PROFILE_COLORS.size])
                                ?.let { editing = it.id; naming = it.id }
                        }
                    }
                }
            }
        } else {
            val adults = profiles.count { !it.isChild }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 80.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "head") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileFace(current, 72.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(current.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                            Text(if (current.isChild) tx("Çocuk profili", "Kid profile") else tx("Yetişkin profili", "Adult profile"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item(key = "name") {
                    ManagerRow(null, tx("Ad", "Name"), current.name, Modifier.focusRequester(editFocus)) { naming = current.id }
                }
                item(key = "emoji") {
                    ManagerRow(null, tx("Simge", "Icon"), current.emoji + if (current.avatar != null) tx("  (avatar kullanılıyor)", "  (avatar in use)") else "") {
                        Profiles.update(ctx, current.id) { it.copy(emoji = PROFILE_EMOJIS[(PROFILE_EMOJIS.indexOf(it.emoji) + 1).mod(PROFILE_EMOJIS.size)], avatar = null) }
                    }
                }
                item(key = "color") {
                    ManagerRow(null, tx("Renk", "Color"), tx("Değiştirmek için Tamam", "OK to change"), trailing = Color(current.color)) {
                        Profiles.update(ctx, current.id) { it.copy(color = PROFILE_COLORS[(PROFILE_COLORS.indexOf(it.color) + 1).mod(PROFILE_COLORS.size)]) }
                    }
                }
                item(key = "avatar") {
                    ManagerRow(null, tx("Avatar", "Avatar"),
                        tx("Bu profile girip Ekstra → Avatar Düzenleyici → \"Profil resmi yap\"", "Enter this profile, then Extras → Avatar Maker → \"Use as profile\"")) {}
                }
                item(key = "kind") {
                    ManagerRow(null, tx("Tür", "Type"), if (current.isChild) tx("Çocuk", "Kid") else tx("Yetişkin", "Adult")) {
                        if (!current.isChild && adults <= 1) {
                            message = tx("En az bir yetişkin profili olmalı", "There must be at least one adult profile")
                        } else if (current.isChild && !current.hasPin && dev.litedroid.launcher.data.Parental.anyLimits(ctx)) {
                            message = tx("Çocuk sınırları varken yetişkin profillerinin PIN'i olmalı: önce bu profile PIN belirle", "Adult profiles need a PIN while kid limits exist: set a PIN for this profile first")
                        } else Profiles.update(ctx, current.id) { it.copy(kind = if (it.isChild) ProfileKind.ADULT else ProfileKind.CHILD) }
                    }
                }
                item(key = "pin") {
                    val arrows = current.pinKind == PinKind.ARROWS
                    ManagerRow(null,
                        when {
                            !current.hasPin -> tx("PIN belirle", "Set a PIN")
                            arrows -> tx("Yön kodunu değiştir", "Change arrow code")
                            else -> tx("PIN'i değiştir", "Change PIN")
                        },
                        if (arrows) tx("Profile girerken kumandanın yön tuşlarıyla sorulur", "Asked on the remote's arrows when entering")
                        else tx("Profile girerken tuş takımıyla sorulur", "Asked on the keypad when entering")) {
                        pinStepKind = if (current.hasPin) current.pinKind else PinKind.DIGITS
                        pinStep = current.id to null
                    }
                }
                item(key = "pin_kind") {
                    val arrows = current.hasPin && current.pinKind == PinKind.ARROWS
                    ManagerRow(null, tx("Giriş yöntemi", "Entry method"),
                        if (arrows) tx("Yön kodu · rakam PIN'e geçmek için Tamam", "Arrow code · OK to switch to a digit PIN")
                        else tx("Rakam PIN · yön koduna geçmek için Tamam (ör. ↑↑←→, dışarıdan anlaşılmaz)", "Digit PIN · OK to switch to an arrow code (e.g. ↑↑←→, hard to spot)")) {
                        pinStepKind = if (arrows) PinKind.DIGITS else PinKind.ARROWS
                        pinStep = current.id to null
                    }
                }
                if (current.hasPin) {
                    item(key = "pin_off") {
                        ManagerRow(null, tx("PIN'i kaldır", "Remove PIN"), null) {
                            if (!current.isChild && dev.litedroid.launcher.data.Parental.anyLimits(ctx)) {
                                message = tx("Bir çocuk profilinde sınır varken yetişkin PIN'i kaldırılamaz", "An adult PIN can't be removed while a kid profile has limits")
                            } else {
                                Profiles.setPin(ctx, current.id, null)
                                message = tx("PIN kaldırıldı", "PIN removed")
                            }
                        }
                    }
                }
                val canDelete = current.id != Profiles.MAIN && (current.isChild || adults > 1)
                if (canDelete) {
                    item(key = "delete") {
                        ManagerRow(null, if (confirmDelete) tx("Emin misin? Silmek için tekrar Tamam", "Sure? Press OK again to delete") else tx("Profili sil", "Delete profile"),
                            tx("Ayarları, rekorları, masalları ve Wrapped'i de silinir", "Its settings, records, stories and Wrapped are deleted too"), destructive = true) {
                            if (!confirmDelete) confirmDelete = true else {
                                Profiles.remove(ctx, current.id)
                                confirmDelete = false
                                editing = null
                            }
                        }
                    }
                }
                message?.let { m -> item(key = "msg") { Text(m, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }
            }
        }

        naming?.let { id ->
            val p = profiles.firstOrNull { it.id == id }
            if (p != null) {
                BackHandler { naming = null }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    TextInputDialog(
                        title = tx("Profil adı", "Profile name"),
                        subtitle = tx("\"Kim İzliyor?\" ekranında ve selamlamada görünür", "Shown on \"Who's watching?\" and in the greeting"),
                        initial = p.name,
                        allowEmpty = false,
                        maxLength = 16,
                        onSave = { v ->
                            Profiles.update(ctx, id) { it.copy(name = v.trim().take(16).ifBlank { it.name }) }
                            naming = null
                            // Yeni yetişkin: çocuk sınırları varsa PIN hemen istenir
                            if (!p.isChild && !p.hasPin && dev.litedroid.launcher.data.Parental.anyLimits(ctx)) {
                                pinStepKind = PinKind.DIGITS
                                pinStep = id to null
                                message = tx("Çocuk sınırları olduğu için bu yetişkin profiline PIN gerekli", "Kid limits are on, so this adult profile needs a PIN")
                            }
                        },
                        onCancel = { naming = null },
                    )
                }
            }
        }
        pinStep?.let { (id, first) ->
            val p = profiles.firstOrNull { it.id == id }
            key(first) {
                val arrows = pinStepKind == PinKind.ARROWS
                PinDialog(
                    title = when {
                        first != null && arrows -> tx("Kodu tekrar tuşla", "Press the code again")
                        first != null -> tx("PIN'i tekrar gir", "Enter the PIN again")
                        arrows -> tx("Yeni yön kodu", "New arrow code")
                        else -> tx("Yeni PIN", "New PIN")
                    },
                    subtitle = if (arrows && first == null) tx("4 yön seç (ör. ↑ ↑ ← →) · Tamam: son adımı sil", "Choose 4 arrows (e.g. ↑ ↑ ← →) · OK: undo last step") else null,
                    face = p,
                    kind = pinStepKind,
                    check = { pin -> first == null || pin == first },
                    onSuccess = { pin ->
                        if (first == null) pinStep = id to pin else {
                            Profiles.setPin(ctx, id, pin, pinStepKind)
                            pinStep = null
                            message = if (arrows) tx("Yön kodu kaydedildi", "Arrow code saved") else tx("PIN kaydedildi", "PIN saved")
                        }
                    },
                    onCancel = { pinStep = null },
                )
            }
        }
    }
}

@Composable
private fun ManagerRow(
    face: Profile?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: Color? = null,
    destructive: Boolean = false,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.06f), label = "bg")
    val ink = when {
        destructive && focused -> Color(0xFFD93025)
        destructive -> Color(0xFFFF8A8A)
        focused -> Color(0xFF14121F)
        else -> Color.White
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onSelect)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (face != null) {
            ProfileFace(face, 44.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) Text(subtitle, color = ink.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        if (trailing != null) Box(Modifier.size(26.dp).clip(CircleShape).background(trailing).border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape))
    }
}
