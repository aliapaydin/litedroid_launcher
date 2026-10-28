package dev.litedroid.launcher.tools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.isSelectKey
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private const val TAG = "LiteDroidBrowser"

private data class QuickSite(val title: String, val url: String, val color: Color)

private val QUICK = listOf(
    QuickSite("Google", "https://www.google.com", Color(0xFF4285F4)),
    QuickSite("YouTube", "https://m.youtube.com", Color(0xFFFF3B30)),
    QuickSite(tx("Vikipedi", "Wikipedia"), tx("https://tr.m.wikipedia.org", "https://en.m.wikipedia.org"), Color(0xFF9AA0A6)),
    QuickSite(tx("Haberler", "News"), "https://news.google.com", Color(0xFF34A853)),
    QuickSite("GitHub", "https://github.com", Color(0xFF6E5494)),
    QuickSite(tx("Hava durumu", "Weather"), "https://www.windy.com", Color(0xFF2ED3C8)),
)

/** Adres çubuğuna yazılan: adres mi, arama mı. */
private fun toUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return "https://www.google.com"
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (!t.contains(' ') && t.contains('.') && !t.endsWith('.')) return "https://$t"
    return "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
}

private object BrowserHistory {
    fun load(ctx: Context): List<Pair<String, String>> = runCatching {
        val a = JSONArray(ctx.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped("litedroid_browser"), Context.MODE_PRIVATE).getString("history", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("t") to o.getString("u") } }
    }.getOrDefault(emptyList())

    fun add(ctx: Context, title: String, url: String) {
        if (!url.startsWith("http")) return
        val list = (listOf(title.ifBlank { url } to url) + load(ctx).filter { it.second != url }).take(12)
        val a = JSONArray().apply { list.forEach { put(JSONObject().put("t", it.first).put("u", it.second)) } }
        ctx.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped("litedroid_browser"), Context.MODE_PRIVATE).edit().putString("history", a.toString()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped("litedroid_browser"), Context.MODE_PRIVATE).edit().remove("history").apply()
    }
}

/** Sayfadaki odaklı metin kutusu (arama kutusu, form alanı) için okuma/yazma. */
private const val JS_ACTIVE_INPUT = """(function(){var e=document.activeElement;if(!e)return '';var t=e.tagName;
var bad=['button','submit','checkbox','radio','range','color','file','image','reset','hidden'];
if((t==='INPUT'&&bad.indexOf((e.type||'').toLowerCase())<0)||t==='TEXTAREA'){return JSON.stringify({v:e.value||'',p:e.placeholder||e.getAttribute('aria-label')||'',pw:(e.type||'')==='password'});}
if(e.isContentEditable){return JSON.stringify({v:e.innerText||'',p:'',pw:false});}return '';})()"""

private fun jsSetInput(value: String, submit: Boolean) = """(function(v,go){var e=document.activeElement;if(!e)return;
if(e.isContentEditable&&e.tagName!=='INPUT'&&e.tagName!=='TEXTAREA'){e.innerText=v;}else{
var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');if(d&&d.set){d.set.call(e,v);}else{e.value=v;}}
e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));
if(go){['keydown','keypress','keyup'].forEach(function(n){e.dispatchEvent(new KeyboardEvent(n,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));});
var f=e.form;if(f){if(f.requestSubmit){f.requestSubmit();}else{f.submit();}}}})(${JSONObject.quote(value)},$submit)"""

private class TextRequest(val title: String, val initial: String, val password: Boolean, val forUrl: Boolean)

