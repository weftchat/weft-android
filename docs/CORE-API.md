# SimpleX core v7.0.2 — the JSON API Weft uses

Extracted from `third_party/simplex-chat` at tag `v7.0.2`. The Haskell core is authoritative
(`src/Simplex/Chat/Library/Commands.hs` parses commands, the Aeson instances define the JSON); the
official Kotlin app (`apps/multiplatform/.../model/SimpleXAPI.kt`, `ChatModel.kt`) is a client of it.
Conventions: sum types are objects tagged with `"type"`; `Maybe` fields are **omitted** when empty;
timestamps are ISO-8601 strings.

## Envelope
- `chatSendCmdRetry` → `{"result":{"type":"<response>",…}}` or `{"error":{"type":"<chatError>",…}}`.
- `chatRecvMsgWait` → same envelope for events; `""` on timeout.
- ChatError: `{"type":"error","errorType":{"type":"noActiveUser"|"commandError"|…}}`,
  `{"type":"errorAgent","agentError":…}`, `{"type":"errorStore","storeError":{"type":"contactNotFound",…}}`,
  `{"type":"errorDatabase",…}`. (`chatCmdError` is WebSocket-only, never over JNI.)

## Opening the store — `chatMigrateInit(dbPath, dbKey, "yesUp")`
- `{"type":"ok"}` → controller handle in element 1.
- **Wrong key (wrong PIN):** `{"type":"errorNotADatabase","dbFile":"…_chat.db"}`.
- Others: `errorMigration`, `errorSQL`, `invalidConfirmation`, `agentError`.

## Startup
1. `/set file paths {"appFilesFolder":…,"appTempFolder":…,"appAssetsFolder":…}` → `cmdOk` (before `/_start`).
2. `/_files_encrypt on` → `cmdOk`.
3. `/u` → `activeUser {user}`, or error `noActiveUser` (→ onboarding).
4. No user: `/_create user {"profile":{"displayName":"…","fullName":""},"pastTimestamp":false}` →
   `activeUser`. Errors: `userExists`, `invalidDisplayName {displayName, validName}`. The first user is
   created **with the preset operators and servers**.
5. `/_start main=on` → `chatStarted` / `chatRunning`. Commands on a user (`/_servers`, `/_get chat`,
   `/_send`, `/_read`, `/_server test`) fail with `chatNotStarted` before this; `/_get chats` does not.
6. Stop: `/_stop` → `chatStopped`, then `chatCloseStore(ctrl)`.

User: `userId`, `agentUserId`, `userContactId`, `localDisplayName`, `profile {profileId, displayName,
fullName, localAlias, …}`, `activeUser`, …

## Servers — only the user's own relay
- `/_operators` (no user or start needed) → `serverOperatorConditions {conditions:{serverOperators:[…], …}}`.
  Set every operator `"enabled":false` with `/_operators [ServerOperator…]`: a disabled operator's servers
  leave the agent config entirely.
- `/_servers <userId>` → `userServers {userServers:[UserOperatorServers]}`: one group per operator plus
  one group with no `operator` for custom servers.
- `/_servers <userId> [UserOperatorServers]` → `cmdOk`, or `commandError "user servers validation error(s)…"`.
  `/_validate_servers <userId> […]` → `userServersValidation {serverErrors, serverWarnings}`.
- UserOperatorServers: `{"operator":null,"smpServers":[UserServer],"xftpServers":[UserServer],"chatRelays":[]}`.
  UserServer: `{"server":"smp://fp:pwd@host","preset":false,"enabled":true,"roles":{},"deleted":false}`
  (omit `serverId` for new ones; `"deleted":true` removes one).
- **Both SMP and XFTP are required** (`noServers` error for either). **If the resulting list for a
  protocol is empty, the core silently falls back to the random preset servers** — Weft must not create
  or join connections until the user's relay is set.
- Test: `/_server test <userId> smp://…` → `serverTestResult {testServer, testFailure?:{testStep, testError}}`
  (no `testFailure` = passed).

## Chat list — `/_get chats <userId> pcc=on`
→ `apiChats {chats:[{chatInfo, chatItems:[last item], chatStats}]}`.
- chatInfo: `{"type":"direct","contact":Contact}` | `group` | `contactConnection {contactConnection:PCC}` | …
- Contact: `contactId`, `localDisplayName`, `profile{displayName,…}`, `activeConn?`, `contactStatus`,
  `chatSettings`, `mergedPreferences`, `chatTs?`, …
