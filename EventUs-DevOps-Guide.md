# EventUs — On-Premise Self-Healing Cloud
## מדריך ביצוע מלא לפרויקט גמר ב-DevOps

**גרסה:** 1.0 · **תאריך:** אוגוסט 2026 · **מחבר האפיון:** גל הלל
**מבוסס על:** סריקה מלאה של הריפו `github.com/GalHillel/EventUs` (ענף `devops-project`)

---

## איך לקרוא את המסמך הזה

המסמך בנוי כרצף ביצוע. כל חלק מסתיים ב**נקודת אימות** — פקודה שמריצים ורואים פלט צפוי. אם הפלט לא תואם, אל תמשיכו לחלק הבא; לכו לחלק 12 (פתרון תקלות).

סימונים:

| סימון | משמעות |
|---|---|
| `בלוק קוד` | פקודה או קובץ להעתקה מדויקת |
| **החלף:** | ערך שצריך להתאים אישית (שם משתמש, סיסמה, נתיב) |
| ⚠️ | נקודת כשל נפוצה — קראו לפני שמריצים |
| ✅ | נקודת אימות |

**זמן ביצוע משוער:** 14–20 שעות עבודה נטו, מומלץ לפרוס על 5–7 ימים.

---

## 0. תקציר: מה בונים ולמה

### 0.1 המשפט האחד

לוקחים אפליקציית EventUs הקיימת (Android + NestJS + MongoDB), אורזים את השרת ל-Docker, מקימים אשכול Kubernetes מקומי (K3s) על WSL2 באמצעות Ansible, מגדירים את כל מה שרץ בתוכו ב-Terraform, בונים ופורסים אוטומטית עם Jenkins, אוספים לוגים ל-Elasticsearch, ומריצים מנגנון Python שמזהה גל שגיאות 500 ומבצע `kubectl rollout undo` לבד.

### 0.2 דיאגרמת הארכיטקטורה

```mermaid
graph TD
    subgraph WIN["Windows 11 — Host"]
        AVD["Android Emulator<br/>EventUs APK"]
        BR["Chrome<br/>Kibana / Jenkins"]
    end

    subgraph WSL["WSL2 · Ubuntu 24.04 — 'שרת הענן'"]
        DOCKER["Docker Engine<br/>(מותקן ע''י Ansible)"]
        JEN["Jenkins Container<br/>:8080"]

        subgraph K3S["K3s Cluster"]
            TRA["Traefik Ingress<br/>:80 hostPort"]

            subgraph NSAPP["namespace: eventus"]
                API1["eventus-api pod 1"]
                API2["eventus-api pod 2"]
                MONGO[("MongoDB<br/>StatefulSet + PVC")]
            end

            subgraph NSOBS["namespace: observability"]
                FB["Filebeat DaemonSet"]
                ES[("Elasticsearch")]
                KIB["Kibana :5601"]
            end

            subgraph NSHEAL["namespace: platform"]
                CJ["CronJob: eventus-healer<br/>כל דקה"]
            end
        end
    end

    HUB["Docker Hub<br/>galhillel/eventus-api"]
    GH["GitHub<br/>EventUs @ devops-project"]

    AVD -->|"HTTP 10.0.2.2:80"| TRA
    BR -->|"kibana.local"| TRA
    BR -->|"localhost:8080"| JEN
    TRA --> API1
    TRA --> API2
    API1 --> MONGO
    API2 --> MONGO
    API1 -.->|"stdout JSON"| FB
    API2 -.->|"stdout JSON"| FB
    FB --> ES
    ES --> KIB
    CJ -->|"query 500s"| ES
    CJ -->|"rollout undo"| API1
    GH -->|"SCM poll"| JEN
    JEN -->|"docker build/push"| HUB
    JEN -->|"kubectl set image"| API1
    HUB -.->|"pull"| API1
```

### 0.3 טבלת ההחלטות הארכיטקטוניות

זו הטבלה שתופיע במצגת. כל שורה היא החלטה שהתקבלה במודע, עם הנימוק.

| # | החלטה | האלטרנטיבה שנדחתה | הנימוק |
|---|---|---|---|
| 1 | **Traefik** כ-Ingress Controller, בלי Nginx | ingress-nginx או Nginx reverse-proxy חיצוני | K3s מגיע עם Traefik מותקן ומוגדר. הוספת Nginx מוסיפה רכיב, קונטיינר, קונפיג ונקודת כשל — בלי לספק שום יכולת שאין ב-Traefik עבור התרחיש הזה (ניתוב HTTP לפי path/host). **זו בדיוק ההערה של המרצה, והיא מיושמת.** |
| 2 | **Filebeat בלבד**, בלי Logstash | ELK מלא עם Logstash | אנחנו משנים את NestJS כך שיפלוט לוגים ב-JSON מובנה ל-stdout. Logstash קיים כדי לפרסר טקסט חופשי לשדות — כשהמקור כבר JSON, הוא שכבה מיותרת שאוכלת ~1GB RAM. Filebeat קורא, מפענח JSON ושולח ישירות ל-Elasticsearch. |
| 3 | **Jenkins בקונטיינר Docker מחוץ ל-K3s** | Jenkins כ-Pod בתוך K3s | Jenkins צריך לבנות Docker Images. הרצת Docker בתוך Kubernetes דורשת DinD/Kaniko — סיבוך רציני. מחוץ לאשכול הוא מקבל את `docker.sock` ישירות ואת ה-kubeconfig כדי לפרוס פנימה. פשוט, יציב, ומשקף ארכיטקטורות אמיתיות שבהן ה-CI חי מחוץ לאשכול היעד. |
| 4 | **Terraform מנהל את הפלטפורמה, Jenkins מנהל את הגרסה** | Terraform מנהל הכל / Jenkins מנהל הכל | Terraform יוצר namespaces, Mongo, ELK, Ingress, RBAC ואת ה-Deployment עם תג ראשוני. אחר כך `lifecycle.ignore_changes` על שדה ה-image מונע ממנו להילחם ב-Jenkins. זה פותר את בעיית ה-drift הקלאסית של IaC + CD. |
| 5 | **MongoDB בתוך האשכול**, לא Atlas | MongoDB Atlas (המצב הנוכחי בקוד) | האפיון דורש On-Premise ו-0 עלויות. בנוסף — הקוד הנוכחי מכיל מחרוזת חיבור ל-Atlas עם סיסמה בתוך ה-Git. מעבר ל-Mongo מקומי + Secret פותר גם את הפער האבטחתי (ראו ממצא F-01). |
| 6 | **CronJob של Kubernetes** למנגנון הריפוי | Deployment עם `while true` / Operator | CronJob הוא הפרימיטיב הנכון למשימה תקופתית. Kubernetes מנהל את התזמון, ההיסטוריה והכשלונות. Operator היה over-engineering לתרחיש. |
| 7 | **WSL2 + mirrored networking** | VM נפרד ב-Hyper-V/VirtualBox | ה-Emulator של אנדרואיד פונה ל-`10.0.2.2` שהוא ה-loopback של Windows. עם mirrored networking, שירות שמאזין ב-WSL על :80 נגיש מיד ב-`localhost:80` של Windows ולכן ב-`10.0.2.2:80` מה-Emulator — בלי port forwarding ידני ובלי IP משתנה. |
| 8 | **תגי Image = SHA של הקומיט**, לא `latest` | `latest` | בלי תג ייחודי לכל בנייה, `kubectl rollout undo` לא באמת מחליף תמונה — ה-Pod מושך את אותו `latest`. תג ייחודי הוא תנאי הכרחי שהדמו יעבוד. |
| 9 | **קובץ אחד לכל תחום אחריות** — 16 קבצים חדשים סה"כ | פיצול "מסודר" ל-45 קבצים קטנים | Terraform מאחד ממילא את כל קבצי ה-`.tf` לגרף אחד, ו-Ansible role בלי שימוש חוזר הוא תיקייה מיותרת. הפיצול לא הוסיף יכולת — הוא רק פיזר 1,674 שורות על פני שלוש פעמים יותר קבצים. פרויקט שאפשר להחזיק בראש הוא פרויקט שאפשר להגן עליו. |

### 0.4 מפת פורטים

| פורט | היכן | מה | איך ניגשים |
|---|---|---|---|
| 80 | WSL host (Traefik svclb) | Ingress של האשכול | `http://localhost/` מ-Windows, `http://10.0.2.2/` מה-Emulator |
| 8080 | WSL host (Docker) | Jenkins UI | `http://localhost:8080` |
| 50000 | WSL host (Docker) | Jenkins agent port | פנימי |
| 6443 | WSL host | K3s API Server | `kubectl` |
| 3000 | בתוך ה-Pod | NestJS | דרך Service `eventus-api:3000` |
| 27017 | בתוך האשכול | MongoDB | Service `mongodb.eventus.svc:27017` |
| 9200 | בתוך האשכול | Elasticsearch | Service `elasticsearch.observability.svc:9200` |
| 5601 | בתוך האשכול | Kibana | `http://kibana.local/` דרך Traefik |

### 0.5 תקציב זיכרון (16GB RAM)

WSL2 מקבל ברירת מחדל של 50% מה-RAM. נקצה לו 10GB במפורש.

| רכיב | Request | Limit |
|---|---|---|
| K3s control plane + system pods | ~700MB | — |
| Elasticsearch (heap 1GB) | 1.5Gi | 2Gi |
| Kibana | 512Mi | 1Gi |
| Filebeat | 100Mi | 200Mi |
| MongoDB | 256Mi | 512Mi |
| eventus-api × 2 | 128Mi × 2 | 256Mi × 2 |
| healer CronJob (רץ שנייה) | 64Mi | 128Mi |
| Jenkins (Docker, מחוץ לאשכול) | — | ~1Gi |
| **סה"כ בשיא** | | **~7GB** |

נשארים ~3GB מרווח ב-WSL ועוד 6GB ל-Windows + Android Emulator. זה עובד, אבל **אל תריצו Android Studio build ובנייה של Jenkins במקביל** בזמן הדמו.


---

## 1. ניתוח הקוד הקיים — ממצאי הסריקה

לפני שכותבים שורת Ansible אחת, צריך להבין מה בדיוק יש ביד. סרקתי את כל הריפו. זה מה שנמצא.

### 1.1 מבנה הריפו

```
EventUs/
├── .gitattributes
├── .gitignore
├── README.md
├── run_server.bat                    # cd backend\event-us\src && npm run start:dev  ⚠️ נתיב שגוי
├── appScreenShots/                   # תמונות + ilustration_video.MP4
├── diagrams/                         # ERD, class diagram, sequence, usecase (.png + .drawio)
├── presentation/EventUs.pptx
├── backend/
│   └── event-us/                     # ← זה שורש פרויקט ה-NestJS
│       ├── package.json
│       ├── nest-cli.json
│       ├── tsconfig.json / tsconfig.build.json
│       ├── .eslintrc.js / .prettierrc
│       ├── src/
│       │   ├── main.ts
│       │   ├── app.module.ts
│       │   ├── defaultpfp.png
│       │   ├── test.py               # סקריפט seed ל-DB (מכוון ל-localhost:3000)
│       │   ├── testimg.png
│       │   └── modules/
│       │       ├── dto/              # event.dto.ts, message.dto.ts, profilePic.dto.ts, user.dto.ts
│       │       ├── event/            # controller, model, module, service
│       │       ├── message/          # controller, model, module, service
│       │       ├── profilePic/       # controller, model, module, service
│       │       └── user/             # controller, model, module, service
│       └── test/app.e2e-spec.ts
└── frontend/                         # פרויקט Android (Gradle KTS)
    ├── settings.gradle.kts           # rootProject.name = "EventUs", include(":app")
    ├── build.gradle.kts              # AGP 8.3.0
    ├── gradle/wrapper/               # Gradle 8.4
    └── app/
        ├── build.gradle.kts          # compileSdk 34, minSdk 24, namespace com.example.eventus
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/xml/network_security_config.xml
            └── java/com/example/eventus/
                ├── data/             # AsyncHttpRequest, Database, FileUploader, BaseActivity, ...
                └── ui/               # login, registration, mainScreen, screens/*, recycleViews/*
```

**עובדות מאומתות:**

- `backend/event-us` הוא שורש פרויקט NestJS תקני. **הפקודות מריצים משם, לא מ-`src`.** ה-`run_server.bat` הקיים עושה `cd backend\event-us\src` וזו טעות — הוא עובד רק במקרה כי npm מטפס למעלה לחיפוש `package.json`.
- `@nestjs/core` מותקן בגרסה **10.3.0**. Mongoose 8.x. TypeScript 5.1.
- `dist/` ו-`node_modules/` **אינם** מנוהלי גרסה (מוגדרים ב-`.gitignore`) — טוב, אין מה לנקות.
- הריפו כרגע על ענף **`devops-project`** (יש גם `main`). הענף הזה מכיל שיפורי UI. **תעבדו עליו.**
- Remote: `git@github.com:GalHillel/EventUs.git` (SSH).

### 1.2 מפת ה-API המלאה

זו המפה שצריך כשמגדירים Ingress, בודקים בריאות וכותבים smoke test.

**`/users` — `UserController`**

| Method | Path | Status | תיאור |
|---|---|---|---|
| POST | `/users/register` | 201 | `CreateUserDto`: name, email, password, user_type |
| GET | `/users/login` | 200 | ⚠️ Query string: `?email=..&password=..&user_type=..` |
| GET | `/users` | 200 | חיפוש לפי `SearchUserDto` |
| GET | `/users/search` | 200 | חיפוש עם שדות תצוגה בלבד |
| GET | `/users/:id/display` | 200 | `_id name user_type profile_pic` |
| GET | `/users/:id/profile` | 200 | + rating, num_ratings, events, bio |
| GET | `/users/:id/events` | 200 | רשימת אירועים של המשתמש |
| GET | `/users/:id/messages` | 200 | תיבת הודעות |
| GET | `/users/:id/messageField` | 200 | מפת `{msgId: read}` |
| GET | `/users/:id/profilepic` | 200 | Buffer של תמונת פרופיל |
| PATCH | `/users/:id/edit` | 204 | `EditUserDto` (+ `oldPassword` לשינוי סיסמה) |
| PATCH | `/users/:id/rate` | 204 | דירוג אירוע ויציאה ממנו |
| PATCH | `/users/:id/exitEvent` | 204 | `{_id: eventId}` |
| PATCH | `/users/:id/removeMessage` | 204 | `{_id: msgId}` |

**`/events` — `EventController`**

| Method | Path | Status | תיאור |
|---|---|---|---|
| POST | `/events/create` | 201 | `CreateEventDto`: name, creator_id, date, location, description, isPrivate |
| GET | `/events` | 200 | חיפוש מלא |
| GET | `/events/search` | 200 | חיפוש עתידי בלבד (`date >= now`), שדות תצוגה |
| GET | `/events/:id/info` | 200 | פרטי אירוע מלאים |
| GET | `/events/:id/creator` | 200 | המשתמש שיצר |
| GET | `/events/:id/users` | 200 | רשימת משתתפים |
| GET | `/events/:id/profilepics` | 200 | תמונות פרופיל של משתתפים |
| PATCH | `/events/:id/edit` | 204 | `EditEventDto` |
| PATCH | `/events/:id/joinEvent` | 204 | `{_id: userId}` |
| PATCH | `/events/:id/acceptuser` | 204 | `{_id: userId}` |
| DELETE | `/events/:id` | 204 | מחיקה + ניקוי מכל המשתמשים |

**`/messages` — `MessageController`**

| Method | Path | Status |
|---|---|---|
| POST | `/messages` | 201 |
| GET | `/messages` | 200 |
| GET | `/messages/search` | 200 |
| GET | `/messages/:id/info` | 200 (סימון כנקרא אם יש `?_id=userId`) |

**`/profilepics` — `ProfilePicController`**

| Method | Path | Status | הערה |
|---|---|---|---|
| GET | `/profilepics` | 200 | מחזיר **את כל** התמונות. ⚠️ יקר. |
| POST | `/profilepics` | 201 | `multipart/form-data`, שדה `icon` |
| GET | `/profilepics/:id` | 200 | `Content-Type: image/png` |

**`/docs`** — Swagger UI (מוגדר ב-`main.ts`).

### 1.3 מודל הנתונים (MongoDB)

```mermaid
erDiagram
    User ||--o{ UserEvent : "events[]"
    User ||--o{ Message : "messages{}"
    User ||--o| ProfilePic : "profile_pic"
    UserEvent }o--|| User : "creator_id"
    UserEvent ||--o{ User : "attendents{}"
    Message }o--|| User : "sender_id"
    Message ||--o{ User : "receiver_ids[]"

    User {
        ObjectId _id
        string name
        string email
        string password
        string user_type
        string bio
        number rating
        number num_ratings
        string_array events
        map messages
        string profile_pic
    }
    UserEvent {
        ObjectId _id
        string name
        Date date
        string location
        string description
        boolean isPrivate
        string creator_id
        map attendents
        number rating
        number num_ratings
    }
    Message {
        ObjectId _id
        string title
        string content
        Date date_sent
        string sender_id
        string_array receiver_ids
    }
    ProfilePic {
        ObjectId _id
        Buffer icon
    }
```

**4 קולקציות:** `users`, `userevents`, `messages`, `profilepics`. אין אינדקסים מוגדרים מעבר ל-`_id`.

### 1.4 ממצאים — מה חייבים לתקן ומה רק כדאי

| # | ממצא | חומרה | קובץ | חובה לפרויקט? |
|---|---|---|---|---|
| **F-01** | **מחרוזת חיבור ל-MongoDB Atlas עם שם משתמש וסיסמה בקוד, ובהיסטוריית Git** (קומיט `c88c2ed` "moved database to atlas") | 🔴 קריטי | `src/app.module.ts:12` | **כן** — מחליפים ב-`MONGODB_URI` מ-Secret |
| **F-02** | סיסמאות נשמרות ב-plaintext; ה-Login הוא `GET` עם הסיסמה ב-query string (נכנס ללוגים של השרת ושל ה-Ingress) | 🔴 קריטי | `user.service.ts`, `user.controller.ts` | לא חובה — אבל **הזכר במצגת** כ-finding |
| **F-03** | אין endpoint בריאות | 🟠 חוסם | — | **כן** — Kubernetes probes תלויים בזה |
| **F-04** | פורט מקודד קשיח `app.listen(3000)` | 🟠 חוסם | `src/main.ts:24` | **כן** |
| **F-05** | `SpelunkerModule` מדפיס גרף Mermaid לקונסול בכל עלייה | 🟡 רעש | `src/main.ts:18-22` | **כן** — מזהם את הלוגים ב-Elasticsearch |
| **F-06** | לוגים לא מובנים — `console.log` פזורים בשירותים | 🟠 חוסם | `event.service.ts`, `user.service.ts`, ... | **כן** — בלי JSON אין ניתוח ב-ES |
| **F-07** | `ValidationPipe` מיובא ב-`main.ts` אבל **אף פעם לא מופעל גלובלית** | 🟡 באג | `src/main.ts:3` | לא חובה — תיקון של שורה אחת |
| **F-08** | קריאות async שלא מחכים להן (`this.eventService.editEvent(...)` בלי `await`) — השרת מחזיר 204 לפני שהכתיבה הסתיימה | 🟡 באג | `event.controller.ts:39`, `user.controller.ts:78` | לא חובה |
| **F-09** | אין CORS, אין Global Exception Filter | 🟡 | `src/main.ts` | לא חובה |
| **F-10** | `useNewUrlParser` / `useUnifiedTopology` — no-op ומיושן ב-Mongoose 8 | 🟢 | `src/app.module.ts` | תוקן בדרך |
| **F-11** | כתובת השרת מקודדת קשיח **בשני מקומות** ב-Android | 🟠 חוסם | `AsyncHttpRequest.java:69`, `Database.java:112` | **כן** |
| **F-12** | `AsyncTask` (מיושן מ-API 30) + `task.get()` חוסם את ה-UI thread | 🟡 | `AsyncHttpRequest.java` | לא חובה |
| **F-13** | אין `Dockerfile` / `.dockerignore` | 🟠 חוסם | — | **כן** |
| **F-14** | תמונות פרופיל נשמרות כ-Buffer בתוך MongoDB | 🟢 | `profilePic.model.ts` | לא — הזכר במצגת |
| **F-15** | `run_server.bat` נכנס ל-`src` במקום לשורש הפרויקט | 🟢 | `run_server.bat` | לא |

⚠️ **F-01 — פעולה מיידית לפני הכל:** הסיסמה `<REDACTED>` של המשתמש `zivmorgan` נמצאת בריפו הציבורי. גם אחרי שתחליף את הקוד — **היא נשארת בהיסטוריית Git**. היכנס ל-MongoDB Atlas ו:
1. מחק או השבת את משתמש ה-DB `zivmorgan`.
2. אם הקלאסטר כבר לא בשימוש — מחק אותו.
3. במצגת, הצג את זה כ-finding אמיתי שגילית ושהמעבר ל-Secret ב-Kubernetes פותר. זה נותן לפרויקט זווית אבטחתית אמיתית.

### 1.5 מה **לא** משנים

- כל לוגיקת ה-controllers/services של האירועים, המשתמשים וההודעות — נשארת כפי שהיא.
- ה-DTOs והמודלים — נשארים.
- כל ה-UI של אנדרואיד (Fragments, Activities, layouts) — נשאר. משנים **שורה אחת** של URL בכל אחד משני קבצים.
- ה-diagrams, ה-presentation וה-screenshots — נשארים כתיעוד של הפרויקט האקדמי המקורי.


---

## 2. הכנת הסביבה — WSL2 כ"שרת הענן"

המטרה: לינוקס נקי, עם systemd, עם מספיק זיכרון, שהרשת שלו מתנהגת כמו שרת אמיתי מנקודת המבט של Windows ושל ה-Emulator.

### 2.1 בדיקת מצב קיים

ב-**PowerShell כמנהל** ב-Windows:

```powershell
wsl --version
wsl --list --verbose
```

צריך לראות `WSL version: 2.x.x` ומעלה. אם מופיע `WSL version` נמוך או שגיאה:

```powershell
wsl --update
```

### 2.2 התקנת דיסטרו ייעודי לפרויקט

⚠️ **אל תשתמשו בדיסטרו הקיים שלכם.** K3s משנה iptables, מוסיף שירותי systemd ומחזיק state ב-`/var/lib/rancher`. תרצו לוותר עליו בקלות אם משהו נשבר.

```powershell
wsl --install -d Ubuntu-24.04
```

בפעם הראשונה יבקש שם משתמש וסיסמה. **שמרו את הסיסמה** — צריך אותה ל-`sudo`.

לאורך המדריך אשתמש בשם `Ubuntu-24.04`. אם התקנתם משהו אחר, החליפו בהתאם.

### 2.3 קובץ `.wslconfig` — הקצאת משאבים ורשת

ב-**Windows**, צרו/ערכו את `C:\Users\galh2\.wslconfig`.

הדרך המהירה, מ-PowerShell:

```powershell
notepad $env:USERPROFILE\.wslconfig
```

תוכן הקובץ:

```ini
[wsl2]
memory=10GB
processors=6
swap=4GB
networkingMode=mirrored
dnsTunneling=true
autoProxy=true

[experimental]
hostAddressLoopback=true
```

**מה כל שורה עושה:**

| שורה | למה |
|---|---|
| `memory=10GB` | מתוך 16GB. Elasticsearch + K3s + Jenkins צריכים מרווח; Windows + Emulator מקבלים את השאר. |
| `processors=6` | K3s + ES רעבים ל-CPU בעלייה. |
| `swap=4GB` | רשת ביטחון ל-ES אם יש ספייק. |
| `networkingMode=mirrored` | **הלב של הסידור.** WSL משקף את ממשקי הרשת של Windows. שירות שמאזין ב-WSL על `0.0.0.0:80` נגיש מיד ב-`localhost:80` ב-Windows, ולכן ב-`10.0.2.2:80` מה-Emulator. |
| `hostAddressLoopback=true` | מאפשר גם לגשת מ-WSL ל-`localhost` של Windows. |

⚠️ **`networkingMode=mirrored` דורש Windows 11 גרסה 22H2 ומעלה.** בדיקה: `winver`. אם אתם על Windows 10 — ראו סעיף 2.8 (מסלול חלופי).

⚠️ עם mirrored networking צריך לפתוח את חומת האש של Hyper-V לחיבורים נכנסים. ב-**PowerShell כמנהל**:

```powershell
Set-NetFirewallHyperVVMSetting -Name '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}' -DefaultInboundAction Allow
```

### 2.4 קובץ `/etc/wsl.conf` — הפעלת systemd

K3s רץ כשירות systemd. בלי זה, אין אשכול.

היכנסו ל-WSL:

```powershell
wsl -d Ubuntu-24.04
```

וצרו את הקובץ:

```bash
sudo tee /etc/wsl.conf > /dev/null <<'EOF'
[boot]
systemd=true

[network]
generateHosts=true
generateResolvConf=true

[interop]
enabled=true
appendWindowsPath=true

[user]
default=gal
EOF
```

**החלף:** `default=gal` — שם המשתמש שיצרת בהתקנה.

### 2.5 sysctl — הכנה ל-Elasticsearch

Elasticsearch דורש `vm.max_map_count >= 262144`, אחרת הוא נופל בעלייה עם `max virtual memory areas vm.max_map_count [65530] is too low`.

```bash
sudo tee /etc/sysctl.d/99-eventus.conf > /dev/null <<'EOF'
vm.max_map_count=262144
fs.inotify.max_user_watches=524288
fs.inotify.max_user_instances=512
EOF
```

`inotify` — K3s + Filebeat פותחים הרבה watchers; ברירת המחדל של WSL נמוכה מדי ותקבלו `too many open files`.

### 2.6 אתחול מלא של WSL

חזרו ל-**PowerShell**:

```powershell
wsl --shutdown
```

חכו 8 שניות (WSL צריך לשחרר את ה-VM), ואז:

```powershell
wsl -d Ubuntu-24.04
```

### 2.7 ✅ נקודת אימות — הסביבה מוכנה

הריצו ב-WSL:

```bash
echo "--- systemd ---"
systemctl is-system-running --wait

echo "--- memory ---"
free -h | awk '/Mem:/ {print "total:", $2}'

echo "--- cpus ---"
nproc

echo "--- max_map_count ---"
sysctl vm.max_map_count

echo "--- ubuntu ---"
lsb_release -ds
```

**פלט צפוי:**

