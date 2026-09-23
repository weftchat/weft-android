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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.weft.data.CoreChats
import app.weft.data.CoreInvitations
import app.weft.data.CoreProfile
import app.weft.data.CoreRelays
import app.weft.data.WeftSession
import app.weft.design.TabItem
import app.weft.design.WeftBottomSheet
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftTabBar
import app.weft.design.WeftToastHost
import app.weft.design.WeftToastState
import app.weft.design.backdropSource
import app.weft.design.rememberBackdrop
import app.weft.design.tabBarBottom
import app.weft.lock.CorePinVault
import app.weft.nav.NavMode
import app.weft.nav.PinMode
import app.weft.nav.Route
import app.weft.nav.TABS
import app.weft.nav.WeftNavHost
import app.weft.nav.WeftNavigator
import app.weft.relay.DemoRelays
import app.weft.ui.add.AddContactScreen
import app.weft.ui.add.ScanScreen
import app.weft.ui.chats.ChatKind
import app.weft.ui.chats.ChatsScreen
import app.weft.ui.common.PlaceholderScreen
import app.weft.ui.conversation.ConversationScreen
import app.weft.ui.onboarding.OnboardingScreen
import app.weft.ui.pin.PinScreen
import app.weft.ui.profile.ProfileScreen
import app.weft.ui.security.SecurityScreen
import app.weft.ui.sheets.AddRelaySheet
import app.weft.ui.sheets.FingerprintSheet
import app.weft.ui.sheets.NewSheet
import app.weft.ui.sheets.RelayAdd
import app.weft.ui.sheets.Sheet
import app.weft.ui.sheets.TimerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Weft is dark only: light status/navigation bar icons on a transparent bar.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            // First run: create identity. Later: the PIN, or straight to Chats if still unlocked.
            val nav = remember {
                WeftNavigator(
                    when {
                        !WeftSession.hasIdentity -> Route.Onboard
                        WeftSession.userId.value != null -> Route.Chats
                        else -> Route.Pin(PinMode.Enter)
                    },
                )
            }
            val scope = rememberCoroutineScope()
            // The nickname typed on screen 1, used when the PIN creates the identity.
            var pendingNickname by rememberSaveable { mutableStateOf("") }
            val vault = remember { CorePinVault { pendingNickname } }
            val toast = remember { WeftToastState() }
            var sheet by remember { mutableStateOf<Sheet?>(null) }
            // Keeps the closing sheet's content on screen while it slides away.
            var lastSheet by remember { mutableStateOf<Sheet>(Sheet.New) }
            fun open(s: Sheet) { lastSheet = s; sheet = s }
            val onTabs = nav.current in TABS
            // `.has-tabs` keeps 94 dp clear for the bar; toasts sit 104 dp up. Both follow the bar
            // when the system navigation area pushes it higher.
            val lift = tabBarBottom() - 14.dp
            val backdrop = rememberBackdrop()
            val failedRelay = stringResource(R.string.relay_failed)
            Box(Modifier.fillMaxSize().background(WeftColors.bg)) {
                WeftNavHost(nav, Modifier.backdropSource(backdrop)) { route ->
                    when (route) {
                        Route.Onboard -> OnboardingScreen(
                            makeIdentity = {
                                withContext(Dispatchers.Default) { WeftSession.device.create() }.also(CoreProfile::setFingerprint)
                            },
                            onChoosePin = { nickname ->
                                pendingNickname = nickname
                                nav.push(Route.Pin(PinMode.Choose))
                            },
                        )
                        is Route.Pin -> PinScreen(
                            start = route.mode,
                            vault = vault,
                            toast = toast::show,
                            onUnlocked = { nav.root(Route.Chats) },
                        )
                        Route.Chats -> ChatsScreen(
                            chatList = CoreChats,
                            relays = DemoRelays,
                            bottomPadding = 94.dp + lift,
                            toast = toast::show,
                            onLock = {
                                scope.launch {
                                    WeftSession.lock()
                                    nav.root(Route.Pin(PinMode.Enter))
                                }
                            },
                            onNew = { open(Sheet.New) },
                            onOpen = { chat ->
                                CoreChats.markRead(chat.id)
                                nav.push(if (chat.kind == ChatKind.Group) Route.GroupChat(chat.id, chat.name) else Route.Conversation(chat.id))
                            },
                        )
                        Route.Add -> AddContactScreen(
                            CoreInvitations,
                            bottomPadding = 94.dp + lift,
                            toast = toast::show,
                            onAddRelay = { open(Sheet.AddRelay) },
                            onScan = { nav.push(Route.Scan) },
                        )
                        Route.Scan -> ScanScreen(toast = toast::show, onBack = nav::pop, onJoined = { nav.root(Route.Chats) })
                        Route.Security -> SecurityScreen(bottomPadding = 94.dp + lift, onAddRelay = { open(Sheet.AddRelay) })
                        Route.Profile -> ProfileScreen(
                            store = CoreProfile,
                            bottomPadding = 94.dp + lift,
                            toast = toast::show,
                            onShowQr = { open(Sheet.Fingerprint) },
                            onChangePin = { nav.push(Route.Pin(PinMode.Choose)) },
                            onSecurity = { nav.root(Route.Security) },
                            onDelete = { nav.push(Route.Wipe) },
                        )
                        Route.Wipe -> PlaceholderScreen("Emergency wipe", onBack = nav::pop)
                        is Route.Conversation -> ConversationScreen(
                            chatId = route.chatId,
                            chatList = CoreChats,
                            conversations = CoreChats,
                            toast = toast::show,
                            onBack = nav::pop,
                            onTimer = { open(Sheet.Timer(route.chatId)) },
                        )
                        is Route.GroupChat -> PlaceholderScreen(route.name, onBack = nav::pop)
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
                    backdrop = backdrop,
                )
                WeftBottomSheet(visible = sheet != null, onDismiss = { sheet = null }) {
                    when (val s = lastSheet) {
                        Sheet.New -> NewSheet(
                            onContact = { sheet = null; nav.root(Route.Add) },
                            onGroup = { sheet = null; nav.push(Route.NewGroup) },
                        )
                        Sheet.Fingerprint -> FingerprintSheet(CoreProfile.profile.value.fingerprint)
                        Sheet.AddRelay -> AddRelaySheet(
                            onAdd = { address ->
                                val failure = runCatching { CoreRelays.test(address) }.getOrElse { it.message ?: "error" }
                                when {
                                    failure != null -> RelayAdd.Failed(failedRelay.format(failure))
                                    else -> try {
                                        CoreRelays.add(address)
                                        RelayAdd.Added
                                    } catch (e: CoreRelays.NeedPartner) {
                                        RelayAdd.NeedPartner(e.need)
                                    } catch (e: Exception) {
                                        RelayAdd.Failed(failedRelay.format(e.message ?: "error"))
                                    }
                                }
                            },
                            onDone = { message ->
                                sheet = null
                                toast.show(message, WeftIcon.Check)
                            },
                        )
                        is Sheet.Timer -> TimerSheet(
                            current = CoreChats.chats.value.firstOrNull { it.id == s.chatId }?.timer,
                            onPick = { timer, message ->
                                CoreChats.setTimer(s.chatId, timer)
                                sheet = null
                                toast.show(message, WeftIcon.Timer)
                            },
                        )
                    }
                }
                // Toasts sit 104 dp up over the tab bar, 96 dp over a composer, 40 dp elsewhere.
                val toastBottom = when {
                    onTabs -> 104.dp + lift
                    nav.current is Route.Conversation || nav.current is Route.GroupChat -> 96.dp
                    else -> 40.dp
                }
                WeftToastHost(toast, bottom = toastBottom)
            }
        }
    }
}