- Connection: `connStatus` (`new|prepared|joined|requested|accepted|snd-ready|ready|deleted|failed …`),
  `connectionCode? {securityCode, verifiedAt}` → verified = present. ready = `ready`; can send = `ready|snd-ready`.
- chatStats: `unreadCount`, `minUnreadItemId`, `unreadChat`.

## Chat items — `/_get chat @<contactId> count=50`
Pagination: `count=N` | `before=<itemId> count=N` | `after=<itemId> count=N` | `around=<id> count=N`.
→ `apiChat {chat:{chatInfo, chatItems:[oldest first], chatStats}}`.
ChatItem: `chatDir {"type":"directSnd"|"directRcv"}`, `meta`, `content`.
- meta: `itemId`, `itemTs`, `itemText`, `itemStatus`, `itemTimed? {ttl, deleteAt?}`, `itemDeleted?`, `createdAt`.
- itemStatus: `sndNew` · `sndSent {sndProgress}` (one tick) · `sndRcvd {msgRcptStatus}` (two ticks) ·
  `sndErrorAuth` · `sndError` · `sndWarning` · `rcvNew` · `rcvRead` · `invalid`.
- content: `{"type":"sndMsgContent"|"rcvMsgContent","msgContent":{"type":"text","text":…}|{"type":"image","text","image"}|…}`,
  plus `sndChatFeature`/`rcvChatFeature`, `rcvDirectE2EEInfo`, `rcvDecryptionError`, `sndDeleted`, …

## Send — `/_send @<id> live=off ttl=default sign=off json [ComposedMessage]`
ComposedMessage: `{"msgContent":{"type":"text","text":"hi"},"mentions":{}}` → `newChatItems {chatItems:[{chatInfo, chatItem}]}`.

## Read — `/_read chat @<id>` → `cmdOk`; `/_read chat items @<id> 101,102` → `itemsReadForChat`.

## One-time invitations
- Create: `/_connect <userId> incognito=off` → `invitation {connLinkInvitation:{connFullLink, connShortLink?}, connection:PCC}`.
- Join: `/_connect plan <userId> <link>` → `connectionPlan {connLink:{connFullLink, connShortLink?}, connectionPlan:{type:"invitationLink", invitationLinkPlan:{type:"ok"|"ownLink"|"connecting"|"known"}}|…}`;
  then `/_connect <userId> incognito=off <connFullLink>[ <connShortLink>]` → `sentConfirmation`
  (a short link alone is rejected: resolve it with the plan first).
- Events: `contactConnecting {contact}` → `contactSndReady {contact}` → `contactConnected {contact}`.

## Events (1:1) — `result.type`
`newChatItems`, `chatItemsStatusesUpdated {chatItems}`, `chatItemUpdated {chatItem}`,
`chatItemsDeleted {chatItemDeletions:[{deletedChatItem}], timed}`, `contactConnecting`, `contactSndReady`,
`contactConnected`, `contactUpdated {fromContact, toContact}`, `contactDeletedByContact`, `chatInfoUpdated`.
Errors can also arrive as `{"error":…}`.

## Disappearing messages
`/_set prefs @<contactId> {Preferences}` → `contactPrefsUpdated {fromContact, toContact}`. The object
**replaces all** of the contact's overrides: rebuild it from `contact.mergedPreferences.<feature>.userPreference.preference`
and change only `timedMessages {"allow":"yes","ttl":<secs>}` (off: `{"allow":"no"}`).
State: `contact.mergedPreferences.timedMessages {enabled:{forUser, forContact}, userPreference:{preference:{allow, ttl?}}, contactPreference}`.
Items are timed only when `forUser && forContact`; sent items get `meta.itemTimed {ttl, deleteAt}`, received
ones get `deleteAt` once read. Expiry → `chatItemsDeleted` with `timed:true`.

## Verification
`/_get code @<id>` → `contactCode {connectionCode:"12345 67890 …"}`;
`/_verify code @<id> <code>` → `connectionVerified {verified, expectedCode}`.
