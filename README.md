# شاشة مباشر — Android / Android TV (يشتغل أوفلاين)

التطبيق يفتح موقعك على Netlify (elegant-empanada-94e121) لما فيه إنترنت، ولما ما فيه
يقدّم النسخة المضمّنة داخل الـ APK (app/src/main/assets/web) بنفس الـ origin، فتظهر التنزيلات
والسجل والحسابات (IndexedDB / localStorage) كما هي.

## البناء
1. Android Studio -> Open -> هذا المجلد -> انتظر Gradle Sync.
2. Build -> Build APK(s) -> app/build/outputs/apk/debug/app-debug.apk

## تحديث النسخة المضمّنة
بعد كل نسخة جديدة: انسخ index.html و sw.js وباقي ملفات الموقع إلى app/src/main/assets/web
وأعد البناء. (النسخة المضمّنة الآن = v61.)

## مهم
- افتح التطبيق مرة واحدة بالإنترنت قبل الأوفلاين (ليحفظ نسخة hls.js داخل التطبيق).
- لو الأندرويد رفض التثبيت فوق القديم (توقيع مختلف) لازم حذف القديم، وحذفه يمسح التنزيلات.

## البناء من GitHub (بدون Android Studio)
1. أنشئ مستودع **Private** جديد على GitHub.
2. ارفع كل محتويات هذا المجلد (مع مجلد .github المخفي) في الجذر.
3. تبويب Actions -> Build APK -> انتظر ~5 دقائق -> نزّل LiveScreenTV-apk من Artifacts (zip فيه app-debug.apk).
التوقيع ثابت (app/livescreen.keystore) فكل نسخة جديدة تتثبّت فوق السابقة بدون حذف.
