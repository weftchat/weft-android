package app.weft.design

import android.view.inputmethod.EditorInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * `.switch` — 50×30 dp. Knob slides 20 dp (280 ms EaseOut); while pressed it stretches from 24 to
 * 29 dp towards the centre (180 ms). Track: #2C2B3E off, accent on.
 */
@Composable
fun WeftSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) WeftColors.accent else WeftColors.switchOff, tween(if (reduce) 0 else 220), label = "track")
    val knobColor by animateColorAsState(if (checked) WeftColors.onAccent else WeftColors.text, tween(if (reduce) 0 else 220), label = "knob")
    val knobW by animateDpAsState(if (pressed) 29.dp else 24.dp, WeftMotion.out(WeftMotion.SWITCH_STRETCH, reduce), label = "knobW")
    val x by animateDpAsState(
        when {
            checked && pressed -> 15.dp
            checked -> 20.dp
            else -> 0.dp
        },
        WeftMotion.out(WeftMotion.SWITCH, reduce), label = "knobX",
    )
    Box(
        modifier
            .size(50.dp, 30.dp)
            .semantics { this.contentDescription = contentDescription; stateDescription = if (checked) "On" else "Off" }
            .clickable(source, indication = null, role = Role.Switch) { onCheckedChange(!checked) }
            .background(track, CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + x, y = 3.dp)
                .size(knobW, 24.dp)
                .dropShadow(CircleShape, Shadow(6.dp, Color.Black.copy(alpha = 0.4f), offset = DpOffset(0.dp, 2.dp)))
                .background(knobColor, CircleShape),
        )
    }
}

/** `.seg` — equal pills in a glass capsule; the highlight slides 340 ms EaseOut. */
@Composable
fun WeftSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(WeftColors.glass, CircleShape)
            .cssBorder(1.dp, WeftColors.line, CircleShape)
            .padding(4.dp),
    ) {
        val cell = maxWidth / options.size
        val x by animateDpAsState(cell * selected, WeftMotion.out(WeftMotion.SEGMENT, reduce), label = "seg")
        Box(Modifier.offset(x = x).width(cell).height(36.dp).background(Color.White.copy(alpha = 0.10f), CircleShape))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val ink by animateColorAsState(if (on) WeftColors.text else WeftColors.muted, tween(200), label = "segInk")
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .semantics { this.selected = on }
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { WeftText(label, style = WeftType.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W700, color = ink)) }
            }
        }
    }
}

/** `.cbox` — 24 dp, radius 8. Fill 200 ms; tick scales in from 0.6 over 260 ms EaseOut. */
@Composable
fun WeftCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val fill by animateColorAsState(if (checked) WeftColors.accent else Color.Transparent, tween(if (reduce) 0 else 200), label = "cbFill")
    val border by animateColorAsState(if (checked) WeftColors.accent else WeftColors.emptyDot, tween(if (reduce) 0 else 200), label = "cbBorder")
    val tick by animateFloatAsState(if (checked) 1f else 0.6f, WeftMotion.out(260, reduce), label = "tick")
    Box(
        modifier.size(24.dp).background(fill, RoundedCornerShape(8.dp)).border(2.dp, border, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) WeftIconView(WeftIcon.Check, 14.dp, WeftColors.onAccent, Modifier.scale(tick), stroke = 3f)
    }
}

/** `.radio` — 22 dp ring; the accent dot scales 0 → 1 over 240 ms. */
@Composable
fun WeftRadio(checked: Boolean, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val border by animateColorAsState(if (checked) WeftColors.accent else WeftColors.emptyDot, tween(if (reduce) 0 else 200), label = "radioBorder")
    val dot by animateFloatAsState(if (checked) 1f else 0f, WeftMotion.out(240, reduce), label = "radioDot")
    Box(modifier.size(22.dp).border(2.dp, border, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).scale(dot).background(WeftColors.accent, CircleShape))
    }
}

/**
 * Wraps text fields so the keyboard runs in incognito mode (Android's
 * IME_FLAG_NO_PERSONALIZED_LEARNING): it must not learn from what is typed in Weft.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            val incognito = PlatformTextInputMethodRequest { outAttributes: EditorInfo ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
            next.startInputMethod(incognito)
        },
    ) { content() }
}

/**
 * `.input` — 52 dp, radius 16, glass. Focus: border accent @ 55 %, fill accent @ 6 %.
 * Always incognito; no autocorrect.
 */
@Composable
fun WeftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLength: Int = Int.MAX_VALUE,
) {
    val reduce = rememberReduceMotion()
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) WeftColors.accent.copy(alpha = 0.55f) else WeftColors.line, tween(if (reduce) 0 else 200), label = "inBorder")
    val fill by animateColorAsState(if (focused) WeftColors.accent.copy(alpha = 0.06f) else WeftColors.glass, tween(if (reduce) 0 else 200), label = "inFill")
    val shape = RoundedCornerShape(16.dp)
    IncognitoKeyboard {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            singleLine = true,
            interactionSource = source,
            textStyle = WeftType.input.copy(color = WeftColors.text),
            cursorBrush = SolidColor(WeftColors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Text),
            modifier = modifier.fillMaxWidth().height(52.dp).background(fill, shape).border(1.dp, border, shape),
            decorationBox = { inner ->
                Box(Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) WeftText(placeholder, style = WeftType.input.copy(color = WeftColors.faint))
                    inner()
                }
            },
        )
    }
}

/** A centred mono caption such as `.fine`. */
@Composable
fun WeftFine(text: String, modifier: Modifier = Modifier) {
    WeftText(text, style = WeftType.fine.copy(color = WeftColors.faint, textAlign = TextAlign.Center), modifier = modifier.fillMaxWidth())
}
