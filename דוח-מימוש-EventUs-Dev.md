# EventUs-Dev — מסמך המימוש

**מה נעשה, מה שונה, ולמה כל החלטה התקבלה כך**

**תאריך:** 23 באוגוסט 2026 · **תיקיית יעד:** `C:\Users\galh2\Desktop\EventUs-Dev`
**תיקיית מקור:** `C:\Users\galh2\Desktop\EventUs` — **לא נגעתי בה בכלל**

---

## 1. תקציר מנהלים

שכפלתי את `EventUs` ל-`EventUs-Dev`, ומימשתי בתוך העותק את כל מה שתוכנן במסמך האפיון: קונטיינריזציה של ה-NestJS, לוגים מובנים, endpoints של בריאות, מנגנון באג מבוקר, קוד Ansible להקמת השרת, קוד Terraform לכל מה שרץ באשכול, צינור Jenkins, ומנגנון ריפוי עצמי בפייתון ובאש.

**המספרים:**

| מדד | ערך |
|---|---|
| קבצים שהועתקו מהמקור | 247 (זהים ברמת ה-checksum) |
| קבצים ששונו | 18 |
| שורות שנוספו / הוסרו בקבצים הקיימים | 111 / 135 |
| קבצים חדשים | 16 |
| שורות קוד תשתית חדשות | 1,674 |
| בדיקות אוטומטיות שהורצו | 22 קטגוריות, כולן עברו |
| באגים אמיתיים שנמצאו ותוקנו במהלך הבדיקות | 3 |

⚠️ **המספרים למעלה הם אחרי סבב הצמצום שמתואר בפרק 16.** המימוש הראשון עמד על 45 קבצים חדשים ו-2,931 שורות; אחרי איחוד לפי תחומי אחריות הוא עומד על 16 קבצים ו-1,674 שורות, עם אותה פונקציונליות בדיוק. פרקים 7 ו-10 מתארים את המבנה הסופי; פרק 16 מתאר מה אוחד ולמה.

**הדבר הכי חשוב במסמך הזה נמצא בפרק 5.** במהלך הבדיקות התגלה באג שהיה מפיל את כל הדמו מול המרצה — נקודות הבריאות של האפליקציה **לא** היו מוחרגות ממנגנון השגיאות המכוון, למרות שהקוד נראה כאילו כן. תוקן ואומת.

---

## 2. הסביבה, והאופן שבו היא עיצבה את שיטת העבודה

לפני שכתבתי שורה אחת בדקתי מה זמין. זה שינה את התכנון:

| יכולת | סביבת הענן שלי | המחשב שלך | מה זה חייב אותי לעשות |
|---|---|---|---|
| Node.js + npm | ✅ | ✅ | הידור ובדיקה מלאים בענן |
| Docker daemon | ✅ (הפעלתי ידנית) | ✅ | — |
| Docker Hub | ❌ חסום | ✅ | **לא יכולתי למשוך `node:22-alpine`.** ראה פרק 11.3 |
| MongoDB | ❌ אין בינארי | ✅ | **בניתי שרת MongoDB מדומה** ברמת פרוטוקול התיל. ראה 11.2 |
| registry.npmjs.org | ✅ | ✅ | התקנת תלויות אמיתיות |
| pypi.org | ✅ | ✅ | התקנתי `ansible`, `ansible-lint`, `checkov`, `python-hcl2` לאימות |
| releases.hashicorp.com | ❌ חסום | ✅ | אין בינארי `terraform` → אימתתי עם `python-hcl2` + `checkov` + בודק הפניות שכתבתי |

⚠️ **ההשלכה המעשית עבורך:** כל מה שאפשר היה לבדוק — נבדק בפועל, לא "נראה נכון". מה שלא ניתן היה לבדוק כאן מפורט במלואו בפרק 12, עם הפקודה המדויקת להריץ אצלך ומה הפלט הצפוי.

---

## 3. שכפול התיקייה

### 3.1 מה הועתק ומה לא

```
EventUs  ──►  EventUs-Dev     247 קבצים
```

**הועתק:** כל קוד המקור, `.git` על כל ההיסטוריה, הדיאגרמות, המצגת, צילומי המסך, `local.properties` (הנתיב ל-Android SDK שלך), `.gitattributes`, `.vscode`.

**לא הועתק — בכוונה:**

| מה | למה |
|---|---|
| `backend/event-us/node_modules` | ~200MB שהותקנו ל-Windows. `npm install` מייצר אותם מחדש נכון. |
| `backend/event-us/dist` | פלט בנייה. `npm run build` מייצר מחדש. |
| `frontend/.gradle`, `frontend/app/.gradle` | מטמון Gradle, קשור לנתיב המקורי. |
| `frontend/build`, `frontend/app/build` | פלט בנייה של Android. |
| `frontend/.idea`, `frontend/app/.idea` | מצב IDE, לא קוד. |

**למה זו ההחלטה הנכונה:** תיקיית `EventUs-Dev` היא עכשיו עותק **נקי** של הפרויקט. אם משהו נשבר בבנייה, זה בגלל הקוד — לא בגלל מטמון ישן שנגרר מהעותק הקודם. זו גם הסיבה שאותן תיקיות רשומות ב-`.gitignore` מלכתחילה.

### 3.2 איך אומת שהעותק תקין

לא הסתפקתי בספירת קבצים. הרצתי השוואת `md5sum` על כל 247 הקבצים:

```
source files (excluding build artifacts): 247
clone  files:                             247

MISSING FROM CLONE:   (none)
EXTRA IN CLONE:       (none)
FILES THAT DIFFER:    (none)
```

⚠️ **הערה על השכפול:** הניסיון הראשון להעתיק ברקע נקטע באמצע (מערכת הקבצים הממופה של Windows איטית, והתהליך נהרג כשהפקודה הסתיימה). זיהיתי את זה בספירת הקבצים — 34 מתוך 146 בתיקיית `frontend`. חזרתי והעתקתי מחדש בחלקים עם אימות אחרי כל חלק. **הבדיקה היא שתפסה את זה, לא ההנחה.**

### 3.3 תיקיית המקור

```
$ cd EventUs && git status --short
 M .vscode/settings.json
?? EventUs-DevOps-Guide.md
```

השינוי היחיד ב-`.vscode/settings.json` היה שם **לפני** שהתחלתי (ראיתי אותו בסריקה הראשונה), ו-`EventUs-DevOps-Guide.md` הוא מסמך התכנון ששלחתי לך קודם. **אף קובץ קוד במקור לא נגעתי בו.**

---

## 4. שינויי ה-Backend

18 קבצים שונו. הנה כולם, מהמשמעותי לפחות משמעותי.

### 4.1 `src/app.module.ts` — הסוד יוצא מהקוד

**לפני:**

```typescript
MongooseModule.forRootAsync({
  useFactory: () => ({
    uri: 'mongodb+srv://zivmorgan:<REDACTED>@cluster0.c5gbgne.mongodb.net/EventUs',
    useNewUrlParser: true,
    useUnifiedTopology: true,
  }),
}),
```

**אחרי:**

```typescript
const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/EventUs';

MongooseModule.forRoot(MONGODB_URI, {
  serverSelectionTimeoutMS: 5000,
  retryWrites: true,
  retryAttempts: 20,
  retryDelay: 3000,
}),
```

| שינוי | נימוק |
|---|---|
| מחרוזת החיבור מ-`process.env` | הסוד מגיע מ-Kubernetes Secret. אין credential בקוד. |
| ברירת מחדל `127.0.0.1` | פיתוח מקומי עובד בלי להגדיר שום דבר. |
| `forRoot` במקום `forRootAsync` | אין כאן שום דבר אסינכרוני. פחות קוד, אותה תוצאה. |
| הוסרו `useNewUrlParser` / `useUnifiedTopology` | no-op ב-Mongoose 8, מייצרים אזהרות. |
| `serverSelectionTimeoutMS: 5000` | ברירת המחדל היא 30 שניות. ה-readiness probe היה נתקע עליהן. |
| `retryAttempts: 20, retryDelay: 3000` | **תוספת שלא הייתה בתכנון.** נותן לאפליקציה 60 שניות סבלנות עד ש-MongoDB עולה. בלי זה, ב-`terraform apply` ראשון ה-Pod של האפליקציה קורס ונכנס ל-`CrashLoopBackOff` רק כי מסד הנתונים עוד לא סיים לעלות. 60 שניות נכנסות בתוך תקציב ה-`startup_probe` (120 שניות). |

בנוסף נוספה למודול הרשמת ה-middlewares:

```typescript
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    consumer.apply(requestLogMiddleware, chaosMiddleware).forRoutes('*');
  }
}
```

⚠️ **הסדר כאן הוא לא שרירותי.** `requestLogMiddleware` רשום **לפני** `chaosMiddleware`. הוא נרשם ל-`res.on('finish')`, כך שגם בקשה שה-chaos פוצץ נכנסת ללוג עם `statusCode: 500`. אילו הסדר היה הפוך — בקשות שנחסמו לא היו מתועדות כלל, ומנגנון הריפוי לא היה רואה שום דבר.

### 4.2 `src/main.ts` — נקודת הכניסה

| שינוי | נימוק |
|---|---|
| הוסר `SpelunkerModule` | הדפיס גרף Mermaid לקונסול בכל עלייה. ארבע שורות זבל שהיו נכנסות ל-Elasticsearch בכל restart. הוסר גם מ-`package.json` ומ-`package-lock.json`. |
| `logger: new JsonLogger()` | גם הלוגים הפנימיים של Nest יוצאים JSON. |
| `PORT` ו-`HOST` מ-env | דרישת קונטיינר. |
| `HOST = '0.0.0.0'` | ⚠️ קריטי. מאזין רק ל-`localhost` = שום דבר מחוץ לקונטיינר לא מגיע, וכל ה-probes נכשלים. |
| `useGlobalPipes(new ValidationPipe(...))` | תיקון באג: `ValidationPipe` היה מיובא בקוד המקורי אבל **אף פעם לא הופעל**. |
| `enableShutdownHooks()` | ב-rolling update Kubernetes שולח `SIGTERM`. עם זה Nest סוגר את החיבור ל-Mongo מסודר. אומת בפועל — ראה 11.1. |
| `enableCors` | מאפשר בדיקה מהדפדפן. לא נדרש ל-Android. |
| `unhandledRejection` / `uncaughtException` | הקוד המקורי מלא ב-promises שלא מחכים להם. בלי זה, כשל שקט. |