/**
 * LiteDroid tarayıcısı: cihazdaki Android System WebView motoruyla tek sekmeli, hafif bir tarayıcı.
 * Kumandayla imleç (fare oku) gezdirilir, Tamam tıklar; kenarlarda sayfa kayar. Sayfadaki metin
 * kutularına LiteDroid'in kendi giriş penceresiyle yazılır. İndirmeler kendi indiricisine gider.
 * Kapanınca WebView tamamen yok edilir; arkada bellek ya da işlem bırakmaz.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var webError by remember { mutableStateOf<String?>(null) }
    var showHome by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(100) }
    var canBack by remember { mutableStateOf(false) }
    var desktop by remember { mutableStateOf(false) }
    var textZoom by remember { mutableIntStateOf(110) }
    var panel by remember { mutableStateOf(false) }
    var textReq by remember { mutableStateOf<TextRequest?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(BrowserHistory.load(context)) }
    // İmleç (px, sayfa alanına göre)
    var cx by remember { mutableFloatStateOf(-1f) }
    var cy by remember { mutableFloatStateOf(-1f) }
    var pageW by remember { mutableFloatStateOf(1f) }
    var pageH by remember { mutableFloatStateOf(1f) }
    val pageFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }
    val homeFocus = remember { FocusRequester() }
    remember { Downloader.loadExisting(context.applicationContext) }

    fun createWeb(): WebView? = web ?: runCatching {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Odak Compose'da kalır; tıklamalar ve kaydırma imleçten gönderilir
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                setGeolocationEnabled(false)
                allowFileAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                this.textZoom = textZoom
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    if (u.scheme == "http" || u.scheme == "https") return false
                    // intent:, market: gibi adresler ilgili uygulamada açılır
                    runCatching {
                        val i = if (u.scheme == "intent") Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, u)
                        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { toast = tx("Bu bağlantı açılamadı", "Couldn't open this link") }
                    return true
                }

                override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                    url = u
                    canBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, u: String) {
                    url = u
                    canBack = view.canGoBack()
                    BrowserHistory.add(context, view.title.orEmpty(), u)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, t: String?) {
                    title = t.orEmpty()
                }

                // Kamera, mikrofon, konum gibi izinler verilmez
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.deny()
                }
            }
            setDownloadListener { dUrl, ua, disposition, mime, _ ->
                val name = URLUtil.guessFileName(dUrl, disposition, mime)
                Downloader.start(context, dUrl, ua, name, mime)
                panel = true
                toast = tx("İndiriliyor: $name", "Downloading: $name")
            }
        }.also { web = it }
    }.getOrElse {
        Log.w(TAG, "webview unavailable", it)
        webError = tx("Bu cihazda Android System WebView yok ya da kapalı. Play Store'dan \"Android System WebView\"ı kurup güncelle.",
            "Android System WebView is missing or disabled. Install or update \"Android System WebView\" from the Play Store.")
        null
    }

    fun go(input: String) {
        val w = createWeb() ?: return
        showHome = false
        w.loadUrl(toUrl(input))
        scope.launch {
            delay(150)
            runCatching { pageFocus.requestFocus() }
        }
    }

    // Kapanınca: sayfa durur, motor ve belleği tamamen bırakılır
    DisposableEffect(Unit) {
        onDispose {
            web?.let { w ->
                runCatching {
                    w.stopLoading()
                    w.loadUrl("about:blank")
                    w.onPause()
                    w.removeAllViews()
                    w.destroy()
                }
            }
            web = null
        }
    }
    // Ana ekrana dönülünce (LiteDroid arkaya geçince) JavaScript ve zamanlayıcılar durur
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> web?.let { it.onPause(); it.pauseTimers() }
                Lifecycle.Event.ON_START -> web?.let { it.onResume(); it.resumeTimers() }
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    // Telefondan "TV'de aç" ile gelen bağlantılar
    LaunchedEffect(Unit) {
        BrowserLaunch.pending.collect { u ->
            if (u != null) {
                BrowserLaunch.pending.value = null
                go(u)
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_500)
            toast = null
        }
    }
    LaunchedEffect(showHome) {
        delay(120)
        runCatching { if (showHome) homeFocus.requestFocus() else pageFocus.requestFocus() }
    }

    BackHandler {
        when {
            textReq != null -> textReq = null
            panel -> panel = false
            !showHome && web?.canGoBack() == true -> web?.goBack()
            !showHome -> showHome = true
            else -> onExit()
        }
    }

    fun clickAt(x: Float, y: Float) {
        val w = web ?: return
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        w.dispatchTouchEvent(down)
        w.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        // Bir metin kutusuna tıklandıysa LiteDroid'in giriş penceresi açılır
        scope.launch {
            delay(350)
            w.evaluateJavascript(JS_ACTIVE_INPUT) { raw ->
                val s = runCatching { JSONArray("[$raw]").getString(0) }.getOrNull().orEmpty()
                if (s.isNotEmpty()) {
                    val o = runCatching { JSONObject(s) }.getOrNull() ?: return@evaluateJavascript
                    textReq = TextRequest(o.optString("p").ifBlank { tx("Yaz", "Type") }, o.optString("v"), o.optBoolean("pw"), forUrl = false)
                }
            }
        }
    }

    fun wheel(x: Float, y: Float, v: Float, h: Float) {
        val w = web ?: return
        val t = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            setAxisValue(MotionEvent.AXIS_VSCROLL, v)
            setAxisValue(MotionEvent.AXIS_HSCROLL, h)
        })
        val e = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        w.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0D0E16))) {
        // ---- Araç çubuğu ----
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF171826)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BarButton("✕", Modifier) { onExit() }
            BarButton("◀", Modifier.focusRequester(barFocus), enabled = canBack || !showHome) {
                if (web?.canGoBack() == true && !showHome) web?.goBack() else showHome = true
            }
            BarButton("▶", Modifier, enabled = web?.canGoForward() == true) { web?.goForward() }
            BarButton(if (progress < 100) "■" else "↻", Modifier, enabled = !showHome) { if (progress < 100) web?.stopLoading() else web?.reload() }
            BarButton("⌂", Modifier) { showHome = true }
            // Adres çubuğu
            var addrFocused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (addrFocused) Color.White else Color.White.copy(alpha = 0.08f))
                    .onFocusChanged { addrFocused = it.isFocused }
                    .onRemoteSelect({ textReq = TextRequest(tx("Adres ya da arama", "Address or search"), if (showHome) "" else url, false, forUrl = true) })
                    .focusable()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val secure = url.startsWith("https://")
                Text(
                    if (showHome || url.isBlank()) tx("🔍  Ara ya da adres yaz", "🔍  Search or type an address")
                    else (if (secure) "🔒 " else "") + url.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                    color = if (addrFocused) Color(0xFF14131C) else Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BarButton("A−", Modifier, enabled = !showHome) {
                textZoom = (textZoom - 15).coerceAtLeast(70)
                web?.settings?.textZoom = textZoom
            }
            BarButton("A+", Modifier, enabled = !showHome) {
                textZoom = (textZoom + 15).coerceAtMost(220)
                web?.settings?.textZoom = textZoom
            }
            BarButton(if (desktop) "🖥" else "📱", Modifier, enabled = !showHome) {
                desktop = !desktop
                web?.let { w ->
                    w.settings.userAgentString = if (desktop) DESKTOP_UA else null
                    w.settings.useWideViewPort = true
                    w.reload()
                }
                toast = if (desktop) tx("Masaüstü sürümü", "Desktop version") else tx("Mobil sürüm", "Mobile version")
            }
            val active = Downloader.active
            BarButton(if (active > 0) "⬇ $active" else "⬇", Modifier) { panel = !panel }
        }
        // İlerleme çubuğu
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF171826))) {
            if (progress < 100 && !showHome) Box(Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(Color(0xFF3BA7FF)))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // ---- Sayfa + imleç ----
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .focusRequester(pageFocus)
                    .onPreviewKeyEvent { e ->
                        if (showHome || web == null) return@onPreviewKeyEvent false
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key.isSelectKey
                        if (cx < 0) { cx = pageW / 2; cy = pageH / 2 }
                        // Basılı tutunca imleç hızlanır
                        val rep = e.nativeKeyEvent.repeatCount
                        val step = with(density) { (14 + minOf(rep, 12) * 4).dp.toPx() }
                        val edge = with(density) { 6.dp.toPx() }
                        when (e.key) {
                            Key.DirectionUp -> {
                                if (cy <= edge) {
                                    if (web?.canScrollVertically(-1) == true) wheel(cx, cy, 2.5f, 0f)
                                    else runCatching { barFocus.requestFocus() }
                                } else cy = (cy - step).coerceAtLeast(0f)
                                true
                            }
                            Key.DirectionDown -> {
                                if (cy >= pageH - edge) wheel(cx, cy, -2.5f, 0f) else cy = (cy + step).coerceAtMost(pageH - 1)
                                true
                            }
                            Key.DirectionLeft -> {
                                if (cx <= edge) wheel(cx, cy, 0f, 2.5f) else cx = (cx - step).coerceAtLeast(0f)
                                true
                            }
                            Key.DirectionRight -> {
                                if (cx >= pageW - edge) wheel(cx, cy, 0f, -2.5f) else cx = (cx + step).coerceAtMost(pageW - 1)
                                true
                            }
                            Key.ChannelUp, Key.PageUp -> { wheel(cx, cy, 10f, 0f); true }
                            Key.ChannelDown, Key.PageDown -> { wheel(cx, cy, -10f, 0f); true }
                            else -> if (e.key.isSelectKey) {
                                if (rep == 0) clickAt(cx, cy)
                                true
                            } else false
                        }
                    }
                    .focusable(),
            ) {
                val w0 = with(density) { maxWidth.toPx() }
                val h0 = with(density) { maxHeight.toPx() }
                SideEffect {
                    pageW = w0
                    pageH = h0
                }
                val w = web
                if (w != null) AndroidView(factory = { w }, modifier = Modifier.fillMaxSize())
                if (!showHome && w != null) {
                    // Fare oku
                    Canvas(Modifier.fillMaxSize()) {
                        val x = if (cx < 0) size.width / 2 else cx
                        val y = if (cy < 0) size.height / 2 else cy
                        val s = 1.dp.toPx()
                        val p = Path().apply {
                            moveTo(x, y)
                            lineTo(x, y + 22 * s)
                            lineTo(x + 6 * s, y + 17 * s)
                            lineTo(x + 10 * s, y + 26 * s)
                            lineTo(x + 14 * s, y + 24 * s)
                            lineTo(x + 10 * s, y + 15.5f * s)
                            lineTo(x + 17 * s, y + 15.5f * s)
                            close()
                        }
                        drawPath(p, Color.White)
                        drawPath(p, Color.Black, style = Stroke(1.6f * s))
                    }
                }
            }

            // ---- Başlangıç sayfası ----
            if (showHome) {
                BrowserHome(
                    history = history,
                    error = webError,
                    focus = homeFocus,
                    onSearch = { textReq = TextRequest(tx("Adres ya da arama", "Address or search"), "", false, forUrl = true) },
                    onOpen = { go(it) },
                    onClearHistory = {
                        BrowserHistory.clear(context)
                        history = emptyList()
                    },
                )
            }

            // ---- İndirilenler ----
            if (panel) DownloadsPanel(Modifier.align(Alignment.CenterEnd)) { toast = it }

            toast?.let {
                Text(
                    it, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    // ---- Metin girişi (adres çubuğu ya da sayfadaki kutu) ----
    textReq?.let { req ->
        TextEntry(req, onCancel = {
            textReq = null
            runCatching { if (showHome) homeFocus.requestFocus() else pageFocus.requestFocus() }
        }) { value, submit ->
            textReq = null
            if (req.forUrl) {
                if (value.isNotBlank()) go(value)
            } else {
                web?.evaluateJavascript(jsSetInput(value, submit), null)
                runCatching { pageFocus.requestFocus() }
            }
        }
    }
    }
    LaunchedEffect(showHome) { if (showHome) history = BrowserHistory.load(context) }
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

@Composable
private fun BrowserHome(
    history: List<Pair<String, String>>,
    error: String?,
    focus: FocusRequester,
    onSearch: () -> Unit,
    onOpen: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0D0E16)).padding(horizontal = 60.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌐 " + tx("LiteDroid Tarayıcı", "LiteDroid Browser"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(tx("Yön tuşları imleci gezdirir · Tamam: tıkla · Kenarlar: kaydır · Geri: önceki sayfa", "Arrows move the pointer · OK: click · Edges: scroll · Back: previous page"),
            color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        error?.let {
            Text(it, color = Color(0xFFFFB4A8), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
        }
        HomeTile(tx("🔍  Google'da ara ya da adres yaz", "🔍  Search Google or type an address"), Color(0xFF3A3658), Modifier.widthIn(max = 640.dp).fillMaxWidth().focusRequester(focus), onClick = onSearch)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QUICK.forEach { q -> HomeTile(q.title, q.color.copy(alpha = 0.75f), Modifier.width(128.dp)) { onOpen(q.url) } }
        }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tx("SON GEZİLENLER", "RECENT"), color = Color(0xFF3BA7FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                HomeTile(tx("Geçmişi temizle", "Clear history"), Color.White.copy(alpha = 0.08f), Modifier, onClick = onClearHistory)
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { it.second }) { (t, u) ->
                    HomeTile("$t  ·  " + u.removePrefix("https://").removePrefix("www.").take(60), Color.White.copy(alpha = 0.05f), Modifier.fillMaxWidth()) { onOpen(u) }
                }
            }
        }
    }
}

@Composable
private fun HomeTile(label: String, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else bg)
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF14131C) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BarButton(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(34.dp)
            .widthIn(min = 38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect({ if (enabled) onClick() })
            .focusable()
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = when {
            focused -> Color(0xFF14131C)
            enabled -> Color.White
            else -> Color.White.copy(alpha = 0.3f)
        }, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DownloadsPanel(modifier: Modifier, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(420.dp)
            .background(Color(0xF2141522))
            .border(1.dp, Color.White.copy(alpha = 0.08f))
            .padding(18.dp),
    ) {
        Text("⬇ " + tx("İndirilenler", "Downloads"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(tx("Dosyalar ayrıca İndirilenler/LiteDroid klasörüne kaydedilir. Tamam: aç/kur · Basılı tut: sil", "Also saved to Downloads/LiteDroid. OK: open/install · Hold: delete"),
            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        if (Downloader.items.isEmpty()) {
            Text(tx("Henüz indirme yok.", "No downloads yet."), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.focusRequester(first).focusable())
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Downloader.items.toList(), key = { it.id }) { item ->
                DownloadRow(item, if (item === Downloader.items.firstOrNull()) Modifier.focusRequester(first) else Modifier, onOpen = {
                    when (item.state) {
                        DownloadItem.State.RUNNING -> {
                            Downloader.cancel(item)
                            onMessage(tx("İndirme durduruldu", "Download stopped"))
                        }
                        DownloadItem.State.DONE -> if (!Downloader.open(context, item)) onMessage(tx("Bu dosyayı açacak uygulama yok", "No app can open this file"))
                        else -> Downloader.delete(item)
                    }
                }) {
                    Downloader.delete(item)
                    onMessage(tx("Silindi", "Deleted"))
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, modifier: Modifier, onOpen: () -> Unit, onDelete: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onOpen, onDelete)
            .focusable()
            .padding(12.dp),
    ) {
        val fg = if (focused) Color(0xFF14131C) else Color.White
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (item.isApk) "📦" else "📄", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(item.name, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        val status = when (item.state) {
            DownloadItem.State.RUNNING -> "${Downloader.size(item.done)} / ${Downloader.size(item.total)} · ${Downloader.size(item.speed)}/s · " + tx("Tamam: durdur", "OK: stop")
            DownloadItem.State.DONE -> Downloader.size(item.total) + " · " + if (item.isApk) tx("Tamam: kur", "OK: install") else tx("Tamam: aç", "OK: open")
            DownloadItem.State.FAILED -> tx("Başarısız: ", "Failed: ") + (item.error ?: "")
            DownloadItem.State.CANCELLED -> tx("Durduruldu", "Stopped")
        }
        Text(status, color = fg.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.state == DownloadItem.State.RUNNING) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f))) {
                val p = item.progress
                Box(Modifier.fillMaxWidth(if (p < 0) 0.3f else p.coerceAtLeast(0.02f)).height(4.dp).background(Color(0xFF3BA7FF)))
            }
        }
    }
}

/** LiteDroid'in kendi metin girişi: TV klavyesi açılır, "Git" ile gönderilir. */
@Composable
private fun TextEntry(req: TextRequest, onCancel: () -> Unit, onDone: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf(req.initial) }
    // Telefondan gelen yazı da buraya düşer
    DisposableEffect(Unit) {
        PhoneBridge.listeners++
        onDispose { PhoneBridge.listeners-- }
    }
    LaunchedEffect(Unit) { PhoneBridge.text.collect { text = it } }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(700.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF1C1A2E)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp)).padding(24.dp),
        ) {
            Text(req.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (req.password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        req.password -> KeyboardType.Password
                        req.forUrl -> KeyboardType.Uri
                        else -> KeyboardType.Text
                    },
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    keyboard?.hide()
                    onDone(text, true)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(if (req.forUrl) tx("Git", "Go") else tx("Gönder", "Submit"), Color(0xFF3BA7FF), Modifier) {
                    keyboard?.hide()
                    onDone(text, true)
                }
                if (!req.forUrl) HomeTile(tx("Sadece yaz", "Just fill in"), Color(0xFF3A3658), Modifier) {
                    keyboard?.hide()
                    onDone(text, false)
                }
                HomeTile(tx("Vazgeç", "Cancel"), Color(0xFF3A3658), Modifier) {
                    keyboard?.hide()
                    onCancel()
                }
            }
        }
    }
}