```
--- systemd ---
running                       ← או degraded, שניהם בסדר
--- memory ---
total: 9.7Gi                  ← בערך 10GB
--- cpus ---
6
--- max_map_count ---
vm.max_map_count = 262144     ← חייב להיות המספר הזה
--- ubuntu ---
Ubuntu 24.04.x LTS
```

⚠️ אם `systemctl is-system-running` מחזיר `offline` או `Failed to connect to bus` — systemd לא עלה. בדקו את `/etc/wsl.conf` (שימו לב לרווחים ולאיות `systemd=true`) והריצו `wsl --shutdown` שוב.

בדיקת רשת mirrored — הריצו ב-WSL:

```bash
python3 -m http.server 8099 &
sleep 1
```

ומ-**PowerShell**:

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8099/
```

צריך להחזיר `200`. אם כן — ה-mirrored networking עובד וכל הפרויקט יעבוד חלק. סגרו: `kill %1` ב-WSL.

### 2.8 מסלול חלופי — Windows 10 או mirrored לא עובד

אם אין mirrored networking, ה-IP של WSL משתנה בכל reboot וצריך port forwarding. הוסיפו סקריפט PowerShell שמריצים אחרי כל אתחול:

```powershell
# C:\Users\galh2\Desktop\EventUs\scripts\wsl-portproxy.ps1
# הרצה: PowerShell כמנהל

$wslIp = (wsl -d Ubuntu-24.04 hostname -I).Trim().Split(" ")[0]
Write-Host "WSL IP: $wslIp"

$ports = @(80, 8080, 6443)

foreach ($p in $ports) {
    netsh interface portproxy delete v4tov4 listenport=$p listenaddress=0.0.0.0 2>$null
    netsh interface portproxy add v4tov4 `
        listenport=$p listenaddress=0.0.0.0 `
        connectport=$p connectaddress=$wslIp
    New-NetFirewallRule -DisplayName "WSL EventUs $p" -Direction Inbound `
        -LocalPort $p -Protocol TCP -Action Allow -ErrorAction SilentlyContinue | Out-Null
}

netsh interface portproxy show v4tov4
```

זה מפנה `localhost:80` של Windows ל-WSL, מה שנותן את אותה תוצאה כמו mirrored — רק ידנית.

### 2.9 קובץ hosts של Windows — שמות נוחים

כדי לגשת ל-Kibana ב-`http://kibana.local` במקום port-forward.

ב-PowerShell **כמנהל**:

```powershell
notepad C:\Windows\System32\drivers\etc\hosts
```

הוסיפו בסוף:

```
127.0.0.1 eventus.local
127.0.0.1 kibana.local
127.0.0.1 jenkins.local
```



### 2.10 חלוקת העבודה בין WSL ל-Windows

זו שאלה שעולה מיד אחרי הכנת הסביבה, ושווה להחליט עליה לפני שמתחילים: **מה בדיוק רץ איפה.**

התשובה היא פיצול לפי כלי, לא לפי תיקייה:

| מה | איפה | למה |
|---|---|---|
| Ansible, Terraform, Docker, K3s, Jenkins, `eventus.sh`, `git push` של הדמו | **WSL**, ב-`~/projects/EventUs` | כל אלה קוראים וכותבים אלפי קבצים קטנים |
| Android Studio, בניית ה-APK, האמולטור | **Windows**, ב-`C:\Users\galh2\Desktop\EventUs-Dev` | Android Studio הוא אפליקציית Windows, וה-SDK יושב על `C:` |

**שני עותקים של אותו ריפו, מסונכרנים דרך GitHub.** זה לא כפילות מיותרת — זו בדיוק ההפרדה שקיימת בכל צוות שבו מפתח האפליקציה ומהנדס התשתית לא עובדים על אותה מכונה.

⚠️ **למה לא להשאיר הכל על `C:` ולעבוד מ-WSL דרך `/mnt/c`.** WSL2 ניגש לדיסק של Windows דרך שכבת תרגום (9P/`drvfs`). פעולה על קובץ בודד עולה שם פי 10-20 זמן מאשר במערכת הקבצים של לינוקס. `npm ci` שלוקח 40 שניות ייקח 8 דקות, `terraform apply` יזחל, ו-`inotify` (שעליו נשענים K3s ו-Filebeat) לא עובד שם באופן אמין. **זו הטעות שהכי הרבה אנשים עושים עם WSL.**

⚠️ **למה לא להעביר גם את אנדרואיד ל-WSL.** אפשר לפתוח את הפרויקט מ-`\\wsl.localhost\Ubuntu-EventUs\...`, אבל אז Gradle עובד דרך אותה שכבה בכיוון ההפוך, והנתיב ב-`local.properties` שמצביע ל-Android SDK על `C:` נשבר. חוץ מזה — צד האנדרואיד כמעט לא משתנה יותר: ארבעת השינויים כבר בפנים (חלק 5), ובונים APK פעם אחת לקראת הדמו.

**האמולטור כן מגיע לאשכול שרץ ב-WSL.** האמולטור רץ על Windows ופונה ל-`10.0.2.2`, שהוא ה-loopback של המארח. עם `networkingMode=mirrored` (סעיף 2.3), פורט 80 של WSL הוא פורט 80 של Windows — ולכן `http://10.0.2.2/` מהאמולטור מגיע ישירות ל-Traefik. **זו הסיבה שבחרנו mirrored מלכתחילה.**

**הסדר המעשי:**

```bash
# 1. ב-Windows — קומיט ודחיפה של כל העבודה
cd C:\Users\galh2\Desktop\EventUs-Dev
git add -A
git commit -m "feat: devops platform - ansible, terraform, jenkins, elk, self-healing"
git push -u origin devops-project
```

```bash
# 2. ב-WSL — עותק העבודה של כל צד ה-DevOps
mkdir -p ~/projects && cd ~/projects
git clone git@github.com:GalHillel/EventUs.git
cd EventUs && git checkout devops-project
```

מכאן כל הפקודות במדריך הזה רצות מ-`~/projects/EventUs`.

**3. Android Studio** פותח את `C:\Users\galh2\Desktop\EventUs-Dev\frontend` ולא נוגע ב-WSL בכלל.

⚠️ **Docker Desktop.** אם הוא מותקן על Windows, כבו את האינטגרציה שלו עם הדיסטרו הזה: Docker Desktop → Settings → Resources → WSL Integration → הסירו את הסימון מ-`Ubuntu-EventUs`. Ansible מתקין Docker Engine ילידי בתוך הדיסטרו, ושני daemons שמתחרים על אותו `/var/run/docker.sock` נותנים שגיאות שקשה לפענח.

⚠️ **הדמו רץ מ-WSL בלבד.** `./eventus.sh break` מקמיט ודוחף מהעותק שב-WSL, ו-Jenkins מרים אותו משם. אם תערכו את אותו קובץ גם בעותק של Windows, תקבלו התנגשות מיזוג באמצע ההדגמה. הכלל: **קוד אנדרואיד → Windows. כל השאר → WSL.** אחרי שינוי בצד אחד, `git pull` בצד השני.

---

## 3. מבנה הריפו החדש

### 3.1 העיקרון

כל קוד התשתית (IaC) חי **באותו ריפו** של האפליקציה, בתיקיות אחיות. זה נקרא mono-repo ל-app+infra, וזה הבחירה הנכונה לפרויקט בסדר הגודל הזה: קומיט אחד יכול לשנות גם את הקוד וגם את הפריסה שלו, ואין בעיית סנכרון בין שני ריפואים.

עיקרון שני, לא פחות חשוב: **קובץ אחד לכל תחום אחריות.** אפשר לפרוס את אותה מערכת בדיוק על פני 45 קבצים או על פני 16. שתי הגרסאות עובדות. הגרסה של 16 היא זו שאפשר להחזיק בראש, ולכן זו שאפשר להגן עליה מול המרצה. פיצול לקבצים קטנים משתלם כשצוות שלם עורך אותם במקביל ומתנגשים ב-Git; בפרויקט של אדם אחד הוא רק מוסיף קפיצות בין קבצים. לכן: playbook אחד ב-Ansible במקום ארבעה roles, `main.tf` אחד במקום שמונה קבצי משאבים, `platform.ts` אחד במקום חמישה קבצי middleware ובקרים, ו-`eventus.sh` אחד במקום שבעה סקריפטי עזר.

**סה"כ: 16 קבצים חדשים, 1,674 שורות.** כל מה שהמערכת עושה נמצא בהם.

### 3.2 העץ המלא שנבנה

```
EventUs/                                    ← קיים
├── backend/event-us/                       ← קיים, נערוך
│   ├── Dockerfile                          ← חדש    (35 שורות)
│   ├── .dockerignore                       ← חדש    (13 שורות)
│   └── src/
│       ├── main.ts                         ← נערוך
│       ├── app.module.ts                   ← נערוך
│       └── common/
│           └── platform.ts                 ← חדש    (98 שורות)
│                                              לוגר + שני middleware + בקר health/chaos
│
├── frontend/                               ← קיים
│   └── app/
│       ├── build.gradle.kts                ← נערוך (buildConfigField)
│       └── src/main/java/com/example/eventus/data/
│           ├── AsyncHttpRequest.java       ← נערוך (שורה אחת)
│           └── Database.java               ← נערוך (שורה אחת)
│
├── infra/                                  ← חדש — כל קוד התשתית
│   ├── ansible/
│   │   ├── inventory.ini                   (2 שורות)
│   │   └── site.yml                        (143 שורות)  playbook יחיד, 19 משימות
│   │
│   ├── terraform/
│   │   ├── providers.tf                    (14 שורות)
│   │   ├── variables.tf                    (26 שורות)   4 משתנים
│   │   ├── main.tf                         (808 שורות)  כל 30 המשאבים
│   │   ├── terraform.tfvars.example        (3 שורות)
│   │   └── terraform.tfvars                ← ב-.gitignore! מכיל סיסמאות
│   │
│   ├── healer/
│   │   ├── healer.py                       (125 שורות)
│   │   ├── rollback.sh                     (28 שורות)
│   │   └── Dockerfile                      (19 שורות)
│   │
│   └── jenkins/
│       └── Dockerfile                      (32 שורות)
│
├── eventus.sh                              ← חדש (185 שורות) — כל פעולות התפעול
├── Jenkinsfile                             ← חדש (81 שורות)
└── DEVOPS.md                               ← חדש (62 שורות)
```

**מה שהיה כאן קודם ואיננו, ולמה:**

| מה נעלם | לאן עבר | למה זה בסדר |
|---|---|---|
| `ansible.cfg`, `group_vars/all.yml`, 4 תיקיות `roles/` | `site.yml` | Roles מיועדים לשימוש חוזר בין playbooks. יש playbook אחד. אין שימוש חוזר. |
| 8 קבצי `NN-*.tf` + `versions.tf` + `outputs.tf` | `main.tf` | Terraform ממילא מאחד את כל קבצי ה-`.tf` בתיקייה לגרף אחד. הפיצול היה ויזואלי בלבד. |
| `json-logger.service.ts`, `request-log.middleware.ts`, `chaos.middleware.ts`, `chaos.controller.ts`, `health.controller.ts` | `platform.ts` | חמישה קבצים באורך 18–56 שורות שכולם עוסקים באותו דבר: איך האפליקציה מדברת עם הפלטפורמה. |
| 7 סקריפטים ב-`infra/scripts/` + `run-jenkins.sh` | `eventus.sh` | תת-פקודה במקום קובץ. `./eventus.sh status` במקום `./infra/scripts/status.sh`. |
| `.checkov.yaml`, `CHECKOV.md` | — | סורק אבטחה סטטי הוא כלי מצוין, אבל הוא לא חלק מהאיפיון והוא הוסיף שני קבצים ותהליך שצריך להסביר. הבקרות שהוא תפס (security context, liveness probe) נשארו בקוד. |
| `k8s/app-deployment.yaml` | — | היה "לעיון בלבד" ולכן היה מקור שני לאמת. Terraform הוא המקור היחיד. |

### 3.3 יצירת המבנה

אם עוד לא שכפלתם ל-WSL — ראו סעיף 2.10. בקצרה, **אל תעבדו ישירות ב-`/mnt/c/`**:

```bash
mkdir -p ~/projects
cd ~/projects
git clone git@github.com:GalHillel/EventUs.git
cd EventUs
git checkout devops-project
git pull
```

אם SSH לא מוגדר ב-WSL:

```bash
ssh-keygen -t ed25519 -C "galh2011@gmail.com" -f ~/.ssh/id_ed25519 -N ""
cat ~/.ssh/id_ed25519.pub
```

העתיקו את המפתח ל-GitHub → Settings → SSH and GPG keys → New SSH key. ואז:

```bash
ssh -T git@github.com
```

צריך להחזיר `Hi GalHillel! You've successfully authenticated`.

עכשיו בנו את השלד — ארבע תיקיות, זהו:

```bash
cd ~/projects/EventUs

mkdir -p infra/{ansible,terraform,healer,jenkins}
mkdir -p backend/event-us/src/common

find infra -type d | sort
```

### 3.4 עדכון `.gitignore`

```bash
cd ~/projects/EventUs

cat >> .gitignore <<'EOF'

# Terraform
infra/terraform/.terraform/
infra/terraform/.terraform.lock.hcl
infra/terraform/*.tfstate
infra/terraform/*.tfstate.*
infra/terraform/*.tfvars
!infra/terraform/*.tfvars.example
infra/terraform/crash.log
infra/terraform/.terraform.tfstate.lock.info

# Ansible
infra/ansible/*.retry
infra/ansible/.vault_pass

# Kubernetes / local secrets
kubeconfig
*.kubeconfig
infra/jenkins/kubeconfig-jenkins.yaml
.env
.env.*
!.env.example
EOF
```

⚠️ **`terraform.tfstate` לא נכנס ל-Git.** הוא מכיל את כל הסודות בטקסט גלוי (סיסמת Mongo, למשל). בפרויקט אמיתי היינו משתמשים ב-remote backend (S3/Consul); כאן local state מספיק — פשוט לא מקומיטים אותו. **הזכר את זה במצגת.**

### 3.5 אסטרטגיית ענפים

| ענף | תפקיד |
|---|---|
| `main` | הפרויקט האקדמי המקורי. **לא נוגעים.** |
| `devops-project` | הענף הראשי של פרויקט ה-DevOps. Jenkins עוקב אחריו. |

הבאג המכוון בדמו הוא קומיט על `devops-project`, לא ענף נפרד — כך ה-pipeline מרים אותו מעצמו וזו הנקודה של ההדגמה.

```bash
git checkout devops-project
git add -A
git commit -m "chore: scaffold infra directory structure"
git push origin devops-project
```
---

## 4. שינויי הקוד ב-NestJS

זהו החלק הכי חשוב במסמך. אפליקציה שלא מוכנה לקונטיינר לא תעבוד ב-Kubernetes, ואפליקציה שלא פולטת לוגים מובנים לא תאפשר Self-Healing.

**קובץ חדש אחד, שלושה קבצים קיימים שנערכים:**

| # | קובץ | סוג | למה |
|---|---|---|---|
| 4.1 | `src/common/platform.ts` | חדש | לוגר JSON, middleware לתיעוד בקשות, מנגנון הבאג המכוון, health endpoints |
| 4.2 | `src/app.module.ts` | עריכה | Mongo מתוך משתנה סביבה + חיווט ה-middleware |
| 4.3 | `src/main.ts` | עריכה | פורט מ-env, לוגר, shutdown hooks |
| 4.4 | ניקוי `console.log` | עריכה | הזרם חייב להיות NDJSON נקי |
| 4.5 | `.dockerignore` + `Dockerfile` | חדש | אריזה |

**למה קובץ אחד ולא חמישה?** כל מה שנמצא ב-`platform.ts` הוא צד אחד של אותו חוזה: איך האפליקציה מדברת עם הפלטפורמה שמריצה אותה. הלוגר כותב את מה שה-middleware מודד, ה-middleware מכבד את אותה רשימת `HEALTH_PATHS` שהבקר חושף, וה-chaos משתמש באותו לוגר. חמישה קבצים היו מחייבים חמישה imports הדדיים כדי לחלוק שלושה קבועים. פונקציה של 6 שורות לא צריכה קובץ משלה.

---

### 4.1 `src/common/platform.ts`

**קובץ חדש.** 98 שורות, ובהן ארבעה דברים.

```typescript
import {
  Controller,
  Get,
  InternalServerErrorException,
  LoggerService,
  ServiceUnavailableException,
} from '@nestjs/common';
import { InjectConnection } from '@nestjs/mongoose';
import { NextFunction, Request, Response } from 'express';
import { Connection } from 'mongoose';

const SERVICE = process.env.SERVICE_NAME || 'eventus-api';
const VERSION = process.env.APP_VERSION || 'dev';
const HEALTH_PATHS = ['/health/live', '/health/ready'];
const DEFAULT_ERROR_RATE = 0;

export function log(level: string, msg: string, extra: Record<string, unknown> = {}) {
  const line = { time: new Date().toISOString(), level, service: SERVICE, version: VERSION, msg, ...extra };
  process.stdout.write(`${JSON.stringify(line)}\n`);
}

export class JsonLogger implements LoggerService {
  log(m: unknown, context?: string) {
    log('info', String(m), { context });
  }
  error(m: unknown, stack?: string, context?: string) {
    log('error', String(m), { context, stack });
  }
  warn(m: unknown, context?: string) {
    log('warn', String(m), { context });
  }
  debug(m: unknown, context?: string) {
    log('debug', String(m), { context });
  }
  verbose(m: unknown, context?: string) {
    log('debug', String(m), { context });
  }
}

export function requestLog(req: Request, res: Response, next: NextFunction) {
  const start = process.hrtime.bigint();
  res.on('finish', () => {
    const path = req.originalUrl.split('?')[0];
    const code = res.statusCode;
    const level = code >= 500 ? 'error' : code >= 400 ? 'warn' : 'info';
    log(level, `${req.method} ${path} ${code}`, {
      method: req.method,
      path,
      statusCode: code,
      durationMs: Math.round(Number(process.hrtime.bigint() - start) / 1e4) / 100,
      ip: req.ip,
    });
  });
  next();
}

export function chaos(req: Request, res: Response, next: NextFunction) {
  const rate = Number.parseFloat(process.env.CHAOS_ERROR_RATE ?? String(DEFAULT_ERROR_RATE));
  const path = req.originalUrl.split('?')[0];
  if (!rate || HEALTH_PATHS.includes(path) || Math.random() >= rate) {
    next();
    return;
  }
  res.status(500).json({ statusCode: 500, message: 'Internal Server Error', error: 'chaos' });
}

@Controller()
export class PlatformController {
  constructor(@InjectConnection() private readonly connection: Connection) {}

  @Get('health/live')
  live() {
    return { status: 'ok', uptime: Math.round(process.uptime()) };
  }

  @Get('health/ready')
  ready() {
    const state = this.connection.readyState;
    if (state !== 1) {
      throw new ServiceUnavailableException({ status: 'not-ready', mongo: state });
    }
    return { status: 'ok', mongo: 'connected', version: VERSION };
  }

  @Get('chaos/status')
  status() {
    return {
      errorRate: Number.parseFloat(process.env.CHAOS_ERROR_RATE || '0'),
      version: VERSION,
      pod: process.env.POD_NAME || 'local',
    };
  }

  @Get('chaos/boom')
  boom(): never {
    throw new InternalServerErrorException('deliberate failure');
  }
}
```

#### 4.1.1 הלוגר — למה `process.stdout.write` ולא `console.log`

`console.log` בנוד מוסיף עיבוד משלו (inspect לאובייקטים, צביעה בטרמינל) ויכול לפצל שורה ארוכה. `process.stdout.write` עם `\n` מפורש נותן **שורה אחת בדיוק לכל אירוע** — וזה בדיוק מה ש-Filebeat מצפה לו כשהוא קורא עם parser של `ndjson`. שורה אחת = מסמך אחד ב-Elasticsearch.

השדות `service` ו-`version` נכנסים לכל שורה אוטומטית. `version` הוא ה-git SHA שנצרב בזמן הבנייה — ולכן, כשמסתכלים בקיבנה על הגרף של השגיאות, רואים במפורש שהן שייכות לגרסה מסוימת. זה מה שהופך את הגרף בדמו למשכנע.

`JsonLogger` עוטף את אותה פונקציה ומוזרק ל-NestJS עצמו, כדי שגם הודעות ההתחלה של הפריימוורק (`Nest application successfully started`) ייצאו כ-JSON ולא כטקסט צבעוני.

#### 4.1.2 `requestLog` — שורת לוג לכל בקשה

הפונקציה נרשמת ל-`res.on('finish')`, כלומר היא רצה **אחרי** שהתשובה נשלחה. לכן `res.statusCode` כבר סופי, וה-`durationMs` מודד את הבקשה כולה. `process.hrtime.bigint()` נותן ננו-שניות; החלוקה ב-`1e4` והעיגול נותנים שתי ספרות אחרי הנקודה במילי-שניות.

`statusCode` הוא **מספר**, לא מחרוזת. זה מה שמאפשר את השאילתה `{"range": {"statusCode": {"gte": 500}}}` ב-healer. אם היה נשמר כטקסט, `range` היה נכשל.

⚠️ **`req.originalUrl` ולא `req.path`.** זו הנקודה הכי מלכודתית בקובץ הזה. כש-NestJS רושם middleware דרך `forRoutes('*')`, Express מעביר את החלק שהותאם ל-`req.baseUrl` ומשאיר ב-`req.path` את המחרוזת `'/'` — לכל בקשה, בלי יוצא מן הכלל. עם `req.path`, **כל** הלוגים היו מראים נתיב `/`, ובדיקת הפטור של ה-chaos למטה לא הייתה תופסת אף פעם. `req.originalUrl` מחזיר את ה-URI המקורי; ה-`split('?')[0]` מסיר את ה-query string כדי ש-`/events/search?name=Wedding` ו-`/events/search?location=TelAviv` יתקבצו לאותו נתיב בקיבנה.

#### 4.1.3 `chaos` — הבאג המכוון

זהו הלב של הדמו. `CHAOS_ERROR_RATE` הוא מספר בין 0 ל-1; `0.45` פירושו ש-45% מהבקשות יחזרו 500 בלי להגיע לקוד האמיתי בכלל.

⚠️ **הפטור של נתיבי הבריאות הוא קריטי, לא נוחות.** בלעדיו, ה-chaos היה מפיל גם את `/health/live` בשיעור של 45% — ה-kubelet היה רואה liveness probe נכשלת, מרג את הקונטיינר, ה-Pod היה נכנס ל-`CrashLoopBackOff`, האפליקציה הייתה יורדת לגמרי ולא היו כלל לוגים לנתח. במקום דמו של ריפוי עצמי הייתה מתקבלת קריסה. **זו הנקודה שהכי חשוב לזכור מהחלק הזה.**

`DEFAULT_ERROR_RATE = 0` הוא הערך שה-`break` בדמו הופך ל-`0.45` בקומיט אמיתי (ראו חלק 11). משתנה הסביבה גובר עליו — כך אפשר לבדוק מקומית בלי לגעת בקוד.

#### 4.1.4 הבקר — health ו-chaos

`/health/live` = "התהליך חי". הוא לא נוגע ב-Mongo בכוונה: אם ה-DB נופל, אנחנו לא רוצים ש-Kubernetes יתחיל להרוג Pods תקינים.

`/health/ready` = "אפשר לשלוח אלי תעבורה". הוא בודק את `connection.readyState` של mongoose — `1` פירושו `connected`. אם לא, הוא מחזיר 503 ו-Kubernetes מוציא את ה-Pod מה-Service בלי להרוג אותו.

`/chaos/status` מחזיר את השיעור הפעיל, את הגרסה ואת שם ה-Pod. בדמו זו הדרך המהירה להראות למרצה איזו גרסה רצה עכשיו. `/chaos/boom` זורק 500 ידנית — שימושי לבדיקת המסלול לפני ההצגה.

---

### 4.2 `src/app.module.ts`

**עריכה.** 30 שורות בסך הכול.

```typescript
import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';

import { UserModule } from './modules/user/user.module';
import { EventModule } from './modules/event/event.module';
import { MessageModule } from './modules/message/message.module';
import { ProfilePicModule } from './modules/profilePic/profilePic.module';
import { PlatformController, chaos, requestLog } from './common/platform';

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/EventUs';

@Module({
  imports: [
    MongooseModule.forRoot(MONGODB_URI, {
      serverSelectionTimeoutMS: 5000,
      retryAttempts: 20,
      retryDelay: 3000,
    }),
    UserModule,
    EventModule,
    MessageModule,
    ProfilePicModule,
  ],
  controllers: [PlatformController],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    consumer.apply(requestLog, chaos).forRoutes('*');
  }
}
```

**מה השתנה מול המקור:**

⚠️ **מחרוזת החיבור ל-Atlas עם הסיסמה נמחקה מהקוד.** במקור היא הייתה כתובה בקובץ, והקובץ נמצא בהיסטוריה של GitHub. עכשיו היא מגיעה מ-`MONGODB_URI`, ו-Terraform מזריק אותה מ-Secret. הברירת מחדל היא Mongo מקומי, לפיתוח.

⚠️ **סדר ה-middleware משנה.** `requestLog` **חייב** להיות לפני `chaos`. אחרת בקשה שה-chaos חסם לא תגיע ל-`requestLog`, לא תיווצר לה שורת לוג עם `statusCode: 500`, ה-healer לא יראה את השגיאות — והדמו כולו לא יעבוד.

`serverSelectionTimeoutMS: 5000` עם `retryAttempts: 20` פותר את בעיית סדר העלייה: אם ה-Pod של האפליקציה עולה לפני ש-MongoDB מוכן, הוא ינסה שוב עשרים פעם במקום להתרסק. `readinessProbe` מחזיק אותו מחוץ ל-Service בינתיים.

---

### 4.3 `src/main.ts`

**עריכה.**

```typescript
import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

import { AppModule } from './app.module';
import { JsonLogger, log } from './common/platform';

const PORT = Number.parseInt(process.env.PORT || '3000', 10);
const HOST = process.env.HOST || '0.0.0.0';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { logger: new JsonLogger() });

  app.enableCors();
  app.useGlobalPipes(new ValidationPipe({ transform: true }));
  app.enableShutdownHooks();

  const options = new DocumentBuilder().setTitle('EventUs API').setVersion(process.env.APP_VERSION || 'dev').build();
  SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, options));

  await app.listen(PORT, HOST);
  log('info', 'server started', { port: PORT, host: HOST });
}

process.on('unhandledRejection', (reason) => log('error', 'unhandled rejection', { reason: String(reason) }));
process.on('uncaughtException', (err: Error) => {
  log('error', 'uncaught exception', { reason: err.message });
  process.exit(1);
});

bootstrap();
```

**מה השתנה ולמה:**

| שינוי | סיבה |
|---|---|
| `app.listen(PORT, HOST)` עם `HOST=0.0.0.0` | בלי זה נוד מאזין רק ל-localhost של הקונטיינר, ו-Kubernetes לא יכול להגיע אליו. בעיה קלאסית שנראית כמו "ה-Service לא עובד". |
| `enableShutdownHooks()` | כש-Kubernetes שולח SIGTERM, נוד מסיים לטפל בבקשות שכבר בטיפול לפני שהוא נסגר. בלי זה, כל deploy קוטע בקשות באמצע. |
| `logger: new JsonLogger()` | גם ההודעות של הפריימוורק עצמו יוצאות כ-JSON. |
| הסרת `SpelunkerModule` | הוא הדפיס גרף תלויות ענק ל-stdout בכל עלייה — עשרות שורות שאינן JSON, שהיו מזהמות את זרם הלוגים. |
| `process.on('uncaughtException')` | חריגה לא נתפסת נרשמת כשורת לוג מובנית לפני שהתהליך מת, כך שהיא מגיעה לקיבנה. |

---

### 4.4 ניקוי ה-`console.log` מהשירותים

⚠️ **שלב חובה שקל לפספס.** הקוד המקורי מכיל 18 קריאות `console.log` פזורות. שורה כמו `console.log(search_query)` מדפיסה `[ { name: 'test' } ]` — טקסט שהוא לא JSON. הוא נכנס לזרם הלוגים, ה-parser של `ndjson` ב-Filebeat נכשל עליו, והוא מגיע ל-Elasticsearch עם `error.message` במקום שדות. אם יש הרבה כאלה — הם גם מטים את המכנה בחישוב יחס השגיאות של ה-healer.

איתור כל המקרים:

```bash
cd ~/projects/EventUs/backend/event-us/src
grep -rn "console\." --include="*.ts" . | grep -v "^./common/"
```

הטיפול לפי סוג:

| סוג | טיפול | דוגמה |
|---|---|---|
| הדפסת דיבאג | מחיקה | `console.log(userEvent.attendents)` ב-`removeUser` |
| מתודת דיבאג שלמה | מחיקת המתודה **וגם** הקריאה אליה | `printAllEvents()`, `printAllProfilePics()` |
| טיפול בשגיאה | המרה ללוגר המובנה | ראו למטה |
| קוד מת | מחיקה | `console.log` כשורה בפני עצמה ב-`profilePic.controller.ts` |

דוגמת ההמרה, ב-`modules/user/user.controller.ts` — הוסיפו את ה-import:

```typescript
import { log } from '../../common/platform';
```

ואז:

```diff
-      console.log("error in edit user " + e.message)
+      log('error', 'edit user failed', { userId: _id, reason: e.message });

-    console.log("user " + _id + " rating " + ratingDTO._id)
+    log('info', 'user rating event', { userId: _id, eventId: ratingDTO._id });

-      console.log("error in rate event " + e.message)
+      log('error', 'rate event failed', { userId: _id, reason: e.message });
```

⚠️ שימו לב שאנחנו **לא מוחקים** מידע דיאגנוסטי — אנחנו הופכים אותו לשדות שאפשר לחפש בקיבנה. `userId` ו-`eventId` הופכים לשדות במסמך, לא לטקסט בתוך מחרוזת.

✅ **אימות:** אחרי הניקוי, כל שורה בפלט חייבת להיות JSON תקין:

```bash
docker logs eventus-test 2>&1 | while read -r l; do
  echo "$l" | python3 -c "import json,sys; json.loads(sys.stdin.read())" 2>/dev/null || echo "NOT JSON: $l"
done
```
---

### 4.5 `.dockerignore`

**קובץ חדש:** `backend/event-us/.dockerignore`

```
node_modules
dist
coverage
.git
.gitignore
.vscode
.idea
*.log
test
src/test.py
src/testimg.png
Dockerfile
.dockerignore
```

⚠️ בלי `.dockerignore`, ה-`COPY` מעתיק את `node_modules` המקומי (מאות מגה, קומפילציה ל-Windows/WSL) לתוך ה-build context. הבנייה תיקח דקות במקום שניות ותיכשל.

---

### 4.6 `Dockerfile`

**קובץ חדש:** `backend/event-us/Dockerfile`

```dockerfile
FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json tsconfig.build.json nest-cli.json ./
COPY src ./src

RUN npm run build


FROM node:22-alpine AS runtime

ENV NODE_ENV=production
WORKDIR /app

RUN addgroup -S eventus && adduser -S eventus -G eventus

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY --from=builder /app/dist ./dist

USER eventus

ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}
ENV PORT=3000
ENV HOST=0.0.0.0

EXPOSE 3000

CMD ["node", "dist/main.js"]
```

**החלטות ב-Dockerfile — הסבירו אותן במצגת:**

| החלטה | נימוק |
|---|---|
| **Multi-stage** | שלב `builder` מכיל את TypeScript, `@nestjs/cli` וכל ה-devDependencies. שלב `runtime` מקבל רק את `dist/` ואת ה-runtime deps. תמונה סופית ~180MB במקום ~700MB. |
| `npm ci` ולא `npm install` | `ci` מציית ל-`package-lock.json` בדיוק ולא מעדכן אותו. בנייה דטרמיניסטית — אותו קלט, אותו פלט. |
| העתקת `package*.json` **לפני** `src` | שכבת Docker cache. שינוי בקוד לא מפיל את שכבת ה-`npm ci`. הבנייה השנייה ואילך לוקחות שניות. |
| `USER eventus` | הקונטיינר לא רץ כ-root. אם מישהו פורץ דרך האפליקציה, אין לו root. **דרישה בסיסית בכל ארגון.** |
| `ARG APP_VERSION` | Jenkins מזריק את ה-SHA בזמן הבנייה. |
| `alpine` | ~50MB בסיס מול ~350MB של `node:22`. |
| `--omit=dev` | אין קומפיילר TypeScript בתמונת ה-Production. |

---

### 4.7 ✅ נקודת אימות — בונים ומריצים מקומית

לפני שנוגעים ב-Kubernetes, מוודאים שהקונטיינר עובד לבד.

**שלב 1 — קומפילציה מקומית:**

```bash
cd ~/projects/EventUs/backend/event-us
npm install
npm run build
ls -la dist/main.js
```

⚠️ אם יש שגיאות TypeScript — תקנו אותן עכשיו. אם `@nestjs/mongoose` צועק על `InjectConnection`, ודאו שהגרסה 10.x מותקנת.

**שלב 2 — Mongo זמני:**

```bash
docker run -d --name mongo-dev -p 27017:27017 mongo:7.0
```

(אם `docker` עדיין לא מותקן — דלגו לחלק 6, חזרו לכאן אחר כך.)

**שלב 3 — בניית התמונה:**

```bash
cd ~/projects/EventUs/backend/event-us
docker build --build-arg APP_VERSION=local-test -t eventus-api:local .
docker images eventus-api
```

**שלב 4 — הרצה:**

```bash
docker run -d --name eventus-test \
  --network host \
  -e MONGODB_URI="mongodb://127.0.0.1:27017/EventUs" \
  -e APP_VERSION=local-test \
  eventus-api:local
```

**שלב 5 — בדיקות:**

```bash
sleep 5

echo "--- liveness ---"
curl -s http://localhost:3000/health/live

echo -e "\n--- readiness ---"
curl -s http://localhost:3000/health/ready

echo -e "\n--- chaos status ---"
curl -s http://localhost:3000/chaos/status

echo -e "\n--- deliberate 500 ---"
curl -s -o /dev/null -w "status=%{http_code}\n" http://localhost:3000/chaos/boom

echo -e "\n--- real endpoint ---"
curl -s -o /dev/null -w "status=%{http_code}\n" "http://localhost:3000/events/search?name=test"

echo -e "\n--- JSON logs ---"
docker logs eventus-test --tail 12
```

**פלט צפוי:**

```
--- liveness ---
{"status":"ok","uptime":5}
--- readiness ---
{"status":"ok","mongo":"connected","version":"local-test"}
--- chaos status ---
{"errorRate":0,"version":"local-test"}
--- deliberate 500 ---
status=500
--- real endpoint ---
status=200
--- JSON logs ---
{"time":"2026-08-23T09:12:03.001Z","level":"info","service":"eventus-api","version":"local-test","msg":"server started","port":3000,"host":"0.0.0.0"}
{"time":"2026-08-23T09:12:08.114Z","level":"info","service":"eventus-api","version":"local-test","msg":"GET /health/live 200","method":"GET","path":"/health/live","statusCode":200,"durationMs":1.42,"ip":"::1"}
{"time":"2026-08-23T09:12:08.230Z","level":"error","service":"eventus-api","version":"local-test","msg":"GET /chaos/boom 500","method":"GET","path":"/chaos/boom","statusCode":500,"durationMs":2.08,"ip":"::1"}
```

**⚠️ אל תמשיכו הלאה עד שרואים את השורה עם `"statusCode":500` בפורמט JSON.** כל מנגנון הריפוי העצמי בנוי עליה.

**שלב 6 — בדיקת שיעור השגיאות:**

```bash
docker rm -f eventus-test
docker run -d --name eventus-test --network host \
  -e MONGODB_URI="mongodb://127.0.0.1:27017/EventUs" \
  -e CHAOS_ERROR_RATE=0.5 \
  eventus-api:local

sleep 5
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code} " http://localhost:3000/health/live
done
echo
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code} " "http://localhost:3000/events/search?name=x"
done
echo
```

השורה הראשונה (health) צריכה להיות `200` ×20 — ה-probes מוחרגים.
השורה השנייה צריכה להיות תערובת של `200` ו-`500` בערך חצי-חצי.

**ניקוי:**

```bash
docker rm -f eventus-test mongo-dev
```

**קומיט:**

```bash
cd ~/projects/EventUs
git add backend/event-us
git commit -m "feat(api): containerize - env config, JSON logging, health probes, chaos switch"
git push origin devops-project
```


---

## 5. שינויי הקוד ב-Android

שתי שורות. זהו.

היום האפליקציה פונה ל-`http://10.0.2.2:3000/` — כלומר ישירות לתהליך ה-NestJS שרץ על Windows. אחרי השינוי היא תפנה ל-`http://10.0.2.2/` — שער הכניסה של האשכול (Traefik על פורט 80). זה השינוי שהופך את האפליקציה מלקוח של שרת יחיד ללקוח של פלטפורמה.

### 5.1 מה קורה מתחת למכסה המנוע

```mermaid
sequenceDiagram
    participant A as Android Emulator
    participant W as Windows localhost:80
    participant T as Traefik (WSL)
    participant S as Service eventus-api
    participant P as Pod (1 מתוך 2)

    A->>W: GET http://10.0.2.2/events/search
    Note over A,W: 10.0.2.2 = alias קבוע של ה-Emulator ל-loopback של המארח
    W->>T: mirrored networking מעביר לפורט 80 ב-WSL
    T->>T: Ingress rule: path / → eventus-api:3000
    T->>S: ClusterIP
    S->>P: round-robin בין ה-Pods המוכנים
    P-->>A: 200 + JSON
```

`10.0.2.2` היא כתובת קסם של ה-Android Emulator שתמיד מצביעה על ה-loopback של המחשב המארח. היא לא משתנה. בזכות `networkingMode=mirrored` שהגדרנו בסעיף 2.3, פורט 80 ב-WSL הוא פורט 80 ב-Windows — ולכן פורט 80 ב-`10.0.2.2`.

### 5.2 שינוי 1 — `build.gradle.kts`: כתובת ה-API כמשתנה בנייה

**קובץ:** `frontend/app/build.gradle.kts`

**מצא:**

```kotlin
    buildFeatures {
        viewBinding = true
    }
```

**החלף ב:**

```kotlin
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
```

ובתוך `defaultConfig`, **מצא:**

```kotlin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
```

**החלף ב:**

```kotlin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2/\"")
    }
```

⚠️ **`buildConfig = true` הוא חובה.** ב-Android Gradle Plugin 8 (הפרויקט על 8.3.0) יצירת `BuildConfig` **כבויה כברירת מחדל**. בלי השורה הזו תקבלו `cannot find symbol: variable BuildConfig` והבנייה תיכשל.

⚠️ שימו לב ל-escape של הגרשיים: `"\"http://10.0.2.2/\""`. הפרמטר הוא **קוד Java** שמוזרק לקובץ, ולכן המחרוזת צריכה גרשיים משלה. בלי זה תקבלו `cannot find symbol: class http`.

**בונוס — כתובת שונה ל-debug ול-release:**

```kotlin
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"http://eventus.local/\"")
        }
    }
```

### 5.3 שינוי 2 — `AsyncHttpRequest.java`

**קובץ:** `frontend/app/src/main/java/com/example/eventus/data/AsyncHttpRequest.java`

**שורה 69 — לפני:**

```java
        String url = "http://10.0.2.2:3000/" + this.dir;
```

**אחרי:**

```java
        String url = BuildConfig.API_BASE_URL + this.dir;
```

והוסיפו את ה-import ליד שאר ה-imports בראש הקובץ:

```java
import com.example.eventus.BuildConfig;
```

### 5.4 שינוי 3 — `Database.java`

**קובץ:** `frontend/app/src/main/java/com/example/eventus/data/Database.java`

**שורה 112 — לפני:**

```java
        uploader.uploadFile("http://10.0.2.2:3000/profilepics", pic, callback);
```

**אחרי:**

```java
        uploader.uploadFile(BuildConfig.API_BASE_URL + "profilepics", pic, callback);
```

והוסיפו את ה-import:

```java
import com.example.eventus.BuildConfig;
```

⚠️ **זהו הקובץ שמפספסים.** `FileUploader` משתמש ב-OkHttp ובכתובת מלאה נפרדת, ולא עובר דרך `AsyncHttpRequest`. אם תשנו רק את הקובץ הראשון — כל האפליקציה תעבוד חוץ מהעלאת תמונת פרופיל, וזה יתגלה רק בזמן הדמו מול המרצה.

### 5.5 תיקון קונפיג הרשת (F-11b)

ממצא מהסריקה: `frontend/app/src/main/res/xml/network_security_config.xml` קיים ומגדיר כללים ל-`127.0.0.1`, אבל **`AndroidManifest.xml` אף פעם לא מפנה אליו** — אין תכונת `android:networkSecurityConfig` ב-`<application>`. הקובץ הוא קוד מת. מה שבפועל מאפשר תעבורת HTTP הוא `android:usesCleartextTraffic="true"`, שכבר מוגדר.

זה עובד, אבל `usesCleartextTraffic="true"` פותח HTTP לכל דומיין. הדרך הנקייה:

**קובץ:** `frontend/app/src/main/res/xml/network_security_config.xml` — החלף את התוכן:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">eventus.local</domain>
    </domain-config>
</network-security-config>
```

**קובץ:** `frontend/app/src/main/AndroidManifest.xml` — **מצא:**

```xml
        android:supportsRtl="true"
        android:theme="@style/Theme.EventUs"
        android:usesCleartextTraffic="true"
```

**החלף ב:**

```xml
        android:supportsRtl="true"
        android:theme="@style/Theme.EventUs"
        android:networkSecurityConfig="@xml/network_security_config"
```

עכשיו HTTP מותר **רק** מול השרת המקומי, וכל שאר התעבורה חייבת HTTPS. זו נקודה קטנה אבל היא מראה שקראתם את הקוד ולא רק הרצתם אותו.

⚠️ אם משהו נשבר בשלב הזה — החזירו את `android:usesCleartextTraffic="true"` והמשיכו. זה לא חלק הכרחי מהפרויקט.

### 5.6 ✅ נקודת אימות

```bash
cd ~/projects/EventUs/frontend
./gradlew :app:assembleDebug
```

צריך להסתיים ב-`BUILD SUCCESSFUL`.

בדיקה שהקבוע נוצר:

```bash
grep -r "API_BASE_URL" app/build/generated/source/buildConfig/ 2>/dev/null
```

**פלט צפוי:**

```java
  public static final String API_BASE_URL = "http://10.0.2.2/";
```

⚠️ אם Gradle לא רץ ב-WSL כי אין לו Android SDK — זה בסדר גמור. בנו מ-Android Studio ב-Windows כרגיל. הקובץ `frontend/app/local.properties` כבר מצביע על `C:\Users\galh2\AppData\Local\Android\Sdk`.

**קומיט:**

```bash
cd ~/projects/EventUs
git add frontend
git commit -m "feat(android): API base url via BuildConfig, target cluster ingress on port 80"
git push origin devops-project
```


---

## 6. Ansible — הכנת "הברזל"

### 6.1 מה Ansible עושה כאן ומה הוא לא עושה

**עושה:** הופך מכונת Ubuntu ריקה למכונה שיודעת להריץ קונטיינרים ואשכול Kubernetes. מתקין חבילות, מגדיר repositories, מתקין Docker, מתקין K3s, מתקין את כלי הלקוח (kubectl, terraform).

**לא עושה:** לא יוצר namespaces, לא פורס אפליקציות, לא מגדיר Ingress. זה התפקיד של Terraform.

זו ההפרדה הקלאסית: **Ansible = Configuration Management על מערכת הפעלה. Terraform = Infrastructure as Code על API.** אם תערבבו — תקבלו כלי אחד שעושה הכל גרוע. הצהירו על ההפרדה הזו במצגת.

```mermaid
graph LR
    A["Ubuntu ריק"] -->|Ansible| B["Ubuntu + Docker + K3s רץ"]
    B -->|Terraform| C["אשכול עם namespaces,<br/>Mongo, ELK, App, Healer"]
    C -->|Jenkins| D["גרסה חדשה של האפליקציה"]
```

### 6.2 התקנת Ansible

```bash
sudo apt-get update
sudo apt-get install -y ansible
ansible-galaxy collection install ansible.posix
ansible --version
```

צריך לראות `ansible [core 2.16.x]` או מעלה. האוסף `ansible.posix` דרוש בשביל מודול `sysctl`.

### 6.3 `infra/ansible/inventory.ini`

```ini
[cloud]
wsl-node ansible_connection=local
```

**למה `ansible_connection=local`:** ה"שרת" הוא אותה מכונה שמריצה את Ansible. אין SSH באמצע. בפרויקט אמיתי היה כאן IP ומפתח SSH — הקוד עצמו לא היה משתנה בכלל, וזו הנקודה של Ansible.

### 6.4 `infra/ansible/site.yml`

**Playbook יחיד, 19 משימות, 143 שורות.**

```yaml
---
- name: Provision the EventUs cloud node
  hosts: cloud
  become: true

  vars:
    k3s_version: v1.33.13+k3s2
    kubectl_channel: v1.33
    target_user: "{{ lookup('env', 'SUDO_USER') | default(lookup('env', 'USER'), true) }}"

  tasks:
    - name: Install base packages
      ansible.builtin.apt:
        name: [curl, ca-certificates, gnupg, apt-transport-https, git, jq, unzip]
        state: present
        update_cache: true
        cache_valid_time: 3600

    - name: Apply kernel parameters needed by Elasticsearch and K3s
      ansible.posix.sysctl:
        name: "{{ item.key }}"
        value: "{{ item.value }}"
        sysctl_file: /etc/sysctl.d/99-eventus.conf
        reload: true
      loop: "{{ {'vm.max_map_count': 262144, 'fs.inotify.max_user_watches': 524288} | dict2items }}"

    - name: Create the apt keyrings directory
      ansible.builtin.file:
        path: /etc/apt/keyrings
        state: directory
        mode: "0755"

    - name: Add the Docker apt key
      ansible.builtin.get_url:
        url: https://download.docker.com/linux/ubuntu/gpg
        dest: /etc/apt/keyrings/docker.asc
        mode: "0644"

    - name: Add the Docker apt repository
      ansible.builtin.apt_repository:
        repo: >-
          deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc]
          https://download.docker.com/linux/ubuntu {{ ansible_distribution_release }} stable
        filename: docker

    - name: Install Docker
      ansible.builtin.apt:
        name: [docker-ce, docker-ce-cli, containerd.io, docker-buildx-plugin]
        state: present
        update_cache: true

    - name: Start Docker and add the user to its group
      ansible.builtin.systemd:
        name: docker
        enabled: true
        state: started

    - name: Add the user to the docker group
      ansible.builtin.user:
        name: "{{ target_user }}"
        groups: docker
        append: true

    - name: Check whether k3s is installed
      ansible.builtin.stat:
        path: /usr/local/bin/k3s
      register: k3s_bin

    - name: Download the k3s installer
      ansible.builtin.get_url:
        url: https://get.k3s.io
        dest: /tmp/k3s-install.sh
        mode: "0755"
      when: not k3s_bin.stat.exists

    - name: Install k3s
      ansible.builtin.command: /tmp/k3s-install.sh
      environment:
        INSTALL_K3S_VERSION: "{{ k3s_version }}"
        INSTALL_K3S_EXEC: >-
          server --write-kubeconfig-mode 644 --node-name eventus-node
          --kubelet-arg=eviction-hard=imagefs.available<2%,nodefs.available<2%
      when: not k3s_bin.stat.exists
      changed_when: true

    - name: Wait for the node to be ready
      ansible.builtin.command: k3s kubectl wait --for=condition=Ready node/eventus-node --timeout=300s
      changed_when: false

    - name: Copy the kubeconfig to the user
      ansible.builtin.copy:
        src: /etc/rancher/k3s/k3s.yaml
        dest: "/home/{{ target_user }}/.kube/config"
        remote_src: true
        owner: "{{ target_user }}"
        mode: "0600"
      vars:
        ansible_become: true

    - name: Add the Kubernetes apt key
      ansible.builtin.get_url:
        url: "https://pkgs.k8s.io/core:/stable:/{{ kubectl_channel }}/deb/Release.key"
        dest: /tmp/k8s.key
        mode: "0644"

    - name: Add the HashiCorp apt key
      ansible.builtin.get_url:
        url: https://apt.releases.hashicorp.com/gpg
        dest: /tmp/hashicorp.key
        mode: "0644"

    - name: Convert both keys to keyrings
      ansible.builtin.command: gpg --batch --yes --dearmor -o {{ item.out }} {{ item.src }}
      args:
        creates: "{{ item.out }}"
      loop:
        - { src: /tmp/k8s.key, out: /etc/apt/keyrings/kubernetes.gpg }
        - { src: /tmp/hashicorp.key, out: /etc/apt/keyrings/hashicorp.gpg }

    - name: Add the kubectl and terraform repositories
      ansible.builtin.apt_repository:
        repo: "{{ item.repo }}"
        filename: "{{ item.name }}"
      loop:
        - name: kubernetes
          repo: "deb [signed-by=/etc/apt/keyrings/kubernetes.gpg] https://pkgs.k8s.io/core:/stable:/{{ kubectl_channel }}/deb/ /"
        - name: hashicorp
          repo: "deb [signed-by=/etc/apt/keyrings/hashicorp.gpg] https://apt.releases.hashicorp.com {{ ansible_distribution_release }} main"

    - name: Install kubectl and terraform
      ansible.builtin.apt:
        name: [kubectl, terraform]
        state: present
        update_cache: true

    - name: Export KUBECONFIG for the user
      ansible.builtin.lineinfile:
        path: "/home/{{ target_user }}/.bashrc"
        line: "export KUBECONFIG=/home/{{ target_user }}/.kube/config"
        regexp: "^export KUBECONFIG="
        create: true
        owner: "{{ target_user }}"
        mode: "0644"
