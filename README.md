# RapidResQ — Real-Time Emergency Incident Coordination Platform

**Report. Respond. Resolve. In Real Time.**

RapidResQ is a mobile-first, AI-assisted emergency incident reporting, verification, post-GIS dispatch, and field coordination platform built with **Kotlin + Jetpack Compose** for Android and powered by a **Node.js + Express + Socket.IO + Supabase (PostgreSQL + PostGIS)** production backend.

> **Disclaimer:** RapidResQ is a coordination and rapid-response platform for managed ecosystems and academic research—not a replacement for official government emergency services (e.g., Dial 112).

---

## 👥 Team & Academic Info
- **Team:** Team DataDrift — **Apoorv Jain, Rajkaran Singh, Chirag Kamariya**
- **Department:** CSIT / Department of Cyber Security, Acropolis Institute of Technology & Research (AITR), Indore
- **Guide:** Prof. Vandana Kate
- **Production API:** `https://rapidresq-api-3x8n.onrender.com`

---

## 🚀 Version 1.0.0 Release Summary
RapidResQ v1.0.0 delivers an end-to-end, enterprise-grade emergency response pipeline designed for zero friction, high-speed telemetry, and uncompromised security.

### 🛡️ Core Security & Architectural Pillars
1. **Emergency-First Citizen Guest SOS (Rule 6):** Citizen emergency reporting is **NEVER** gated behind login or OTP. 1-tap Guest SOS enables immediate submission with GPS and media.
2. **Multi-Role 2-Step Gmail OTP Authentication:** All authenticated accounts across all 4 roles (`CITIZEN`, `DISPATCHER`, `RESPONDER`, `ADMIN`) are protected by 2-step Email OTP MFA via pooled SMTP connection delivery.
3. **Human-in-the-Loop Verification before Auto-Dispatch:** Every report enters `REPORTED` status first for human dispatcher verification before optional PostGIS nearest-unit auto-dispatch triggers.
4. **SHA-256 Tamper-Evident Hash-Chained Audit Logs:** Every operational action computes a cryptographic hash chain (`record_hash = sha256(prevHash + rowData)`) with instant on-demand verification (`GET /api/admin/audit/verify`).
5. **Server-Side Gemini AI Suite (Rule 8):** Multimodal AI Triage (summary, suggested severity, recommended unit, spam risk score) and Role-Scoped AI Assistant Chat executing strictly on the backend with strict RBAC context isolation. Never exposes API keys in the APK.
6. **Hardware GPS, OpenStreetMap & Persistent Offline Queue:** High-accuracy location capture, reverse geocoding, live responder unit tracking via `osmdroid`, and automatic SQLite queue replay when connectivity transitions online.

---

## 🏗️ Technical Architecture

### Android App
- **Language & Framework:** Kotlin 2.1.0, Jetpack Compose, Material 3 (Obsidian Dark Command Theme)
- **Architecture:** Clean MVVM with `StateFlow` reactive flows
- **Networking & Sockets:** OkHttp 4.12.0, Socket.IO Client 2.1.0 over Production HTTPS/WSS
- **Maps & Telemetry:** osmdroid 6.1.18 (OpenStreetMap), Google Play Services Location 21.3.0
- **Offline Persistence:** Android SQLite (`SQLiteOpenHelper`)
- **Session Security:** Android Keystore AES-256-GCM encrypted session storage (`KeystoreSessionManager`)
- **Media Loading:** Coil 2.7.0 with video frame decoder

### Backend Service (Render Deployment)
- **Runtime:** Node.js (ES Modules), Express 4.21, Socket.IO 4.8
- **AI Triage & Chat:** `@google/genai` (v1.x) with `gemini-3.5-flash-lite`
- **Database:** Supabase (PostgreSQL + PostGIS spatial functions)
- **Security & Utilities:** `bcryptjs` (salted hashing), `jsonwebtoken` (JWT), `helmet`, `cors`, `express-rate-limit`, `nodemailer` (connection pooled SMTP)

---

## 📋 Role Capabilities & User Flow

```
   [Citizen] ---> 1-Tap SOS / Login ---> Submit Report + GPS + Media
                                              |
   [Dispatcher Command] <--- Real-Time Socket Notification <--- /
            |
      Human Verification (Verify / Reject)
            |
   [Auto-Dispatch Engine (PostGIS Ranking) OR Manual Unit Assignment]
            |
   [First Responder Unit] <--- Mobile Push / Socket Alert
            |
      Accept ---> En Route ---> On Site ---> Responding ---> Resolve
                                              |
   [Admin Governance] <--- SHA-256 Hash Chain Audit + Live Analytics
```

### 1. Citizen Portal
- 1-tap Guest Emergency SOS or authenticated Citizen login with 2-step OTP.
- 4 emergency categories: *Road Accident*, *Fire / Hazard*, *Medical Emergency*, *Public Safety*.
- Hardware GPS location capture with reverse geocoding and interactive map pin adjustment.
- Photo and short video evidence picker.
- Live incident tracking screen with vertical progress timeline and responder unit map tracking.

### 2. Dispatcher Command Center
- Live incident queue with status filter chips (`Active`, `All`, `Pending`, `Verified`, `Rejected`, `Resolved`).
- Real-time Socket.IO alerts and 60-second dispatch escalation timer.
- Human-in-the-loop verification, quick-reason rejection modal, and PostGIS nearest-unit distance ranking.
- Auto-Dispatch Engine toggle (`ON`: Auto-assigns nearest unit immediately after verification; `OFF`: Manual assignment).
- Full interactive **Live Map** tab showing all active incidents and units across Indore.

### 3. First Responder Field Unit
- Unit status & privacy-conscious **Share Live Unit GPS** duty toggle.
- Active mission card displaying incident pin, location description, and field note input.
- Finite-State Machine (FSM) status progression (`Assigned` → `Accepted` → `En Route` → `On Site` → `Responding` → `Resolved`).

### 4. Admin Governance Dashboard
- 5 comprehensive management tabs: `Incidents`, `Units`, `Personnel`, `Audit Log`, `Analytics`.
- Provisioning modals for adding response units and creating staff accounts (`Admin`, `Dispatcher`, `Responder`).
- Interactive role editor to promote/reassign personnel roles on demand.
- Cryptographic **SHA-256 Audit Chain Verification** button detecting any database tampering.
- Live operational analytics cards for incident categories, severities, escalations, and unit performance.

---

## 🛠️ Installation & Local Setup

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- Android SDK 35 / API 26+ device or emulator
- Node.js 18+ (for local backend development)

### Running the Android App
1. Open the project root folder in Android Studio.
2. Allow Gradle sync to complete.
3. Connect an Android device with USB Debugging enabled (or start an emulator).
4. Run the project (`Shift + F10`). The app connects automatically to the live production HTTPS backend on Render (`https://rapidresq-api-3x8n.onrender.com`).

---

## 📄 License & Academic Attribution
Developed by **Team DataDrift** at Acropolis Institute of Technology & Research (AITR), Indore.
For academic, research, and non-commercial emergency coordination demonstration.
