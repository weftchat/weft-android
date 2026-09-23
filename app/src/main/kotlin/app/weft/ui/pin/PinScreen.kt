package app.weft.ui.pin

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.design.PIN_DELETE
import app.weft.design.PinDots
import app.weft.design.PinKeypad
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftLogo
import app.weft.design.WeftText
import app.weft.design.WeftType
import app.weft.design.pinShake
import app.weft.design.rememberReduceMotion
import app.weft.lock.PinVault
import app.weft.lock.SetResult
import app.weft.nav.PinMode
import app.weft.nav.PinPurpose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LENGTH = 6

private val DIGITS = mapOf(
    Key.Zero to "0", Key.One to "1", Key.Two to "2", Key.Three to "3", Key.Four to "4",
    Key.Five to "5", Key.Six to "6", Key.Seven to "7", Key.Eight to "8", Key.Nine to "9",
    Key.NumPad0 to "0", Key.NumPad1 to "1", Key.NumPad2 to "2", Key.NumPad3 to "3", Key.NumPad4 to "4",
    Key.NumPad5 to "5", Key.NumPad6 to "6", Key.NumPad7 to "7", Key.NumPad8 to "8", Key.NumPad9 to "9",
)

/**
 * Screen 2 — PIN. Choose → Repeat (a mismatch shakes, toasts and starts over) → Weft opens;
 * Enter checks the PIN. Digits come from the in-app keypad or a hardware keyboard, never the
 * system keyboard. The typed digits live only in memory, never in saved state.
 */
@Composable
fun PinScreen(
    start: PinMode,
    purpose: PinPurpose,
    vault: PinVault,
    toast: (String, WeftIcon) -> Unit,
    onUnlocked: (firstTime: Boolean) -> Unit,
) {
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(start) }
    var typed by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val mismatch = stringResource(R.string.pin_mismatch)
    val taken = stringResource(R.string.pin_taken)
    val needRelay = stringResource(R.string.add_no_relay_short)
    val ready = stringResource(when (purpose) {
        PinPurpose.Own -> R.string.pin_ready
        PinPurpose.Duress -> R.string.pin_ready_duress
        PinPurpose.Panic -> R.string.pin_ready_panic
    })
    val failed = stringResource(R.string.pin_failed)

    // Wrong PIN: red rings + shake; the dots clear at 520 ms and the screen resets at 540 ms.
    fun fail(then: PinMode) = scope.launch {
        error = true
        launch { shake.pinShake(reduce) }
        delay(520); typed = ""; error = false
        delay(20); mode = then; busy = false
    }

    suspend fun checkNow(pin: String) {
        when (mode) {
            PinMode.Choose -> { first = pin; mode = PinMode.Repeat; typed = ""; busy = false }
            PinMode.Repeat -> if (pin == first) {
                first = null
                when (vault.set(pin)) {
                    SetResult.Ok -> { toast(ready, WeftIcon.Check); onUnlocked(true) }
                    SetResult.Taken -> { toast(taken, WeftIcon.Alert); mode = PinMode.Choose; typed = ""; busy = false }
                    SetResult.NeedRelay -> { toast(needRelay, WeftIcon.Alert); mode = PinMode.Choose; typed = ""; busy = false }
                }
            } else {
                first = null
                toast(mismatch, WeftIcon.Alert)
                fail(PinMode.Choose)
            }
            PinMode.Enter -> if (vault.unlock(pin)) onUnlocked(false) else fail(PinMode.Enter)
        }
    }

    fun check(pin: String) = scope.launch {
        try {
            checkNow(pin)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("Weft", "PIN check failed", e)
            toast(failed, WeftIcon.Alert)
            first = null
            typed = ""
            mode = if (mode == PinMode.Enter) PinMode.Enter else PinMode.Choose
            busy = false
        }
    }

    fun press(key: String) {
        if (busy) return
        if (key == PIN_DELETE) { typed = typed.dropLast(1); return }
        if (typed.length >= LENGTH) return
        typed += key
        if (typed.length == LENGTH) {
            busy = true
            val pin = typed
            scope.launch { delay(160); check(pin) }
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val bottomInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    Column(
        Modifier
            .fillMaxSize()
            .background(WeftColors.bg)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (val k = e.key) {
                    Key.Backspace -> press(PIN_DELETE)
                    in DIGITS -> press(DIGITS.getValue(k))
                    else -> return@onKeyEvent false
                }
                true
            }
            .padding(start = 24.dp, end = 24.dp, top = 96.dp, bottom = maxOf(44.dp, bottomInset)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        // `.pin-logo::before` — accent @ 20 %, 20 dp beyond the logo, blurred 24 px.
        Box(Modifier.size(60.dp).dropShadow(CircleShape, Shadow(48.dp, WeftColors.accent.copy(alpha = 0.2f), spread = 20.dp))) {
            WeftLogo(60.dp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (title, sub) = when (mode) {
                PinMode.Choose -> when (purpose) {
                    PinPurpose.Own -> R.string.pin_choose_title to R.string.pin_choose_sub
                    PinPurpose.Duress -> R.string.pin_choose_duress_title to R.string.pin_choose_duress_sub
                    PinPurpose.Panic -> R.string.pin_choose_panic_title to R.string.pin_choose_panic_sub
                }
                PinMode.Repeat -> R.string.pin_repeat_title to R.string.pin_repeat_sub
                PinMode.Enter -> R.string.pin_enter_title to R.string.pin_enter_sub
            }
            WeftText(stringResource(title), style = WeftType.pinTitle.copy(color = WeftColors.text, textAlign = TextAlign.Center))
            WeftText(stringResource(sub), style = WeftType.pinSub.copy(color = WeftColors.muted, textAlign = TextAlign.Center))
        }
        PinDots(filled = typed.length, length = LENGTH, error = error, shake = shake.value)
        Spacer(Modifier.weight(1f))
        PinKeypad(onKey = ::press)
    }
}