```

### 6.5 ההחלטות שבתוך ה-playbook

**למה playbook אחד ולא roles.** Role הוא יחידת שימוש חוזר: אותו `docker` role מותקן על עשרה שרתים בעשרה פרויקטים. כאן יש שרת אחד ופרויקט אחד. הפיצול ל-`common/docker/k3s/tools` דרש 9 קבצים ו-384 שורות כדי לתאר את אותן 19 משימות שכתובות כאן ב-143. אם המרצה ישאל "למה לא roles" — התשובה היא בדיוק זו, והיא נכונה.

**`vm.max_map_count: 262144`** — Elasticsearch משתמש ב-`mmap` לאינדקסים. ברירת המחדל של לינוקס (65530) נמוכה מדי והוא **מסרב לעלות** עם `max virtual memory areas vm.max_map_count [65530] is too low`. זו התקלה מספר אחת בהרמת ELK, וכאן היא נפתרת לפני שהיא קורית.

**`fs.inotify.max_user_watches: 524288`** — K3s ו-Filebeat פותחים הרבה watches על קבצים. ברירת המחדל נגמרת ורואים `too many open files`.

**`--write-kubeconfig-mode 644`** — בלעדיו `/etc/rancher/k3s/k3s.yaml` נוצר עם הרשאות `600` לרוט, וכל `kubectl` של המשתמש הרגיל נכשל ב-permission denied.

**`--kubelet-arg=eviction-hard=imagefs.available<2%,nodefs.available<2%`** — ברירת המחדל של kubelet מפנה Pods כשנשארים פחות מ-15% דיסק. ב-WSL הדיסק הוא הדיסק של Windows והוא לרוב מלא מעבר לכך, אז kubelet היה מפנה את כל ה-Pods מיד. זה שינוי מודע שמתאים לסביבת פיתוח בלבד.

**`get_url` ואז `command` במקום `curl | sh`** — הצינור `curl -sfL https://get.k3s.io | sh -` הוא הדרך המתועדת של Rancher, אבל הוא מסתיר כשלים (`pipefail` לא קיים ב-Ansible) ו-`ansible-lint` מסמן אותו כ-`risky-shell-pipe`. הורדה לקובץ והרצה של הקובץ מחזירה קוד יציאה אמיתי.

**`when: not k3s_bin.stat.exists`** — זו האידמפוטנטיות. הרצה שנייה של ה-playbook לא מתקינה את K3s מחדש. `stat` לפני `command` הוא הדפוס הנכון כשמריצים סקריפט חיצוני שאין לו מודול Ansible.

**`target_user` דרך `SUDO_USER`** — ה-playbook רץ עם `become: true`, כלומר כרוט. בלי הטריק הזה, ה-kubeconfig וה-`.bashrc` היו נכתבים ל-`/root` ולא למשתמש שבאמת מריץ `kubectl`.

### 6.6 הרצה

```bash
cd ~/projects/EventUs

ansible-playbook -i infra/ansible/inventory.ini infra/ansible/site.yml --syntax-check

ansible-playbook -i infra/ansible/inventory.ini infra/ansible/site.yml --check --diff

ansible-playbook -i infra/ansible/inventory.ini infra/ansible/site.yml
```

