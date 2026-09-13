# Navilan Moi Android – Tamil Bitmap / Xprinter XP-80

இந்த Android Studio project:
1. Blogger-ல் இயங்கும் Navilan Moi software-ஐ WebView-ல் திறக்கும்.
2. JavaScript `AndroidPrinter.printReceipt(html,copies)` bridge-ஐ வழங்கும்.
3. Receipt HTML-ஐ off-screen WebView-ல் Android தமிழ் font மூலம் render செய்யும்.
4. Render ஆன receipt-ஐ bitmap/raster ஆக மாற்றும்.
5. Bluetooth SPP மூலம் Xprinter XP-80 போன்ற ESC/POS printer-க்கு `GS v 0` raster data அனுப்பும்.
6. 80mm printer-க்கு 576 dots width default ஆக பயன்படுத்தப்படுகிறது.
7. Receipt முடிவில் cut command அனுப்பப்படும்.

## Printer setup
- Android Settings → Bluetooth-ல் XP-80 printer-ஐ முதலில் Pair செய்யவும்.
- App-ல் Print அழுத்தும்போது paired printer-களில் XP-80/Xprinter/POS-80 போன்ற பெயர் உள்ளதை தேர்வு செய்யும்.
- முதல் முறையாக Android 12+ Bluetooth permission கேட்கும்.

## முக்கியம்
Xprinter பல மாடல்களில் தமிழ் Unicode bytes நேரடியாக வேலை செய்யாது. இந்த project தமிழ் receipt-ஐ image/raster ஆக அனுப்புவதால் அந்த பிரச்சினையை தவிர்க்கிறது.

## Blogger integration
உங்கள் web XML-ன் print function-ல் Android app detection சேர்க்கவும்:
`if(window.AndroidPrinter && AndroidPrinter.printReceipt){ AndroidPrinter.printReceipt(receiptHtml, copies); return; }`
`receiptHtml` என்பது தற்போது print popup-க்கு பயன்படுத்தும் முழு receipt HTML string ஆக இருக்க வேண்டும்.

## Build
Android Studio-ல் project folder-ஐ Open செய்து Gradle Sync → Build APK.


# Android Studio இல்லாமல் APK உருவாக்குவது

இந்த project-ல் GitHub Actions cloud build workflow சேர்க்கப்பட்டுள்ளது.

## முறை
1. GitHub.com-ல் ஒரு புதிய repository உருவாக்கவும்.
2. இந்த project-ன் அனைத்து files-ஐ repository-க்கு upload செய்யவும்.
3. `.github/workflows/build-apk.yml` இருந்தால் GitHub தானாக build செய்யும்.
4. GitHub → Actions → `Build Navilan Moi APK` → Run workflow.
5. Build முடிந்ததும் workflow-ன் Artifacts பகுதியில் `NavilanMoi-XP80-debug` ZIP கிடைக்கும்.
6. ZIP-ஐ download செய்து அதிலுள்ள `app-debug.apk`-ஐ Android phone-ல் install செய்யலாம்.

## கவனம்
- இது Debug APK. வெளியிடும்/Play Store APK அல்ல.
- Android phone-ல் Bluetooth permission அனுமதிக்க வேண்டும்.
- XP-80 முதலில் phone Bluetooth settings-ல் pair செய்யப்பட வேண்டும்.
- தமிழ் ரசீது raster image ஆக அனுப்பப்படும்.

இந்த build-ஐ இங்கே நேரடியாக APK ஆக compile செய்ய Android SDK/Gradle build environment இல்லை; அதனால் cloud build workflow சேர்க்கப்பட்டுள்ளது.
