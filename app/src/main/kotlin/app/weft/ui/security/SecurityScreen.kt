package app.weft.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.weft.R
import app.weft.data.CoreRelays
import app.weft.design.ItemTone
import app.weft.design.TagTone
import app.weft.design.WeftCard
import app.weft.design.WeftColors
import app.weft.design.WeftIcon
import app.weft.design.WeftItem
import app.weft.design.WeftSectionHeader
import app.weft.design.WeftTag
import app.weft.design.WeftText
import app.weft.design.WeftType

/**
 * Screen 9 — Security. Phase 1 has the Relays section only: the user's own relays and "Add your own
 * relay". The phone-seizure and privacy sections come with Phase 2.
 */
@Composable
fun SecurityScreen(bottomPadding: Dp, onAddRelay: () -> Unit) {
    val servers by CoreRelays.servers.collectAsState()
    LaunchedEffect(Unit) { runCatching { CoreRelays.load() } }
    Column(Modifier.fillMaxSize().background(WeftColors.bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 14.dp)) {
            WeftText(stringResource(R.string.security_title), style = WeftType.screenTitle.copy(color = WeftColors.text), modifier = Modifier.padding(start = 2.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column {
                WeftSectionHeader(stringResource(R.string.security_relays))
                WeftCard {
                    servers.forEachIndexed { i, address ->
                        WeftItem(
                            WeftIcon.Server,
                            relayHost(address),
                            first = i == 0,
                            detail = stringResource(if (address.startsWith("xftp://")) R.string.relay_kind_files else R.string.relay_kind_messages),
                            tone = ItemTone.Safe,
                            titleStyle = WeftType.relayName,
                            textGap = 2.dp,
                            trailing = { WeftTag(stringResource(R.string.relay_tag_in_use), TagTone.On) },
                        )
                    }
                    WeftItem(
                        WeftIcon.Plus,
                        stringResource(R.string.relay_add),
                        first = servers.isEmpty(),
                        detail = stringResource(R.string.relay_add_detail),
                        chevron = true,
                        onClick = onAddRelay,
                    )
                }
            }
        }
    }
}

/** "smp://fingerprint:password@host:5223" → "host:5223". Never shows the password. */
fun relayHost(address: String): String =
    address.substringAfter("://").substringAfter('@').substringBefore(',').ifEmpty { address.substringBefore(':') }