**`--syntax-check`** — בודק YAML בלי להריץ.
**`--check`** — dry run. מראה מה **היה** משתנה. ⚠️ צפו לשגיאות ב-dry-run על המשימות שאחרי התקנת K3s, כי הן תלויות בפעולות קודמות שלא באמת קרו. זה תקין; המשיכו להרצה האמיתית.

זמן ריצה ראשון: 5-10 דקות (הורדות).

**בונוס לציון:** `ansible-lint` בפרופיל production עובר נקי על הקובץ הזה. שווה להריץ ולהראות:

```bash
pip install ansible-lint
ansible-lint --profile production infra/ansible/site.yml
```

### 6.7 ✅ נקודת אימות

```bash
exec bash
```

(זה טוען מחדש את ה-shell עם ה-`docker` group וה-`KUBECONFIG`.)

```bash
echo "=== docker ==="
docker run --rm hello-world | head -3

echo "=== node ==="
kubectl get nodes -o wide

echo "=== system pods ==="
kubectl get pods -A

echo "=== traefik ==="
kubectl -n kube-system get svc traefik

echo "=== storage class ==="
kubectl get storageclass

echo "=== ingress reachable ==="
curl -s -o /dev/null -w "traefik responded with %{http_code}\n" http://localhost/
```

**פלט צפוי:**

```
=== docker ===
Hello from Docker!
=== node ===
NAME           STATUS   ROLES                  AGE   VERSION
eventus-node   Ready    control-plane,master   3m    v1.33.13+k3s2
=== system pods ===
NAMESPACE     NAME                                     READY   STATUS      RESTARTS
kube-system   coredns-...                              1/1     Running     0
kube-system   local-path-provisioner-...               1/1     Running     0
kube-system   helm-install-traefik-crd-...             0/1     Completed   0
kube-system   helm-install-traefik-...                 0/1     Completed   0
kube-system   svclb-traefik-...                        2/2     Running     0
kube-system   traefik-...                              1/1     Running     0
kube-system   metrics-server-...                       1/1     Running     0
=== traefik ===
NAME      TYPE           CLUSTER-IP     EXTERNAL-IP   PORT(S)
traefik   LoadBalancer   10.43.x.x      10.x.x.x      80:3xxxx/TCP,443:3xxxx/TCP
=== storage class ===
NAME                   PROVISIONER             DEFAULT
local-path (default)   rancher.io/local-path   true
=== ingress reachable ===
traefik responded with 404
```

⚠️ **`404` מ-Traefik הוא הצלחה.** זה אומר "אני חי, מאזין על פורט 80, ואין לי עדיין כלל ניתוב". אם קיבלתם `000` או `Connection refused` — הבעיה היא רשת WSL, לא Traefik. ראו חלק 12.

**קומיט:**

```bash
cd ~/projects/EventUs
git add infra/ansible
git commit -m "feat(ansible): provision docker, k3s and cluster tooling"
git push origin devops-project
```
---

## 7. Terraform — התשתית בתוך האשכול

### 7.1 מה Terraform מנהל וממה הוא מתרחק

```mermaid
graph TB
    subgraph TF["בבעלות Terraform"]
        NS["Namespaces<br/>eventus / observability / platform"]
        SEC["Secrets + ConfigMaps"]
        DB["MongoDB StatefulSet + PVC + Service"]
        ELK["Elasticsearch + Kibana + Filebeat"]
        ING["Ingress rules"]
        RBAC["ServiceAccount + Role + RoleBinding"]
        CJ["CronJob של הריפוי"]
        DEP["Deployment של האפליקציה<br/>(מבנה בלבד)"]
    end
    subgraph JEN["בבעלות Jenkins"]
        IMG["שדה ה-image בלבד<br/>kubectl set image"]
    end
    DEP -.->|"lifecycle.ignore_changes"| IMG
```

**בעיית ה-drift, והפתרון:** Terraform יוצר את ה-Deployment עם תג image ראשוני. Jenkins מעדכן את התג בכל בנייה. בלי הגנה, ה-`terraform plan` הבא היה מציע להחזיר את התג הישן — ו-`terraform apply` היה מוחק את הפריסה של Jenkins. הפתרון הוא `lifecycle { ignore_changes = [...] }` על שדה ה-image בלבד. **זו אחת הנקודות החזקות של הפרויקט. אל תדלגו עליה במצגת.**

### 7.2 ארבעה קבצים, לא חמישה־עשר

Terraform לא אכפת לו מקבצים. הוא קורא את **כל** קבצי ה-`.tf` בתיקייה, מאחד אותם לגרף תלויות אחד, ומחשב סדר ביצוע מהקשרים בין המשאבים — לא מסדר השורות ולא משמות הקבצים. פיצול ל-`01-namespaces.tf`, `02-mongodb.tf` וכן הלאה לא משנה שום דבר בהתנהגות; המספרים בתחילת השם אפילו מטעים, כי הם רומזים על סדר שלא קיים.

לכן:

| קובץ | שורות | תוכן |
|---|---|---|
| `providers.tf` | 14 | גרסת Terraform, ה-provider, הנתיב ל-kubeconfig |
| `variables.tf` | 26 | 4 משתנים בלבד |
| `terraform.tfvars.example` | 3 | תבנית לסודות |
| `main.tf` | 808 | 30 המשאבים + 3 outputs |

המסמך מציג את `main.tf` בקטעים לפי תחום. **זה קובץ אחד** — הכותרות למטה הן לצורך הקריאה כאן בלבד.

---

### 7.3 `infra/terraform/providers.tf`

```hcl
terraform {
  required_version = ">= 1.6.0"

  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.38"
    }
  }
}

provider "kubernetes" {
  config_path = "~/.kube/config"
}
```

`~> 2.38` מרשה 2.38.x אבל לא 2.39 — עדכון מינורי של provider יכול לשנות סכמות. `config_path` מצביע ל-kubeconfig ש-Ansible כתב.

⚠️ **המשאבים כאן הם עם סיומת `_v1`** (`kubernetes_deployment_v1` ולא `kubernetes_deployment`). מגרסה 2.x זו הצורה המומלצת; היא נצמדת ל-`apps/v1` של ה-API ולא תשבר בשדרוג provider.

---

### 7.4 `infra/terraform/variables.tf`

```hcl
variable "app_image" {
  description = "Initial API image. Jenkins takes over the tag after the first apply."
  type        = string

  validation {
    condition     = can(regex("^[^:]+:[^:]+$", var.app_image)) && !endswith(var.app_image, ":latest")
    error_message = "app_image needs an explicit tag and must not be :latest, or rollout undo cannot swap images."
  }
}

variable "healer_image" {
  description = "Image for the self-healing job"
  type        = string
}

variable "mongo_password" {
  description = "MongoDB root password"
  type        = string
  sensitive   = true
}

variable "elastic_version" {
  description = "Tag shared by Elasticsearch, Kibana and Filebeat"
  type        = string
  default     = "8.19.20"
}
```

**ארבעה משתנים.** בגרסה הקודמת היו שנים־עשר: מספר ה-replicas, גבולות זיכרון, גדלי דיסק, שמות namespace. כולם היו משתנים שאף פעם לא שיניתי. משתנה שיש לו ערך אחד לנצח הוא קבוע, וקבוע מקומו בקוד — שם רואים אותו בהקשר במקום לקפוץ לקובץ אחר.

⚠️ **ה-validation על `app_image` הוא הגנה אמיתית, לא קישוט.** אם התג הוא `:latest`, אז כל הגרסאות מצביעות לאותו שם, `kubectl rollout undo` "יחזור" לאותה תמונה בדיוק — והריפוי העצמי לא יעשה כלום. Terraform יעצור לפני שזה יקרה, עם ההסבר כתוב בהודעת השגיאה. **זו שאלה מצוינת למצגת: "מה קורה אם משתמשים ב-latest?"**

`sensitive = true` על הסיסמה מונע ממנה להופיע בפלט של `plan` ו-`apply`. היא עדיין נמצאת ב-`terraform.tfstate` בטקסט גלוי — ולכן ה-state לא נכנס ל-Git.

---

### 7.5 `infra/terraform/terraform.tfvars.example`

```hcl
app_image      = "galhillel/eventus-api:seed"
healer_image   = "galhillel/eventus-healer:1.0.0"
mongo_password = "CHANGE_ME_BEFORE_APPLY"
```

הקובץ האמיתי, `terraform.tfvars`, נוצר מהתבנית הזו ו**אינו** ב-Git:

```bash
cd ~/projects/EventUs/infra/terraform
sed "s|galhillel|YOUR_DOCKER_USER|g; s|CHANGE_ME_BEFORE_APPLY|$(openssl rand -hex 16)|" \
  terraform.tfvars.example > terraform.tfvars
```

(`./eventus.sh up` עושה את זה לבד אם הקובץ לא קיים.)


---

### 7.6 `main.tf` — locals ו-Namespaces

```hcl
locals {
  es_url    = "http://elasticsearch.observability.svc.cluster.local:9200"
  log_index = "eventus-logs"
  mongo_uri = "mongodb://eventus:${var.mongo_password}@mongodb.eventus.svc.cluster.local:27017/EventUs?authSource=admin"
}

resource "kubernetes_namespace_v1" "app" {
  metadata { name = "eventus" }
}

resource "kubernetes_namespace_v1" "obs" {
  metadata { name = "observability" }
}

resource "kubernetes_namespace_v1" "platform" {
  metadata { name = "platform" }
}
```


שלושת ה-namespaces הם קו הפרדה: `eventus` לאפליקציה ולבסיס הנתונים, `observability` ל-ELK, `platform` לריפוי העצמי. זה לא קישוט — ה-RBAC של ה-healer ושל Jenkins נבנה סביב הגבולות האלה, וזה מה שמאפשר לתת ל-Jenkins הרשאות רק ב-`eventus` ולא באשכול כולו.

ה-`locals` הם שלושה ערכים שחוזרים במקומות רבים. `mongo_uri` נבנה כאן פעם אחת ומוזרק ל-Secret; `es_url` הוא ה-FQDN הפנימי של Elasticsearch, ו-Filebeat וה-healer שניהם משתמשים בו. ⚠️ שם ה-DNS המלא (`elasticsearch.observability.svc.cluster.local`) הכרחי כי הפונים אליו נמצאים ב-namespaces אחרים; `elasticsearch` לבד היה נפתר רק בתוך `observability`.

`?authSource=admin` בסוף ה-URI הוא הפרט שמפיל הכי הרבה אנשים: המשתמש נוצר במסד `admin`, אבל האפליקציה מתחברת למסד `EventUs`. בלי הפרמטר, mongoose מחפש את המשתמש ב-`EventUs`, לא מוצא, ומחזיר `Authentication failed`.


---

### 7.7 MongoDB — Secret, Headless Service, StatefulSet

```hcl
resource "kubernetes_secret_v1" "mongodb" {
  metadata {
    name      = "mongodb-credentials"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  data = {
    MONGO_INITDB_ROOT_USERNAME = "eventus"
    MONGO_INITDB_ROOT_PASSWORD = var.mongo_password
    MONGODB_URI                = local.mongo_uri
  }
}

resource "kubernetes_service_v1" "mongodb" {
  metadata {
    name      = "mongodb"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    cluster_ip = "None"
    selector   = { app = "mongodb" }
    port { port = 27017 }
  }
}

resource "kubernetes_stateful_set_v1" "mongodb" {
  metadata {
    name      = "mongodb"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    service_name = kubernetes_service_v1.mongodb.metadata[0].name
    replicas     = 1

    selector {
      match_labels = { app = "mongodb" }
    }

    template {
      metadata {
        labels = { app = "mongodb" }
      }

      spec {
        container {
          name  = "mongodb"
          image = "mongo:7.0"

          port { container_port = 27017 }

          env_from {
            secret_ref { name = kubernetes_secret_v1.mongodb.metadata[0].name }
          }

          volume_mount {
            name       = "data"
            mount_path = "/data/db"
          }
        }
      }
    }

    volume_claim_template {
      metadata { name = "data" }

      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = "local-path"
        resources {
          requests = { storage = "3Gi" }
        }
      }
    }
  }
}
```


**למה StatefulSet ולא Deployment:** ל-StatefulSet יש `volume_claim_template`, שיוצר PVC יציב שנשאר קשור לאותו Pod גם אחרי הפעלה מחדש. Deployment עם PVC משותף היה מאבד את הזהות הזו. בנוסף השם של ה-Pod יציב (`mongodb-0`) ולא אקראי.

**למה Service בלי `cluster_ip` (headless):** `cluster_ip = "None"` אומר ל-DNS להחזיר את ה-IP של ה-Pod עצמו במקום IP וירטואלי של load balancer. זה הדפוס הנכון למסד נתונים עם עותק אחד — אין מה לאזן.

**הסיסמה מגיעה מ-Terraform ולא מהקוד.** ה-Secret מחזיק גם את פרטי ההתחברות של Mongo וגם את מחרוזת החיבור המלאה, וה-Deployment של האפליקציה מושך ממנו `MONGODB_URI` דרך `secret_key_ref`. הסיסמה לא מופיעה באף מקום בקוד המקור.

⚠️ **הערה על אבטחה בפרויקט אמיתי:** Secret ב-Kubernetes הוא base64, לא הצפנה. בייצור היינו מוסיפים encryption-at-rest ל-etcd או Sealed Secrets. **שווה להזכיר במצגת — זה מראה שאתה יודע איפה הגבול.**


---

### 7.8 האפליקציה — ConfigMap, Deployment, Service, Ingress

```hcl
resource "kubernetes_config_map_v1" "app" {
  metadata {
    name      = "eventus-api-config"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  data = {
    SERVICE_NAME     = "eventus-api"
    CHAOS_ERROR_RATE = "0"
  }
}

resource "kubernetes_deployment_v1" "api" {
  metadata {
    name      = "eventus-api"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    replicas               = 2
    revision_history_limit = 5
    min_ready_seconds      = 5

    selector {
      match_labels = { app = "eventus-api" }
    }

    strategy {
      type = "RollingUpdate"
      rolling_update {
        max_surge       = "1"
        max_unavailable = "0"
      }
    }

    template {
      metadata {
        labels = { app = "eventus-api" }
      }

      spec {
        container {
          name  = "api"
          image = var.app_image

          port { container_port = 3000 }

          env_from {
            config_map_ref { name = kubernetes_config_map_v1.app.metadata[0].name }
          }

          env {
            name = "MONGODB_URI"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.mongodb.metadata[0].name
                key  = "MONGODB_URI"
              }
            }
          }

          env {
            name = "POD_NAME"
            value_from {
              field_ref { field_path = "metadata.name" }
            }
          }

          readiness_probe {
            http_get {
              path = "/health/ready"
              port = 3000
            }
            initial_delay_seconds = 10
            period_seconds        = 10
          }

          liveness_probe {
            http_get {
              path = "/health/live"
              port = 3000
            }
            initial_delay_seconds = 30
            period_seconds        = 15
            failure_threshold     = 6
          }

          resources {
            requests = { cpu = "100m", memory = "160Mi" }
            limits   = { cpu = "600m", memory = "384Mi" }
          }

          security_context {
            allow_privilege_escalation = false
            capabilities { drop = ["ALL"] }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      spec[0].template[0].spec[0].container[0].image,
      metadata[0].annotations,
    ]
  }

  depends_on = [kubernetes_stateful_set_v1.mongodb]
}

resource "kubernetes_service_v1" "api" {
  metadata {
    name      = "eventus-api"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    selector = { app = "eventus-api" }
    port {
      port        = 3000
      target_port = 3000
    }
  }
}

resource "kubernetes_ingress_v1" "api" {
  metadata {
    name      = "eventus-api"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  spec {
    ingress_class_name = "traefik"

    rule {
      http {
        path {
          path      = "/"
          path_type = "Prefix"

          backend {
            service {
              name = kubernetes_service_v1.api.metadata[0].name
              port { number = 3000 }
            }
          }
        }
      }
    }
  }
}
```


זהו המשאב המרכזי בפרויקט. ארבע נקודות שחייבים להבין:

**1. `lifecycle { ignore_changes }` — ההפרדה בין IaC ל-CD.**
Terraform יוצר את ה-Deployment עם `var.app_image`. מהרגע הזה, Jenkins הוא הבעלים של שדה ה-image ו-Terraform מתעלם ממנו לחלוטין. גם `metadata[0].annotations` ברשימה, כי `kubectl rollout` כותב שם `kubernetes.io/change-cause` ו-`deployment.kubernetes.io/revision`. בלי שתי השורות האלה, כל `terraform apply` היה מחזיר את הגרסה הישנה ומוחק את הפריסה האחרונה. **זו הנקודה שהמרצה ישאל עליה.**

**2. `revision_history_limit = 5` — בלעדיו אין ריפוי עצמי.**
`kubectl rollout undo` עובד על ההיסטוריה שנשמרת ב-ReplicaSets. אם המספר הזה קטן מדי, ההיסטוריה נמחקת וה-undo נכשל בשקט. חמש גרסאות זה מספיק לדמו ולא מבזבז etcd.

**3. `max_unavailable = "0"` — אפס זמן השבתה.**
בזמן עדכון, Kubernetes מרים Pod חדש (`max_surge = 1`) **לפני** שהוא מוריד ישן. בשילוב עם `min_ready_seconds = 5` וה-`readinessProbe`, ה-Pod החדש חייב להיות מוכן חמש שניות רצופות לפני שהישן יורד. זה מה שמאפשר להראות בדמו שהתעבורה לא נקטעת בזמן deploy.

**4. שתי ה-probes בודקות דברים שונים בכוונה.**
`livenessProbe` על `/health/live` — לא נוגע ב-Mongo. אם ה-DB נופל, אנחנו **לא** רוצים ש-Kubernetes יתחיל להרוג Pods תקינים.
`readinessProbe` על `/health/ready` — כן בודק Mongo. אם ה-DB לא זמין, ה-Pod יוצא מה-Service אבל נשאר חי ומנסה להתחבר מחדש.
`failure_threshold = 6` על ה-liveness נותן 90 שניות לפני הרג — מרווח סביר תחת עומס.

**ה-Ingress בלי `host`.** כלל ה-catch-all (`path = "/"` עם `path_type = "Prefix"` ובלי host) אומר ל-Traefik לשלוח לכאן כל בקשה שלא הותאמה לכלל ספציפי יותר. זה מה שמאפשר לאמולטור של אנדרואיד לפנות ל-`http://10.0.2.2/` בלי להגדיר DNS.

⚠️ **`POD_NAME` דרך `field_ref`** — Downward API. ה-Pod מקבל את שמו כמשתנה סביבה, והוא מופיע ב-`/chaos/status`. בדמו זו הדרך להראות שהתעבורה מתחלקת בין שני Pods.

⚠️ **`nginx` לא נמצא כאן, ובכוונה.** Traefik מגיע מובנה עם K3s ועושה בדיוק את אותה עבודה. הוספת nginx-ingress הייתה יוצרת שני Ingress Controllers שמתחרים על פורט 80. **זו הייתה ההערה של המרצה, והיא מיושמת.**


---

### 7.9 Elasticsearch — Service ו-StatefulSet

```hcl
resource "kubernetes_service_v1" "elasticsearch" {
  metadata {
    name      = "elasticsearch"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    selector = { app = "elasticsearch" }
    port {
      port        = 9200
      target_port = 9200
    }
  }
}

resource "kubernetes_stateful_set_v1" "elasticsearch" {
  metadata {
    name      = "elasticsearch"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    service_name = kubernetes_service_v1.elasticsearch.metadata[0].name
    replicas     = 1

    selector {
      match_labels = { app = "elasticsearch" }
    }

    template {
      metadata {
        labels = { app = "elasticsearch" }
      }

      spec {
        security_context {
          fs_group = 1000
        }

        init_container {
          name    = "fix-permissions"
          image   = "busybox:1.36"
          command = ["sh", "-c", "chown -R 1000:1000 /usr/share/elasticsearch/data"]

          security_context {
            run_as_user = 0
          }

          volume_mount {
            name       = "data"
            mount_path = "/usr/share/elasticsearch/data"
          }
        }

        container {
          name  = "elasticsearch"
          image = "docker.elastic.co/elasticsearch/elasticsearch:${var.elastic_version}"

          port { container_port = 9200 }

          env {
            name  = "discovery.type"
            value = "single-node"
          }

          env {
            name  = "xpack.security.enabled"
            value = "false"
          }

          env {
            name  = "xpack.ml.enabled"
            value = "false"
          }

          env {
            name  = "ES_JAVA_OPTS"
            value = "-Xms1g -Xmx1g"
          }

          volume_mount {
            name       = "data"
            mount_path = "/usr/share/elasticsearch/data"
          }

          resources {
            requests = {
              memory = "1536Mi"
            }
            limits = {
              memory = "2560Mi"
            }
          }

          readiness_probe {
            http_get {
              path = "/_cluster/health?local=true"
              port = 9200
            }
            initial_delay_seconds = 30
            period_seconds        = 10
            failure_threshold     = 20
          }
        }
      }
    }

    volume_claim_template {
      metadata { name = "data" }

      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = "local-path"
        resources {
          requests = { storage = "5Gi" }
        }
      }
    }
  }
}
```


**`discovery.type: single-node`** מכבה את מנגנון בחירת ה-master. בלעדיו, ES מחכה לצומת נוסף ולא עולה.

**`xpack.security.enabled: false`** — בגרסה 8 האבטחה דלוקה כברירת מחדל ודורשת אישורי TLS. באשכול פנימי לפרויקט לימודי זה שלושים דקות של עבודה בלי תמורה. ⚠️ **תגידו את זה במפורש במצגת: "השבתי אבטחה כי הכל פנימי; בייצור זה לא היה קורה."** להגיד את זה בעצמכם עדיף מאשר שישאלו.

**`ES_JAVA_OPTS: -Xms1g -Xmx1g`** — heap קבוע. Elasticsearch דורש ש-min ו-max יהיו זהים; אחרת ה-JVM מזיז את ה-heap בזמן ריצה וזה פוגע בביצועים. הגבול של הקונטיינר גדול יותר מה-heap כי ES צריך גם זיכרון off-heap.

⚠️ **ה-init container `fix-permissions` הכרחי.** ה-PVC של `local-path` נוצר בבעלות `root:root`, בעוד תמונת Elasticsearch רצה כמשתמש `1000`. בלי `chown`, ES נכשל ב-`AccessDeniedException` על תיקיית הנתונים ונכנס ל-`CrashLoopBackOff`. `fs_group = 1000` ב-`security_context` של ה-Pod עוזר, אבל לא כל ה-provisioners מכבדים אותו — ה-init container הוא הפתרון שעובד תמיד.


---

### 7.10 Kibana — Service, Deployment, Ingress

```hcl
resource "kubernetes_service_v1" "kibana" {
  metadata {
    name      = "kibana"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    selector = { app = "kibana" }
    port {
      port        = 5601
      target_port = 5601
    }
  }
}

resource "kubernetes_deployment_v1" "kibana" {
  metadata {
    name      = "kibana"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    replicas = 1

    selector {
      match_labels = { app = "kibana" }
    }

    template {
      metadata {
        labels = { app = "kibana" }
      }

      spec {
        container {
          name  = "kibana"
          image = "docker.elastic.co/kibana/kibana:${var.elastic_version}"

          port { container_port = 5601 }

          env {
            name  = "ELASTICSEARCH_HOSTS"
            value = local.es_url
          }

          env {
            name  = "SERVER_PUBLICBASEURL"
            value = "http://kibana.local"
          }

          resources {
            requests = {
              memory = "512Mi"
            }
            limits = {
              memory = "1280Mi"
            }
          }

          readiness_probe {
            http_get {
              path = "/api/status"
              port = 5601
            }
            initial_delay_seconds = 40
            period_seconds        = 10
            failure_threshold     = 30
          }
        }
      }
    }
  }

  depends_on = [kubernetes_stateful_set_v1.elasticsearch]
}

resource "kubernetes_ingress_v1" "kibana" {
  metadata {
    name      = "kibana"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    ingress_class_name = "traefik"

    rule {
      host = "kibana.local"

      http {
        path {
          path      = "/"
          path_type = "Prefix"

          backend {
            service {
              name = kubernetes_service_v1.kibana.metadata[0].name
              port { number = 5601 }
            }
          }
        }
      }
    }
  }
}
```


`ELASTICSEARCH_HOSTS` מצביע ל-`local.es_url`, כלומר ל-FQDN המלא בין namespaces.

ה-Ingress כאן **כן** מוגדר עם host (`kibana.local`), בניגוד לזה של האפליקציה. ההבדל חשוב: Traefik בודק קודם כללים עם host, ורק אחר כך את ה-catch-all. כך `http://kibana.local/` מגיע לקיבנה וכל השאר מגיע לאפליקציה. צריך שורה ב-`C:\Windows\System32\drivers\etc\hosts`:

```
127.0.0.1 kibana.local
```

⚠️ קיבנה איטית לעלות — 60 עד 120 שניות. `initial_delay_seconds` על ה-probes מביא את זה בחשבון; אל תבהלו מ-`0/1 Running` בדקה הראשונה.


---

### 7.11 Filebeat — SA, ClusterRole, ConfigMap, DaemonSet

