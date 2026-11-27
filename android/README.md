# PESS Android

نسخة Android هجينة للنظام الحالي:

- **Room** يحفظ إعدادات الاتصال محليًا.
- **WebView** يفتح واجهة PESS من خادم Node.js عبر HTTPS.
- **Firebase Auth + Firestore** اختياريان للمزامنة السحابية، ويحتاجان ملف `google-services.json` حقيقيًا.

## البناء

من داخل مجلد `android` وعلى جهاز يحتوي Android SDK متوافقًا مع معماريته:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew assembleDebug
```

الناتج:

```text
app/build/outputs/apk/debug/app-debug.apk
```

في بيئة Manus الحالية تم تجهيز SDK وGradle، لكن البيئة ARM64 وأداة Android `AAPT2` المتاحة x86-64؛ لذلك يجب تنفيذ البناء النهائي على Cloud Computer/x86 أو جهاز تطوير Android عادي.

## تفعيل Firebase

1. أنشئ مشروعًا في Firebase.
2. أضف تطبيق Android بالحزمة `com.pees.agents`.
3. نزّل `google-services.json` وضعه داخل `android/app/`.
4. فعّل Anonymous Authentication وCloud Firestore.
5. شغّل البناء؛ سيتم تطبيق Google Services تلقائيًا عند وجود الملف.

بدون الملف تعمل النسخة في وضع Room + WebView، وتوضح داخل التطبيق أن Firebase غير مفعّل.
