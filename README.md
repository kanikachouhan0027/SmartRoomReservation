# Smart Room Reservation with AI (Android)

A native Android app for booking meeting rooms, seminar halls, labs and discussion rooms on a campus or in an office.
Built with **Kotlin + Jetpack Compose (Material 3)**, **MVVM** and a local **SQLite** database, so it works fully offline.

## Features

| Area | What it does |
|---|---|
| Accounts | Register and log in. Passwords are stored as hashes, and the app remembers who is logged in |
| Live availability | The home screen shows which rooms are free right now |
| Search & filters | Search by room or building, and filter by amenity or "Free now" |
| Room details | Capacity, location, amenities and the bookings for any day |
| Booking | Pick a date and times, with a live "room is free / already booked" check |
| Room finder | Enter the time, number of people and amenities; you see only free rooms, smallest suitable first |
| No double booking | A room can never be booked twice for overlapping times |
| My bookings | Upcoming and history tabs; cancel a booking |
| Admin panel | Simple counts, add or remove rooms, view or cancel any booking |
| **AI Room Assistant** | Type or speak a request like "book a room for 6 people tomorrow at 3 pm with a projector". Google Gemini understands it, and the app shows free rooms with a Book button |
| **AI usage insights** | Admin taps one button; Gemini reads the last 30 days of booking numbers and explains them, with suggestions |

### Booking rules (in `util/BookingRules.kt`)
- Rooms are open 8:00 AM – 9:00 PM
- A booking lasts 15 minutes to 4 hours, on one day, at most 30 days ahead
- The number of attendees must fit the room's capacity

## Turning on the AI (free, 2 minutes)
1. On a laptop or phone, open **aistudio.google.com** and sign in with a Google account.
2. Click **Get API key → Create API key**, then copy the key.
3. In the app go to **Profile → AI settings**, paste the key and tap **Save AI settings**.
4. Open the **AI** tab and try: *"Book a room for 4 people tomorrow at 11 am with a whiteboard"*.

The key is stored only on the phone (SharedPreferences), not in the code or on GitHub. The model name defaults to `gemini-3.8-flash` and can be changed in the same screen. The AI needs internet; everything else works offline.

## Demo accounts
| Role | Email | Password |
|---|---|---|
| Student / member | `student@campus.edu` | `student123` |
| Admin | `admin@campus.edu` | `admin123` |

The login screen has buttons that fill these in. Eight sample rooms and a few bookings are added on first launch.

## Getting the APK

### Option A: GitHub (no software needed)
1. Create a new GitHub repository and upload this whole folder (including the hidden `.github` folder).
2. Open the repo's **Actions** tab. The **Build APK** workflow runs automatically, taking about 5–8 minutes.
3. Open the finished run, scroll to **Artifacts** and download **SmartRoom-APK**.
4. Unzip it. `app-release.apk` is the file to install.

### Option B: Android Studio
1. Open Android Studio, choose **File → Open** and select this folder.
2. Wait for Gradle sync to finish. It downloads dependencies the first time.
3. Run the app on an emulator or phone with ▶, or use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. The APK is written to `app/build/outputs/apk/`.

### Installing on a phone
Copy the APK to the phone and open it. Allow "Install unknown apps" for your file manager or browser if Android asks. The app needs Android 8.0 or newer.

> The release APK is signed with the standard debug key so it installs right away. Before publishing on the Play Store, create your own keystore (**Build → Generate Signed Bundle/APK**).

## Project structure
```
app/src/main/java/com/smartroom/reserve/
├── MainActivity.kt              # entry point
├── data/
│   ├── Models.kt                # User, Room, Booking, BookingStatus, AdminStats
│   ├── DbHelper.kt              # SQLite schema + seed data
│   └── Repository.kt            # all queries, clash check, room finder, admin counts, AI usage report
├── ai/
│   ├── GeminiClient.kt          # sends the HTTPS request to Gemini, reads the answer
│   └── AiAssistant.kt           # prompt for the assistant + turns the AI's JSON into a room search
├── util/
│   ├── BookingRules.kt          # booking policies (unit-tested)
│   ├── TimeUtils.kt             # date/time helpers
│   └── Security.kt              # password hashing
└── ui/
    ├── AppViewModel.kt          # MVVM ViewModel (StateFlow)
    ├── AppNavigation.kt         # navigation + bottom bar
    ├── theme/Theme.kt
    ├── components/Components.kt # shared cards, chips, pickers
    └── screens/                 # Auth, Home, RoomDetail, Booking, FindRoom, MyBookings, Admin, Profile
app/src/test/.../BookingRulesTest.kt  # 11 unit tests for the booking rules
```

## Database schema
- **users** (id, name, email UNIQUE, password_hash, is_admin)
- **rooms** (id, name, building, floor, capacity, amenities, description, active)
- **bookings** (id, room_id → rooms, user_id → users, start_time, end_time, purpose, attendees, status, created_at)
  - status: `BOOKED` or `CANCELLED`

## Tech stack
Kotlin 2.0 · Google Gemini API (REST) · Jetpack Compose (BOM 2024.09) · Material 3 · Navigation Compose · ViewModel + StateFlow · Coroutines · SQLite · JUnit · minSdk 26 / targetSdk 35

## Ideas for extension
- Firebase/Firestore backend so all phones share one live database
- Check-in with a QR code on the room door, and auto-release of rooms when nobody turns up
- IoT occupancy sensor (PIR/ESP32) showing whether a room is really in use
- Push reminders before a booking starts
- Recurring bookings (e.g. every Monday)
