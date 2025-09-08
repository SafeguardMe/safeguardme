# 🛡️ SafeguardMe

**SafeguardMe** is a modern, AI-assisted personal safety app designed for real-time crisis response, emergency contact coordination, and AI-powered protection. Built with Kotlin and Jetpack Compose, powered by Firebase and ML Kit, SafeguardMe empowers users with tools to document, report, and respond to safety incidents swiftly and intelligently.

Authors: 
Chidi MT
Mahlangu I
Matshehla TM
Makuwa RL
Lebea N

---

## 📱 Features


### ✅ Core Features

- **Secure Login & Registration**
  - Firebase Authentication (email/password)
  - Password reset support
- **Safety Status Toggle**
  - Set your current safety status (Safe, At Risk, Emergency)
  - Auto-sync with cloud and emergency contacts
- **Emergency Contacts**
  - Add, edit, delete trusted contacts
  - Automatically notify them in case of incidents
- **Incident Reporting**
  - Report incidents with photo, description, location, and type
  - Optional anonymous submission
- **Incident History**
  - View previous reports
  - Filter by date or severity
- **Voice-Activated Trigger**
  - Set a secret keyword (e.g., "safeword") to trigger emergency protocol
  - ML Kit-powered voice recognition
- **AI Assistance** (Planned)
  - Provide intelligent suggestions in a crisis
  - NLP-based help assistant

---

## 🔐 Security & Permissions

| Feature                  | Permissions Required                         |
|--------------------------|----------------------------------------------|
| Location Tracking        | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| Emergency Calling        | `CALL_PHONE`                                 |
| Audio Trigger            | `RECORD_AUDIO`                               |
| Media Uploads            | `READ_MEDIA_IMAGES`, `CAMERA`                |
| Firebase Cloud Sync      | `INTERNET`, `ACCESS_NETWORK_STATE`           |

All sensitive screens support future enhancements like biometric authentication.

---

## 🧰 Tech Stack

| Layer               | Stack                                                                 |
|---------------------|-----------------------------------------------------------------------|
| Language            | Kotlin                                                                |
| UI Framework        | Jetpack Compose                                                       |
| Architecture        | MVVM + Repository Pattern + DI (Hilt)                                 |
| Backend             | Firebase (Firestore, Auth, Storage)                                   |
| Realtime Features   | ML Kit (Voice Trigger), Firebase Sync                                |
| Navigation          | Jetpack Navigation (Compose)                                          |
| Storage             | Jetpack DataStore for settings                                        |
| Testing             | JUnit, MockK, Espresso (Planned)                                      |

---

## 🔧 Local Development

### ✅ Prerequisites

- Android Studio Hedgehog or newer
- Kotlin 1.9+
- Java 17
- Firebase Project with Authentication + Firestore + Storage enabled

### 🔨 Setup Steps

1. **Clone the Repository**
   ```bash
   git clone https://github.com/SafeguardMe/safeguardme.git
   cd safeguardme
2. Add Firebase Config

Place your google-services.json in the app/ directory.

Run the App

./gradlew clean assembleDebug
Optional: Enable ML Kit

4. Add voice trigger keywords in TriggerScreen.kt.

Enable ML Kit speech models in Firebase.


🙋 Contributing
Pull requests are welcome! Please follow our architecture, write clear commit messages, and ensure new code has sufficient test coverage.

1. Fork the repo
2. Create your feature branch: git checkout -b feature/my-feature
3. Commit your changes: git commit -am 'Add feature'
4. Push to the branch: git push origin feature/my-feature
5. Open a pull request

🛡️ License
This project is licensed under the MIT License - see the LICENSE file for details.
