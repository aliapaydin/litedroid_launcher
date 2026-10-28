package dev.litedroid.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.Profile
import dev.litedroid.launcher.data.ProfileKind
import dev.litedroid.launcher.data.Profiles
import dev.litedroid.launcher.tools.ProfileFace
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

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
            SlotPinDialog(
                title = tx("${p.name} için PIN", "PIN for ${p.name}"),
                subtitle = tx("▲▼ rakamı çevir · ◀ ▶ hane · Tamam: giriş", "▲▼ spin · ◀ ▶ digit · OK: enter"),
                face = p,
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
// Slot makinesi PIN seçici
// ---------------------------------------------------------------------------

/**
 * Dört makaralı PIN seçici: ▲▼ makarayı çevirir, ◀ ▶ haneler arasında gezer, Tamam onaylar.
 * Kumandada rakam tuşu varsa o da yazar. [check] doğruysa [onSuccess]; yanlışsa makaralar sallanır.
 */
@Composable
fun SlotPinDialog(
    title: String,
    subtitle: String,
    face: Profile?,
    check: (String) -> Boolean,
    onSuccess: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val reels = remember { mutableStateListOf(0, 0, 0, 0) }
    var index by remember { mutableIntStateOf(0) }
    // Dokunulan haneler: başka haneye geçilince rakam gizlenir (● görünür)
    val touched = remember { mutableStateListOf(false, false, false, false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fr.requestFocus() }
    }
    BackHandler(onBack = onCancel)

    fun pin() = reels.joinToString("") { it.mod(10).toString() }
    fun submit() {
        val value = pin()
        if (check(value)) onSuccess(value) else {
            error = tx("PIN yanlış, tekrar dene", "Wrong PIN, try again")
            scope.launch {
                for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45))
            }
            for (i in 0..3) {
                reels[i] = 0
                touched[i] = false
            }
            index = 0
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
                val digit = when (e.key) {
                    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3
                    Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7
                    Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9
                    else -> null
                }
                when {
                    e.key.isSelectKey -> {
                        if (down && e.nativeKeyEvent.repeatCount == 0) armed = true
                        if (!down && armed) {
                            armed = false
                            submit()
                        }
                    }
                    !down -> Unit
                    digit != null -> {
                        // Rakam tuşu: makara o rakama döner, sonraki haneye geçilir
                        val cur = reels[index]
                        reels[index] = cur - cur.mod(10) + digit
                        touched[index] = true
                        error = null
                        if (index < 3) index++ else submit()
                    }
                    e.key == Key.DirectionUp -> { reels[index] = reels[index] + 1; touched[index] = true; error = null }
                    e.key == Key.DirectionDown -> { reels[index] = reels[index] - 1; touched[index] = true; error = null }
                    e.key == Key.DirectionLeft -> index = (index - 1).coerceAtLeast(0)
                    e.key == Key.DirectionRight -> { touched[index] = true; index = (index + 1).coerceAtMost(3) }
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF221A44), Color(0xFF120F24))))
                .border(1.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(32.dp))
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (face != null) {
                ProfileFace(face, 64.dp)
                Spacer(Modifier.height(10.dp))
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.offset(x = shake.value.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Yalnızca üzerinde durulan makara rakamı gösterir; diğerleri gizli
                for (i in 0..3) Reel(reels[i], focused = i == index, masked = i != index, filled = touched[i])
            }
            Spacer(Modifier.height(14.dp))
            Text(
                error ?: tx("Hepsi tamamsa Tamam'a bas", "Press OK when all set"),
                color = if (error != null) Color(0xFFFF8A9A) else Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Tek makara: sayılar aşağı yukarı kayarak döner, ortadaki seçili rakamdır. */
@Composable
private fun Reel(pos: Int, focused: Boolean, masked: Boolean = false, filled: Boolean = false) {
    // Hızlı ve akıcı: sert bir yay, kısa sürede oturur (basılı tutunca da yetişir)
    val a by animateFloatAsState(pos.toFloat(), spring(dampingRatio = 0.82f, stiffness = 1700f, visibilityThreshold = 0.002f), label = "reel")
    val glow by rememberInfiniteTransition(label = "g").animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "g")
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .size(width = 78.dp, height = 168.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0A16), Color(0xFF26214A), Color(0xFF0B0A16))))
            .border(if (focused) 3.dp else 1.dp, if (focused) Gold.copy(alpha = glow) else Color.White.copy(alpha = 0.15f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val itemH = 54.dp
        val base = floor(a).toInt()
        if (masked) {
            // Gizli hane: girildiyse nokta, girilmediyse çizgi
            Text(if (filled) "●" else "–", color = Color.White.copy(alpha = if (filled) 0.95f else 0.35f), fontSize = if (filled) 30.sp else 40.sp, fontWeight = FontWeight.Black)
        } else for (k in -2..3) {
            val n = base + k
            val off = n - a
            if (abs(off) > 2.2f) continue
            Text(
                "${n.mod(10)}",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                // Yukarı basınca büyük rakam aşağıdan gelir (makara yukarı döner)
                modifier = Modifier
                    .offset(y = itemH * -off)
                    .graphicsLayer {
                        val d = abs(off)
                        alpha = (1f - d * 0.42f).coerceIn(0f, 1f)
                        scaleX = 1f - d * 0.22f
                        scaleY = 1f - d * 0.22f
                    },
            )
        }
        // Üst ve alt gölge: makara derinliği
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
        Box(Modifier.fillMaxWidth().height(2.dp).offset(y = (-30).dp).background(Color.White.copy(alpha = 0.08f)))
        Box(Modifier.fillMaxWidth().height(2.dp).offset(y = 30.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

/**
 * Yetişkin doğrulaması: PIN'i olan bir yetişkin varsa onun PIN'i sorulur (herhangi bir yetişkinin PIN'i kabul).
 * Hiçbir yetişkinin PIN'i yoksa hemen geçer.
 */
@Composable
fun AdultGate(onOk: () -> Unit, onCancel: () -> Unit) {
    val adults = Profiles.adults.filter { it.hasPin }
    if (adults.isEmpty()) {
        LaunchedEffect(Unit) { onOk() }
        return
    }
    SlotPinDialog(
        title = tx("Yetişkin PIN'i", "Adult PIN"),
        subtitle = tx("Devam etmek için bir yetişkin profilinin PIN'i", "Enter an adult profile's PIN to continue"),
        face = null,
        check = { pin -> adults.any { Profiles.checkPin(it, pin) } },
        onSuccess = { onOk() },
        onCancel = onCancel,
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
                            if (p.hasPin) tx("PIN var", "PIN set") else tx("PIN yok", "No PIN"),
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
                    ManagerRow(null, if (current.hasPin) tx("PIN'i değiştir", "Change PIN") else tx("PIN belirle", "Set a PIN"),
                        tx("Profile girerken slot makinesiyle sorulur", "Asked with the slot picker when entering")) { pinStep = current.id to null }
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
                SlotPinDialog(
                    title = if (first == null) tx("Yeni PIN", "New PIN") else tx("PIN'i tekrar gir", "Enter the PIN again"),
                    subtitle = tx("▲▼ rakamı çevir · ◀ ▶ hane · Tamam", "▲▼ spin · ◀ ▶ digit · OK"),
                    face = p,
                    check = { pin -> first == null || pin == first },
                    onSuccess = { pin ->
                        if (first == null) pinStep = id to pin else {
                            Profiles.setPin(ctx, id, pin)
                            pinStep = null
                            message = tx("PIN kaydedildi", "PIN saved")
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