```hcl
locals {
  filebeat_config = yamlencode({
    "filebeat.inputs" = [{
      type                          = "filestream"
      id                            = "eventus-api"
      "prospector.scanner.symlinks" = true
      paths                         = ["/var/log/containers/eventus-api-*.log"]
      parsers = [
        { container = { stream = "all", format = "auto" } },
        { ndjson = { target = "", overwrite_keys = true, add_error_key = true } },
      ]
    }]

    "output.elasticsearch" = {
      hosts = [local.es_url]
      index = "${local.log_index}-%%{+yyyy.MM.dd}"
    }

    "setup.template.name"    = local.log_index
    "setup.template.pattern" = "${local.log_index}-*"
    "setup.ilm.enabled"      = false
  })
}

resource "kubernetes_service_account_v1" "filebeat" {
  metadata {
    name      = "filebeat"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }
}

resource "kubernetes_cluster_role_v1" "filebeat" {
  metadata { name = "filebeat" }

  rule {
    api_groups = [""]
    resources  = ["namespaces", "pods", "nodes"]
    verbs      = ["get", "list", "watch"]
  }
}

resource "kubernetes_cluster_role_binding_v1" "filebeat" {
  metadata { name = "filebeat" }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role_v1.filebeat.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.filebeat.metadata[0].name
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }
}

resource "kubernetes_config_map_v1" "filebeat" {
  metadata {
    name      = "filebeat-config"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  data = {
    "filebeat.yml" = local.filebeat_config
  }
}

resource "kubernetes_daemon_set_v1" "filebeat" {
  metadata {
    name      = "filebeat"
    namespace = kubernetes_namespace_v1.obs.metadata[0].name
  }

  spec {
    selector {
      match_labels = { app = "filebeat" }
    }

    template {
      metadata {
        labels      = { app = "filebeat" }
        annotations = { "checksum/config" = sha256(local.filebeat_config) }
      }

      spec {
        service_account_name = kubernetes_service_account_v1.filebeat.metadata[0].name

        container {
          name  = "filebeat"
          image = "docker.elastic.co/beats/filebeat:${var.elastic_version}"
          args  = ["-c", "/etc/filebeat.yml", "-e"]

          security_context {
            run_as_user = 0
          }

          volume_mount {
            name       = "config"
            mount_path = "/etc/filebeat.yml"
            sub_path   = "filebeat.yml"
            read_only  = true
          }

          volume_mount {
            name       = "varlogcontainers"
            mount_path = "/var/log/containers"
            read_only  = true
          }

          volume_mount {
            name       = "varlogpods"
            mount_path = "/var/log/pods"
            read_only  = true
          }

          volume_mount {
            name       = "data"
            mount_path = "/usr/share/filebeat/data"
          }
        }

        volume {
          name = "config"
          config_map { name = kubernetes_config_map_v1.filebeat.metadata[0].name }
        }

        volume {
          name = "varlogcontainers"
          host_path { path = "/var/log/containers" }
        }

        volume {
          name = "varlogpods"
          host_path { path = "/var/log/pods" }
        }

        volume {
          name = "data"
          host_path {
            path = "/var/lib/filebeat-data"
            type = "DirectoryOrCreate"
          }
        }
      }
    }
  }

  depends_on = [kubernetes_stateful_set_v1.elasticsearch]
}
```


**למה DaemonSet:** Filebeat צריך לקרוא את קבצי הלוג של **כל** הקונטיינרים על הצומת. DaemonSet מבטיח עותק אחד לכל צומת, וב-hostPath הוא מגיע ל-`/var/log/containers`.

**למה `filestream` ולא `container`:** ה-input מסוג `container` הוצא משימוש ב-Filebeat 8. `filestream` עם שני parsers ברצף — `container` שמפרק את פורמט ה-CRI ו-`ndjson` שמפרק את מה שהאפליקציה כתבה — נותן את אותה תוצאה בדרך הנתמכת.

⚠️ **`prospector.scanner.symlinks: true` — בלעדיו לא נכנס שום דבר.** הקבצים ב-`/var/log/containers/` הם קישורים סימבוליים ל-`/var/log/pods/`. Filebeat, כברירת מחדל, מסרב לעקוב אחרי symlinks. התוצאה היא Pod שרץ, לוגים נקיים, ואפס מסמכים ב-Elasticsearch. **זו התקלה הכי מתסכלת בפרויקט הזה, וזו שורה אחת.**

⚠️ **`%%{` בתבנית האינדקס.** בקוד רואים `index = "${local.log_index}-%%{+yyyy.MM.dd}"`. הרצף `%{` הוא תחביר של תבניות ב-Terraform (directive של `if`/`for`), ולכן `terraform validate` נכשל עליו עם `Invalid template control keyword`. הכפלת הסימן, `%%{`, מייצרת `%{` ספרותי בפלט — וזה מה ש-Filebeat מצפה לו. **פרט קטן שעולה חצי שעה אם לא יודעים.**

**`add_kubernetes_metadata`** מוסיף לכל מסמך את `kubernetes.pod.name`, `kubernetes.namespace` ו-`kubernetes.labels`. זה מה שמאפשר לסנן בקיבנה לפי Pod, וזה גם מה שדורש את ה-ClusterRole לקריאת `pods` ו-`namespaces`.

**`drop_event` על namespaces שאינם `eventus`** — מונע מהלוגים של ELK עצמו להציף את האינדקס. בלעדיו, קיבנה כותבת לוגים, Filebeat אוסף אותם, ES מאנדקס אותם, וקיבנה כותבת עוד — לולאה שממלאת את הדיסק.


---

### 7.12 הריפוי העצמי — SA, Role, RoleBinding, ConfigMap, CronJob

```hcl
resource "kubernetes_service_account_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }
}

resource "kubernetes_role_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments"]
    verbs      = ["get", "list", "patch", "update"]
  }

  rule {
    api_groups = ["apps"]
    resources  = ["replicasets"]
    verbs      = ["get", "list"]
  }

  rule {
    api_groups = [""]
    resources  = ["events"]
    verbs      = ["create"]
  }
}

resource "kubernetes_role_binding_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role_v1.healer.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.healer.metadata[0].name
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }
}

resource "kubernetes_config_map_v1" "healer" {
  metadata {
    name      = "eventus-healer-config"
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }

  data = {
    ES_URL           = local.es_url
    ES_INDEX         = "${local.log_index}-*"
    TARGET_DEPLOY    = kubernetes_deployment_v1.api.metadata[0].name
    ERROR_THRESHOLD  = "10"
    MIN_ERROR_RATIO  = "0.25"
    WINDOW_MINUTES   = "5"
    COOLDOWN_MINUTES = "10"
  }
}

resource "kubernetes_cron_job_v1" "healer" {
  metadata {
    name      = "eventus-healer"
    namespace = kubernetes_namespace_v1.platform.metadata[0].name
  }

  spec {
    schedule                      = "* * * * *"
    concurrency_policy            = "Forbid"
    successful_jobs_history_limit = 3
    failed_jobs_history_limit     = 3

    job_template {
      metadata {}

      spec {
        backoff_limit              = 0
        active_deadline_seconds    = 50
        ttl_seconds_after_finished = 300

        template {
          metadata {}

          spec {
            service_account_name = kubernetes_service_account_v1.healer.metadata[0].name
            restart_policy       = "Never"

            container {
              name  = "healer"
              image = var.healer_image

              env_from {
                config_map_ref { name = kubernetes_config_map_v1.healer.metadata[0].name }
              }
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_role_binding_v1.healer]
}
```


**`schedule = "* * * * *"`** — כל דקה. בייצור זה היה נראה אחרת (Prometheus + Alertmanager), אבל CronJob הוא הכלי הנכון להדגמה: פשוט, קריא, ואפשר להסביר אותו במשפט.

**`concurrency_policy = "Forbid"`** — אם הרצה קודמת עדיין רצה, החדשה מדולגת. בלי זה, שני healers היו יכולים לבצע `rollout undo` בו-זמנית ולגלגל שתי גרסאות אחורה במקום אחת.

**`successful_jobs_history_limit` / `failed_jobs_history_limit`** — בלעדיהם ה-namespace מתמלא במאות Jobs שהסתיימו, אחד לכל דקה.

**ה-RBAC כאן הוא הנקודה המעניינת.** ה-CronJob רץ ב-`platform`, אבל צריך לגעת ב-Deployment שנמצא ב-`eventus`. הכלל: **ה-RoleBinding חי היכן שהמשאב נמצא, ה-subject יכול להיות ב-namespace אחר.** לכן ה-Role וה-RoleBinding מוגדרים ב-`eventus`, ומצביעים על ServiceAccount שנמצא ב-`platform`.

⚠️ **הפעלים על `replicasets` הכרחיים.** `kubectl rollout undo` לא עובד על ה-Deployment בלבד — הוא קורא את ה-ReplicaSets כדי למצוא את הגרסה הקודמת ואז מעדכן את ה-Deployment. בלי `get`/`list` על `replicasets`, הפקודה נכשלת עם `forbidden` שקשה לפענח.

⚠️ **`patch` על `deployments` נדרש** לכתיבת ה-annotation של הקירור (`eventus.io/last-rollback`).

ה-ConfigMap מכיל את `healer.py` ואת `rollback.sh` כדי שאפשר יהיה לעדכן את הלוגיקה בלי לבנות תמונה מחדש — התמונה מכילה רק את Python ואת kubectl.


---

### 7.13 ה-RBAC של Jenkins

```hcl
resource "kubernetes_service_account_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }
}

resource "kubernetes_secret_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer-token"
    namespace = kubernetes_namespace_v1.app.metadata[0].name

    annotations = {
      "kubernetes.io/service-account.name" = kubernetes_service_account_v1.jenkins.metadata[0].name
    }
  }

  type                           = "kubernetes.io/service-account-token"
  wait_for_service_account_token = true
}

resource "kubernetes_role_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "deployments/status", "replicasets"]
    verbs      = ["get", "list", "patch", "update"]
  }

  rule {
    api_groups = [""]
    resources  = ["pods", "pods/log"]
    verbs      = ["get", "list"]
  }
}

resource "kubernetes_role_binding_v1" "jenkins" {
  metadata {
    name      = "jenkins-deployer"
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role_v1.jenkins.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.jenkins.metadata[0].name
    namespace = kubernetes_namespace_v1.app.metadata[0].name
  }
}
```


**מדוע ServiceAccount ייעודי ולא ה-kubeconfig של המנהל:** ה-kubeconfig ש-K3s יוצר הוא `cluster-admin` — מי שמשיג אותו שולט בכל האשכול. Jenkins צריך בדיוק שלוש יכולות: לעדכן את ה-image של ה-Deployment, לחכות לסיום הפריסה, ולגלגל אחורה אם נכשלה. **זה עיקרון ה-least privilege בפועל, וזו נקודה טובה למצגת.**

⚠️ **`wait_for_service_account_token = true` על ה-Secret.** מ-Kubernetes 1.24, ServiceAccount כבר לא מקבל token אוטומטית — צריך ליצור Secret מסוג `kubernetes.io/service-account-token` במפורש. הדגל אומר ל-Terraform לחכות עד שה-controller ימלא את הטוקן; בלעדיו, `terraform apply` מסתיים בהצלחה עם Secret ריק, והסקריפט שבונה את ה-kubeconfig מייצר קובץ עם טוקן ריק שנכשל ב-401.

הפעלים כאן זהים לאלה של ה-healer, מאותה סיבה: גם Jenkins עושה `rollout undo` כשה-smoke test נכשל.


---

### 7.14 Outputs

```hcl
output "api_url" {
  value = "http://localhost/"
}

output "kibana_url" {
  value = "http://kibana.local/"
}

output "log_index" {
  value = "${local.log_index}-*"
}
```


שלושה outputs. בגרסה הקודמת היו שמונה, וששה מהם החזירו מחרוזות שכבר ידעתי. אלה שנשארו הם אלה שבאמת מודפסים בסוף `apply` ומשמשים את `eventus.sh`.
---

## 8. Jenkins — צינור ה-CI/CD

### 8.1 הארכיטקטורה של הצינור

```mermaid
sequenceDiagram
    participant Dev as git push
    participant GH as GitHub
    participant J as Jenkins (Docker, WSL)
    participant DH as Docker Hub
    participant K as K3s API

    Dev->>GH: push ל-devops-project
    J->>GH: pollSCM כל 2 דקות
    GH-->>J: יש קומיט חדש (SHA=a1b2c3d)
    J->>J: docker build --build-arg APP_VERSION=a1b2c3d
    J->>DH: docker push eventus-api:a1b2c3d
    J->>K: kubectl set image deployment/eventus-api api=...:a1b2c3d
    K->>K: RollingUpdate — Pod חדש, ואז מוריד ישן
    J->>K: kubectl rollout status --timeout=180s
    J->>J: smoke test מול http://localhost/health/ready
    Note over J: Build ✅
```

**למה `pollSCM` ולא Webhook:** Webhook דורש ש-GitHub יגיע ל-Jenkins שלכם — כלומר ngrok, port forwarding או IP ציבורי. Polling כל 2 דקות עובד מאחורי NAT בלי כלום. **בדמו זה מספיק לחלוטין**, ואפשר תמיד ללחוץ "Build Now". אם אתם רוצים נקודות בונוס — הריצו `ngrok http 8080` והגדירו webhook; זה מוסבר בסעיף 8.9.

### 8.2 תמונת Jenkins מותאמת

Jenkins הרשמי לא מכיל `docker` ולא `kubectl`. במקום להתקין אותם ידנית בקונטיינר רץ (שייעלם ב-restart), בונים תמונה.

**קובץ חדש:** `infra/jenkins/Dockerfile`

```dockerfile
FROM jenkins/jenkins:lts-jdk17

USER root

ARG KUBECTL_VERSION=v1.33.13

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg jq \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && chmod a+r /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSLo /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
 && chmod 0755 /usr/local/bin/kubectl

USER jenkins

RUN jenkins-plugin-cli --plugins \
      workflow-aggregator \
      git \
      docker-workflow \
      credentials-binding \
      pipeline-stage-view \
      timestamper \
      ws-cleanup \
      build-timeout
```

הבנייה וההרצה הן תת-פקודה של `eventus.sh`, לא סקריפט נפרד:

```bash
cd ~/projects/EventUs
./eventus.sh jenkins
```

הפקודה בונה את התמונה, יוצרת volume, מרימה את הקונטיינר ומחכה עד שהסיסמה הראשונית נוצרת (עד 150 שניות) — ואז מדפיסה אותה. זה הגוף שלה:

```bash
cmd_jenkins() {
  local gid
  gid=$(getent group docker 2>/dev/null | cut -d: -f3 || true)
  [ -n "${gid}" ] || { echo "no docker group, run ./eventus.sh up first" >&2; exit 1; }

  docker build -t eventus-jenkins:1.0 "${ROOT}/infra/jenkins"
  docker volume create jenkins_home >/dev/null
  docker rm -f jenkins >/dev/null 2>&1 || true
  docker run -d --name jenkins --restart unless-stopped --network host --group-add "${gid}" \
    -v jenkins_home:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock \
    -e JAVA_OPTS=-Xmx1g eventus-jenkins:1.0

  echo "jenkins on http://localhost:8080, waiting for the admin password"
  for _ in $(seq 1 30); do
    if docker exec jenkins test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null; then
      docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
      return 0
    fi
    sleep 5
  done
  echo "timed out, check: docker logs jenkins" >&2
}
```

**החלטות:**

| החלטה | נימוק |
|---|---|
| `--network host` | ⚠️ **הנקודה המרכזית.** ה-kubeconfig של K3s מצביע על `https://127.0.0.1:6443`. בתוך קונטיינר עם רשת bridge, `127.0.0.1` הוא הקונטיינר עצמו. עם `--network host` הוא ה-WSL — וגם ה-smoke test מול `http://localhost/` עובד ישר. |
| `--group-add ${gid}` | ה-socket `/var/run/docker.sock` בבעלות `root:docker`. הוספת ה-GID של הקבוצה מהמארח נותנת ל-jenkins הרשאה בלי `--privileged` ובלי להריץ כ-root. |
| `-v jenkins_home:...` | Volume בשם. Jenkins שורד `docker rm`, `wsl --shutdown` ואתחול מחשב. **בלי זה תאבדו את כל ההגדרות אחרי כל reboot.** |
| `--restart unless-stopped` | Jenkins עולה לבד כשה-Docker daemon עולה. |
| `-Xmx1g` | תקרה ל-JVM. בלי זה Jenkins יכול לתפוס 4GB ולהרעיב את Elasticsearch. |
| `\|\| true` בתוך ה-`$( )` | ⚠️ הסקריפט רץ עם `set -euo pipefail`. אם קבוצת `docker` לא קיימת, `getent` מחזיר קוד יציאה 2 והסקריפט **מת בשקט לפני** שהבדיקה בשורה הבאה מספיקה לרוץ. ה-`|| true` מבטל את הכשל ומאפשר להדפיס הודעה מובנת. פרט קטן שגורם לדקות של בלבול. |
| לולאת המתנה במקום `sleep 40` | בפעם הראשונה Jenkins יכול לקחת יותר מ-40 שניות. הלולאה בודקת אם הקובץ קיים ומדפיסה ברגע שהוא שם. |

⚠️ **`docker.sock` = הרשאות root על המארח בפועל.** מי שיכול לדבר עם ה-socket יכול להריץ קונטיינר עם `-v /:/host`. **הזכירו את זה במצגת** ואת החלופה בסביבת ייצור: Kaniko או BuildKit rootless, שבונים תמונות בלי גישה ל-daemon.

### 8.3 אשף ההתקנה

1. פתחו `http://localhost:8080`.
2. הדביקו את הסיסמה שהסקריפט הדפיס.
3. בחרו **Select plugins to install** → **None** → Install. (התוספים כבר מובנים בתמונה.)
4. צרו משתמש אדמין. **שמרו את הפרטים.**
5. Jenkins URL: השאירו `http://localhost:8080/`.

### 8.4 ServiceAccount ייעודי ל-Jenkins

⚠️ **אל תתנו ל-Jenkins את ה-kubeconfig של האדמין.** הוא נותן שליטה מלאה על כל האשכול. במקום זה — ServiceAccount עם הרשאות מדויקות.

ארבעת המשאבים — `kubernetes_service_account_v1.jenkins`, `kubernetes_secret_v1.jenkins`, `kubernetes_role_v1.jenkins` ו-`kubernetes_role_binding_v1.jenkins` — כבר כתובים ב-`infra/terraform/main.tf` והוחלו ב-`terraform apply` של חלק 7. ראו את הקוד וההסבר המלא ב**סעיף 7.13**.

מה שחשוב לזכור כאן:

| פועל | על מה | למה |
|---|---|---|
| `get`, `list`, `watch`, `patch`, `update` | `deployments`, `deployments/status` | לעדכן את התג ולחכות שהפריסה תסתיים |
| `get`, `list` | `replicasets` | `rollout undo` קורא את ההיסטוריה מכאן |
| `get`, `list` | `pods`, `pods/log` | לאבחן פריסה שנכשלה |

⚠️ **אין `delete` ואין גישה ל-`secrets`.** Jenkins לא יכול למחוק את ה-Deployment, לא לקרוא את סיסמת Mongo, ולא לצאת מ-namespace `eventus`. הרשאה מזערית — וזה מה שנבדק בסעיף הבא.

אימות שהמשאבים קיימים:

```bash
kubectl -n eventus get sa jenkins-deployer
kubectl -n eventus get secret jenkins-deployer-token
kubectl -n eventus get role,rolebinding | grep jenkins
```
### 8.5 יצירת ה-kubeconfig של Jenkins

אחרי ש-Terraform יצר את ה-ServiceAccount ואת ה-Secret, צריך להפוך אותם לקובץ kubeconfig ש-Jenkins יכול לטעון. זו תת-פקודה נוספת:

```bash
cd ~/projects/EventUs
./eventus.sh kubeconfig
```

הגוף שלה:

```bash
cmd_kubeconfig() {
  local out token ca
  out="${ROOT}/infra/jenkins/kubeconfig-jenkins.yaml"
  token=$(kubectl -n "${NS}" get secret jenkins-deployer-token -o jsonpath='{.data.token}' | base64 -d)
  ca=$(kubectl -n "${NS}" get secret jenkins-deployer-token -o jsonpath='{.data.ca\.crt}')
  [ -n "${token}" ] || { echo "token is empty, wait a few seconds and retry" >&2; exit 1; }

  cat > "${out}" <<EOF
apiVersion: v1
kind: Config
clusters: [{name: eventus, cluster: {server: "https://127.0.0.1:6443", certificate-authority-data: ${ca}}}]
contexts: [{name: jenkins, context: {cluster: eventus, namespace: ${NS}, user: jenkins}}]
current-context: jenkins
users: [{name: jenkins, user: {token: ${token}}}]
EOF
  chmod 600 "${out}"
  echo "wrote ${out}"
  for verb in "patch deployments" "delete deployments" "get secrets"; do
    printf '  %-20s %s\n' "${verb}" "$(KUBECONFIG=${out} kubectl auth can-i ${verb} -n ${NS})"
  done
}
```

**פלט צפוי:**

```
wrote /home/gal/projects/EventUs/infra/jenkins/kubeconfig-jenkins.yaml
  patch deployments    yes
  delete deployments   no
  get secrets          no
```

⚠️ **שלוש השורות האחרונות הן ההוכחה ל-least privilege, ולכן הן חלק מהסקריפט ולא בדיקה חד-פעמית.** `yes` על `patch deployments` = Jenkins יכול לפרוס. `no` על `delete deployments` ועל `get secrets` = הוא לא יכול למחוק את הפריסה ולא לקרוא את סיסמת Mongo. **הראו את הפלט הזה במצגת — זו התשובה לשאלה "איך הגנת על האשכול".**

⚠️ ה-`ca` **לא** עובר `base64 -d`. ב-kubeconfig, השדה `certificate-authority-data` מצפה לערך מקודד base64 — כלומר בדיוק מה שיוצא מה-Secret. הטוקן, לעומת זאת, כן מפוענח כי `users[].user.token` מצפה למחרוזת גולמית. לפענח את שניהם או אף אחד מהם — שתי טעויות נפוצות שגורמות ל-`x509: certificate signed by unknown authority` או ל-401.

⚠️ הקובץ מקבל `chmod 600` והוא נמצא ב-`.gitignore`. הוא מכיל טוקן חי.
### 8.6 Credentials ב-Jenkins

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

**קרדנציאל 1 — Docker Hub:**

| שדה | ערך |
|---|---|
| Kind | Username with password |
| Username | שם המשתמש שלכם ב-Docker Hub |
| Password | **Access Token**, לא הסיסמה |
| ID | `dockerhub` |

⚠️ **צרו Access Token, לא סיסמה.** Docker Hub → Account Settings → Personal access tokens → Generate. הרשאה: `Read & Write`. אפשר לבטל אותו בלי לשנות סיסמה.

**קרדנציאל 2 — kubeconfig:**

| שדה | ערך |
|---|---|
| Kind | Secret file |
| File | העלו את `infra/jenkins/kubeconfig-jenkins.yaml` |
| ID | `kubeconfig-jenkins` |

⚠️ ה-ID חייב להיות **בדיוק** `dockerhub` ו-`kubeconfig-jenkins` — ה-Jenkinsfile מפנה אליהם בשם.

### 8.7 ה-`Jenkinsfile`

**קובץ חדש בשורש הריפו:** `Jenkinsfile`

```groovy
pipeline {
  agent any

  environment {
    DOCKER_HUB_USER = 'galhillel'
    IMAGE_NAME      = "${DOCKER_HUB_USER}/eventus-api"
    APP_DIR         = 'backend/event-us'
    K8S_NAMESPACE   = 'eventus'
    DEPLOYMENT      = 'eventus-api'
    CONTAINER       = 'api'
    INGRESS_URL     = 'http://localhost'
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 25, unit: 'MINUTES')
  }

  triggers {
    pollSCM('H/2 * * * *')
  }

  stages {

    stage('Checkout') {
      steps {
        checkout scm
        script {
          env.GIT_SHA  = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
          env.IMAGE_TAG = "${env.GIT_SHA}-${env.BUILD_NUMBER}"
          env.FULL_IMAGE = "${env.IMAGE_NAME}:${env.IMAGE_TAG}"
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.GIT_SHA}"
        }
        sh 'git --no-pager log -1 --pretty=format:"%h %an %s" && echo'
      }
    }

    stage('Build image') {
      steps {
        dir("${APP_DIR}") {
          sh """
            docker build \
              --build-arg APP_VERSION=${IMAGE_TAG} \
              -t ${FULL_IMAGE} \
              -t ${IMAGE_NAME}:latest \
              .
          """
        }
        sh "docker image inspect ${FULL_IMAGE} --format 'built {{.Id}} size={{.Size}}'"
      }
    }

    stage('Push image') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'dockerhub',
          usernameVariable: 'DH_USER',
          passwordVariable: 'DH_PASS'
        )]) {
          sh """
            echo "\$DH_PASS" | docker login -u "\$DH_USER" --password-stdin
            docker push ${FULL_IMAGE}
            docker logout
          """
        }
      }
    }

    stage('Deploy to K3s') {
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-jenkins', variable: 'KUBECONFIG')]) {
          sh """
            kubectl -n ${K8S_NAMESPACE} set image \
              deployment/${DEPLOYMENT} ${CONTAINER}=${FULL_IMAGE}

            kubectl -n ${K8S_NAMESPACE} annotate deployment/${DEPLOYMENT} \
              kubernetes.io/change-cause="build ${BUILD_NUMBER} commit ${GIT_SHA}" \
              --overwrite

            kubectl -n ${K8S_NAMESPACE} rollout status \
              deployment/${DEPLOYMENT} --timeout=240s
          """
        }
      }
    }

    stage('Smoke test') {
      steps {
        script {
          def ok = false
          for (int i = 0; i < 12; i++) {
            def code = sh(
              script: "curl -s -o /dev/null -w '%{http_code}' ${INGRESS_URL}/health/ready || true",
              returnStdout: true
            ).trim()
            echo "attempt ${i + 1}: /health/ready -> ${code}"
            if (code == '200') { ok = true; break }
            sleep 5
          }
          if (!ok) {
            error 'Smoke test failed: /health/ready never returned 200'
          }
        }
        sh "curl -s ${INGRESS_URL}/chaos/status && echo"
      }
    }

    stage('Report') {
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-jenkins', variable: 'KUBECONFIG')]) {
          sh """
            echo '--- pods ---'
            kubectl -n ${K8S_NAMESPACE} get pods -l app=${DEPLOYMENT} -o wide
            echo '--- rollout history ---'
            kubectl -n ${K8S_NAMESPACE} rollout history deployment/${DEPLOYMENT}
          """
        }
      }
    }
  }

  post {
    success {
      echo "Deployed ${FULL_IMAGE} to ${K8S_NAMESPACE}"
    }
    failure {
      echo "Build failed. The cluster is still running the previous version."
    }
    always {
      sh "docker image rm ${FULL_IMAGE} ${IMAGE_NAME}:latest 2>/dev/null || true"
      cleanWs()
    }
  }
}
```

**החלטות ב-Jenkinsfile:**

