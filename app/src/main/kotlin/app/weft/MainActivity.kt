package app.weft

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.weft.design.WeftColors
import app.weft.design.WeftToastHost
import app.weft.design.WeftToastState
import app.weft.identity.DemoIdentityMaker
import app.weft.nav.PinMode
import app.weft.nav.Route
import app.weft.nav.WeftNavHost
import app.weft.nav.WeftNavigator
import app.weft.ui.onboarding.OnboardingScreen
import app.weft.ui.pin.PinScreen
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Weft is dark only: light status/navigation bar icons on a transparent bar.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            val nav = remember { WeftNavigator(Route.Onboard) }
            val toast = remember { WeftToastState() }
            Box(Modifier.fillMaxSize().background(WeftColors.bg)) {
                WeftNavHost(nav) { route ->
                    when (route) {
                        Route.Onboard -> OnboardingScreen(
                            makeIdentity = DemoIdentityMaker::create,
                            onChoosePin = { nav.push(Route.Pin(PinMode.Choose)) },
                        )
                        is Route.Pin -> PinScreen(route.mode)
                    }
                }
                WeftToastHost(toast, bottom = 40.dp)
            }
        }
    }
}
