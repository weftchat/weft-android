package app.weft

import app.weft.design.WeftType
import app.weft.design.WeftText
import app.weft.design.weftClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import app.weft.ui.call.callClock
import app.weft.ui.call.IncomingCallScreen
import app.weft.ui.call.CallWebView
import app.weft.ui.call.CallScreen
import app.weft.data.CoreNetwork
import app.weft.data.CoreCalls
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import app.weft.ui.sheets.AddCallServerSheet
import app.weft.data.CallServers
import app.weft.ui.sheets.GroupLinkSheet
import app.weft.ui.sheets.GroupSheet
import app.weft.ui.group.NewGroupScreen
import app.weft.data.CoreGroups
import app.weft.ui.sheets.LinkSheet
import app.weft.privacy.SensitiveClipboard
import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.weft.data.CoreChats
import app.weft.data.CoreInvitations
import app.weft.data.CoreProfile
import app.weft.data.CoreRelays
import app.weft.data.LiveRelays
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
import app.weft.nav.PinPurpose
import app.weft.lock.SecondCodeVault
import app.weft.nav.Route
import app.weft.nav.TABS
import app.weft.nav.WeftNavHost
import app.weft.nav.WeftNavigator
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
import app.weft.ui.sheets.RelaySheet
import app.weft.ui.sheets.AutoLockSheet
import app.weft.ui.sheets.UsbWipeSheet
import app.weft.data.UsbGuard
import app.weft.ui.security.autoLockValue
import app.weft.data.AutoLock
import app.weft.ui.sheets.FingerprintSheet
import app.weft.ui.wipe.WipeScreen
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
        // FLAG_SECURE (BRIEF): no screenshots, no screen recording, and a blank tile in the app switcher.
        // Only debug builds leave it off, so the emulator can be screenshotted while testing.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        }
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
            // A wipe that did not start from the wipe screen (10 wrong PINs, USB guard) leaves no trace on
            // screen: the app simply returns to the start, as if it had never been set up.
            val wiped by WeftSession.wiped.collectAsState()
            LaunchedEffect(wiped) {
                if (wiped && nav.current !is Route.Wipe) { pendingNickname = ""; sheet = null; nav.root(Route.Onboard) }
            }
            val context = LocalContext.current
            var autoLockSeconds by remember { mutableIntStateOf(AutoLock.seconds(context)) }
            var usbWipe by remember { mutableStateOf(UsbGuard.wipeOnUsb(context)) }
            val usbWipeOn = stringResource(R.string.toast_usb_wipe_on)
            val usbWipeOff = stringResource(R.string.toast_usb_wipe_off)
            val toastNow = stringResource(R.string.toast_autolock_now)
            val toastAfter = stringResource(R.string.toast_autolock)
            // Locked (by the timer, the padlock or the USB guard): whatever was on screen gives way to the PIN.
            val userId by WeftSession.userId.collectAsState()
            val screenLocked by WeftSession.screenLocked.collectAsState()
            LaunchedEffect(userId, screenLocked) {
                val c = nav.current
                if ((userId == null || screenLocked) && WeftSession.hasIdentity && c !is Route.Pin && c !is Route.Onboard && c !is Route.Wipe) {
                    sheet = null
                    nav.root(Route.Pin(PinMode.Enter))
                }
            }
            val backdrop = rememberBackdrop()
            val failedRelay = stringResource(R.string.relay_failed)
            val linkCopied = stringResource(R.string.toast_link_copied)
            val groupCreated = stringResource(R.string.group_created)
            // --- calls (Phase 4) ---
            val call by CoreCalls.call.collectAsState()
            var callMinimized by remember { mutableStateOf(false) }
            val callEnded = stringResource(R.string.call_ended)
            val callEndedShort = stringResource(R.string.call_ended_short)
            val noServers = stringResource(R.string.call_no_servers)
            val busy = stringResource(R.string.call_busy)
            val noMic = stringResource(R.string.call_no_mic)
            val torNote = stringResource(R.string.call_tor_note)
            var afterMic by remember { mutableStateOf<(() -> Unit)?>(null) }
            val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) afterMic?.invoke() else toast.show(noMic, WeftIcon.MicOff)
                afterMic = null
            }
            /** BRIEF: the microphone is asked for only when the first call starts or is answered. */
            fun withMic(action: () -> Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
                else { afterMic = action; micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            }
            fun report(r: CoreCalls.StartResult) {
                when (r) {
                    CoreCalls.StartResult.NoServers -> toast.show(noServers, WeftIcon.Alert)
                    CoreCalls.StartResult.Busy -> toast.show(busy, WeftIcon.Alert)
                    CoreCalls.StartResult.Ok -> { callMinimized = false; if (CoreNetwork.tor.value) toast.show(torNote, WeftIcon.Mask) }
                }
            }
            fun startCall(contactId: Long, name: String) = report(CoreCalls.start(contactId, name, video = false))
            LaunchedEffect(Unit) {
                CoreCalls.ended.collect { seconds -> toast.show(seconds?.let { callEnded.format(callClock(it)) } ?: callEndedShort, WeftIcon.HangUp) }
            }
            val groupLeft = stringResource(R.string.group_left)
            val groupRemoved = stringResource(R.string.group_removed)
            val groupInviteCopied = stringResource(R.string.group_link_copied)
            val switchedTo = stringResource(R.string.relay_switched)
            LaunchedEffect(Unit) {
                LiveRelays.switchedOnItsOwn.collect { name -> toast.show(switchedTo.format(name), WeftIcon.Refresh) }
            }
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
                            purpose = route.purpose,
                            vault = when (route.purpose) {
                                PinPurpose.Own -> vault
                                PinPurpose.Duress -> remember { SecondCodeVault(panic = false) }
                                PinPurpose.Panic -> remember { SecondCodeVault(panic = true) }
                            },
                            toast = toast::show,
                            onUnlocked = { if (route.purpose == PinPurpose.Own) nav.root(Route.Chats) else nav.pop() },
                        )
                        Route.Chats -> {
                            AskForNotices()
                            ChatsScreen(
                            chatList = CoreChats,
                            relays = LiveRelays,
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
                        }
                        Route.Add -> AddContactScreen(
                            CoreInvitations,
                            bottomPadding = 94.dp + lift,
                            toast = toast::show,
                            onAddRelay = { open(Sheet.AddRelay) },
                            onScan = { nav.push(Route.Scan) },
                        )
                        Route.Scan -> ScanScreen(toast = toast::show, onBack = nav::pop, onJoined = { nav.root(Route.Chats) })
                        Route.Security -> SecurityScreen(
                            bottomPadding = 94.dp + lift,
                            toast = toast::show,
                            onAddRelay = { open(Sheet.AddRelay) },
                            onRelay = { open(Sheet.Relay(it)) },
                            onAddCallServer = { open(Sheet.AddCallServer) },
                            onChoose = { nav.push(Route.Pin(PinMode.Choose, it)) },
                            onWipe = { nav.push(Route.Wipe) },
                            autoLockSeconds = autoLockSeconds,
                            onAutoLock = { open(Sheet.AutoLock) },
                            usbWipe = usbWipe,
                            onUsbWipe = { on ->
                                if (on) open(Sheet.UsbWipe)
                                else {
                                    UsbGuard.setWipeOnUsb(context, false)
                                    usbWipe = false
                                    toast.show(usbWipeOff, WeftIcon.Alert)
                                }
                            },
                        )
                        Route.Profile -> ProfileScreen(
                            store = CoreProfile,
                            bottomPadding = 94.dp + lift,
                            toast = toast::show,
                            onShowQr = { open(Sheet.Fingerprint) },
                            onChangePin = { nav.push(Route.Pin(PinMode.Choose)) },
                            onSecurity = { nav.root(Route.Security) },
                            onDelete = { nav.push(Route.Wipe) },
                        )
                        Route.Wipe -> WipeScreen(
                            onBack = nav::pop,
                            onStartOver = { pendingNickname = ""; nav.root(Route.Onboard) },
                        )
                        is Route.Conversation -> ConversationScreen(
                            chatId = route.chatId,
                            chatList = CoreChats,
                            conversations = CoreChats,
                            toast = toast::show,
                            onBack = nav::pop,
                            onTimer = { open(Sheet.Timer(route.chatId)) },
                            onLink = { open(Sheet.Link(it)) },
                            onCall = {
                                val name = CoreChats.chats.value.firstOrNull { it.id == route.chatId }?.name.orEmpty()
                                withMic { startCall(route.chatId.removePrefix("@").toLong(), name) }
                            },
                        )
                        is Route.GroupChat -> ConversationScreen(
                            chatId = route.chatId,
                            chatList = CoreChats,
                            conversations = CoreChats,
                            toast = toast::show,
                            onBack = nav::pop,
                            onTimer = {},
                            onLink = { open(Sheet.Link(it)) },
                            onGroupInfo = { open(Sheet.Group(route.chatId, route.name)) },
                        )
                        Route.NewGroup -> NewGroupScreen(
                            chatList = CoreChats,
                            toast = toast::show,
                            onBack = nav::pop,
                            onCreated = { id, name ->
                                nav.pop()
                                nav.push(Route.GroupChat(id, name))
                                toast.show(groupCreated, WeftIcon.Users)
                            },
                        )
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
                        Sheet.AutoLock -> AutoLockSheet(
                            current = autoLockSeconds,
                            onPick = { seconds ->
                                AutoLock.setSeconds(context, seconds)
                                autoLockSeconds = seconds
                                sheet = null
                                toast.show(
                                    if (seconds == 0) toastNow else toastAfter.format(context.getString(autoLockValue(seconds))),
                                    WeftIcon.Timer,
                                )
                            },
                        )
                        Sheet.UsbWipe -> UsbWipeSheet(
                            onTurnOn = {
                                UsbGuard.setWipeOnUsb(context, true)
                                usbWipe = true
                                sheet = null
                                toast.show(usbWipeOn, WeftIcon.Check)
                            },
                            onCancel = { sheet = null },
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
                        is Sheet.Group -> {
                            var info by remember(s) { mutableStateOf<CoreGroups.Info?>(null) }
                            LaunchedEffect(s) { info = runCatching { CoreGroups.info(s.chatId) }.getOrNull() }
                            GroupSheet(
                                info = info,
                                onRemove = { m ->
                                    scope.launch {
                                        runCatching { CoreGroups.remove(s.chatId, m.id) }.onSuccess {
                                            info = runCatching { CoreGroups.info(s.chatId) }.getOrNull()
                                            toast.show(groupRemoved.format(m.name), WeftIcon.Users)
                                        }
                                    }
                                },
                                onInvite = { open(Sheet.GroupLink(s.chatId, s.name)) },
                                onLeave = {
                                    scope.launch {
                                        runCatching { CoreGroups.leave(s.chatId) }.onSuccess {
                                            sheet = null
                                            nav.root(Route.Chats)
                                            toast.show(groupLeft, WeftIcon.Users)
                                        }
                                    }
                                },
                            )
                        }
                        is Sheet.GroupLink -> {
                            var link by remember(s) { mutableStateOf<String?>(null) }
                            LaunchedEffect(s) { link = runCatching { CoreGroups.link(s.chatId) }.getOrNull() }
                            GroupLinkSheet(s.name, link, onCopy = {
                                SensitiveClipboard.copy(context, it)
                                sheet = null
                                toast.show(groupInviteCopied, WeftIcon.Copy)
                            })
                        }
                        Sheet.AddCallServer -> AddCallServerSheet(
                            onAdd = { CallServers.add(it) },
                            onDone = { message ->
                                sheet = null
                                toast.show(message, WeftIcon.Phone)
                            },
                        )
                        is Sheet.Link -> LinkSheet(
                            url = s.url,
                            torBrowser = remember { runCatching { context.packageManager.getPackageInfo(TOR_BROWSER, 0); true }.getOrDefault(false) },
                            onTor = {
                                sheet = null
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).setPackage(TOR_BROWSER)) }
                            },
                            onBrowser = {
                                sheet = null
                                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)), null)) }
                            },
                            onCopy = {
                                SensitiveClipboard.copy(context, s.url)
                                sheet = null
                                toast.show(linkCopied, WeftIcon.Copy)
                            },
                        )
                        is Sheet.Relay -> RelaySheet(
                            address = s.address,
                            onTest = { CoreRelays.check(s.address) },
                            onRemove = {
                                try { CoreRelays.remove(s.address); true } catch (_: CoreRelays.LastOfKind) { false }
                            },
                            latencyMs = { CoreRelays.status.value[s.address]?.latencyMs },
                            onRemoved = { message ->
                                sheet = null
                                toast.show(message, WeftIcon.Trash)
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
                call?.let { c ->
                    if (c.phase != CoreCalls.Phase.Incoming) {
                        val video = c.peerVideo && c.phase == CoreCalls.Phase.Connected && !callMinimized
                        CallWebView(if (video) Modifier.fillMaxSize() else Modifier.size(1.dp))
                        if (!callMinimized) {
                            CallScreen(c, CoreProfile.profile.value.nickname.take(1).uppercase().ifEmpty { "Y" }, onMinimize = { callMinimized = true })
                        } else {
                            val label = stringResource(R.string.call_back, c.name)
                            Box(
                                Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
                                    .background(WeftColors.safe.copy(alpha = 0.14f), CircleShape)
                                    .weftClickable { callMinimized = false }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) { WeftText(label, style = WeftType.monoBody.copy(color = WeftColors.safe)) }
                        }
                    } else if (WeftSession.isUnlocked && !screenLocked) {
                        IncomingCallScreen(
                            c,
                            onAnswer = { withMic { report(CoreCalls.accept()) } },
                            onDecline = { CoreCalls.reject() },
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

/**
 * Asks once for notifications (Android 13+), the first time the chat list opens: without them
 * "New message" and "Weft is running" stay hidden (receiving still works).
 */
@Composable
private fun AskForNotices() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("weft.guard", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("askedNotices", false)) {
            prefs.edit().putBoolean("askedNotices", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private const val TOR_BROWSER = "org.torproject.torbrowser"
