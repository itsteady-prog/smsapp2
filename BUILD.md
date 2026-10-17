# Storicracy SMS Gateway (Android)

A very small Android app. It asks the Storicracy Office for text messages that are waiting, sends them from this phone's SIM card, and tells the Office whether each one left. No SMS provider and no per-message fee. Standard rates on the phone's SIM apply.

It uses one small library, a QR reader, for the "Scan code to link" button. The rest is seven short Java files, so it is light on memory and battery.

## What you need

1. A spare Android phone (Android 6 or newer) with a SIM that can send SMS, kept charged, switched on and online.
2. A computer with Android Studio (free, from developer.android.com/studio).
3. In the Office: Settings, Text messages. You are the Superadmin or the CEO.

## Build the app

1. Download the app source from Settings, Text messages, "Download the Android app (source)". Unzip it.
2. Open Android Studio. Choose Open, and pick the unzipped folder `storicracy-sms-gateway`.
3. Wait while Android Studio syncs (the first time it downloads the Android build tools). If it asks to install an Android SDK platform, accept.
4. Choose Build, Build Bundle(s) / APK(s), Build APK(s). When it finishes, click "locate". The file is `app/build/outputs/apk/debug/app-debug.apk`.
5. Copy that file to the phone (cable, Drive, or email to yourself).

## Install on the phone

1. Open the file on the phone. Android will ask to allow installs from that app (Files, Chrome or Drive). Allow it for this one install.
2. If Play Protect warns about an unknown app, choose "Install anyway". The app is yours and its source is in this folder.
3. Do not publish it on Google Play. Google blocks apps that send SMS on their own, and this one is for your company phone only.

## Link it to the Office by scanning a code

1. In the Office, go to Settings, Text messages. Switch text messages on, choose the events, and save.
2. Under Phones, type a name (for example "Office phone") and choose "Link a phone by scanning a code". A QR code appears. It works once and expires in 10 minutes.
3. On the phone, open the app and tap "Scan code to link". Allow the camera and point it at the code. The app says "Linked as ..." and fills in the address and token for you. You can also scan the code with the phone's own camera app, which offers to open this app.
4. Android asks to allow sending SMS right after linking. Accept. Then tap "Check the connection". It should say Connected.

## Link it by token (if scanning is not possible)

1. In the Office, go to Settings, Text messages. Switch text messages on, choose the events, and save.
2. Under Phones, type a name (for example "Office phone") and choose "Link a phone". A token appears **once**. Copy it.
3. On the phone, open the app. The Office address is filled in for you. Paste the token. Switch "Gateway is on".
4. Tap Save, then "Allow sending SMS" and accept. Tap "Check the connection". It should say Connected.
5. Back in the Office, choose "Send myself a test text" (your own number must be proved first under My account, Text messages). Tap "Check for texts now" on the phone. The test text arrives.

## Keep it reliable

- Phone settings, Battery, this app: choose "Unrestricted" or "Don't optimise", or Android may delay checks when the phone sleeps.
- The phone checks every few minutes (you set the number in the Office). Android can add a small delay when the phone is idle.
- If the phone goes offline, texts wait in the Office queue. Nothing is lost. A text the phone picked up but never reported on goes back in the queue after 10 minutes, up to three tries.
- To stop a phone for good (lost or stolen): Settings, Text messages, Phones, Revoke. It stops at once.
- The token is a password. Anyone who has it can collect your waiting texts. Do not share it.

## Two ways to send

- **Automatic (default).** The app sends each text from the SIM in the background and reports whether it left.
- **By hand.** Switch on "Send by hand in my SMS app". The app collects texts but does not send them. Tap "Send the next waiting text by hand". The phone's own SMS app opens with the number and message filled in. Tap Send there, come back, and answer "Did you send it?". Do this within 10 minutes, or the Office queues the text again.

## What the app sends and stores

- The camera is used only inside the "Scan code to link" screen, and nothing is recorded or kept.
- It sends the token over HTTPS to your Office address only. Plain http addresses are refused.
- It keeps the address, the token and the last status on the phone. It does not keep message history, and backups are switched off.

## Limits

- Only the phone's default SIM is used. On a dual SIM phone, set the SIM for SMS in the phone's settings.
- Carriers may block a SIM that sends many texts quickly. The Office limits how many go out each hour (you choose the number) and the app waits between texts.
- I could not build this app on the machine that wrote it, because Android's build tools were not reachable. The code was checked against Android's programming interfaces for errors, but your first build is the real test. If Android Studio reports an error, send it to the Office team and it will be fixed.
