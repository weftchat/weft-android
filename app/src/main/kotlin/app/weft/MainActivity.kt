package app.weft

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.weft.design.TabItem
import app.weft.design.WeftBottomSheet
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftNavOption
import app.weft.design.WeftSheetTitle
import app.weft.design.WeftTabBar
import app.weft.design.WeftToastHost
import app.weft.design.WeftToastState
import app.weft.design.tabBarBottom
import app.weft.identity.DemoIdentityMaker
import app.weft.lock.DemoPinVault
import app.weft.nav.NavMode
import app.weft.nav.PinMode
import app.weft.nav.Route
import app.weft.nav.TABS
import app.weft.nav.WeftNavHost
import app.weft.nav.WeftNavigator
import app.weft.relay.DemoRelays
import app.weft.ui.chats.ChatsScreen
import app.weft.ui.chats.DemoChatList
import app.weft.ui.common.PlaceholderScreen
import app.weft.ui.onboarding.OnboardingScreen
import app.weft.ui.pin.PinScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Weft is dark only: light status/navigation bar icons on a transparent bar.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            val nav = remember { WeftNavigator(Route.Onboard) }
            val toast = remember { WeftToastState() }
            var newSheet by rememberSaveable { mutableStateOf(false) }
            val onTabs = nav.current in TABS
            // `.has-tabs` keeps 94 dp clear for the bar; toasts sit 104 dp up. Both follow the bar
            // when the system navigation area pushes it higher.
            val lift = tabBarBottom() - 14.dp
            Box(Modifier.fillMaxSize().background(WeftColors.bg)) {
                WeftNavHost(nav) { route ->
                    when (route) {
                        Route.Onboard -> OnboardingScreen(
                            makeIdentity = DemoIdentityMaker::create,
                            onChoosePin = { nav.push(Route.Pin(PinMode.Choose)) },
                        )
                        is Route.Pin -> PinScreen(
                            start = route.mode,
                            vault = DemoPinVault,
                            toast = toast::show,
                            onUnlocked = { nav.root(Route.Chats) },
                        )
                        Route.Chats -> ChatsScreen(
                            chatList = DemoChatList,
                            relays = DemoRelays,
                            bottomPadding = 94.dp + lift,
                            toast = toast::show,
                            onLock = { nav.root(Route.Pin(PinMode.Enter)) },
                            onNew = { newSheet = true },
                            onOpen = { chat ->
                                DemoChatList.markRead(chat.id)
                                nav.push(Route.Conversation(chat.id, chat.name))
                            },
                        )
                        Route.Add -> PlaceholderScreen(stringResource(R.string.tab_add))
                        Route.Security -> PlaceholderScreen(stringResource(R.string.tab_security))
                        Route.Profile -> PlaceholderScreen(stringResource(R.string.tab_profile))
                        is Route.Conversation -> PlaceholderScreen(route.name, onBack = nav::pop)
                        Route.NewGroup -> PlaceholderScreen("New group", onBack = nav::pop)
                    }
                }
                WeftTabBar(
                    items = listOf(
                        TabItem(stringResource(R.string.tab_chats), WeftIcon.Chat),
                        TabItem(stringResource(R.string.tab_add), WeftIcon.UserPlus),
                        TabItem(stringResource(R.string.tab_security), WeftIcon.Shield),
                        TabItem(stringResource(R.string.tab_profile), WeftIcon.User),
                    ),
                    selected = TABS.indexOf(nav.current),
                    visible = onTabs,
                    onSelect = { i -> if (TABS[i] != nav.current) nav.root(TABS[i], NavMode.Fade) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                WeftBottomSheet(visible = newSheet, onDismiss = { newSheet = false }) {
                    WeftSheetTitle(stringResource(R.string.sheet_new_title))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeftNavOption(WeftIcon.UserPlus, stringResource(R.string.sheet_new_contact), stringResource(R.string.sheet_new_contact_detail)) {
                            newSheet = false
                            nav.root(Route.Add)
                        }
                        WeftNavOption(WeftIcon.Users, stringResource(R.string.sheet_new_group), stringResource(R.string.sheet_new_group_detail)) {
                            newSheet = false
                            nav.push(Route.NewGroup)
                        }
                    }
                }
                WeftToastHost(toast, bottom = if (onTabs) 104.dp + lift else 40.dp)
            }
        }
    }
}
