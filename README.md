# SMS Forwarder

A fully local Android app that relays incoming SMS from specific senders to
specific recipients using nothing but the device's own SMS stack —
`BroadcastReceiver` in, `SmsManager` out. No servers, no network calls,
no third-party SDKs.

## How it works

- **`SmsReceiver`** — a manifest-registered `BroadcastReceiver` listening for
  `android.provider.Telephony.SMS_RECEIVED`. On every incoming message it
  checks the master switch, looks up a matching rule, and if found, relays
  the message body via `SmsManager.sendMultipartTextMessage`.
- **`RuleStore`** — SharedPreferences-backed storage (JSON array under one
  key) holding the list of `ForwardRule`s and the master on/off flag. Sender
  matching is done on a normalized number (last 10 digits, so country-code
  prefixes don't break matches) or an uppercased alphanumeric ID for
  sender IDs like `AD-HDFCBK`.
- **`MainActivity`** — the master toggle, the rules list, and buttons to add
  a rule manually or import one from the inbox. Turning the switch on
  triggers the runtime permission prompts (`RECEIVE_SMS`, `READ_SMS`,
  `SEND_SMS`) the first time.
- **`ImportSendersActivity`** — queries `content://sms/inbox` for distinct
  `address` values (most recent first) and returns the tapped one to
  `MainActivity`, which pre-fills the add-rule dialog.

## Project structure

```
app/src/main/java/com/aayush/smsforwarder/
  MainActivity.kt          — rules list + master toggle + add dialog
  SmsReceiver.kt            — the forwarding logic itself
  RuleStore.kt               — SharedPreferences persistence + matching
  ForwardRule.kt             — data model
  RulesAdapter.kt             — RecyclerView adapter for the rules list
  ImportSendersActivity.kt    — one-tap inbox import screen
app/src/main/res/layout/     — activity_main, item_rule, dialog_add_rule,
                                activity_import_senders, item_sender
app/src/main/AndroidManifest.xml
```

## Building

Open the `SmsForwarder/` folder in Android Studio (Koala or newer) and let
Gradle sync — it targets `compileSdk 34` / `minSdk 23`. Or from the command
line, once you've generated a wrapper (`gradle wrapper` if you have Gradle
installed locally):

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Notes / things to be aware of

- **Default SMS app on Android 4.4+**: some OEMs only deliver
  `SMS_RECEIVED` to the currently-set default SMS app, or delay delivery to
  non-default apps. If forwarding seems unreliable on a particular phone,
  setting this app as the default SMS app (or at minimum disabling battery
  optimization for it) usually fixes it.
- **Carrier SMS charges apply** to the forwarded message, since forwarding
  goes out as a real outbound SMS via `SmsManager`, not over the internet —
  that's the tradeoff for keeping everything on-device.
- **Rate limiting**: Android throttles apps that send many SMS in a short
  window (anti-spam protection). Fine for personal forwarding volumes;
  not built for bulk relay.
- The receiver's intent-filter priority is set to `999` so it sees the
  message promptly, but Android doesn't guarantee delivery order across
  apps — this is best-effort, same as any third-party SMS forwarder.