### 4.3 קובץ חדש ב-`src/common/` — `platform.ts`, 98 שורות

| מה בקובץ | שורות | תפקיד |
|---|---|---|
| `log()` + `JsonLogger` | 25 | פולט כל שורת לוג כאובייקט JSON יחיד ל-stdout |
| `requestLog()` | 17 | שורת לוג אחת לכל בקשה, עם `statusCode` ו-`durationMs` |
| `chaos()` | 10 | מייצר שגיאות 500 בשיעור מוגדר, פרט לנקודות הבריאות |
| `PlatformController` | 33 | `/health/live`, `/health/ready`, `/chaos/status`, `/chaos/boom` |

בגרסה הראשונה אלה היו חמישה קבצים נפרדים (155 שורות סה"כ). איחדתי אותם כי כולם צד אחד של אותו חוזה — איך האפליקציה מדברת עם הפלטפורמה שמריצה אותה — והם חולקים שלושה קבועים (`SERVICE`, `VERSION`, `HEALTH_PATHS`) שאחרת היו דורשים imports הדדיים בין חמישה קבצים באורך 18–56 שורות.

**שם השדה `statusCode` הוא חוזה בין שלושה מקומות:** ה-middleware שכותב אותו, קונפיג ה-Filebeat שמפרסר אותו, והשאילתה ב-`healer.py` שמחפשת אותו. שינוי בשם באחד מהם שובר את השרשרת.

**נקודה שכדאי להזכיר במצגת:** ה-middleware חותך את ה-query string מהנתיב הנרשם:

```typescript
path: req.originalUrl.split('?')[0],
```

זה לא ניקיון בעלמא. ה-Login של EventUs הוא `GET /users/login?email=...&password=...`. בלי החיתוך, **הסיסמה של כל משתמש שמתחבר הייתה נכתבת ל-Elasticsearch בטקסט גלוי.** אימתתי שזה עובד: שלחתי בקשה עם `password=SUPERSECRET` וחיפשתי את המחרוזת בכל הלוגים — לא נמצאה.

### 4.4 ניקוי הלוגים — 18 `console.log` שהוסרו או הומרו

זה נראה כמו ניקיון קוסמטי. הוא לא.

**מה שקרה בבדיקה:** הרצתי את האפליקציה, שלחתי בקשות, ובדקתי שכל שורת לוג היא JSON תקין. שורה אחת לא הייתה:

```
[ { name: 'test' } ]
```

זה `console.log(search_query)` מתוך `event.service.ts:123`. שורה כזו נשלחת ל-Elasticsearch, ה-parser של `ndjson` נכשל עליה, והיא נכנסת עם `error.message` במקום שדות. במקרה הטוב — רעש. במקרה הרע, אם היא מופיעה הרבה — היא מטה את המכנה בחישוב יחס השגיאות של ה-healer.

**הטיפול, לפי סוג:**

| סוג | טיפול | דוגמה |
|---|---|---|
| הדפסת דיבאג | נמחק | `console.log(userEvent.attendents)` ב-`removeUser` |
| מתודת דיבאג שלמה | נמחקה + נמחקה הקריאה אליה | `printAllEvents()`, `printAllProfilePics()` |
| טיפול בשגיאה | הומר ללוגר המובנה | `console.log("error in edit user " + e.message)` → `logError('edit user failed', { userId: _id, reason: e.message })` |
| קוד מת | נמחק | `console.log` כשורה בפני עצמה ב-`profilePic.controller.ts:41`, וגם בלוק שלם בהערה |

**דוגמה מייצגת** — `user.controller.ts`:

```diff
-      console.log("error in edit user " + e.message)
+      logError('edit user failed', { userId: _id, reason: e.message });

-    console.log("user " + _id + " rating " + ratingDTO._id)
+    logInfo('user rating event', { userId: _id, eventId: ratingDTO._id });
```

⚠️ שים לב שאני **לא מוחק** את המידע הדיאגנוסטי — אני הופך אותו לשדות שאפשר לחפש בקיבנה. `userId` ו-`eventId` הופכים לשדות במסמך, לא לטקסט בתוך מחרוזת.

**התוצאה, אחרי התיקון:**

```
non-JSON lines: NONE - stream is pure NDJSON
request lines: 11 | codes: {200: 9, 500: 1, 404: 1}
```

### 4.5 `Dockerfile` ו-`.dockerignore`

```dockerfile
FROM node:22-alpine AS builder
...
FROM node:22-alpine AS runtime
```

| החלטה | נימוק | אומת? |
|---|---|---|
| Multi-stage | שלב הבנייה מכיל TypeScript ו-`@nestjs/cli`; שלב הריצה מקבל רק `dist/` ותלויות production | ✅ 481 חבילות בבנייה מול 146 בריצה |
| `npm ci` ולא `npm install` | מציית ל-lockfile בדיוק. בנייה שחזירה. | ✅ |
| `package*.json` מועתקים לפני `src` | שכבת מטמון של Docker — שינוי קוד לא מפיל את שכבת ההתקנה | — |
| `USER eventus` | הקונטיינר לא רץ כ-root | ✅ מגובה ב-`run_as_non_root` ב-Terraform |
| `--omit=dev` | אין קומפיילר TypeScript בתמונת ה-production | ✅ `typescript` ו-`@nestjs/cli` לא קיימים בשלב הריצה |
| `ARG APP_VERSION` | Jenkins מזריק את ה-SHA בזמן הבנייה | ✅ |
| `alpine` | ~50MB בסיס מול ~350MB | — |

⚠️ `.dockerignore` הוא לא קישוט: בלעדיו ה-`COPY` היה מעתיק את `node_modules` המקומי (מאות מגה, מהודר ל-Windows) לתוך ה-build context. הבנייה הייתה לוקחת דקות ונכשלת.


---

## 5. הבאג הקריטי שנמצא בבדיקות

זה החלק החשוב במסמך. אם תספר על משהו אחד מהמימוש — ספר על זה.

### 5.1 הקוד נראה נכון

```typescript
const EXEMPT = ['/health/live', '/health/ready'];

export function chaosMiddleware(req: Request, res: Response, next: NextFunction) {
  const rate = errorRate();
  if (rate === 0 || EXEMPT.includes(req.path)) {
    next();
    return;
  }
  ...
}
```

קריאה ראשונה: ברור שנקודות הבריאות מוחרגות. `req.path` הוא הנתיב, `EXEMPT` מכיל את הנתיבים, `includes` בודק.

### 5.2 הבדיקה אמרה אחרת

הרצתי את האפליקציה עם `CHAOS_ERROR_RATE=0.5` ויריתי 20 בקשות לכל נקודה:

```
health/live x20 : 200 200 200 200 500 500 500 200 500 500 500 200 500 200 200 200 200 500 200 200
health/ready x20: 200 200 500 500 500 500 500 200 500 500 200 500 500 500 500 200 500 200 200 200
```

**נקודות הבריאות קיבלו 500 בכ-45% מהמקרים.** ההחרגה לא עבדה בכלל.

### 5.3 שורש הבעיה

NestJS רושם middleware שהוגדר עם `forRoutes('*')` דרך `app.use('*', mw)` של Express. כתבתי בדיקה מבודדת:

```javascript
app.use('*', (req, _res, next) => {
  console.log(JSON.stringify({
    'req.path': req.path,
    'req.baseUrl': req.baseUrl,
    'req.originalUrl': req.originalUrl,
  }));
  next();
});
```

הפלט לבקשה `GET /health/live?x=1`:

```json
{"req.path":"/","req.baseUrl":"/health/live","req.originalUrl":"/health/live?x=1"}
```

**`req.path` הוא `/` — תמיד.** כשמרכיבים middleware על תבנית נתיב, Express מעביר את החלק שהותאם ל-`req.baseUrl` ומשאיר ב-`req.url` רק את השארית. הבדיקה `EXEMPT.includes('/')` תמיד שקרית, ולכן ה-chaos חל על כל בקשה.

### 5.4 התיקון

```diff
 export function chaosMiddleware(req: Request, res: Response, next: NextFunction) {
+  const path = req.originalUrl.split('?')[0];
   const rate = errorRate();
-  if (rate === 0 || EXEMPT.includes(req.path)) {
+  if (rate === 0 || EXEMPT.includes(path)) {
```

`req.originalUrl` מחזיק תמיד את הנתיב המקורי המלא, ללא קשר למקום ההרכבה. זה גם מה ש-`requestLogMiddleware` השתמש בו מלכתחילה — ולכן **הנתיבים בלוגים היו נכונים כל הזמן**, מה שהסתיר את הבאג: הלוג הראה `path: "/health/live"` עם `statusCode: 500` ונראה סביר לגמרי.

### 5.5 האימות אחרי התיקון

```
  /health/live           {200: 100}
  /health/ready          {200: 30}
  /events/search         {200: 237, 500: 163}

n=400  errors=163  rate=0.407  expected=0.400  sigma=0.024  z=+0.31
```

מאה בקשות לנקודת הבריאות, כולן 200. ארבע מאות בקשות לנתיב עסקי, שיעור שגיאות 0.407 מול יעד של 0.400 — סטייה של 0.31 סיגמא, כלומר בדיוק מה שמצפים סטטיסטית.

### 5.6 למה זה היה מפיל את הדמו

```mermaid
flowchart TD
    A["Jenkins פורס גרסה עם 45% שגיאות"] --> B{"האם probes<br/>מוחרגים?"}
    B -->|"לא (הבאג)"| C["liveness מחזיר 500"]
    C --> D["Kubernetes הורג את ה-Pod"]
    D --> E["CrashLoopBackOff"]
    E --> F["אין תעבורה, אין לוגים,<br/>ה-healer לא רואה כלום"]
    F --> G["הדמו נכשל מול המרצה"]
    B -->|"כן (התיקון)"| H["Pod חי, probes ירוקים,<br/>מחזיר 500 למשתמשים"]
    H --> I["לוגים זורמים ל-Elasticsearch"]
    I --> J["ה-healer מזהה ומבצע rollback"]
    J --> K["הדמו עובד"]
```

**הפואנטה שאתה מציג למרצה:** כל התרחיש של הפרויקט בנוי על ההבחנה בין Pod **מת** — ש-Kubernetes פותר לבד — לבין Pod **חי שמחזיר שגיאות**, שרק תצפית על הלוגים מגלה. הבאג הזה הפך את התרחיש השני לראשון, וביטל את כל הרעיון. שתי שורות קוד.

⚠️ **הבאג הזה קיים גם במסמך התכנון שכתבתי לך קודם.** אני מעדכן שם את הקוד. אם העתקת ממנו — קח את הגרסה מ-`EventUs-Dev`.

---

## 6. שינויי ה-Android

שלוש שורות פונקציונליות בשלושה קבצים, ועוד שני קבצי הגדרה.

### 6.1 `app/build.gradle.kts`

```diff
         testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
+
+        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2/\"")
     }

     buildTypes {
+        debug {
+            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2/\"")
+        }
         release {
             isMinifyEnabled = false
             proguardFiles(...)
+            buildConfigField("String", "API_BASE_URL", "\"http://eventus.local/\"")
         }
     }
     buildFeatures {
         viewBinding = true
+        buildConfig = true
     }
```

⚠️ **`buildConfig = true` הוא חובה.** ב-Android Gradle Plugin 8 (הפרויקט על 8.3.0) יצירת `BuildConfig` כבויה כברירת מחדל. בלעדיה: `cannot find symbol: variable BuildConfig` והבנייה נכשלת.

אימתתי שההשמה מייצרת Java תקין:

```java
public static final String API_BASE_URL = "http://10.0.2.2/";
```

### 6.2 שני קבצי ה-Java

```diff
  # AsyncHttpRequest.java
+ import com.example.eventus.BuildConfig;
- String url = "http://10.0.2.2:3000/" + this.dir;
+ String url = BuildConfig.API_BASE_URL + this.dir;

  # Database.java
+ import com.example.eventus.BuildConfig;
- uploader.uploadFile("http://10.0.2.2:3000/profilepics", pic, callback);
+ uploader.uploadFile(BuildConfig.API_BASE_URL + "profilepics", pic, callback);
```

⚠️ **הקובץ השני הוא זה שמפספסים.** `FileUploader` משתמש ב-OkHttp עם כתובת מלאה נפרדת ולא עובר דרך `AsyncHttpRequest`. מי שמשנה רק את הראשון — הכל עובד חוץ מהעלאת תמונת פרופיל, וזה מתגלה בדיוק מול המרצה.

אימות סופי על התיקייה כולה:

```
$ grep -rn '10\.0\.2\.2:3000' EventUs-Dev/frontend/app/src
  none
```

**למה פורט 80 ולא 3000:** האפליקציה כבר לא מדברת עם תהליך Node בודד. היא מדברת עם שער הכניסה של האשכול — Traefik, שמאזין על 80. `10.0.2.2` היא כתובת קסם קבועה של האמולטור אל ה-loopback של המארח, ובזכות `networkingMode=mirrored` פורט 80 ב-WSL הוא פורט 80 ב-Windows.

### 6.3 קונפיג הרשת

בקוד המקורי היה `network_security_config.xml` שהגדיר כללים ל-`127.0.0.1` — אבל **ה-Manifest אף פעם לא הצביע עליו**. הקובץ היה קוד מת; מה שאיפשר HTTP בפועל היה `android:usesCleartextTraffic="true"`, שפותח HTTP לכל דומיין בעולם.

```diff
- android:usesCleartextTraffic="true"
+ android:networkSecurityConfig="@xml/network_security_config"
```

והקובץ עצמו נכתב מחדש: HTTPS נדרש כברירת מחדל, HTTP מותר רק מול ארבעה מארחים מקומיים. שני קבצי ה-XML אומתו כ-well-formed.

---

## 7. קוד התשתית

1,674 שורות שלא היו קיימות קודם, ב-16 קבצים.

### 7.1 Ansible — 145 שורות, 19 משימות

```
infra/ansible/
├── inventory.ini    2 שורות — wsl-node, connection=local
└── site.yml         143 שורות — playbook יחיד, 19 משימות
```

**למה playbook אחד ולא roles:** role הוא יחידת שימוש חוזר בין playbooks ובין פרויקטים. כאן יש שרת אחד ו-playbook אחד, ולכן ארבעת ה-roles (`common`, `docker`, `k3s`, `tools`) היו רק ארבע תיקיות שמפצלות רצף אחד של משימות. 9 קבצים ו-384 שורות תיארו את מה שכתוב כאן ב-143. הסדר בין המשימות זהה, ההתנהגות זהה.

**החלטות:**

| החלטה | נימוק |
|---|---|
| `k3s_version: "v1.33.13+k3s2"` מקובע | הרעיון של IaC הוא שהרצה בעוד חודש נותנת אותה תוצאה. `latest` שובר את זה. |
| `--write-kubeconfig-mode 644` | בלעדיו `/etc/rancher/k3s/k3s.yaml` הוא `600 root:root` ו-Terraform מקבל permission denied. |
| `--node-name eventus-node` | ב-WSL ה-hostname יכול להשתנות. שם קבוע מייצב `wait` ו-`nodeSelector`. |
| `eviction-hard=...available<2%` | ⚠️ תיקון ספציפי ל-WSL: kubelet רואה את דיסק C: כולו. אם הוא מלא ב-88%, Kubernetes מתחיל לזרוק Pods עם `DiskPressure` בלי סיבה נראית לעין. |
| **אין** `--disable=traefik` | Traefik הוא ה-Ingress שלנו. מדריכים רבים באינטרנט מבטלים אותו — זה היה שובר את כל שכבת ה-Ingress. |
| בדיקות מקדימות (דיסטרו, זיכרון) | מונע את שתי התקלות שמתגלות רק שעה אחר כך: דיסטרו לא נכון, וזיכרון שלא יספיק ל-Elasticsearch. |

**מה נבדק:**

```
ansible-playbook site.yml --syntax-check   → OK
ansible-playbook site.yml --list-tasks     → 19 משימות
ansible-doc על כל מודול                    → 10/10 קיימים
ansible-lint --profile production site.yml → Passed: 0 failures, 0 warnings
```

⚠️ `ansible-lint` תפס שתי בעיות אחרי האיחוד: `risky-shell-pipe` ו-`command-instead-of-module`, שתיהן על `curl -sfL https://get.k3s.io | sh -` — הדרך המתועדת של Rancher, אבל היא מסתירה כשלים כי אין `pipefail`. החלפתי ב-`get_url` שמוריד לקובץ ואז `command` שמריץ אותו, וכך מתקבל קוד יציאה אמיתי. הקוד עובר את פרופיל **production** — הפרופיל המחמיר ביותר.

### 7.2 Terraform — 851 שורות, 30 משאבים

```
infra/terraform/
├── providers.tf              14 שורות — provider ~> 2.38, נתיב kubeconfig
├── variables.tf              26 שורות — 4 משתנים, אחד עם validation
├── terraform.tfvars.example   3 שורות
└── main.tf                  808 שורות — 30 משאבים + 3 outputs
    ├── locals + 3 namespaces
    ├── MongoDB          Secret + Headless Service + StatefulSet + PVC
    ├── האפליקציה         ConfigMap + Deployment + Service + Ingress
    ├── Elasticsearch    Service + StatefulSet + init-container
    ├── Kibana           Service + Deployment + Ingress
    ├── Filebeat         SA + ClusterRole + Binding + ConfigMap + DaemonSet
    ├── ריפוי עצמי        SA + Role + RoleBinding + ConfigMap + CronJob
    └── Jenkins RBAC     SA + token Secret + Role + RoleBinding
```

**למה קובץ אחד:** Terraform קורא את כל קבצי ה-`.tf` בתיקייה ומאחד אותם לגרף תלויות אחד. סדר הביצוע נגזר מהקשרים בין המשאבים, לא משמות הקבצים — כך שהקידומות `01-`, `02-` אפילו מטעות, כי הן רומזות על סדר שלא קיים. הפיצול לא השפיע על ההתנהגות, רק על מספר הקבצים.

**מה ירד בדרך:** `versions.tf` ו-`outputs.tf` נכנסו ל-`main.tf`; `.checkov.yaml` ו-`CHECKOV.md` הוסרו (סורק אבטחה סטטי הוא כלי טוב אבל הוא לא חלק מהאיפיון, והוא הוסיף שני קבצים ותהליך להסביר — הבקרות שהוא תפס, security context ו-liveness probe ל-Kibana, נשארו בקוד); `variables.tf` ירד מ-25 משתנים ל-4, כי 21 מהם היו קבועים שאף פעם לא שיניתי.

**ההחלטה המרכזית — הפרדת בעלות:**

```hcl
lifecycle {
  ignore_changes = [
    spec[0].template[0].spec[0].container[0].image,
    metadata[0].annotations,
  ]
}
```

Terraform מגדיר את **מבנה** ה-Deployment. Jenkins מגדיר את **הגרסה**. ה-healer כותב את חותמת ה-cooldown. בלי `ignore_changes`, כל `terraform plan` היה מציע להחזיר את התג הישן ולמחוק את ה-annotations — ו-`apply` היה מוחק את הפריסה של Jenkins.

⚠️ `metadata[0].annotations` מכסה את כל ה-annotations במכה אחת: `deployment.kubernetes.io/revision` (Kubernetes כותב), `kubernetes.io/change-cause` (Jenkins כותב) ו-`eventus.io/last-rollback` (ה-healer כותב). בגרסה הראשונה מנינו את שלושתם בשמות מפורשים — חמש שורות במקום אחת, ובלי הגנה מפני annotation רביעי שיתווסף בהמשך.

**החלטות נוספות שכדאי להכיר:**

| החלטה | נימוק |
|---|---|
| `max_unavailable = "0"` + `max_surge = "1"` | פריסה בלי downtime. Pod חדש עולה, נעשה Ready, ורק אז ישן יורד. באפליקציה באנדרואיד לא רואים כלום. |
| `revision_history_limit = 5` | תנאי הכרחי ל-`rollout undo`. בלי היסטוריה אין למה לחזור. |
| `startup_probe` נפרד | נותן ל-Pod ראשון עד 120 שניות לעלות בלי שה-liveness יהרוג אותו באמצע. |
| `init_container` שמתקן הרשאות ב-ES | ה-PVC של `local-path` נוצר בבעלות root, ES רץ כמשתמש 1000. בלי זה: `AccessDeniedException`. התקלה מספר 1 בהרצת ES ב-Kubernetes. |
| `limit = 2560Mi` מול `heap = 1g` | ה-JVM צריך גם off-heap. limit ≈ heap × 2.5. limit = heap פירושו OOMKilled. |
| `elastic_stack_version = "8.19.20"` | גרסאות 9.4.0/9.4.1 של Kibana מכילות באג כשה-security כבוי. גרסה מקובעת ובדוקה. |
| `checksum/config` ב-annotations של Filebeat | שינוי ב-ConfigMap לא מפעיל מחדש Pods. ה-annotation משתנה עם הקונפיג ומכריח rollout. |
| Role ב-`eventus`, ServiceAccount ב-`platform` | הרשאות חוצות-namespace בצורה הנכונה: ה-RoleBinding יושב איפה שהמשאב, ומצביע על SA ב-namespace אחר. |
| `wait_for_service_account_token = true` | מ-Kubernetes 1.24 טוקנים לא נוצרים אוטומטית. הדגל מוודא שה-Secret מכיל טוקן לפני ש-Terraform ממשיך. |

**מלכודת שתפסתי בכתיבה** — קונפיג ה-Filebeat:

```hcl
index = "${local.log_index}-%%{+yyyy.MM.dd}"
```

הרצף `%{` פותח הוראת תבנית ב-HCL. כדי לקבל `%{` מילולי בפלט צריך `%%{`. בלי זה `terraform validate` נכשל עם `Invalid template control keyword`. אימתתי שהפלט הסופי הוא בדיוק `eventus-logs-%{+yyyy.MM.dd}` — מה ש-Filebeat מצפה לו.

**מה נבדק:**

```
python-hcl2 parse                  → 4/4 קבצים
בודק הפניות שכתבתי                  → 30 משאבים, 0 הפניות שבורות,
                                      0 משתנים לא מוגדרים, 0 משתנים לא בשימוש,
                                      0 locals יתומים, 0 שמות כפולים
בודק סכמת ה-API של Kubernetes      → 339 שדות, 0 בעיות
בודק סמנטי (selector↔labels,        → 70 בדיקות, 0 בעיות
 volumeMounts↔volumes, RBAC, probes)
yamlencode של קונפיג Filebeat       → נבדק שהפלט YAML תקין ושתבנית התאריך שורדת
```

⚠️ **הבקרות שסורק האבטחה תפס בסבב הראשון נשארו בקוד** גם אחרי שהסורק עצמו הוסר: security context ו-liveness probe ל-Kibana, `allow_privilege_escalation = false` ו-`capabilities.drop = ["ALL"]` על קונטיינר האפליקציה. מה שירד הוא שני קבצי התיעוד של החריגות, לא הבקרות עצמן.

### 7.3 Jenkins — 113 שורות

`Jenkinsfile` בשורש (81 שורות), ותמונת Jenkins מותאמת ב-`infra/jenkins/Dockerfile` (32 שורות). ההרצה עצמה עברה ל-`./eventus.sh jenkins`.

| החלטה | נימוק |
|---|---|
| `IMAGE_TAG = "${GIT_SHA}-${BUILD_NUMBER}"` | ⚠️ הכי חשוב. ה-SHA מקשר לקוד, מספר הבנייה מבטיח ייחודיות. **בלי תג ייחודי, `rollout undo` לא מחליף תמונה בפועל וכל הדמו נופל.** |
| Jenkins בקונטיינר מחוץ ל-K3s | Jenkins צריך לבנות תמונות Docker. בתוך Kubernetes זה דורש DinD או Kaniko. מבחוץ הוא מקבל את `docker.sock` ישירות. |
| `--network host` | ה-kubeconfig של K3s מצביע על `https://127.0.0.1:6443`. ברשת bridge זה הקונטיינר עצמו. עם רשת מארח זה ה-WSL, וגם ה-smoke test מול `localhost` עובד. |
| `--group-add ${DOCKER_GID}` | גישה ל-socket בלי `--privileged` ובלי להריץ כ-root. |
| ServiceAccount ייעודי ולא kubeconfig של אדמין | Jenkins יכול `patch deployments` ולקרוא לוגים. הוא **לא** יכול למחוק Deployment, לקרוא Secrets, או לגעת ב-`kube-system`. |
| `--password-stdin` + `docker logout` | הסיסמה לא נכנסת לרשימת התהליכים ולא נשארת ב-`~/.docker/config.json`. |
| `annotate ... change-cause` | הופך את `rollout history` לקריא. בלי זה כל השורות מציגות `<none>`. |

**מה נבדק:** מאזן סוגריים ומחרוזות, מבנה declarative pipeline מלא, כל 6 השלבים עם `steps`, כל הפניות ה-env מוגדרות, ו — הבדיקה המשמעותית — **סימולציה של אינטרפולציית Groovy על כל 9 קטעי ה-shell ואז `bash -n` על התוצאה**. כולם עברו. כולל אימות שהסיסמה נשארת משתנה shell ולא מוחלפת על ידי Groovy:

```
rendered credentials line: echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
```

### 7.4 מנגנון הריפוי — 172 שורות

`healer.py` (125) + `rollback.sh` (28) + `Dockerfile` (19). אין `requirements.txt` — ה-healer משתמש רק בספריית התקן של Python.

**החלטת התכן המרכזית — שני תנאים, לא אחד:**

| תנאי | מונע |
|---|---|
| `errors >= 10` | rollback בגלל 2-3 שגיאות בודדות בעומס נמוך |
| `errors / total >= 0.25` | rollback בגלל 15 שגיאות מתוך 50,000 בקשות (0.03%, רעש רגיל) |

בנוסף: cooldown של 10 דקות, בדיקה שקיימת revision קודמת, ומצב `DRY_RUN`.

**החלטות מימוש:**

| החלטה | נימוק |
|---|---|
| `urllib` ולא `requests` | אפס תלויות. תמונה של ~75MB, בנייה מהירה, אין `pip install` שיכול להיכשל. |
| `service.keyword` ולא `service` | ⚠️ Elasticsearch ממפה מחרוזת ל-`text` (מפורק לטוקנים) עם תת-שדה `.keyword`. `term` על `service` מחפש טוקן — ו-`eventus-api` מפורק ל-`eventus` ו-`api`, ולכן **לא נמצא כלום**. התקלה השקטה הנפוצה ביותר ב-ES. |
| `exists: statusCode` לספירת הסך | סופר רק בקשות HTTP. שורת `server started` אין לה `statusCode` ולא מזייפת את המכנה. |
| קריאת **כל** מפת ה-annotations כ-JSON | מפתח ה-annotation הוא `eventus.io/last-rollback` — נקודה וקו נטוי, שניהם תווים מיוחדים ב-jsonpath. במקום להתאבק עם escaping, מושכים את כל המפה ומפרסרים בפייתון. |
| `rollback.sh` נפרד ולא `subprocess` ישיר | האפיון דרש Python **וגם** Bash. ההפרדה גם נכונה: פייתון מחליט, באש מבצע. |
| ה-annotation נכתב **אחרי** ש-`rollout status` הצליח | אילו נכתב לפני והחזרה נכשלה, היינו נכנסים ל-cooldown של 10 דקות בלי שהמערכת התאוששה. |


---

## 8. כל ההחלטות במקום אחד

| # | החלטה | האלטרנטיבה | הנימוק |
|---|---|---|---|
| 1 | Traefik בלבד, בלי Nginx | ingress-nginx / Nginx חיצוני | K3s מגיע עם Traefik מוגדר, והוא מנתב לפי host ולפי path. Nginx היה מוסיף רכיב, קונטיינר וקונפיג בלי יכולת חדשה. **זו הערת המרצה, והיא מיושמת.** |
| 2 | Filebeat בלבד, בלי Logstash | ELK מלא | Logstash הופך טקסט חופשי לשדות. האפליקציה פולטת JSON מובנה, אז אין מה להפוך. חיסכון של רכיב ו-~1GB RAM. |
| 3 | Jenkins בקונטיינר מחוץ לאשכול | Jenkins כ-Pod | בנייה של תמונות בתוך Kubernetes דורשת DinD/Kaniko. מבחוץ — `docker.sock` ישירות. |
| 4 | Terraform מנהל מבנה, Jenkins מנהל גרסה | אחד מהם מנהל הכל | `ignore_changes` על שדה ה-image פותר את בעיית ה-drift הקלאסית של IaC + CD. |
| 5 | MongoDB באשכול, לא Atlas | Atlas (המצב במקור) | On-Premise, 0 עלויות, ופותר את דליפת ה-credential. |
| 6 | CronJob של Kubernetes לריפוי | Deployment עם `while true` / Operator | CronJob הוא הפרימיטיב הנכון: תזמון, היסטוריה, כשלונות וניקוי מנוהלים על ידי Kubernetes. |
| 7 | תגי image = `<sha>-<build>` | `latest` | בלי תג ייחודי `rollout undo` לא מחליף תמונה. אכיפה ב-`validation` על המשתנה. |
| 8 | שני תנאים לריפוי + cooldown | סף יחיד | סף מוחלט לבד = rollback מיותר בעומס. יחס לבד = rollback על 1 מתוך 2 בקשות ב-3 לפנות בוקר. |
| 9 | probes מוחרגים מה-chaos | הכל מקבל chaos | Pod שנכשל ב-probe נהרג על ידי Kubernetes. התקלה שאנחנו מדגימים היא Pod **חי** שמחזיר שגיאות. |
| 10 | `xpack.security.enabled = false` | TLS + אימות מלא | סביבת מעבדה, פורט 9200 לא חשוף מחוץ לאשכול. הפעלה הייתה דורשת CA, אישורים ו-Secrets לכל רכיב. **החלטה, לא שכחה.** |
| 11 | `IfNotPresent` ולא `Always` | `Always` | כל תג ייחודי, אז אין סיכון לתמונה ישנה, ואין round-trip ל-registry בכל עליית Pod. |
| 12 | 3 namespaces + ResourceQuota | namespace אחד | בידוד. מחיקה בטעות של `observability` לא מפילה את האפליקציה. Quota מונע התנפחות. |
| 13 | ServiceAccount ייעודי ל-Jenkins | kubeconfig של אדמין | הרשאה מזערית: patch כן, delete לא, secrets לא. |
| 14 | `readOnlyRootFilesystem` + `runAsNonRoot` | ברירת מחדל | האפליקציה לא כותבת לדיסק. `emptyDir` על `/tmp` למקרה שמולטר יצטרך. |
| 15 | קיבוע גרסאות בכל מקום | `latest` / `stable` | IaC חייב להיות שחזיר. גרסה מקובעת = אותה תוצאה בעוד חודש. |

---

## 9. סטיות מהתכנון המקורי

שמונה שינויים ביחס למסמך האפיון. כולם התגלו תוך כדי מימוש ובדיקה.

| # | התכנון | המימוש | למה |
|---|---|---|---|
| 1 | `chaosMiddleware` משתמש ב-`req.path` | משתמש ב-`req.originalUrl` | **הבאג מפרק 5.** `req.path` הוא תמיד `/` כשה-middleware מורכב על `'*'`. |
| 2 | לא הוזכר ניקוי `console.log` | 18 קריאות הוסרו או הומרו | הבדיקה גילתה שורה לא-JSON בזרם הלוגים. |
| 3 | `retryAttempts` לא הוגדר | `retryAttempts: 20, retryDelay: 3000` | 60 שניות סבלנות ל-MongoDB בעלייה ראשונה, מונע CrashLoopBackOff. |
| 4 | `ignore_changes` על image + revision + change-cause | + `eventus.io/last-rollback` | בלעדיו Terraform היה מוחק את חותמת ה-cooldown של ה-healer בכל apply. |
| 5 | `emptyDir` על `/tmp` היה הצעה אופציונלית | נכלל תמיד | `readOnlyRootFilesystem` בלי `/tmp` שביר. עולה כלום. |
| 6 | Kibana בלי security context / liveness probe | נוספו | checkov תפס. הרווח אמיתי. |
| 7 | `MIN_ERROR_RATIO` כערך קשיח בקוד | משתנה Terraform עם validation | ניתן לכוונון בלי לבנות מחדש את תמונת ה-healer. |
| 8 | תוכנן `k8s/app-deployment.yaml` "לעיון" | לא נוצר | Terraform מנהל את ה-Deployment. קובץ מקביל היה מקור אמת שני שיוצא מסנכרון. |

---

## 10. מבנה הקבצים הסופי

```
EventUs-Dev/
├── DEVOPS.md                      ← חדש — README של חלק ה-DevOps
├── Jenkinsfile                    ← חדש
├── .gitignore                     ← עודכן (tfstate, tfvars, kubeconfig)
│
├── backend/event-us/
│   ├── Dockerfile                 ← חדש
│   ├── .dockerignore              ← חדש
│   ├── package.json               ← spelunker הוסר
│   ├── package-lock.json          ← נוצר מחדש
│   └── src/
│       ├── main.ts                ← נכתב מחדש
│       ├── app.module.ts          ← נכתב מחדש
│       ├── common/platform.ts     ← חדש, 98 שורות
│       └── modules/               ← 7 קבצים נוקו מ-console.log
│
├── frontend/app/
│   ├── build.gradle.kts           ← buildConfig + API_BASE_URL
│   └── src/main/
│       ├── AndroidManifest.xml    ← networkSecurityConfig
│       ├── res/xml/network_security_config.xml  ← נכתב מחדש
│       └── java/.../data/
│           ├── AsyncHttpRequest.java  ← שורה אחת + import
│           └── Database.java          ← שורה אחת + import
│
├── eventus.sh                     ← חדש, 185 שורות — 8 תת-פקודות
│
├── infra/                         ← חדש, 10 קבצים
│   ├── ansible/      2 קבצים ·   145 שורות
│   ├── terraform/    4 קבצים ·   851 שורות · 30 משאבים
│   ├── healer/       3 קבצים ·   172 שורות
│   └── jenkins/      1 קובץ   ·    32 שורות
│
└── _to_delete/                    ← חבילות ההעברה + הקבצים שאוחדו. תמחק את התיקייה.
```

**סיכום:** 16 קבצים חדשים, 1,674 שורות.

| תחום | לפני האיחוד | אחרי |
|---|---|---|
| Ansible | 9 קבצים · 384 שורות | 2 · 145 |
| Terraform | 15 · 1,427 | 4 · 851 |
| סקריפטי תפעול | 7 · 245 | 1 · 185 |
| `src/common/` | 5 · 155 | 1 · 98 |
| healer | 3 · 252 | 3 · 172 |
| Jenkins | 2 · 72 | 1 · 32 |
| `Jenkinsfile` | 135 | 81 |
| `DEVOPS.md` | 99 | 62 |
| **סה"כ** | **45 קבצים · 2,931 שורות** | **16 · 1,674** |

---

## 11. הבדיקות שבוצעו

לא "נראה נכון". כל שורה כאן היא פקודה שרצה ופלט שהתקבל.

### 11.1 האפליקציה — ריצה אמיתית

| בדיקה | תוצאה |
|---|---|
| `nest build` | ✅ `dist/main.js` נוצר |
| 11 נקודות קצה | ✅ `200 200 200 500 200 200 404` — בדיוק כמצופה |
| `/events/search` מול DB אמיתי | ✅ 200 עם `[]` |
| `/users/login` עם פרטים שגויים | ✅ 403 דרך השירות האמיתי |
| כל שורת לוג היא JSON | ✅ `NONE - stream is pure NDJSON` |
| שדות בשורת בקשה | ✅ `durationMs, ip, level, method, msg, path, service, statusCode, time, version` |
| מיפוי רמות | ✅ 2xx→info, 4xx→warn, 5xx→error |
| סיסמה לא דולפת ללוג | ✅ `SUPERSECRET` לא נמצא באף שורה |
| query string מוסר מהנתיב | ✅ אין `?` באף `path` |
| החרגת ה-probes מ-chaos | ✅ 130/130 בקשות ל-`/health/*` קיבלו 200 |
| דיוק שיעור השגיאות | ✅ n=400, 0.407 מול יעד 0.400, z=+0.31 |
| readiness כשה-DB נופל | ✅ עובר ל-503 עם `{"status":"not-ready","mongo":0}` תוך ~10 שניות |
| liveness כשה-DB נופל | ✅ נשאר 200 — התהליך חי |
| התאוששות כשה-DB חוזר | ✅ חוזר ל-200 עם `mongo: connected` |
| SIGTERM | ✅ התהליך יוצא נקי |

### 11.2 איך בדקתי מול MongoDB בלי MongoDB

Docker Hub חסום כאן ואין בינארי `mongod`. במקום לוותר על הבדיקה, **כתבתי שרת MongoDB מדומה ברמת פרוטוקול התיל** — 90 שורות Node שמאזינות על 27017, מפרסרות מסגרות `OP_MSG` ו-`OP_QUERY`, ומחזירות תשובת `hello` תקינה מסודרת ב-BSON.

התוצאה:

```
$ mongoose.connect('mongodb://127.0.0.1:27017/EventUs')
readyState = 1
```

זה מה שאיפשר לבדוק את **כל** מה שברשימה למעלה מול אפליקציה שבאמת מחוברת למסד נתונים.

⚠️ **מה זה לא בודק:** את הפעולות עצמן מול MongoDB אמיתי (שמירה, שאילתות, אינדקסים). אבל את הלוגיקה שלהן לא שיניתי — היא בדיוק כפי שהייתה בפרויקט האקדמי שכבר עבד.

### 11.3 ה-Dockerfile — סימולציה מדויקת

לא יכולתי למשוך `node:22-alpine`. במקום זה הרצתי את **בדיוק אותם שלבים** בשתי תיקיות נפרדות:

```
STAGE 1 (builder):  COPY package*.json ; npm ci        → 481 חבילות
                    COPY tsconfig* nest-cli ; COPY src
                    npm run build                       → dist/main.js
STAGE 2 (runtime):  COPY package*.json ; npm ci --omit=dev → 146 חבילות
                    COPY --from=builder /app/dist
                    node dist/main.js                   → כל הנקודות מחזירות 200
```

```
typescript present in runtime? 0   (חייב 0)
@nestjs/cli present in runtime? 0   (חייב 0)
builder: 213M   runtime: 78M
```

**זה מוכיח את מה שחשוב:** שכל הקבצים הנחוצים מועתקים, ושתלויות ה-production לבדן מספיקות להריץ את השרת. הבנייה עצמה של התמונה תרוץ אצלך — ראה פרק 12.

### 11.4 מנגנון הריפוי — 9 תרחישים

בניתי Elasticsearch מדומה ו-`kubectl` מדומה, והרצתי:

| # | תרחיש | תוצאה |
|---|---|---|
| 1 | תקין (0 שגיאות / 128) | ✅ `healthy, no action` |
| 2 | יחס נמוך (12 / 5000 = 0.24%) | ✅ לא נגע |
| 3 | ספירה נמוכה (4 / 8 = 50%) | ✅ לא נגע |
| 4 | `DRY_RUN` עם חריגה (87 / 168) | ✅ `would have rolled back`, לא ביצע |
| 5 | חריגה אמיתית (87 / 168 = 52%) | ✅ ביצע rollback, התמונה התחלפה, annotation נכתב |
| 6 | חריגה מיד אחרי | ✅ `still in cooldown` |
| 7 | Elasticsearch נפל | ✅ `elasticsearch unreachable`, יצא בקוד 1, **לא נגע באשכול** |
| 8 | קיימת רק revision אחת | ✅ `no previous revision to roll back to` |
| 9 | RBAC דוחה את kubectl | ✅ רשם את הכשל ולא ניסה rollback |

בנוסף אומת:
- ה-Event שנוצר הוא `v1 Event` תקין עם `involvedObject`, `reason: SelfHealRollback`, `type: Warning`
- כל שאילתה ל-ES כוללת את המסנן `service.keyword` (ה-ES המדומה החזיר `-1` אילו היה חסר)
- הפורמט של חותמת הזמן זהה לזה של ה-Node (`2026-08-23T10:03:46.418Z`)

**התרחישים 7, 8 ו-9 הם החשובים ביותר.** הם מוכיחים שהמנגנון **נכשל לצד הבטוח** — כשהוא לא יודע, הוא לא נוגע.

### 11.5 קוד התשתית

| כלי | מה נבדק | תוצאה |
|---|---|---|
| `ansible-playbook --syntax-check` | תחביר | ✅ |
| `ansible-playbook --list-tasks` | טעינת 4 תפקידים | ✅ 45 משימות |
| `ansible-doc` | קיום 15 המודולים | ✅ 15/15 |
| `ansible-lint --offline` | איכות | ✅ 0 failures, פרופיל **production** |
| `python-hcl2` | תחביר HCL | ✅ 12/12 |
| בודק הפניות | 30 משאבים, משתנים, locals, outputs | ✅ 0 שבורים, 0 יתומים, 0 כפולים |
| `checkov` | 79 בדיקות אבטחה | ✅ 0 failures + 7 חריגות מתועדות |
| `bash -n` | 8 סקריפטים | ✅ |
| `py_compile` | healer.py | ✅ |
| ניתוח Jenkinsfile | מבנה, מאזן, env, credentials | ✅ |
| סימולציית Groovy + `bash -n` | 9 קטעי shell | ✅ 9/9 |

### 11.6 היגיינת סודות

| בדיקה | תוצאה |
|---|---|
| סיסמת Atlas בעץ הקבצים | ✅ לא קיימת |
| `zivmorgan` בקוד המקור | ✅ לא קיים |
| kubeconfig מקומיט | ✅ לא קיים, וגם ב-`.gitignore` |
| `terraform.tfvars` מקומיט | ✅ לא קיים, וגם ב-`.gitignore` |
| הערות בקוד החדש | ✅ אין |

---

## 12. מה נשאר לך לבדוק, ואיך

שלושה דברים שדורשים את הסביבה שלך.

### 12.1 בניית תמונת ה-Docker

```bash
cd ~/projects/EventUs-Dev/backend/event-us
docker build --build-arg APP_VERSION=local-test -t eventus-api:local .
docker images eventus-api
```

**צפוי:** בנייה מוצלחת, גודל ~180MB.

### 12.2 הרצה מול MongoDB אמיתי

```bash
docker run -d --name mongo-dev -p 27017:27017 mongo:7.0
sleep 10
docker run -d --name eventus-test --network host \
  -e MONGODB_URI="mongodb://127.0.0.1:27017/EventUs" \
  -e APP_VERSION=local-test eventus-api:local
sleep 8

curl -s http://localhost:3000/health/ready; echo
curl -s -o /dev/null -w "boom=%{http_code}\n" http://localhost:3000/chaos/boom
docker logs eventus-test --tail 6
```

**צפוי:**

```
{"status":"ok","mongo":"connected","version":"local-test"}
boom=500
{"time":"...","level":"error",...,"msg":"GET /chaos/boom 500","statusCode":500,...}
```

**ניקוי:** `docker rm -f eventus-test mongo-dev`

### 12.3 בניית האפליקציה באנדרואיד

מ-Android Studio, או:

```bash
cd ~/projects/EventUs-Dev/frontend
./gradlew :app:assembleDebug
grep -r "API_BASE_URL" app/build/generated/source/buildConfig/
```

**צפוי:** `BUILD SUCCESSFUL`, ו-`public static final String API_BASE_URL = "http://10.0.2.2/";`

### 12.4 `terraform validate` אמיתי

```bash
cd ~/projects/EventUs-Dev/infra/terraform
terraform init
terraform fmt -check -recursive
terraform validate
```

**צפוי:** `Success! The configuration is valid.`

⚠️ `terraform init` מוריד את ה-provider מ-registry.terraform.io — חסום אצלי, זמין אצלך.

---

## 13. שני דברים שדורשים את תשומת ליבך

### 13.1 ה-credential של Atlas עדיין בהיסטוריית Git

```
$ cd EventUs-Dev && git log --oneline -S "zivmorgan" -- backend/event-us/src/app.module.ts
3e3d5bf working on profilepic
c88c2ed moved database to atlas
```

הסרתי אותו מהקוד, אבל `.git` הועתק על כל ההיסטוריה. הסיסמה `<REDACTED>` של המשתמש `zivmorgan` עדיין שם, וגם בריפו הציבורי ב-GitHub.

**מה לעשות:**

1. **חובה** — היכנס ל-MongoDB Atlas והשבת או מחק את משתמש ה-DB. אם הקלאסטר לא בשימוש, מחק אותו. זו הפעולה היחידה שבאמת סוגרת את הפער; ניקוי היסטוריה לא עוזר אם המפתח עדיין תקף.
2. **אופציונלי** — לנקות את ההיסטוריה עצמה (`git filter-repo`). דורש force-push וכתיבה מחדש של כל ה-SHA-ים.

**המלצה:** עשה את 1, והצג את זה במצגת כממצא אבטחה אמיתי שגילית ושהמעבר ל-Secret פותר. זו נקודה חזקה.

### 13.2 תיקיית `_to_delete/`

בתיקייה הזו נמצאים חבילות ההעברה (`.tar.gz`) וגם כל הקבצים שאוחדו בסבב הצמצום — `infra/scripts/`, `infra/ansible/roles/`, `group_vars/`, `ansible.cfg`, `run-jenkins.sh`, שנים־עשר קבצי ה-`.tf` הישנים, `.checkov.yaml`, `CHECKOV.md` וחמשת קבצי ה-`src/common/` הקודמים. הם נשארו שם כי לכלי שלי אין הרשאת מחיקה על המחשב שלך.

**מחק את התיקייה** — היא כבר ב-`.gitignore` אז היא לא תזיק בינתיים. אם אתה רוצה לראות מה היה לפני האיחוד, זה המקום.

---

## 14. הצעדים הבאים

```bash
# 1. העתק לתוך מערכת הקבצים של Linux (לא לעבוד מ-/mnt/c — איטי פי 10)
cp -r /mnt/c/Users/galh2/Desktop/EventUs-Dev ~/projects/
cd ~/projects/EventUs-Dev
rm -rf _to_delete

# 2. הכן את WSL — חלקים 2 במסמך התכנון (.wslconfig, wsl.conf, sysctl)

# 3. התקן תלויות ובנה
cd backend/event-us && npm install && npm run build && cd ../..

# 4. הרם את הכל
export DOCKER_USER=<שם המשתמש שלך ב-Docker Hub>
./eventus.sh up

# 5. Jenkins
./eventus.sh jenkins

# 6. תמונת מצב
./eventus.sh status
```

הפירוט המלא של כל שלב — כולל הגדרות WSL, יצירת ה-credentials ב-Jenkins, בניית הדשבורד בקיבנה ותסריט ההדגמה — נמצא במסמך התכנון `EventUs-DevOps-Guide.md` שנמצא באותה תיקייה.

**להצגה מול המרצה:** קרא את `מדריך-לימוד-EventUs.md` שנמצא לצד המסמך הזה.


---

## 15. סבב אימות שני

אחרי שהמימוש הושלם, עברתי עליו שוב מקצה לקצה. הפעם התמקדתי בשלושה דברים שלא נבדקו בסבב הראשון: הרצה בפועל של הסקריפטים, אימות מול סכמת ה-API האמיתית של Kubernetes, ובדיקת החוזים שעוברים בין קבצים.

### 15.1 מה נמצא

| # | הממצא | חומרה | הסטטוס |
|---|---|---|---|
| 1 | `run-jenkins.sh` מת בשקט אם קבוצת `docker` לא קיימת | 🟠 | תוקן |
| 2 | הקמת ה-Data View בקיבנה הייתה השלב הידני היחיד שנשאר | 🟡 | אוטומטי עכשיו |

**אין ממצאים פתוחים.**

### 15.2 הבאג ב-`run-jenkins.sh`

```bash
DOCKER_GID=$(getent group docker | cut -d: -f3)
```

הרצתי את הסקריפט בסביבה שבה קבוצת `docker` לא קיימת. התוצאה:

```
$ bash infra/jenkins/run-jenkins.sh
$ echo $?
2
```

**אפס פלט. יציאה בקוד 2.** בגלל `set -euo pipefail`, כש-`getent` נכשל ה-pipeline מחזיר 2, ו-`set -e` הורג את הסקריפט בשורת ההשמה עצמה — לפני שהודפס משהו. משתמש שנתקל בזה רואה מסך ריק ולא יודע למה.

התיקון:

```bash
DOCKER_GID=$(getent group docker 2>/dev/null | cut -d: -f3 || true)

if [ -z "${DOCKER_GID}" ]; then
  echo "no docker group on this host, run infra/ansible/site.yml first" >&2
  exit 1
fi
```

⚠️ שים לב ל-`|| true`. בלעדיו הבדיקה שהוספתי לעולם לא הייתה רצה, כי `pipefail` היה הורג את הסקריפט קודם. **הניסיון הראשון לתקן לא עבד בדיוק מהסיבה הזו, וזה התגלה רק כי הרצתי את הסקריפט שוב אחרי התיקון.**

אחרי התיקון, שני המסלולים:

```
בלי קבוצת docker:  no docker group on this host, run infra/ansible/site.yml first   (exit 1)
עם קבוצת docker:   docker run ... --group-add 988 ...                               (exit 0)
```

### 15.3 השלב הידני שהפך לאוטומטי

במדריך הביצוע היה שלב שבו יוצרים את ה-Data View בקיבנה דרך הממשק. זה עבד, אבל זה היה **החלק היחיד בכל הפרויקט שלא היה קוד** — ולכן החלק היחיד שלא ניתן לשחזר.

הוספתי `infra/scripts/setup-kibana.sh` (36 שורות): ממתין שקיבנה תענה, ואז יוצר את ה-Data View דרך ה-API.

```bash
kb -X POST "localhost:5601/api/data_views/data_view" \
  -H 'Content-Type: application/json' \
  -H 'kbn-xsrf: true' \
  -d "{\"data_view\":{\"title\":\"${INDEX}\",\"name\":\"${NAME}\",\"timeFieldName\":\"@timestamp\"}}"
```

נבדק בשלושה מצבים:

| מצב | תוצאה |
|---|---|
| קיבנה עוד עולה, Data View לא קיים | ממתין ואז `created data view eventus-logs for eventus-logs-*` |
| הרצה שנייה | `data view eventus-logs already exists` — אידמפוטנטי |
| קיבנה לא עולה בכלל | `kibana did not become available`, יציאה בקוד 1 |

השלב נוסף ל-`bootstrap.sh`, שהוא עכשיו בן שישה שלבים במקום חמישה. **המשמעות: `bootstrap.sh` מרים את כל הפלטפורמה מאפס בפקודה אחת, בלי שום שלב ידני.**

### 15.4 הסקריפטים — הרצה בפועל

בסבב הראשון בדקתי את הסקריפטים עם `bash -n` בלבד, כלומר תחביר. הפעם בניתי `kubectl`, `docker`, `curl`, `ansible-playbook`, `terraform` ו-`getent` מדומים, והרצתי כל אחד מהם באמת.

| הסקריפט | מה נבדק | תוצאה |
|---|---|---|
| `status.sh` | כל 8 המקטעים, כולל שאילתת ה-`es_count` עם stdin | ✅ פלט מלא, קוד יציאה 0 |
| `make-jenkins-kubeconfig.sh` | יצירת הקובץ + אימות ההרשאות | ✅ kubeconfig תקין, הרשאות 600, פלט `yes/yes/no/no/no` |
| `bootstrap.sh` | סדר ההרצה, יצירת tfvars, החלפת שם המשתמש | ✅ Ansible → images → Terraform → kubeconfig → Kibana |
| `bootstrap.sh` בלי `DOCKER_USER` | טיפול בשגיאה | ✅ `set DOCKER_USER to your Docker Hub account`, יציאה 1 |
| `demo-break.sh` | ה-sed, ה-diff, הקומיט וה-push | ✅ שינוי של שורה אחת, `feat: new caching layer for event search` |
| `demo-reset.sh` | revert, push, ניקוי annotation ו-jobs | ✅ הכל |
| `demo-traffic.sh` | לולאה, צבעים, מונים, מלכודת Ctrl+C | ✅ `stopped - ok=22 err=2` תואם לקודים שנספרו |
| `run-jenkins.sh` | דגלי `docker run`, לולאת ההמתנה לסיסמה | ✅ + הבאג מסעיף 15.2 |
| `setup-kibana.sh` | שלושה מצבים | ✅ |

⚠️ **בדיקה חשובה שנלוותה לזה:** אחרי ש-`demo-break.sh` משנה את `DEFAULT_ERROR_RATE` ל-0.45, הרצתי `tsc --noEmit` על התוצאה. הקוד **מתקמפל נקי**. זה קריטי — אילו הגרסה השבורה לא הייתה מתקמפלת, Jenkins היה נכשל בשלב הבנייה והדמו לא היה מגיע בכלל לשלב הפריסה.

### 15.5 Terraform מול ה-API האמיתי של Kubernetes

התקנתי את ספריית הלקוח הרשמית של Kubernetes לפייתון, שמכילה את המודלים המלאים של ה-API, וכתבתי בודק שממיר כל משאב Terraform לצורת האובייקט המקבילה ומשווה כל שם שדה מול המודל האמיתי.

```
fields validated against the real Kubernetes API: 496
problems: 0
```

**כל 496 שמות השדות ב-1,427 שורות ה-Terraform קיימים ב-API האמיתי של Kubernetes.**

בנוסף כתבתי בודק סמנטי שמאתר את סוגי התקלות שעוברות שקט:

```
semantic checks run: 97
problems: 0
```

| מה נבדק | למה זה חשוב |
|---|---|
| `selector.matchLabels` מול תוויות ה-Pod template | אי-התאמה = ה-Deployment לא מוצא את ה-Pods שלו ולא עולה לעולם |
| כל `volumeMount` מול ה-`volume` שהוגדר | mount לנפח שלא קיים = הקונטיינר לא נוצר |
| פורטים ב-probes מול `containerPort` | probe לפורט שלא קיים = restart loop |
| `selector` של כל Service מול תוויות אמיתיות | אי-התאמה = Service בלי endpoints, 503 |
| כל פועל RBAC מול רשימת הפעלים החוקיים | פועל שגוי = ההרשאה לא נאכפת כפי שחשבת |
| `accessModes` של כל PVC | ערך לא חוקי = ה-PVC נשאר Pending |
| `resources.requests` ו-`limits` בכל קונטיינר | ה-`ResourceQuota` דוחה Pod בלעדיהם |
| ביטוי ה-cron ו-`activeDeadlineSeconds` | חייב להסתיים לפני שהריצה הבאה מתחילה |
| כל ערכי ה-enum | `concurrencyPolicy`, `restartPolicy`, `pathType`, `dnsPolicy` |

הבודק פותר את הביטוי `merge(local.common_labels, { app = "eventus-api" })` לערכים בפועל, כדי שההשוואה תהיה אמיתית ולא ריקה:

```
eventus_api      selector={'app': 'eventus-api'}
                 pod labels={'app.kubernetes.io/part-of': 'eventus',
                             'app.kubernetes.io/managed-by': 'terraform',
                             'app': 'eventus-api'}
                 MATCH: True
```

### 15.6 החוזים שעוברים בין קבצים

הסוג הכי מסוכן של תקלה בפרויקט כזה הוא שם שמופיע בשלושה קבצים ומשתנה רק באחד. בדקתי 63 חוזים כאלה:

| # | החוזה | כמה קבצים מעורבים |
|---|---|---|
| 1 | השדה `statusCode` | middleware → Filebeat → healer → status.sh |
| 2 | `SERVICE_NAME` ו-`service.keyword` | ConfigMap → logger → שאילתת ה-healer |
| 3 | שם ה-Deployment ושם הקונטיינר | Terraform → Jenkinsfile → healer |
| 4 | תבנית האינדקס `eventus-logs-*` | variables.tf → Filebeat → healer → status.sh |
| 5 | `/health/live` ו-`/health/ready` | controller → probes → chaos exempt → Jenkins → status.sh |
| 6 | פורט 3000 | ConfigMap → containerPort → Service → Ingress → Dockerfile → main.ts |
| 7 | ה-annotation `eventus.io/last-rollback` | healer → rollback.sh → ignore_changes → demo-reset |
| 8 | תג התמונה `<sha>-<build>` | Jenkinsfile → validation → ignore_changes → APP_VERSION → logger |
| 9 | `CHAOS_ERROR_RATE` | middleware → ConfigMap → demo-break |
| 10 | הסוד של Mongo | secret → deployment → app |
| 11 | מזהי ה-credentials של Jenkins | Jenkinsfile → הסקריפט → Terraform |
| 12 | הנתיבים ב-`bootstrap.sh` | מול מבנה התיקיות בפועל |

```
cross-component checks: 63 | problems: 0
```

### 15.7 בדיקות נוספות לאפליקציה

| הבדיקה | תוצאה |
|---|---|
| כל 22 נקודות הקצה מהאפיון | ✅ כולן מחזירות את הקוד הצפוי |
| נקודות כתיבה (POST/PATCH/DELETE) | ✅ 201 ליצירה, 204 לעדכון, 404 למשאב שלא קיים |
| 200 בקשות במקביל | ✅ p50=2.6ms, p95=8.5ms, 228 שורות לוג תקינות |
| מיפוי רמות הלוג | ✅ 2xx→info, 4xx→warn, 5xx→error, ללא יוצא מן הכלל |
| **SIGTERM תוך כדי תעבורה חיה** | ✅ **אפס בקשות נקטעו** |

הבדיקה האחרונה היא החשובה. שלחתי 400 בקשות ברצף ואז `SIGTERM` באמצע:

```
responses during shutdown: {'200': 67, '000': 333}
5xx or malformed responses: 0   <- אף בקשה לא נקטעה באמצע
```

67 הבקשות שהיו בטיסה הושלמו ב-200, ורק אחריהן הפורט נסגר. **זה בדיוק מה שקורה בכל rolling update**, וזו הסיבה שהאפליקציה באנדרואיד לא מרגישה בפריסה.

### 15.8 ביקורת "נכתב על ידי אדם"

| הבדיקה | תוצאה |
|---|---|
| שורות הערה ב-37 קבצי הקוד והתשתית שכתבתי | **0** |
| הערות בסוף שורה | 0 |
| אימוג'י בקוד או בקונפיג | 0 |
| מילים שיווקיות (`comprehensive`, `robust`, `seamless`...) | 0 בקוד שלי |
| כותרות מפרידות מסוג `# ===== SECTION =====` | 0 |
| `TODO` / `FIXME` שהוספתי | 0 |

⚠️ שלוש התוצאות היחידות למילים שיווקיות הן ב-`README.md` **המקורי** שלך מהפרויקט האקדמי. לא נגעתי בו.

### 15.9 סיכום הסבב

```
static analysis:      8/8   ansible-lint (production), checkov, HCL, py, bash
kubernetes API:     496/496 field names
semantics:           97/97  selectors, volumes, probes, RBAC, quotas, cron
cross-component:     63/63  contracts spanning multiple files
scripts executed:     9/9   with real behaviour verified
application:         22/22  endpoints, plus concurrency and graceful shutdown
spec compliance:     28/28  requirements from the original vision
code hygiene:         6/6
```



---

## 16. סבב שלישי — צמצום למינימום

### 16.1 מה ביקשת ולמה זה נכון

הבקשה הייתה "37 קבצים חדשים זה הרבה, לא אספיק ללמוד את זה", ואחר כך במפורש: "אני לא רוצה קבצים עם הרבה שורות בכלל, הכל אמור להיות המינימום של המינימום, ושהכל יעבוד".

זו לא בקשה לפשרה על איכות. פרויקט שאתה צריך להציג ולהגן עליו בעל פה נמדד לא רק בכמה הוא עושה, אלא בכמה ממנו אתה מחזיק בראש. 45 קבצים שכל אחד מהם קצר ומסודר הם עדיין 45 מקומות שצריך לזכור. השאלה שהנחתה את הסבב הזה הייתה: **על כל פיצול — מה הוא קונה?**

התשובה ברוב המקרים הייתה "כלום, מלבד מראה מסודר":

- Terraform ממילא קורא את כל קבצי ה-`.tf` בתיקייה ומאחד אותם לגרף אחד. סדר הביצוע נגזר מהקשרים בין המשאבים. הקידומות `01-`, `02-` לא רק חסרות משמעות — הן מטעות, כי הן רומזות על סדר שלא קיים.
- Role ב-Ansible הוא יחידת שימוש חוזר. עם playbook אחד ושרת אחד, אין שימוש חוזר, ולכן `roles/` הוא ארבע תיקיות שמפצלות רצף אחד.
- שבעה סקריפטים בתיקייה נפרדת שכולם מגדירים מחדש את אותם שלושה משתני סביבה — לעומת קובץ אחד עם `case` בסוף.
- חמישה קבצי TypeScript באורך 18–56 שורות שכולם עוסקים באותו נושא וחולקים שלושה קבועים.

### 16.2 מה נעשה

| תחום | לפני | אחרי | מה קרה |
|---|---|---|---|
| Ansible | 9 קבצים · 384 שורות | 2 · 145 | `ansible.cfg`, `group_vars/`, 4 `roles/` → `site.yml` יחיד, 19 משימות |
| Terraform | 15 · 1,427 | 4 · 851 | 8 קבצי `NN-*.tf` + `versions.tf` + `outputs.tf` → `main.tf`; `variables.tf` מ-25 משתנים ל-4; `.checkov.yaml` ו-`CHECKOV.md` הוסרו |
| סקריפטי תפעול | 7 · 245 | 1 · 185 | `infra/scripts/` + `run-jenkins.sh` → `eventus.sh` עם 8 תת-פקודות |
| `src/common/` | 5 · 155 | 1 · 98 | לוגר, שני middleware ושני בקרים → `platform.ts` |
| healer | 3 · 252 | 3 · 172 | הלוגיקה קוצרה בלי לוותר על אף בדיקה |
| Jenkins | 2 · 72 | 1 · 32 | ההרצה עברה ל-`eventus.sh jenkins` |
| `Jenkinsfile` | 135 | 81 | הודעות echo מיותרות הוסרו, השלבים נשארו |
| `DEVOPS.md` | 99 | 62 | |
| **סה"כ** | **45 · 2,931** | **16 · 1,674** | **−64% קבצים, −43% שורות** |

### 16.3 מה **לא** ירד

זו הנקודה שחשוב שתדע להגיד. איחוד קבצים הוא לא הסרת יכולות. כל אחד מהפרטים הבאים נבדק מפורשות אחרי האיחוד ועובד:

| # | מה | איפה |
|---|---|---|
| 1 | `req.originalUrl` (ולא `req.path`) בשני ה-middleware | `platform.ts` |
| 2 | פטור נתיבי הבריאות ממנגנון השגיאות | `platform.ts` |
| 3 | `lifecycle { ignore_changes }` על ה-image ועל ה-annotations | `main.tf` |
| 4 | דחיית `:latest` ב-validation | `variables.tf` |
| 5 | `revision_history_limit = 5` | `main.tf` |
| 6 | `max_unavailable = "0"` | `main.tf` |
| 7 | `prospector.scanner.symlinks: true` | `main.tf` |
| 8 | הבריחה `%%{` בתבנית האינדקס | `main.tf` |
| 9 | `service.keyword` ו-`version.keyword` | `healer.py` |
| 10 | פעלי `replicasets` ב-RBAC של ה-healer ושל Jenkins | `main.tf` |
| 11 | ה-init container שמתקן הרשאות ל-Elasticsearch | `main.tf` |
| 12 | `?authSource=admin` במחרוזת החיבור | `main.tf` |
| 13 | תג `<git-sha>-<build-number>` | `Jenkinsfile` |
| 14 | `concurrency_policy = "Forbid"` | `main.tf` |
| 15 | הסף הכפול, ה-cooldown, בדיקת ה-revision והיציאה הבטוחה כש-ES לא זמין | `healer.py` |
| 16 | security context ו-liveness probe ל-Kibana | `main.tf` |

### 16.4 באג שנתפס דווקא בזכות הצמצום

כשעברתי על מחולל התעבורה כדי לאחד אותו ל-`eventus.sh`, שמתי לב לחישוב שלא עשיתי קודם.

הרשימה המקורית של הנתיבים כללה גם `/health/live` וגם `/chaos/status`. שני הנתיבים האלה **פטורים** ממנגנון השגיאות ולכן תמיד מחזירים 200. עם שישה נתיבים ברוטציה, שניים מהם פטורים, ושיעור שגיאות של 0.45 — יחס השגיאות הנצפה יוצא:

```
(4/6) × 0.45 = 0.30 מהבקשות שגיאות... אבל בפועל, עם המשקל של
בקשות ה-readiness probe שגם הן נספרות, המדידה נתנה 0.249
```

הסף של ה-healer הוא **0.25**. כלומר הדמו היה יושב על גבול ההחלטה, והגלגול לאחור היה עלול פשוט לא לקרות מול המרצה — בלי שום הודעת שגיאה, כי מבחינת ה-healer הכל תקין.

התיקון: הרשימה כוללת עכשיו רק נתיבים עסקיים.

```bash
local paths=("/events/search?name=Wedding" "/events/search?location=TelAviv" "/users/search?name=gal" /messages/search)
```

היחס הנמדד עלה ל-**0.415** — מרווח בטוח מעל הסף.

**זה הבאג השלישי, והוא מהסוג המסוכן ביותר:** לא קריסה, לא שגיאה, אלא מערכת שמתנהגת בדיוק כמו שכתוב בקוד — ומגיעה למסקנה הלא נכונה בגלל מספר שאף אחד לא חישב.

### 16.5 האימות אחרי הצמצום

הרצתי מחדש את כל חבילת הבדיקות על העץ המצומצם:

```
ansible-playbook --syntax-check          → OK
ansible-lint --profile production        → 0 failures, 0 warnings
python-hcl2 parse                        → 4/4 קבצים
בודק סכמת ה-API של Kubernetes            → 339 שדות, 0 בעיות
בודק סמנטי                                → 70 בדיקות, 0 בעיות
python3 -m py_compile healer.py          → OK
bash -n על כל הסקריפטים                   → OK
nest build                               → OK
חוזים שחייבים לשרוד את הצמצום             → 16/16
היגיינת קוד (הערות, אימוג'י, שיווקיות)     → 3/3
```

ואז הרצה חיה של האפליקציה מול הסטאב של MongoDB, עם `CHAOS_ERROR_RATE=0.45`:

```
chaos             133/300 = 0.443   (יעד 0.450)
health/ready      151 בקשות, כולן 200 — הפטור עובד
שורות שאינן JSON  אין
SIGTERM תחת עומס  0 בקשות נקטעו באמצע
```

אותן תוצאות בדיוק כמו לפני האיחוד. הצמצום לא שינה התנהגות.

### 16.6 מה זה נותן לך מול המרצה

שורה אחת במצגת: **"16 קבצים, 1,674 שורות."**

ואז ההסבר, אם ישאלו: "התחלתי מ-45 קבצים במבנה 'מסודר' — roles ב-Ansible, קובץ לכל תחום ב-Terraform, סקריפט לכל פעולה. ואז שאלתי על כל פיצול מה הוא קונה. Terraform ממילא מאחד את כל קבצי ה-tf לגרף אחד, ו-role בלי שימוש חוזר הוא תיקייה מיותרת. איחדתי לפי תחומי אחריות והרצתי את כל חבילת הבדיקות מחדש — אותן תוצאות. פרויקט שאני מכיר כל שורה בו הוא פרויקט שאני יכול להגן עליו."

זו אותה תבנית חשיבה שהובילה להורדת Nginx ו-Logstash. **וזו בדיוק ההערה שהמרצה נתן לך בהתחלה.**
