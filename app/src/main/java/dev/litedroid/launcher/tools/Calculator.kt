package dev.litedroid.launcher.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Hesap makinesi: kumandayla tuşlar arasında gezilir, rakamlar kumandanın rakam tuşlarıyla da
 * yazılabilir. Çarpma/bölme toplamadan önce yapılır. Sağda son işlemler.
 */
class CalcState {
    /** Sırayla sayı, işlem, sayı, işlem... (yazılmakta olan sayı [current]). */
    val parts = mutableStateListOf<String>()
    var current by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    val history = mutableStateListOf<Pair<String, String>>()

    val display: String get() = (parts + current).filter { it.isNotEmpty() }.joinToString(" ")

    /** Yazarken canlı sonuç (yalnızca bir işlem varsa). */
    val preview: String?
        get() = if (parts.size >= 2 && current.isNotEmpty() && current != "-") evaluate(parts + current)?.let { format(it) } else null

    fun press(k: String) {
        error = null
        when (k) {
            in "0".."9" -> if (current.replace("-", "").replace(",", "").length < 15) current = if (current == "0") k else current + k
            "," -> if (',' !in current) current = (current.ifEmpty { "0" }.let { if (it == "-") "-0" else it }) + ","
            "C" -> { parts.clear(); current = "" }
            "⌫" -> when {
                current.isNotEmpty() -> current = current.dropLast(1)
                parts.isNotEmpty() -> {
                    parts.removeAt(parts.lastIndex) // işlem
                    current = parts.removeLastOrNull().orEmpty()
                }
            }
            "±" -> current = if (current.startsWith("-")) current.drop(1) else "-$current"
            "%" -> number(current)?.let { current = format(it.divide(BigDecimal(100), MC)) }
            "+", "−", "×", "÷" -> when {
                current.isNotEmpty() && current != "-" -> { parts += current; parts += k; current = "" }
                parts.isNotEmpty() -> parts[parts.lastIndex] = k
                history.isNotEmpty() -> { parts += history.first().second; parts += k }
            }
            "=" -> {
                if (parts.isEmpty()) return
                val all = parts + current.ifEmpty { "0" }
                val r = evaluate(all)
                if (r == null) {
                    error = tx("Tanımsız (sıfıra bölme)", "Undefined (division by zero)")
                    return
                }
                val text = format(r)
                history.add(0, all.joinToString(" ") to text)
                while (history.size > 8) history.removeAt(history.lastIndex)
                parts.clear()
                current = text
            }
        }
    }

    private fun number(s: String): BigDecimal? = s.replace(".", "").replace(',', '.').toBigDecimalOrNull()

    /** Önce × ÷, sonra + −. */
    private fun evaluate(tokens: List<String>): BigDecimal? {
        val nums = ArrayList<BigDecimal>()
        val ops = ArrayList<String>()
        tokens.forEachIndexed { i, t -> if (i % 2 == 0) nums += (number(t) ?: return null) else ops += t }
        if (nums.size != ops.size + 1) return null
        val n2 = arrayListOf(nums[0])
        val o2 = ArrayList<String>()
        for (i in ops.indices) {
            val b = nums[i + 1]
            when (ops[i]) {
                "×" -> n2[n2.lastIndex] = n2.last().multiply(b, MC)
                "÷" -> {
                    if (b.signum() == 0) return null
                    n2[n2.lastIndex] = n2.last().divide(b, MC)
                }
                else -> { n2 += b; o2 += ops[i] }
            }
        }
        var r = n2[0]
        for (i in o2.indices) r = if (o2[i] == "+") r.add(n2[i + 1], MC) else r.subtract(n2[i + 1], MC)
        return r
    }

    companion object {
        private val MC = MathContext(20, RoundingMode.HALF_EVEN)

        fun format(v: BigDecimal): String {
            val s = v.round(MathContext(14, RoundingMode.HALF_EVEN)).stripTrailingZeros()
            val plain = if (s.abs() >= BigDecimal("1e15") || (s.signum() != 0 && s.abs() < BigDecimal("1e-9"))) s.toString() else s.toPlainString()
            return plain.replace('.', ',')
        }
    }
}

@Composable
fun CalculatorTool(accent: Color) {
    val state = remember { CalcState() }
    val eq = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { eq.requestFocus() }
    }
    val rows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ",", "="),
    )
    Row(
        Modifier
            .fillMaxHeight()
            .onPreviewKeyEvent { e ->
                // Kumandanın rakam tuşları doğrudan yazar
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = when (e.key) {
                    Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"
                    Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"
                    Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"
                    Key.Nine, Key.NumPad9 -> "9"; Key.Backspace, Key.Delete -> "⌫"; Key.NumPadAdd -> "+"
                    Key.NumPadSubtract, Key.Minus -> "−"; Key.NumPadMultiply -> "×"; Key.NumPadDivide, Key.Slash -> "÷"
                    Key.NumPadEnter, Key.Equals, Key.NumPadEquals -> "="; Key.Comma, Key.Period, Key.NumPadDot -> ","
                    else -> null
                }
                if (digit != null) { state.press(digit); true } else false
            },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.width(430.dp)) {
            // Ekran
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(state.preview?.let { "= $it" } ?: state.error ?: " ", color = if (state.error != null) Color(0xFFFFB4A8) else Color.White.copy(alpha = 0.5f), fontSize = 14.sp, maxLines = 1)
                Text(
                    state.display.ifEmpty { "0" }, color = Color.White, fontSize = if (state.display.length > 16) 24.sp else 34.sp,
                    fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.End,
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { k ->
                            val kind = when (k) {
                                "=" -> 2
                                "÷", "×", "−", "+" -> 1
                                "C", "⌫", "%", "±" -> 3
                                else -> 0
                            }
                            CalcKey(k, kind, accent, if (k == "=") Modifier.focusRequester(eq) else Modifier) { state.press(k) }
                        }
                    }
                }
            }
        }
        // Son işlemler
        Column(Modifier.width(300.dp)) {
            Text(tx("SON İŞLEMLER", "HISTORY"), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            if (state.history.isEmpty()) {
                Text(tx("Henüz işlem yok. Rakamları kumandanın rakam tuşlarıyla da yazabilirsin.", "Nothing yet. You can also type digits with the remote's number keys."),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            state.history.forEach { (expr, result) ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(expr, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("= $result", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CalcKey(label: String, kind: Int, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        kind == 2 -> accent
        kind == 1 -> accent.copy(alpha = 0.35f)
        kind == 3 -> Color.White.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Box(
        modifier
            .size(width = 101.dp, height = 50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) Color(0xFF14131C) else Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