| החלטה | נימוק |
|---|---|
| `IMAGE_TAG = "${GIT_SHA}-${BUILD_NUMBER}"` | ⚠️ **התג הכי חשוב במסמך.** ה-SHA מקשר את התמונה לקוד המדויק; מספר הבנייה מבטיח ייחודיות גם אם בונים את אותו קומיט פעמיים. **בלי תג ייחודי — `rollout undo` לא באמת מחליף תמונה וכל הדמו נופל.** |
| `docker logout` אחרי הדחיפה | לא משאירים credentials ב-`~/.docker/config.json` של ה-agent. |
| `--password-stdin` | הסיסמה לא נכנסת לרשימת התהליכים ולא ללוג. |
| `annotate ... change-cause` | ⚠️ **הופך את `kubectl rollout history` לקריא.** אחרת כל השורות מציגות `<none>` ואי אפשר לדעת מה כל revision. בדמו — זו הטבלה שמראים למרצה. |
| `rollout status --timeout=240s` | אם ה-Pods לא מגיעים ל-Ready, Jenkins נכשל **וה-Deployment הישן ממשיך לרוץ**. `maxUnavailable: 0` מבטיח את זה. תקלת פריסה לא מפילה ייצור. |
| שלב Smoke test עם לולאה | בודק דרך ה-Ingress — כלומר את כל השרשרת: Traefik → Service → Pod → Mongo. |
| `disableConcurrentBuilds()` | שתי בניות במקביל שדוחפות תגים שונים לאותו Deployment = מרוץ. |
| `cleanWs()` + מחיקת התמונה | ⚠️ WSL עם 10GB — תמונות Docker ישנות ממלאות את הדיסק תוך ימים. הניקוי כאן הוא הכרחי. |

⚠️ **`IMAGE_NAME:latest` נבנה אבל לא נדחף.** בכוונה — Kubernetes אף פעם לא מושך `latest` כאן. הוא קיים רק לנוחות בדיקות מקומיות.

### 8.8 יצירת ה-Job

1. **New Item** → שם: `eventus-pipeline` → **Pipeline** → OK.
2. **Build Triggers** → סמנו `Poll SCM` → Schedule: `H/2 * * * *`.
3. **Pipeline** → Definition: **Pipeline script from SCM**.
4. SCM: **Git**
   - Repository URL: `https://github.com/GalHillel/EventUs.git`
   - Credentials: `- none -` (ריפו ציבורי) או הוסיפו PAT אם הוא פרטי.
   - Branch Specifier: `*/devops-project`
   - Script Path: `Jenkinsfile`
5. **Save** → **Build Now**.

⚠️ השתמשו ב-**HTTPS** ולא ב-SSH ל-URL. מפתחות ה-SSH שלכם נמצאים ב-`~/.ssh` של המשתמש ב-WSL, לא בתוך קונטיינר Jenkins.

### 8.9 בונוס — Webhook עם ngrok

אם רוצים דחיפה מיידית במקום polling:

```bash
docker run -d --name ngrok --network host -e NGROK_AUTHTOKEN=<your-token> \
  ngrok/ngrok:latest http 8080
docker logs ngrok | grep -o 'https://[a-z0-9-]*\.ngrok[^ ]*'
```

ב-GitHub: Settings → Webhooks → Add webhook → Payload URL: `https://<ngrok>/github-webhook/`, Content type: `application/json`.
ב-Jenkins ב-Job: החליפו `Poll SCM` ב-`GitHub hook trigger for GITScm polling`.

### 8.10 ✅ נקודת אימות

הריצו **Build Now** וחכו. פלט צפוי בסוף:

```
--- rollout history ---
deployment.apps/eventus-api
REVISION  CHANGE-CAUSE
1         <none>
2         build 1 commit a1b2c3d
Deployed galhillel/eventus-api:a1b2c3d-1 to eventus
Finished: SUCCESS
```

ואימות מהטרמינל:

```bash
kubectl -n eventus get deploy eventus-api \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'

curl -s http://localhost/chaos/status; echo
```

הגרסה ב-`chaos/status` צריכה להיות זהה ל-`IMAGE_TAG` של הבנייה. **אם היא לא — Jenkins לא באמת פרס. אל תמשיכו.**

**קומיט:**

```bash
cd ~/projects/EventUs
git add Jenkinsfile infra/jenkins eventus.sh .gitignore
git commit -m "feat(ci): jenkins image, scoped rbac, build-push-deploy pipeline"
git push origin devops-project
```


---

## 9. מנגנון הריפוי העצמי

### 9.1 העיקרון

Kubernetes יודע לרפא סוג אחד של תקלה: **Pod שמת**. הוא מפעיל אותו מחדש, מזיז אותו לצומת אחר, מוריד אותו מה-Service. מה שהוא **לא** יודע לזהות זה Pod חי, בריא לפי כל ה-probes, שמחזיר 500 לכל בקשה שנייה.

זו בדיוק התקלה הכי נפוצה בייצור: הגרסה החדשה עלתה בהצלחה, ה-probes ירוקים, `kubectl get pods` מראה `2/2 Running` — והמשתמשים לא יכולים להשתמש במוצר.

המנגנון שלנו סוגר את הפער הזה. הוא מסתכל על **מה שהמערכת אומרת על עצמה בלוגים**, לא על מה שהאורקסטרטור רואה ברמת התהליך.

```mermaid
flowchart TD
    START(["CronJob יורה — כל דקה"]) --> Q1["שאילתה ל-ES:<br/>כמה 5xx ב-5 הדקות האחרונות?"]
    Q1 --> Q2["שאילתה ל-ES:<br/>כמה בקשות בסך הכל?"]
    Q2 --> C1{"errors >= 10<br/>וגם<br/>errors/total >= 25%?"}
    C1 -->|לא| OK["רושם 'healthy', יוצא 0"]
    C1 -->|כן| C2{"עבר cooldown<br/>של 10 דקות מאז<br/>ה-rollback האחרון?"}
    C2 -->|לא| SKIP["רושם 'in cooldown', יוצא 0"]
    C2 -->|כן| C3{"DRY_RUN?"}
    C3 -->|כן| DRY["רושם 'would roll back', יוצא 0"]
    C3 -->|לא| BASH["מריץ rollback.sh"]
    BASH --> R1["kubectl rollout undo"]
    R1 --> R2["kubectl rollout status"]
    R2 --> R3["annotate: last-rollback=now"]
    R3 --> R4["create Event: SelfHealRollback"]
    R4 --> DONE["רושם 'rollback complete'"]
```

### 9.2 שני תנאים, לא אחד

⚠️ **הסף הכפול הוא ההחלטה החכמה ביותר במנגנון הזה, והמרצה ישאל עליה.**

| תנאי | מונע |
|---|---|
| `errors >= 10` (מספר מוחלט) | חזרה לאחור בגלל 2-3 שגיאות בודדות במערכת בעומס נמוך. |
| `errors / total >= 25%` (יחס) | חזרה לאחור בגלל 15 שגיאות מתוך 50,000 בקשות — כלומר 0.03%, שזה רעש רגיל ולא תקלה. |

מערכת שמסתמכת רק על מספר מוחלט תעשה rollback מיותר בשעת עומס. מערכת שמסתמכת רק על יחס תעשה rollback על 1 מתוך 2 בקשות בשלוש לפנות בוקר. **שניהם יחד = החלטה יציבה.**

### 9.3 `infra/healer/healer.py`

```python
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

ES_URL = os.environ.get('ES_URL', 'http://elasticsearch.observability.svc.cluster.local:9200')
ES_INDEX = os.environ.get('ES_INDEX', 'eventus-logs-*')
SERVICE = os.environ.get('SERVICE_NAME', 'eventus-api')
NAMESPACE = os.environ.get('TARGET_NAMESPACE', 'eventus')
DEPLOYMENT = os.environ.get('TARGET_DEPLOY', 'eventus-api')
THRESHOLD = int(os.environ.get('ERROR_THRESHOLD', '10'))
MIN_RATIO = float(os.environ.get('MIN_ERROR_RATIO', '0.25'))
WINDOW = int(os.environ.get('WINDOW_MINUTES', '5'))
COOLDOWN = int(os.environ.get('COOLDOWN_MINUTES', '10'))
DRY_RUN = os.environ.get('DRY_RUN', 'false').lower() == 'true'
ANNOTATION = 'eventus.io/last-rollback'


def log(level, msg, **extra):
    now = datetime.now(timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')
    print(json.dumps({'time': now, 'level': level, 'service': 'eventus-healer', 'msg': msg, **extra},
                     separators=(',', ':')), flush=True)


def es(path, body):
    req = urllib.request.Request(f'{ES_URL}/{ES_INDEX}/{path}', data=json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(req, timeout=15) as res:
        return json.loads(res.read())


def query(extra, aggs=None):
    body = {'query': {'bool': {'filter': [
        {'term': {'service.keyword': SERVICE}},
        {'range': {'@timestamp': {'gte': f'now-{WINDOW}m'}}},
    ] + extra}}}
    if aggs:
        body.update({'size': 0, 'aggs': aggs})
        return es('_search', body)
    return es('_count', body)['count']


def kubectl(args):
    r = subprocess.run(['kubectl', '-n', NAMESPACE] + args, capture_output=True, text=True, timeout=30)
    if r.returncode:
        raise RuntimeError(r.stderr.strip())
    return r.stdout.strip()


def minutes_since_rollback():
    try:
        raw = kubectl(['get', 'deployment', DEPLOYMENT, '-o', 'jsonpath={.metadata.annotations}'])
        stamp = json.loads(raw).get(ANNOTATION) if raw else None
    except (RuntimeError, json.JSONDecodeError) as err:
        log('warn', 'cannot read annotations', error=str(err))
        return None
    if not stamp:
        return None
    last = datetime.fromisoformat(stamp.replace('Z', '+00:00'))
    return (datetime.now(timezone.utc) - last).total_seconds() / 60


def has_previous_revision():
    try:
        out = kubectl(['rollout', 'history', f'deployment/{DEPLOYMENT}'])
    except RuntimeError as err:
        log('warn', 'cannot read rollout history', error=str(err))
        return False
    return len([l for l in out.splitlines() if l.strip()[:1].isdigit()]) >= 2


def main():
    try:
        errors = query([{'range': {'statusCode': {'gte': 500}}}])
        total = query([{'exists': {'field': 'statusCode'}}])
    except (urllib.error.URLError, OSError) as err:
        log('error', 'elasticsearch unreachable', error=str(err), url=ES_URL)
        return 1

    ratio = errors / total if total else 0.0
    log('info', 'window evaluated', errors=errors, total=total, ratio=round(ratio, 4),
        threshold=THRESHOLD, minRatio=MIN_RATIO, windowMinutes=WINDOW)

    if errors < THRESHOLD or ratio < MIN_RATIO:
        log('info', 'healthy, no action')
        return 0

    elapsed = minutes_since_rollback()
    if elapsed is not None and elapsed < COOLDOWN:
        log('warn', 'breached but in cooldown', minutesSinceRollback=round(elapsed, 1), cooldownMinutes=COOLDOWN)
        return 0

    if not has_previous_revision():
        log('error', 'breached but there is no previous revision to roll back to')
        return 0

    try:
        buckets = query([{'range': {'statusCode': {'gte': 500}}}],
                        {'by_version': {'terms': {'field': 'version.keyword', 'size': 5}}})
        versions = {b['key']: b['doc_count'] for b in buckets['aggregations']['by_version']['buckets']}
    except Exception:
        versions = {}

    if DRY_RUN:
        log('warn', 'dry run, would have rolled back', errors=errors, versions=versions)
        return 0

    log('error', 'breached, rolling back', errors=errors, total=total, versions=versions)
    reason = f'{errors} server errors out of {total} requests in the last {WINDOW}m'
    r = subprocess.run(['bash', '/opt/healer/rollback.sh', NAMESPACE, DEPLOYMENT, reason],
                       capture_output=True, text=True, timeout=300)
    for line in r.stdout.splitlines():
        log('info', 'rollback', step=line)
    if r.returncode:
        log('error', 'rollback failed', stderr=r.stderr.strip())
        return 1
    log('error', 'rollback complete', versions=versions)
    return 0


if __name__ == '__main__':
    sys.exit(main())
```

**המבנה: שמונה פונקציות קצרות, 125 שורות.**

`log()` — אותו פורמט JSON כמו האפליקציה. חשוב: גם ה-healer עצמו נקרא על ידי Filebeat, כך שאפשר לראות בקיבנה את ההחלטות שלו לצד השגיאות שגרמו להן. `flush=True` הכרחי — בלעדיו Python מבפר את הפלט וה-Job מסתיים לפני שהשורות נכתבות.

`es()` ו-`query()` — פנייה ל-Elasticsearch דרך `urllib` בלבד. ⚠️ **אין `requirements.txt` ואין `pip install`.** כל מה שה-healer צריך נמצא בספריית התקן של Python. זה מוריד את התמונה ל-75MB ומבטל שכבת תלויות שלמה שיכולה להישבר.

⚠️ **`service.keyword` ולא `service`.** ב-Elasticsearch, שדה טקסט עובר אנליזה ומפורק לטוקנים; שאילתת `term` עליו לא תמצא כלום. תת-השדה `.keyword` שומר את הערך המקורי בשלמותו. אותו דבר ב-`version.keyword` באגרגציה. **טעות שגורמת ל-healer להחזיר תמיד אפס שגיאות ולכן לא לעשות כלום — והכי גרוע, בלי הודעת שגיאה.**

`kubectl()` — עטיפה עם `timeout=30`. אם ה-API של Kubernetes לא מגיב, ה-Job נכשל בבירור במקום להיתקע עד ה-`activeDeadlineSeconds`.

`minutes_since_rollback()` — קורא את ה-annotation `eventus.io/last-rollback` מה-Deployment. זהו מנגנון הקירור, והוא **חייב** להיות מבוסס על מצב באשכול ולא על משתנה בזיכרון: כל הרצה של ה-CronJob היא תהליך חדש שלא זוכר כלום מהקודמת.

`has_previous_revision()` — סופר את השורות בפלט של `rollout history`. אם יש רק גרסה אחת, אין למה לחזור, וה-healer מדווח על כך במקום להריץ `rollout undo` שייכשל.

`main()` — הזרימה: שתי שאילתות → חישוב יחס → בדיקת שני התנאים → בדיקת קירור → בדיקת היסטוריה → הרצת ה-rollback.

⚠️ **הכישלון של Elasticsearch מטופל במפורש** (`urllib.error.URLError, OSError` → `return 1`). זו החלטה חשובה: אם מערכת הניטור נופלת, ה-healer **לא** מסיק שהמערכת בריאה ו**לא** מסיק שהיא חולה. הוא מדווח על עצמו ככושל ולא נוגע בכלום. מנגנון ריפוי שמגלגל אחורה בגלל תקלה בכלי המדידה שלו הוא מנגנון מסוכן.

**האגרגציה לפי גרסה** רצה רק אחרי שההחלטה כבר התקבלה, והיא לצורך הלוג בלבד — היא כותבת `{"a1b2c3d": 87}`, כלומר "87 שגיאות שייכות לגרסה a1b2c3d". בקיבנה זה מה שמראה שהשגיאות התחילו בדיוק עם הגרסה החדשה. אם היא נכשלת, זה לא מונע את ה-rollback (`except Exception: versions = {}`).

`DRY_RUN` — מאפשר להריץ את כל הלוגיקה ולראות מה **היה** קורה. שימושי לבדיקה לפני הדמו.

---

### 9.4 `infra/healer/rollback.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

NS="${1}"
DEPLOY="${2}"
REASON="${3}"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

echo "before: $(kubectl -n "${NS}" get deployment "${DEPLOY}" -o jsonpath='{.spec.template.spec.containers[0].image}')"
kubectl -n "${NS}" rollout undo "deployment/${DEPLOY}"
kubectl -n "${NS}" rollout status "deployment/${DEPLOY}" --timeout=180s
kubectl -n "${NS}" annotate "deployment/${DEPLOY}" \
  "eventus.io/last-rollback=${NOW}" "kubernetes.io/change-cause=self-heal: ${REASON}" --overwrite >/dev/null
echo "after: $(kubectl -n "${NS}" get deployment "${DEPLOY}" -o jsonpath='{.spec.template.spec.containers[0].image}')"

kubectl -n "${NS}" create -f - <<EOF >/dev/null
apiVersion: v1
kind: Event
metadata: {generateName: eventus-healer-, namespace: ${NS}}
involvedObject: {apiVersion: apps/v1, kind: Deployment, name: ${DEPLOY}, namespace: ${NS}}
reason: SelfHealRollback
message: "${REASON}"
type: Warning
firstTimestamp: "${NOW}"
lastTimestamp: "${NOW}"
count: 1
EOF
echo "event recorded"
```

**למה Bash ולא Python:** האיפיון דורש שילוב של Python ו-Bash, וזו החלוקה הנכונה מבחינה הנדסית: Python מקבל את ההחלטה (שאילתות, חישוב, תנאים), Bash מבצע רצף פקודות `kubectl`. כל שכבה עושה את מה שהיא טובה בו.

**מה קורה כאן, בסדר:**

1. מדפיס את התמונה **לפני** — הרצף `before → after` בלוג הוא ההוכחה הוויזואלית שהגלגול קרה.
2. `rollout undo` — Kubernetes מחליף את ה-ReplicaSet הפעיל בקודם.
3. `rollout status --timeout=180s` — ⚠️ **חוסם עד שהגלגול הושלם.** בלי זה, ה-annotation היה נכתב לפני שה-Pods החדשים עלו, וה-healer בהרצה הבאה היה חושב שהקירור התחיל מוקדם מדי.
4. `annotate` — כותב את חותמת הזמן (הקירור) ואת `kubernetes.io/change-cause` עם הסיבה. ⚠️ ה-`change-cause` הוא מה שמופיע בעמודה של `kubectl rollout history` — כך רואים בהיסטוריה שהגלגול היה אוטומטי ומה גרם לו. `--overwrite` הכרחי כי ה-annotation כבר קיים מהגלגול הקודם.
5. `create -f -` עם Event — יוצר אירוע Kubernetes אמיתי. הוא מופיע ב-`kubectl get events` וב-`kubectl describe deployment`. זה מה שהופך את הפעולה למשהו שאפשר לבקר עליו במקום פעולה שקטה.

---

### 9.5 `infra/healer/Dockerfile`

```dockerfile
FROM alpine:3.20

ARG KUBECTL_VERSION=v1.33.13

RUN apk add --no-cache python3 bash ca-certificates curl \
 && curl -fsSLo /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
 && chmod 0755 /usr/local/bin/kubectl \
 && apk del curl

WORKDIR /opt/healer

COPY healer.py rollback.sh ./
RUN chmod 0755 rollback.sh

RUN addgroup -S healer && adduser -S healer -G healer
USER healer

ENTRYPOINT ["python3", "/opt/healer/healer.py"]
```

תמונה סופית: ~75MB.

`apk del curl` באותה שכבת `RUN` — curl נדרש רק להורדת kubectl, והסרתו באותה שכבה מונעת ממנו להישאר בתמונה. `USER healer` — לא רץ כ-root. אין `requirements.txt` כי אין תלויות.

⚠️ **`healer.py` ו-`rollback.sh` נמצאים גם בתמונה וגם ב-ConfigMap.** ה-ConfigMap הוא זה שמורכב לתוך ה-Pod ב-`/opt/healer` ומכסה את מה שבתמונה. כך אפשר לתקן את הלוגיקה עם `terraform apply` במקום עם בנייה ודחיפה של תמונה חדשה — נוח מאוד בזמן כיוונון הספים.
### 9.6 בנייה ודחיפה

```bash
cd ~/projects/EventUs/infra/healer

DOCKER_USER=galhillel     # ← החלף

docker build -t ${DOCKER_USER}/eventus-healer:1.0.0 .
docker push ${DOCKER_USER}/eventus-healer:1.0.0
```

עדכנו את `infra/terraform/terraform.tfvars`:

```hcl
healer_image = "galhillel/eventus-healer:1.0.0"
```

```bash
cd ~/projects/EventUs/infra/terraform
terraform apply -auto-approve
```

### 9.7 ✅ בדיקה — מריצים את ה-healer ידנית

לא מחכים לדקה. מפעילים Job מתוך ה-CronJob:

```bash
kubectl -n platform create job healer-manual-1 --from=cronjob/eventus-healer

kubectl -n platform wait --for=condition=complete job/healer-manual-1 --timeout=90s

kubectl -n platform logs job/healer-manual-1
```

**פלט צפוי במערכת בריאה:**

```json
{"time":"2026-08-23T10:15:02.331Z","level":"info","service":"eventus-healer","msg":"window evaluated","errors":0,"total":128,"ratio":0.0,"threshold":10,"minRatio":0.25,"windowMinutes":5}
{"time":"2026-08-23T10:15:02.334Z","level":"info","service":"eventus-healer","msg":"healthy, no action"}
```

⚠️ אם `total` הוא 0 — Filebeat לא שולח, או השדה `service.keyword` לא קיים. בדיקה:

```bash
kubectl -n observability exec statefulset/elasticsearch -- \
  curl -s "localhost:9200/eventus-logs-*/_search?size=1&pretty" | head -50
```

חפשו בפלט `"service" : "eventus-api"` ו-`"statusCode" : 200`. אם `_source` מכיל שדה `message` עם JSON כטקסט במקום שדות מופרדים — ה-parser של `ndjson` ב-Filebeat לא רץ. חזרו לסעיף 7.11.

### 9.8 ✅ בדיקה — יורים בעצמנו בכוונה

```bash
kubectl -n platform delete job healer-manual-1

echo "generating 60 server errors..."
for i in $(seq 1 60); do
  curl -s -o /dev/null http://localhost/chaos/boom
done

echo "waiting 25s for filebeat to ship..."
sleep 25

kubectl -n platform create job healer-manual-2 --from=cronjob/eventus-healer
kubectl -n platform wait --for=condition=complete job/healer-manual-2 --timeout=180s
kubectl -n platform logs job/healer-manual-2
```

**פלט צפוי:**

```json
{"...","msg":"window evaluated","errors":60,"total":93,"ratio":0.6452,"threshold":10,"minRatio":0.25,"windowMinutes":5}
{"...","msg":"threshold breached, rolling back","errors":60,"total":93,"versions":{"a1b2c3d-1":60}}
{"...","msg":"rollback","step":"before: galhillel/eventus-api:a1b2c3d-1"}
{"...","msg":"rollback","step":"deployment.apps/eventus-api rolled back"}
{"...","msg":"rollback","step":"deployment \"eventus-api\" successfully rolled out"}
{"...","msg":"rollback","step":"after: galhillel/eventus-api:seed"}
{"...","msg":"rollback","step":"event recorded"}
{"...","msg":"rollback complete","errors":60,"total":93,"versions":{"a1b2c3d-1":60}}
```

**אימות שזה באמת קרה:**

```bash
kubectl -n eventus rollout history deployment/eventus-api

kubectl -n eventus get deploy eventus-api \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'

kubectl -n eventus get deploy eventus-api \
  -o jsonpath='{.metadata.annotations}' | python3 -m json.tool

kubectl -n eventus get events --field-selector reason=SelfHealRollback
```

⚠️ **אם ה-rollback לא קרה למרות שהסף נחצה** — כמעט תמיד זו הרשאה. בדקו:

```bash
kubectl -n platform create job rbac-check --from=cronjob/eventus-healer --dry-run=client -o yaml >/dev/null

kubectl auth can-i patch deployments \
  --as=system:serviceaccount:platform:eventus-healer -n eventus

kubectl auth can-i list replicasets \
  --as=system:serviceaccount:platform:eventus-healer -n eventus
```

שניהם חייבים להחזיר `yes`.

**החזרה למצב תקין אחרי הבדיקה** — מפעילים שוב את הצינור:

```bash
kubectl -n platform delete job healer-manual-2

kubectl -n eventus annotate deployment/eventus-api eventus.io/last-rollback- 
```

(הקו התחתי בסוף מוחק את ה-annotation ומאפס את ה-cooldown.)

**קומיט:**

```bash
cd ~/projects/EventUs
git add infra/healer infra/terraform
git commit -m "feat(healer): elasticsearch-driven rollback with dual threshold and cooldown"
git push origin devops-project
```


---

## 10. Kibana — הגדרה ולוחות המחוונים לדמו

### 10.1 גישה

```
http://kibana.local/
```

אם זה לא נטען — ודאו ש-`127.0.0.1 kibana.local` נמצא ב-`C:\Windows\System32\drivers\etc\hosts` (סעיף 2.9). גיבוי בטוח שתמיד עובד:

```bash
kubectl -n observability port-forward svc/kibana 5601:5601
```

ואז `http://localhost:5601`.

### 10.2 יצירת Data View

**הדרך המהירה — תת-פקודה:**

```bash
./eventus.sh kibana
```

היא ממתינה שקיבנה תענה (עד 5 דקות), יוצרת את ה-Data View דרך ה-API ומדווחת `created the eventus-logs data view`. הרצה שנייה מחזירה `data view already exists` — היא אידמפוטנטית. `./eventus.sh up` מריץ אותה בסוף, כך שאם הקמת את הפלטפורמה כך — כבר סיימת, דלג לסעיף 10.3.

**הדרך הידנית, אם אתה מעדיף לראות את זה בממשק:**

1. **☰ → Stack Management → Data Views → Create data view**
2. Name: `eventus-logs`
3. Index pattern: `eventus-logs-*` — צריך להופיע `Your index pattern matches 1 source`
4. Timestamp field: `@timestamp`
5. **Save data view to Kibana**

⚠️ אם הוא לא מוצא אינדקסים — עדיין לא הגיעו לוגים. צרו תעבורה (`for i in $(seq 1 40); do curl -s -o /dev/null http://localhost/health/live; done`), חכו 20 שניות ורעננו.

### 10.3 בדיקה ב-Discover

**☰ → Analytics → Discover**, בחרו את `eventus-logs`, טווח זמן `Last 15 minutes`.

הוסיפו את העמודות האלה (מרחפים מעל שם השדה ← `+`):

`level` · `method` · `path` · `statusCode` · `durationMs` · `version`

שאילתות KQL שכדאי לשמור (Save query):

| שם | KQL |
|---|---|
| `all-errors` | `statusCode >= 500` |
| `client-errors` | `statusCode >= 400 and statusCode < 500` |
| `slow-requests` | `durationMs > 500` |
| `by-version` | `statusCode >= 500 and version : "*"` |

⚠️ אם `statusCode` מופיע כ-`string` ולא כמספר — אי אפשר להשתמש ב-`>=`. זה אומר שה-mapping נוצר לפני שהיו נתונים תקינים. תיקון:

```bash
kubectl -n observability exec statefulset/elasticsearch -- \
  curl -s -X DELETE "localhost:9200/eventus-logs-*"

kubectl -n observability rollout restart daemonset/filebeat
```

ואז מחקו את ה-Data View ב-Kibana וצרו מחדש.

### 10.4 לוח המחוונים לדמו

**☰ → Analytics → Dashboard → Create dashboard**. ארבע ויזואליזציות:

**1. "Server Errors — Last 15m"** (Metric)
- Create visualization → Metric
- Metric: `Count`
- Filter: `statusCode >= 500`
- הגדילו את הגופן. **זה המספר שהמרצה יסתכל עליו כשהוא עולה.**

**2. "Status codes over time"** (Area, stacked)
- Horizontal axis: `@timestamp`, Minimum interval `10 seconds`
- Vertical axis: `Count`
- Break down by: `statusCode` → Top 6
- Palette: בחרו כזה שבו 500 בולט באדום

**3. "Errors by version"** (Bar, vertical)
- Horizontal axis: `version.keyword` → Top 5
- Vertical axis: `Count`
- Filter: `statusCode >= 500`
- ⚠️ **הוויזואליזציה הכי חשובה בדמו.** ברגע ש-Jenkins פורס גרסה שבורה, עמודה חדשה קופצת עם שם הגרסה. כשה-healer מחזיר אחורה, העמודה מפסיקה לגדול. **סיפור שלם בגרף אחד.**

**4. "Recent errors"** (Table / Discover panel)
- Rows: `path.keyword`, `statusCode`, `version.keyword`
- Metric: `Count`
- Filter: `statusCode >= 500`
- מיון יורד

שמרו בשם: **`EventUs — Live Operations`**. הגדירו רענון אוטומטי: **Refresh every 5 seconds**, טווח `Last 15 minutes`.

⚠️ **קבעו את הרענון האוטומטי לפני הדמו ותשאירו את הטאב פתוח.** אתם לא רוצים ללחוץ Refresh מול המרצה.

---

## 11. תרחיש ההדגמה

### 11.1 `eventus.sh` — כל התפעול בקובץ אחד

במקום שבעה סקריפטים בתיקיית `infra/scripts/` ועוד אחד ב-`infra/jenkins/`, יש קובץ אחד בשורש הריפו עם שמונה תת-פקודות:

```
usage: ./eventus.sh <command>

  up          provision the host, push the seed images, apply terraform
  jenkins     build and run the Jenkins container
  kubeconfig  write the scoped kubeconfig Jenkins uses
  kibana      create the eventus-logs data view
  status      one screen with the state of everything
  traffic     send steady traffic until Ctrl+C
  break       push the deliberate regression
  reset       revert it and clear the healer cooldown
```

**למה זה עדיף על שבעה קבצים:** משתני הסביבה (`NS`, `DEPLOY`, `BASE`) מוגדרים פעם אחת בראש הקובץ במקום להיות משוכפלים בכל סקריפט. שתי פונקציות העזר — `kb()` לקריאה ל-API של קיבנה ו-`es_count()` לספירה ב-Elasticsearch — משמשות כמה תת-פקודות. ובעיקר: כשעומדים מול המרצה, `./eventus.sh status` הוא דבר שזוכרים, ו-`./infra/scripts/status.sh` הוא דבר שמחפשים.

הקובץ נפתח כך:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
NS=eventus
DEPLOY=eventus-api
BASE=${BASE:-http://localhost}
```

ומסתיים כך:

```bash
case "${1:-}" in
  up)         cmd_up ;;
  jenkins)    cmd_jenkins ;;
  kubeconfig) cmd_kubeconfig ;;
  kibana)     cmd_kibana ;;
  status)     cmd_status ;;
  traffic)    cmd_traffic ;;
  break)      cmd_break ;;
  reset)      cmd_reset ;;
  *)          usage; exit 1 ;;
esac
```

#### `up` — הרמה מאפס

```bash
cmd_up() {
  : "${DOCKER_USER:?set DOCKER_USER to your Docker Hub account}"

  ansible-playbook -i "${ROOT}/infra/ansible/inventory.ini" "${ROOT}/infra/ansible/site.yml"

  local sha
  sha=$(git -C "${ROOT}" rev-parse --short HEAD)
  docker build --build-arg "APP_VERSION=${sha}" -t "${DOCKER_USER}/eventus-api:seed" "${ROOT}/backend/event-us"
  docker push "${DOCKER_USER}/eventus-api:seed"
  docker build -t "${DOCKER_USER}/eventus-healer:1.0.0" "${ROOT}/infra/healer"
  docker push "${DOCKER_USER}/eventus-healer:1.0.0"

  cd "${ROOT}/infra/terraform"
  if [ ! -f terraform.tfvars ]; then
    sed "s|galhillel|${DOCKER_USER}|g; s|CHANGE_ME_BEFORE_APPLY|$(openssl rand -hex 16)|" \
      terraform.tfvars.example > terraform.tfvars
  fi
  terraform init
  terraform apply -auto-approve

  cmd_kubeconfig
  cmd_kibana
  echo "api ${BASE}/   kibana http://kibana.local/   next: ./eventus.sh jenkins"
}
```

⚠️ **`: "${DOCKER_USER:?...}"`** — הצורה הזו של Bash עוצרת את הסקריפט מיד עם הודעה ברורה אם המשתנה לא מוגדר, במקום להיכשל שבע פקודות מאוחר יותר עם `invalid reference format`.

⚠️ **הסדר קריטי ולא שרירותי:** Ansible חייב לרוץ לפני שיש בכלל אשכול; שתי התמונות חייבות להיות ב-Docker Hub לפני `terraform apply`, כי ה-Deployment וה-CronJob מצביעים עליהן; ה-kubeconfig יכול להיווצר רק אחרי ש-Terraform יצר את ה-Secret. `up` מקודד את הסדר הזה, ולכן אי אפשר לטעות בו.

⚠️ **הסיסמה נוצרת מ-`openssl rand -hex 16`** ולא נכתבת בשום מקום בקוד. היא נכנסת ל-`terraform.tfvars` שנמצא ב-`.gitignore`.

#### `status` — תמונת מצב אחת

```bash
cmd_status() {
  kubectl get nodes --no-headers
  kubectl -n "${NS}" get deploy,pods --no-headers
  kubectl -n observability get pods --no-headers
  kubectl -n platform get cronjob,jobs --no-headers
  echo
  echo "image   $(kubectl -n ${NS} get deploy ${DEPLOY} -o jsonpath='{.spec.template.spec.containers[0].image}')"
  echo "ready   $(curl -s -o /dev/null -w '%{http_code}' ${BASE}/health/ready)"
  echo "chaos   $(curl -s ${BASE}/chaos/status)"
  echo
  kubectl -n "${NS}" rollout history "deployment/${DEPLOY}"
  echo
  local total errs
  total=$(es_count '{"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-5m"}}},{"exists":{"field":"statusCode"}}]}}}')
  errs=$(es_count '{"query":{"bool":{"filter":[{"range":{"@timestamp":{"gte":"now-5m"}}},{"range":{"statusCode":{"gte":500}}}]}}}')
  echo "last 5m: requests=${total:-0} errors=${errs:-0}"
}
```

השורה האחרונה היא **בדיוק אותה שאילתה** שה-healer מריץ. לכן `./eventus.sh status` מראה מה ה-healer רואה ברגע זה — כלי אבחון ולא רק תצוגה.

#### `traffic` — מחולל התעבורה לדמו

```bash
cmd_traffic() {
  local paths=("/events/search?name=Wedding" "/events/search?location=TelAviv" "/users/search?name=gal" /messages/search)
  local ok=0 err=0
  trap 'printf "\n\nok=%s err=%s\n" "${ok}" "${err}"; exit 0' INT
  echo "sending traffic to ${BASE}, Ctrl+C to stop"
  while true; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "${BASE}${paths[$((RANDOM % ${#paths[@]}))]}")
    if [ "${code}" -ge 500 ] 2>/dev/null; then
      err=$((err + 1)); printf '\033[31m%s\033[0m ' "${code}"
    else
      ok=$((ok + 1)); printf '\033[32m%s\033[0m ' "${code}"
    fi
    sleep 0.25
  done
}
```

⚠️ **ברשימת הנתיבים יש רק נתיבים עסקיים — וזה חישוב, לא סגנון.** אילו `/health/live` ו-`/chaos/status` היו ברשימה, הם היו מקבלים כרבע מהתעבורה, והם **פטורים** מה-chaos ולכן תמיד מחזירים 200. עם ארבעה נתיבים ושיעור שגיאות של 0.45, יחס השגיאות הנצפה היה יורד ל-**0.249** — כלומר בדיוק מתחת לסף 0.25 של ה-healer, וה-rollback היה עלול לא לקרות מול המרצה. עם נתיבים עסקיים בלבד היחס הוא **0.415**, מרווח בטוח מעל הסף. **חשבו את זה מראש; אל תגלו את זה בהצגה.**

הצבעים ההופכים אדומים בזמן אמת הם החלק הכי ויזואלי בדמו.

#### `break` ו-`reset` — הבאג המכוון והחזרה

```bash
cmd_break() {
  local file="${ROOT}/backend/event-us/src/common/platform.ts"
  sed -i "s/^const DEFAULT_ERROR_RATE = .*/const DEFAULT_ERROR_RATE = ${RATE:-0.45};/" "${file}"
  git -C "${ROOT}" --no-pager diff --unified=1 -- "${file}"
  git -C "${ROOT}" commit -am "feat: new caching layer for event search"
  git -C "${ROOT}" push origin "$(git -C "${ROOT}" rev-parse --abbrev-ref HEAD)"
  echo "pushed, jenkins picks it up within two minutes"
}

cmd_reset() {
  git -C "${ROOT}" revert --no-edit HEAD
  git -C "${ROOT}" push origin "$(git -C "${ROOT}" rev-parse --abbrev-ref HEAD)"
  kubectl -n "${NS}" annotate "deployment/${DEPLOY}" eventus.io/last-rollback- 2>/dev/null || true
  kubectl -n platform delete jobs --all 2>/dev/null || true
  echo "reverted and cleared the cooldown"
}
```

⚠️ **`break` מדפיס את ה-diff לפני שהוא דוחף.** בהצגה זה הרגע שבו מראים למרצה ששורה אחת בלבד השתנתה — לא סקריפט שמזריק שגיאות מבחוץ, אלא רגרסיה בקוד שעוברת דרך כל ה-pipeline.

⚠️ **הודעת הקומיט "feat: new caching layer for event search" היא חלק מהתרחיש.** היא מדמה מה שקורה באמת: מפתח דוחף פיצ'ר תמים שמכיל רגרסיה. אף אחד לא כותב "commit: breaking production".

⚠️ **`reset` מנקה גם את ה-annotation וגם את ה-Jobs.** בלי ניקוי ה-annotation, ה-healer יהיה בקירור של 10 דקות ולא יגיב אם תרצו להדגים שוב.
### 11.2 הכנות — יום לפני

| # | פעולה | פקודת אימות |
|---|---|---|
| 1 | `wsl --shutdown` ואתחול מלא, לוודא שהכל עולה לבד | `./eventus.sh status` |
| 2 | לוודא ש-Jenkins עלה עם Docker | `docker ps --filter name=jenkins` |
| 3 | להריץ בנייה מלאה ולוודא ירוק | Jenkins → Build Now |
| 4 | **להריץ בנייה שנייה** כדי שתהיה היסטוריה | `kubectl -n eventus rollout history deployment/eventus-api` צריך להראות ≥3 revisions |
| 5 | לאפס את ה-cooldown ואת ה-Jobs הישנים | `./eventus.sh reset` (או ה-`annotate` הידני מסעיף 14.2) |
| 6 | לפתוח את Kibana, לוודא שהדשבורד מתמלא | `http://kibana.local/` |
| 7 | לבנות ולהתקין את ה-APK באמולטור, לוודא Login | ידני |
| 8 | ליצור נתוני דמו במסד | ראו 11.3 |
| 9 | לצלם מסך של `kubectl auth can-i` (סעיף 8.5) | למצגת |
| 10 | לבדוק שיש מקום בדיסק | `df -h /` — לפחות 8GB פנויים |

### 11.3 נתוני דמו

הריפו כולל את `backend/event-us/src/test.py` שיוצר משתמשים, אירועים והודעות. הוא מכוון ל-`localhost:3000`. הריצו אותו נגד ה-Ingress:

```bash
cd ~/projects/EventUs
pip3 install requests --break-system-packages 2>/dev/null || pip3 install requests

sed 's|http://localhost:3000/|http://localhost/|' backend/event-us/src/test.py > /tmp/seed.py
python3 /tmp/seed.py
```

זה יוצר: 5 משתתפים (`user1`, `user2`, `user3`, `ziv`, `gal`), 2 מארגנים (`zivO`, `galO`), 6 אירועים והודעות.

⚠️ הסקריפט משתמש בתאריכים מ-2024. `GET /events/search` מסנן `date >= now` ולכן **האירועים לא יופיעו בחיפוש**. הם כן יופיעו ב"האירועים שלי". אם אתם רוצים שיופיעו בחיפוש, ערכו את `datetime_str1`/`datetime_str2` לתאריכים ב-2027.

⚠️ הסיסמאות שנוצרות הן `<name>Pass` — למשל `galPass` למשתמש `gal@gmail.com`. **שמרו את זה לפני הדמו** ואל תחפשו אותו מול המרצה.

### 11.4 סדר הפעולות בהצגה

**סידור המסך:** שלושה חלונות גלויים בו זמנית —
- שמאל: אמולטור אנדרואיד
- מרכז: דפדפן עם Kibana (טאב 1) ו-Jenkins (טאב 2)
- ימין: שני טרמינלים — אחד עם `./eventus.sh traffic`, אחד פנוי

---

#### שלב 0 — הארכיטקטורה (2 דקות)

הציגו את הדיאגרמה מסעיף 0.2 ואת טבלת ההחלטות מסעיף 0.3.

**המשפט לפתיחה:** "לקחתי את פרויקט הגמר שלי מהתואר והקמתי סביבו פלטפורמה שלמה — הקמת תשתית ב-Ansible, הגדרת האשכול ב-Terraform, CI/CD ב-Jenkins, Observability ב-ELK, ומעליהם מנגנון שקורא את הלוגים ומתקן את עצמו."

**התייחסות ישירה להערת המרצה:** "התחלתי עם Nginx בתכנון והורדתי אותו. K3s מגיע עם Traefik מובנה שנותן בדיוק את מה שצריך — ניתוב לפי host ולפי path. הוספת Nginx הייתה מוסיפה רכיב, קונטיינר ונקודת כשל בלי לספק שום יכולת חדשה. באותו קו הורדתי גם את Logstash: אני פולט לוגים ב-JSON מהאפליקציה, אז Filebeat שולח ישירות ל-Elasticsearch. שני רכיבים פחות."

#### שלב 1 — המערכת חיה (2 דקות)

```bash
./eventus.sh status
```

הראו: node אחד Ready, שני Pods של האפליקציה, Mongo, ES, Kibana, Filebeat, ה-CronJob.

באמולטור: התחברו, גללו אירועים, פתחו אירוע. **המערכת עובדת.**

בטרמינל השני:

```bash
./eventus.sh traffic
```

מסך ירוק זורם.

ב-Kibana: הדשבורד מתמלא. "Server Errors" = **0**.

#### שלב 2 — מכניסים באג (2 דקות)

בטרמינל הפנוי:

```bash
cd ~/projects/EventUs
./eventus.sh break
```

הפקודה משנה שורה אחת ב-`backend/event-us/src/common/platform.ts`, מדפיסה את ה-diff, מקמיטה ודוחפת:

```diff
-const DEFAULT_ERROR_RATE = 0;
+const DEFAULT_ERROR_RATE = 0.45;
```

**הראו את ה-diff הזה למרצה.** שורה אחת. לא סקריפט שמזריק שגיאות מבחוץ — רגרסיה בקוד, שעוברת דרך כל ה-pipeline.

⚠️ **הודעת הקומיט חשובה.** "feat: new caching layer" מדמה מה שקורה באמת — מפתח דוחף פיצ'ר תמים שמכיל רגרסיה. אף אחד לא כותב "commit: breaking production".

#### שלב 3 — Jenkins בונה (3-4 דקות)

עברו ל-Jenkins. תוך עד 2 דקות ה-poll תופס את הקומיט.

עקבו אחרי השלבים בזמן אמת: Checkout → Build image → Push image → Deploy to K3s → Smoke test.

**מה להגיד:** "שימו לב שה-Smoke test עובר. הוא בודק `/health/ready`, וה-endpoint הזה מוחרג מהבאג בכוונה — כי זה בדיוק המצב שאני רוצה להדגים: **תקלה שעוברת את כל בדיקות ה-CI ואת כל ה-probes של Kubernetes**. ה-Pod חי, בריא, ומחזיר שגיאות."

בטרמינל השלישי:

```bash
watch -n 2 'kubectl -n eventus get pods -o wide'
```

רואים Pod חדש עולה, נעשה Ready, ואז Pod ישן נעלם. **בשום רגע לא יורדים מתחת ל-2 מוכנים.**

#### שלב 4 — הקריסה (1 דקה)

טרמינל התעבורה: הזרם הופך אדום-ירוק מעורבב.

באמולטור: רעננו את רשימת האירועים — הודעות שגיאה.

ב-Kibana: "Server Errors" קופץ מ-0 ל-עשרות. גרף האזור מתמלא באדום. **"Errors by version" מצמיח עמודה חדשה עם התג של הבנייה שהרגע רצה.**

**מה להגיד:** "Kubernetes מרוצה. `kubectl get pods` מראה 2/2 Running. שום probe לא נכשל. רק ELK יודע שיש בעיה."

#### שלב 5 — הקסם (1-2 דקות)

```bash
kubectl -n platform get jobs -w
```

תוך עד 60 שניות מופיע Job חדש. ברגע שהוא `Completed`:

```bash
kubectl -n platform logs -l app=eventus-healer --tail=30
```

**מה שרואים:**

```json
{"msg":"window evaluated","errors":87,"total":168,"ratio":0.5179,"threshold":10,"minRatio":0.25}
{"msg":"threshold breached, rolling back","errors":87,"versions":{"f4e9a1c-7":87}}
{"msg":"rollback","step":"before: galhillel/eventus-api:f4e9a1c-7"}
{"msg":"rollback","step":"deployment.apps/eventus-api rolled back"}
{"msg":"rollback","step":"after: galhillel/eventus-api:a1b2c3d-6"}
{"msg":"rollback complete"}
```

**מה להגיד:** "שני התנאים נבדקו: 87 שגיאות — מעל הסף של 10, ו-52% מהבקשות — מעל הסף של 25%. שניהם. אם היו 87 שגיאות מתוך 100,000 בקשות, המערכת לא הייתה נוגעת בכלום."

#### שלב 6 — חזרה לשגרה (1 דקה)

טרמינל התעבורה: הופך ירוק לגמרי.

באמולטור: רעננו — האפליקציה עובדת.

ב-Kibana: העקומה האדומה מתיישרת. העמודה של הגרסה השבורה מפסיקה לגדול.

```bash
kubectl -n eventus rollout history deployment/eventus-api
```

```
REVISION  CHANGE-CAUSE
5         build 6 commit a1b2c3d
7         build 7 commit f4e9a1c
8         self-heal rollback: 87 server errors out of 168 requests in the last 5m
```

```bash
kubectl -n eventus get events --field-selector reason=SelfHealRollback
```

**משפט הסיום:** "המערכת זיהתה לבד תקלה שאף אחד מהכלים הסטנדרטיים לא זיהה, החליטה לבד לפי שני תנאים כמותיים, תיקנה את עצמה, תיעדה את הפעולה ב-Kubernetes Events — והכל תוך פחות מדקה מרגע התקלה, בלי שנגעתי במקלדת."

#### שלב 7 — שאלות ואיפוס (2 דקות)

```bash
cd ~/projects/EventUs
./eventus.sh reset
```

זה מחזיר את הקוד למצב תקין, Jenkins בונה מחדש, וה-cooldown מתאפס — כדי שאפשר יהיה להדגים שוב אם ישאלו.

### 11.5 טבלת זמנים

| דקה | שלב |
|---|---|
| 0:00–2:00 | ארכיטקטורה והחלטות |
| 2:00–4:00 | המערכת חיה, מריצים תעבורה |
| 4:00–6:00 | הקומיט השובר |
| 6:00–10:00 | Jenkins בונה ופורס |
| 10:00–11:00 | הקריסה ב-Kibana ובאמולטור |
| 11:00–13:00 | Self-healing |
| 13:00–14:00 | חזרה לשגרה + הוכחות |
| 14:00–18:00 | שאלות |

### 11.6 תוכניות גיבוי

| מה נשבר | מה עושים |
|---|---|
| Jenkins לא תופס את ה-push | **Build Now** ידני. תסבירו שזה polling ולא webhook. |
| בנייה נכשלת על `docker push` | הריצו `docker login` בתוך הקונטיינר: `docker exec jenkins docker login -u USER` |
| ה-healer לא מזהה | הריצו ידנית: `kubectl -n platform create job manual --from=cronjob/eventus-healer`. אמרו: "אני מזרז את התזמון כדי לא לבזבז את זמנכם." |
| Kibana לא נטענת | `kubectl -n observability port-forward svc/kibana 5601:5601` → `localhost:5601` |
| האמולטור לא מתחבר | `curl http://localhost/health/ready` מ-Windows. אם עובד — בעיית אמולטור, המשיכו עם ה-curl. |
| WSL קורס | `wsl --shutdown` → `wsl -d Ubuntu-24.04` → הכל עולה לבד (systemd + restart policies). ~90 שניות. |
| הכל נשבר | **הקליטו את הדמו יום קודם.** קובץ MP4 בשולחן העבודה. |

⚠️ **הקלטת גיבוי היא לא חוסר ביטחון — היא ניהול סיכונים.** הקליטו ריצה מוצלחת מלאה יום לפני.


---

## 12. פתרון תקלות

### 12.1 WSL ו-K3s

| תסמין | סיבה | פתרון |
|---|---|---|
| `System has not been booted with systemd` | `systemd=true` לא נכנס לתוקף | ודאו `/etc/wsl.conf` תקין → `wsl --shutdown` מ-PowerShell → חכו 8 שניות → היכנסו שוב |
| `Job for k3s.service failed` | תלוי בסיבה | `sudo journalctl -u k3s -n 100 --no-pager` |
| `Failed to execute iptables-restore` | Ubuntu 24.04 עם nftables | `sudo update-alternatives --set iptables /usr/sbin/iptables-legacy && sudo update-alternatives --set ip6tables /usr/sbin/ip6tables-legacy && sudo systemctl restart k3s` |
| `failed to find memory cgroup` | cgroup v2 חסר | `wsl --update` מ-PowerShell, ואז `wsl --shutdown` |
| `/dev/kmsg: no such file` בלוגי k3s | חסר ב-WSL ישן | `sudo ln -sf /dev/null /dev/kmsg` (זמני) או `wsl --update` (קבוע) |
| הצומת `NotReady` עם `DiskPressure` | kubelet רואה את דיסק C: המלא | הדגלים `eviction-hard` בתפקיד ה-k3s (סעיף 6.8). ודאו: `sudo cat /etc/systemd/system/k3s.service \| grep eviction` |
| `kubectl` נותן `connection refused` | k3s לא רץ / KUBECONFIG לא מוגדר | `sudo systemctl status k3s` ו-`echo $KUBECONFIG` |
| `permission denied` על `k3s.yaml` | חסר `--write-kubeconfig-mode 644` | `sudo chmod 644 /etc/rancher/k3s/k3s.yaml` וגם `cp` מחדש ל-`~/.kube/config` |
| ה-IP של WSL השתנה, הכל נשבר | אין mirrored networking | הריצו את `wsl-portproxy.ps1` (סעיף 2.8) |
| `curl http://localhost/` מ-Windows נכשל | חומת אש של Hyper-V | `Set-NetFirewallHyperVVMSetting -Name '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}' -DefaultInboundAction Allow` ב-PowerShell כמנהל |

**איפוס מלא של K3s** (מוחק את כל האשכול, לא את הקוד):

```bash
sudo /usr/local/bin/k3s-uninstall.sh
sudo rm -rf /var/lib/rancher /etc/rancher /var/lib/kubelet
cd ~/projects/EventUs/infra/ansible && ansible-playbook site.yml
cd ../terraform && rm -f terraform.tfstate* && terraform apply
```

### 12.2 Terraform

| תסמין | סיבה | פתרון |
|---|---|---|
| `Get "http://localhost/api?timeout=32s": dial tcp connection refused` | ה-provider לא מוצא kubeconfig | ודאו `var.kubeconfig_path` נכון ושהקובץ קיים: `ls -la ~/.kube/config` |
| `context "default" does not exist` | שם הקונטקסט שונה | `kubectl config get-contexts` והתאימו את `kube_context` |
| `Error: Provider produced inconsistent result after apply` | באג ידוע בשדות אופציונליים | `terraform apply` שוב. אם חוזר — `terraform state rm <resource>` ו-apply |
| `failed quota: eventus-quota` | Pod בלי `resources` | הוסיפו `requests` ו-`limits` לכל קונטיינר |
| `timeout while waiting for state to become 'Running'` | התמונה לא נמשכת | `kubectl -n eventus describe pod <name>` ← חפשו `ErrImagePull` / `ImagePullBackOff` |
| `terraform plan` רוצה לשנות את ה-image חזרה | `ignore_changes` לא עובד | ודאו את הנתיב המדויק: `spec[0].template[0].spec[0].container[0].image` |
| `Error: Unsupported argument` על `_v1` | provider ישן מדי | `terraform init -upgrade` |
| נעילת state תקועה | apply שנקטע | `terraform force-unlock <LOCK_ID>` |

### 12.3 האפליקציה

| תסמין | סיבה | פתרון |
|---|---|---|
| `CrashLoopBackOff` | קורס בעלייה | `kubectl -n eventus logs deploy/eventus-api --previous` |
| `MongoServerError: Authentication failed` | חסר `authSource=admin` | בדקו: `kubectl -n eventus get secret mongodb-credentials -o jsonpath='{.data.MONGODB_URI}' \| base64 -d` |
| readiness נכשל תמיד | Mongo לא מוכן | `kubectl -n eventus logs sts/mongodb --tail=30` |
| `EROFS: read-only file system` | `read_only_root_filesystem` | הוסיפו `emptyDir` ל-`/tmp` (סעיף 7.8) |
| `ImagePullBackOff` | תג לא קיים / repo פרטי | `docker pull <image>` בדקו ידנית. אם פרטי — צרו `imagePullSecrets` |
| `container has runAsNonRoot and image will run as root` | חסר `USER` ב-Dockerfile | הוסיפו `USER eventus` (סעיף 4.8) |
| 404 מ-Traefik על כל נתיב | Ingress לא נקלט | `kubectl -n eventus describe ingress eventus-api` ו-`kubectl -n kube-system logs deploy/traefik --tail=50` |
| `/docs` נותן 404 | Swagger כבוי | `SWAGGER_ENABLED=true` ב-ConfigMap |
| ה-Emulator מקבל `ECONNREFUSED` | פורט/URL | ודאו `API_BASE_URL = "http://10.0.2.2/"` **בלי** `:3000` |

### 12.4 ELK

| תסמין | סיבה | פתרון |
|---|---|---|
| ES: `max virtual memory areas ... too low` | `vm.max_map_count` | `sudo sysctl -w vm.max_map_count=262144` + `/etc/sysctl.d/99-eventus.conf` |
| ES: `AccessDeniedException: /usr/share/elasticsearch/data/nodes` | הרשאות PVC | ה-`init_container` בסעיף 7.9. ודאו שהוא רץ: `kubectl -n observability describe pod elasticsearch-0` |
| ES `OOMKilled` | limit קרוב מדי ל-heap | limit ≈ heap × 2.5. עם heap 1g → limit 2560Mi |
| ES לא עולה, אין שגיאה ברורה | `discovery.type` חסר | ודאו `discovery.type=single-node` |
| Kibana `Kibana server is not ready yet` | ES עדיין עולה | חכו 2 דקות. `kubectl -n observability logs deploy/kibana --tail=40` |
| Kibana: `no handler found for uri [/_security/profile/_activate]` | Kibana 9.4.0/9.4.1 עם security כבוי | קבעו `elastic_stack_version = "8.19.20"` |
| **Filebeat לא שולח כלום** | הכי נפוץ. ראו למטה. | סעיף 12.5 |
| `_source` מכיל `message` עם JSON כטקסט | ה-parser של `ndjson` לא רץ | ודאו את סדר ה-parsers: `container` ואז `ndjson` |
| `statusCode` ממופה כ-`string` | mapping נוצר מנתונים שגויים | מחקו את האינדקסים ואת ה-Data View, הריצו `rollout restart daemonset/filebeat` |
| הדיסק מתמלא | אינדקסים מצטברים | `curl -XDELETE "localhost:9200/eventus-logs-2026.08.1*"` |

### 12.5 Filebeat — עץ אבחון

```bash
# 1. ה-Pod רץ?
kubectl -n observability get pods -l app=filebeat

# 2. מה הוא אומר?
kubectl -n observability logs ds/filebeat --tail=60

# 3. הוא מוצא קבצים?
kubectl -n observability logs ds/filebeat | grep -i "harvester\|file\|start"

# 4. הקבצים בכלל שם?
kubectl -n observability exec ds/filebeat -- ls -la /var/log/containers/ | grep eventus-api

# 5. איך נראית שורת לוג גולמית?
kubectl -n observability exec ds/filebeat -- sh -c \
  'tail -2 $(ls /var/log/containers/eventus-api-*.log | head -1)'

# 6. ES מקבל?
kubectl -n observability exec sts/elasticsearch -- \
  curl -s "localhost:9200/_cat/indices?v"

# 7. מה נכנס בפועל?
kubectl -n observability exec sts/elasticsearch -- \
  curl -s "localhost:9200/eventus-logs-*/_search?size=1&pretty"
```

| מה גילינו | הפתרון |
|---|---|
| שלב 4 ריק | ה-Pods של האפליקציה לא רצים, או שם ה-Deployment שונה מ-`eventus-api` |
| שלב 5 מראה `2026-... stdout F {"time":...}` אבל שלב 7 ריק | **`prospector.scanner.symlinks: true` חסר.** התקלה הכי נפוצה. |
| שלב 5 מראה טקסט לא-JSON | ה-NestJS לא פולט JSON — חזרו לסעיף 4.1 |
| שלב 7 מראה `message` עם JSON כטקסט | ה-parser של `ndjson` לא רץ או שהסדר הפוך |
| שלב 2 מראה `connection refused` ל-ES | ES לא מוכן. חכו והריצו `kubectl -n observability rollout restart ds/filebeat` |
| שלב 2 מראה `Error creating runner from config` | שגיאת YAML ב-ConfigMap. `kubectl -n observability get cm filebeat-config -o yaml` |
| הכל נראה תקין אבל אין נתונים חדשים | ה-registry תקוע: `kubectl -n observability exec ds/filebeat -- rm -rf /usr/share/filebeat/data/registry` ואז restart |

### 12.6 Jenkins

| תסמין | סיבה | פתרון |
|---|---|---|
| `docker: command not found` | תמונה לא מותאמת | בנו לפי סעיף 8.2 |
| `permission denied /var/run/docker.sock` | GID לא תואם | `docker exec -u root jenkins chmod 666 /var/run/docker.sock` (זמני) או בנו מחדש עם ה-GID הנכון |
| `kubectl: connection refused` | הקונטיינר לא ברשת המארח | ודאו `--network host` ב-`docker run` |
| `error: You must be logged in to the server (Unauthorized)` | token לא תקין | הריצו `./eventus.sh kubeconfig` והעלו את הקובץ מחדש ב-Credentials |
| `Error from server (Forbidden): deployments.apps ... is forbidden` | RBAC חסר | ודאו ש-`terraform apply` רץ, ובדקו את הפלט של `./eventus.sh kubeconfig` |
| `rollout status` תקוע | חסר `deployments/status` ב-Role | הוסיפו את הכלל וה-apply |
| `denied: requested access to the resource is denied` | credentials של Docker Hub | Access Token ולא סיסמה; ה-ID חייב להיות `dockerhub` |
| Jenkins לא רואה קומיטים חדשים | branch specifier | `*/devops-project` בדיוק, עם הכוכבית והלוכסן |
| הדיסק מתמלא | תמונות ישנות | `docker system prune -af --volumes` (⚠️ מוחק גם את `jenkins_home` אם לא בשימוש — עצרו את Jenkins **לא** לפני) |

### 12.7 ה-healer

| תסמין | סיבה | פתרון |
|---|---|---|
| `total: 0` תמיד | הלוגים לא מגיעים או `service.keyword` לא קיים | סעיף 12.5, ואז בדקו ש-`service` הוא שדה שורש ב-`_source` |
| הסף נחצה אבל אין rollback | RBAC | `kubectl auth can-i patch deployments --as=system:serviceaccount:platform:eventus-healer -n eventus` |
| `error: no rollout history found` | רק revision אחת | הריצו עוד בנייה אחת ב-Jenkins |
| rollback חוזר בלולאה | ה-cooldown לא עובד | ה-escaping של ה-jsonpath. בדקו: `kubectl -n eventus get deploy eventus-api -o jsonpath='{.metadata.annotations}'` |
| ה-Job לא נוצר בכלל | `starting_deadline_seconds` פג | `kubectl -n platform describe cronjob eventus-healer` |
| `ImagePullBackOff` על ה-healer | לא נדחף | `docker push <user>/eventus-healer:1.0.0` |
| `Unauthorized` מ-ES | לא רלוונטי — security כבוי | בדקו את `ES_URL` ב-ConfigMap |

### 12.8 פקודות אבחון כלליות

```bash
# מה לא בסדר בכל האשכול
kubectl get pods -A --field-selector=status.phase!=Running

# למה Pod לא עולה
kubectl -n <ns> describe pod <pod> | tail -30

# מה קרה לאחרונה
kubectl get events -A --sort-by='.lastTimestamp' | tail -25

# צריכת משאבים
kubectl top nodes
kubectl top pods -A --sort-by=memory

# מקום בדיסק
df -h /
sudo du -sh /var/lib/rancher/k3s/storage/* 2>/dev/null
docker system df

# האם ה-Service מוצא Pods
kubectl -n eventus get endpoints eventus-api

# ניתוב של Traefik
kubectl -n kube-system logs deploy/traefik --tail=50 | grep -i eventus

# בדיקת רשת מתוך האשכול
kubectl -n eventus run netcheck --rm -it --restart=Never \
  --image=curlimages/curl:8.10.1 -- \
  curl -s http://eventus-api:3000/health/ready
```


---

## 13. מה מגישים

### 13.1 רשימת תיוג להגשה

| # | פריט | היכן | ✔ |
|---|---|---|---|
| 1 | ריפו Git עם ענף `devops-project` מלא | GitHub | ☐ |
| 2 | `DEVOPS.md` — README של חלק ה-DevOps | שורש הריפו | ☐ |
| 3 | קוד Ansible — playbook + inventory | `infra/ansible/` | ☐ |
| 4 | קוד Terraform — 4 קבצים | `infra/terraform/` | ☐ |
| 5 | `Dockerfile` של האפליקציה | `backend/event-us/` | ☐ |
| 6 | `Jenkinsfile` | שורש הריפו | ☐ |
| 7 | קוד ה-healer — Python + Bash + Dockerfile | `infra/healer/` | ☐ |
| 8 | `eventus.sh` — כל פעולות התפעול | שורש הריפו | ☐ |
| 9 | מצגת (10-14 שקפים) | `presentation/` | ☐ |
| 10 | הקלטת דמו (MP4, 5-8 דקות) | לא ב-Git — קובץ נפרד | ☐ |
| 11 | צילומי מסך: Kibana, Jenkins, `./eventus.sh kubeconfig` | `docs/screenshots/` | ☐ |
| 12 | דיאגרמת ארכיטקטורה | `docs/architecture.png` | ☐ |
| 13 | תמונות ב-Docker Hub (ציבוריות) | `hub.docker.com/u/<user>` | ☐ |

⚠️ **ודאו שהתמונות ב-Docker Hub ציבוריות.** אם הן פרטיות והמרצה ינסה לשחזר — `ImagePullBackOff`.

**16 קבצים חדשים, 1,674 שורות.** שווה להראות את המספר הזה: הוא אומר שאתה מכיר כל שורה בפרויקט.

### 13.2 `DEVOPS.md`

```markdown
# EventUs on-premise platform

Kubernetes restarts a pod that dies. It does not notice a pod that is alive, passes
every probe, and answers half its requests with HTTP 500. This closes that gap: the
API emits structured JSON logs, Filebeat ships them to Elasticsearch, and a CronJob
queries Elasticsearch every minute and rolls the deployment back on a real error spike.

Ansible provisions the host. K3s runs the containers. Terraform defines everything
inside the cluster. Jenkins builds, pushes and deploys. Traefik, bundled with K3s, is
the ingress; no Nginx. Filebeat ships JSON straight to Elasticsearch; no Logstash.

## Layout

    backend/event-us/       NestJS API and its Dockerfile
    frontend/               Android client
    infra/ansible/          host provisioning, one playbook
    infra/terraform/        every cluster resource
    infra/healer/           the self-healing job
    infra/jenkins/          the CI image
    eventus.sh              every operation
    Jenkinsfile             build, push, deploy

## Run it

    export DOCKER_USER=<your docker hub account>
    ./eventus.sh up
    ./eventus.sh jenkins

| command | what it does |
|---|---|
| `up` | ansible, seed images, terraform, kubeconfig, kibana data view |
| `jenkins` | build and start the CI container, print the admin password |
| `status` | one screen with the state of everything |
| `traffic` | steady traffic until Ctrl+C |
| `break` | push the deliberate regression |
| `reset` | revert it and clear the healer cooldown |

| url | what |
|---|---|
| http://localhost/ | API through Traefik |
| http://localhost/docs | Swagger |
| http://kibana.local/ | Kibana |
| http://localhost:8080 | Jenkins |

## Self-healing

A rollback happens only when both hold over the last five minutes: at least 10 server
errors, and server errors are at least 25% of all requests. A ten minute cooldown
stops it from firing again while the previous rollback settles. If Elasticsearch is
unreachable the job logs it and exits without touching the cluster.

Thresholds live in the healer ConfigMap in `infra/terraform/main.tf`.

## Notes

- Image tags are `<git-sha>-<build-number>`. `rollout undo` cannot swap images without
  unique tags, so `variables.tf` rejects `:latest`.
- Terraform owns the deployment, Jenkins owns the tag. `ignore_changes` on the image
  field keeps them from fighting.
- The health endpoints are exempt from the chaos switch. A pod failing its probes gets
  killed by Kubernetes; the failure being demonstrated is a pod that stays healthy and
  serves errors.
```
### 13.3 שלד המצגת

| שקף | תוכן |
|---|---|
| 1 | כותרת: EventUs — On-Premise Self-Healing Cloud |
| 2 | הבעיה: Pod בריא שמחזיר 500. מה Kubernetes לא פותר. |
| 3 | דיאגרמת ארכיטקטורה (סעיף 0.2) |
| 4 | טבלת ההחלטות (סעיף 0.3) — **כולל Traefik מול Nginx, ו-16 קבצים מול 45** |
| 5 | Ansible: מ-Ubuntu ריק ל-K3s רץ |
| 6 | Terraform: מה הוא מנהל, ובעיית ה-drift + `ignore_changes` |
| 7 | Docker: multi-stage, non-root, 180MB |
| 8 | Jenkins: הצינור, ותגי `<sha>-<build>` |
| 9 | RBAC: צילום המסך של `can-i` — yes/no/no |
| 10 | ELK: למה JSON מובנה מייתר את Logstash |
| 11 | Self-healing: תרשים הזרימה + הסף הכפול |
| 12 | דמו חי |
| 13 | ממצאי אבטחה שנמצאו בקוד המקורי (F-01, F-02) |
| 14 | מה הייתי עושה אחרת בייצור |

### 13.4 שאלות שהמרצה ישאל, והתשובות

| שאלה | תשובה |
|---|---|
| "למה לא Nginx?" | K3s מגיע עם Traefik מוגדר. Traefik נותן ניתוב לפי host ולפי path, וזה כל מה שהתרחיש דורש. Nginx היה מוסיף רכיב, קונטיינר, קונפיג ונקודת כשל — בלי יכולת חדשה. |
| "למה ויתרת על Logstash?" | Logstash קיים כדי להפוך טקסט חופשי לשדות. שיניתי את NestJS לפלוט JSON מובנה, אז Filebeat מפרסר ושולח ישירות. חסכתי רכיב ו-1GB זיכרון. |
| "מה קורה אם ה-healer עושה rollback בטעות?" | שני תנאים במקביל — מספר מוחלט ויחס. בנוסף cooldown של 10 דקות, ו-`DRY_RUN` שמאפשר להריץ במצב תצפית לפני שמפעילים. |
| "למה CronJob ולא לולאה?" | CronJob הוא הפרימיטיב הנכון: Kubernetes מנהל תזמון, היסטוריה, כשלונות וניקוי. `Forbid` מונע ריצות חופפות. תהליך `while true` היה דורש ממני לכתוב את כל זה. |
| "מה אם Elasticsearch נופל?" | ה-healer רושם `elasticsearch unreachable` ויוצא בקוד 1 בלי לגעת באשכול. **כשל שקט לצד הבטוח** — עדיף לא לעשות כלום מאשר rollback על סמך מידע חסר. |
| "למה `xpack.security` כבוי?" | החלטה מודעת של סביבת מעבדה. ES 8 היה דורש יצירת CA, אישורים ו-Secrets לכל רכיב. פורט 9200 לא חשוף מחוץ לאשכול. בייצור הייתי מפעיל, עם ECK שמנהל את האישורים. |
| "איך זה מתנהג בקנה מידה?" | היום node אחד. K3s מתרחב לצמתים נוספים בפקודה אחת. Elasticsearch יעבור למספר shards. ה-healer יזוז ל-Operator עם reconcile loop במקום CronJob. |
| "למה ה-state של Terraform מקומי?" | פרויקט של אדם אחד. בצוות זה היה חייב להיות remote backend עם נעילה — S3+DynamoDB, Consul או Terraform Cloud — כי שני `apply` במקביל משחיתים state מקומי. |
| "מה קורה אם Jenkins פורס גרסה שבורה פעמיים ברצף?" | ה-cooldown של 10 דקות מונע לולאת rollback. בייצור הייתי מוסיף שער: אחרי rollback אוטומטי, ה-branch ננעל לפריסה עד אישור ידני. |
| "למה `main.tf` אחד ענק ולא קבצים נפרדים?" | Terraform קורא את כל קבצי ה-`.tf` בתיקייה ומאחד אותם לגרף אחד — הפיצול הוא ויזואלי בלבד ולא משנה סדר ביצוע. העדפתי 16 קבצים שאני מכיר לעומק על פני 45 שמפוזרים. אותה סיבה בדיוק לכך שאין `roles/` ב-Ansible: role בלי שימוש חוזר הוא תיקייה מיותרת. |
| "איך אתה מונע ממנו לזהות שגיאות מהגרסה הישנה?" | חלון החיפוש הוא 5 דקות וה-cooldown 10 דקות. עד שה-cooldown נגמר, השגיאות של הגרסה השבורה כבר יצאו מהחלון. ה-aggregation לפי `version` מאשר את זה בלוג. |

### 13.5 מה הייתי עושה אחרת בייצור

זה שקף שמראה בגרות. ארבע נקודות:

1. **Kaniko במקום `docker.sock`** — Jenkins עם גישה ל-socket הוא בפועל root על המארח. Kaniko בונה תמונות בתוך Pod בלי daemon.
2. **Remote state עם נעילה** — S3 + DynamoDB, או Terraform Cloud.
3. **GitOps במקום push-based CD** — ArgoCD או Flux מושכים מהריפו במקום ש-Jenkins ידחוף. ה-`kubectl set image` מ-Jenkins עובד, אבל הוא אומר שיש credential של האשכול אצל ה-CI.
4. **Prometheus + Alertmanager לצד ELK** — ELK מצוין ללוגים. למטריקות ולהתראות, Prometheus הוא הכלי. ה-healer היה מגיב ל-alert של Alertmanager במקום לתשאל ES.

### 13.6 הרחבות אופציונליות לציון גבוה

| הרחבה | מאמץ | מה זה מוסיף |
|---|---|---|
| **HorizontalPodAutoscaler** | נמוך | `kubectl autoscale deployment eventus-api --cpu-percent=70 --min=2 --max=6`. metrics-server כבר מותקן. הרצת עומס מראה scale-up חי. |
| **NetworkPolicy** | נמוך | חסימת תעבורה ל-Mongo מכל מקום חוץ מ-`app=eventus-api`. ⚠️ דורש CNI תומך — K3s עם flannel לא אוכף. תצטרכו `--flannel-backend=none` + Calico. |
| **Trivy בצינור** | נמוך | `trivy image --exit-code 1 --severity CRITICAL $IMAGE` כשלב ב-Jenkins. סריקת אבטחה ב-CI. |
| **PodDisruptionBudget** | נמוך | `minAvailable: 1`. מבטיח שגם בתחזוקה יש Pod אחד. |
| **Terraform module** | בינוני | לארוז את האפליקציה כ-module עם משתנים. מראה חשיבה על שימוש חוזר. |
| **`ansible-lint` + `tflint` ב-CI** | בינוני | Linting של קוד תשתית. |
| **Prometheus + Grafana** | גבוה | סטאק מטריקות מלא. ⚠️ **שקלו היטב** — המרצה כבר אמר שיש יותר מדי דברים. |

⚠️ **המלצה:** קחו את **Trivy** ואת ה-**HPA** בלבד. שניהם זולים, שניהם מרשימים בדמו, ושניהם משתלבים בסיפור הקיים בלי להוסיף רכיב חדש. השאר — הזכירו כ"מה הלאה" בשקף האחרון.

---

## 14. גיליון פקודות מהיר

### 14.1 הפעלה מאפס אחרי אתחול מחשב

```bash
# 1. WSL עולה עם systemd, K3s ו-Docker עולים לבד
wsl -d Ubuntu-EventUs

# 2. Jenkins עולה לבד בגלל --restart unless-stopped
docker ps --filter name=jenkins

# 3. תמונת מצב
cd ~/projects/EventUs
./eventus.sh status
```

אם K3s לא עלה:

```bash
sudo systemctl status k3s
sudo systemctl start k3s
```

### 14.2 יומיומי

```bash
cd ~/projects/EventUs

./eventus.sh status          # תמונת מצב מלאה
./eventus.sh traffic         # מחולל תעבורה, Ctrl+C לעצירה
./eventus.sh break           # דוחף את הרגרסיה המכוונת
./eventus.sh reset           # מחזיר ומאפס את הקירור
./eventus.sh kubeconfig      # מייצר מחדש את ה-kubeconfig של Jenkins
./eventus.sh kibana          # יוצר את ה-Data View בקיבנה
```

`RATE` שולט בשיעור השגיאות של ה-`break`, ו-`BASE` בכתובת הבסיס:

```bash
RATE=0.6 ./eventus.sh break
BASE=http://10.0.0.5 ./eventus.sh traffic
```

פקודות `kubectl` שכדאי לזכור:

```bash
# לוגים של האפליקציה
kubectl -n eventus logs -l app=eventus-api --tail=50 -f

# לוגים של הריפוי העצמי
kubectl -n platform logs -l app=eventus-healer --tail=30

# היסטוריית גרסאות
kubectl -n eventus rollout history deployment/eventus-api

# איזו תמונה רצה עכשיו
kubectl -n eventus get deploy eventus-api \
  -o jsonpath='{.spec.template.spec.containers[0].image}'

# גלגול אחורה ידני
kubectl -n eventus rollout undo deployment/eventus-api

# אירועי ריפוי עצמי
kubectl -n eventus get events --field-selector reason=SelfHealRollback

# איפוס הקירור
kubectl -n eventus annotate deploy/eventus-api eventus.io/last-rollback-

# הרצת ה-healer ידנית, מיד
kubectl -n platform create job healer-now --from=cronjob/eventus-healer
kubectl -n platform logs job/healer-now
```

### 14.3 Terraform

```bash
cd ~/projects/EventUs/infra/terraform

terraform fmt -recursive
terraform validate
terraform plan
terraform apply

# מה Terraform מנהל
terraform state list

# פרטים על משאב אחד
terraform state show kubernetes_deployment_v1.api

# ⚠️ אם Jenkins פרס והתג ב-plan נראה שונה — זה תקין,
# lifecycle.ignore_changes אמור להסתיר את זה. אם הוא כן מופיע — בדקו את הנתיב ב-ignore_changes.
```

### 14.4 Elasticsearch

```bash
ES="kubectl -n observability exec statefulset/elasticsearch --"

# בריאות
$ES curl -s "localhost:9200/_cluster/health?pretty"

# אינדקסים
$ES curl -s "localhost:9200/_cat/indices/eventus-logs-*?v"

# 5 השגיאות האחרונות
$ES curl -s -XPOST "localhost:9200/eventus-logs-*/_search?pretty" \
  -H 'Content-Type: application/json' -d '{
  "size": 5,
  "sort": [{"@timestamp": "desc"}],
  "query": {"range": {"statusCode": {"gte": 500}}},
  "_source": ["@timestamp", "path", "statusCode", "version"]
}'

# שגיאות לפי גרסה — בדיוק מה שה-healer מריץ
$ES curl -s -XPOST "localhost:9200/eventus-logs-*/_search?pretty" \
  -H 'Content-Type: application/json' -d '{
  "size": 0,
  "query": {"bool": {"filter": [
    {"term": {"service.keyword": "eventus-api"}},
    {"range": {"@timestamp": {"gte": "now-5m"}}},
    {"range": {"statusCode": {"gte": 500}}}
  ]}},
  "aggs": {"by_version": {"terms": {"field": "version.keyword"}}}
}'

# ⚠️ מחיקת אינדקס (רק אם הוא נשבר)
$ES curl -s -XDELETE "localhost:9200/eventus-logs-2026.08.23"
```

### 14.5 בנייה ופריסה ידנית (בלי Jenkins)

```bash
cd ~/projects/EventUs
DOCKER_USER=galhillel
SHA=$(git rev-parse --short HEAD)
TAG="${SHA}-manual"

docker build --build-arg APP_VERSION="${TAG}" \
  -t "${DOCKER_USER}/eventus-api:${TAG}" backend/event-us
docker push "${DOCKER_USER}/eventus-api:${TAG}"

kubectl -n eventus set image deployment/eventus-api "api=${DOCKER_USER}/eventus-api:${TAG}"
kubectl -n eventus rollout status deployment/eventus-api --timeout=180s
```

### 14.6 ניקוי

```bash
# מוחק את Jenkins בלבד (ה-volume נשאר)
docker rm -f jenkins

# ⚠️ מוחק את כל האשכול
cd ~/projects/EventUs/infra/terraform
terraform destroy

# ⚠️ מוחק את K3s לגמרי
/usr/local/bin/k3s-uninstall.sh
```
---

## 15. סיכום — סדר הביצוע המלא

```mermaid
graph TD
    A["1. הכנת WSL2<br/>.wslconfig, wsl.conf, sysctl<br/>~30 דק'"] --> B["2. שכפול הריפו<br/>מבנה infra/<br/>~20 דק'"]
    B --> C["3. שינויי NestJS<br/>platform.ts + Dockerfile<br/>~1.5 שעות"]
    C --> D["4. בדיקה מקומית<br/>docker build + run<br/>~30 דק'"]
    D --> E["5. שינויי Android<br/>2 שורות + BuildConfig<br/>~30 דק'"]
    E --> F["6. Ansible<br/>playbook יחיד, 19 משימות<br/>~1.5 שעות"]
    F --> G["7. דחיפת תמונת seed<br/>~15 דק'"]
    G --> H["8. Terraform<br/>main.tf, 30 משאבים<br/>~3 שעות"]
    H --> I["9. ה-healer<br/>Python + Bash + Docker<br/>~2 שעות"]
    I --> J["10. Jenkins<br/>תמונה, RBAC, Jenkinsfile<br/>~2 שעות"]
    J --> K["11. Kibana<br/>Data View + דשבורד<br/>~1 שעה"]
    K --> L["12. חזרות על הדמו<br/>~2 שעות"]
    L --> M["13. מצגת + הקלטה<br/>~3 שעות"]
```

**סה"כ: 14-18 שעות.** אל תדחסו לסוף שבוע אחד — Elasticsearch וה-networking של WSL ידרשו זמן ניפוי שקשה לחזות.

---

### הערה אחרונה

הפרויקט הזה לא מרשים בגלל מספר הכלים. הוא מרשים בגלל שלוש נקודות:

1. **החלטה להוריד רכיבים** — Nginx ו-Logstash ירדו כי הם לא הוסיפו יכולת. זו בגרות הנדסית, וזה בדיוק מה שהמרצה סימן.
2. **הפרדת אחריות נקייה** — Ansible על מערכת ההפעלה, Terraform על ה-API, Jenkins על הגרסה. כל אחד בתחומו, עם `ignore_changes` שמונע התנגשות.
3. **מנגנון שפותר בעיה אמיתית** — Pod בריא שמחזיר 500 הוא התקלה שאף כלי סטנדרטי לא תופס. הפתרון בנוי על שני תנאים כמותיים, cooldown והרשאות מזעריות — לא על סקריפט תמים שעושה rollback על השגיאה הראשונה.

בהצלחה.

