# RapidResQ — Real-Time Emergency Incident Coordination Platform

**Report. Respond. Resolve. In Real Time.**

RapidResQ is a mobile-first, AI-assisted emergency incident reporting, verification, dispatch, and field coordination platform built with **Kotlin + Jetpack Compose**.

> **Disclaimer:** RapidResQ is a coordination and rapid-response platform for managed ecosystems and academic research—not a replacement for official government emergency services (e.g., Dial 112).

## Team & Academic Info
- **Team:** Team DataDrift — Apoorv Jain, Rajkaran Singh, Chirag Kamariya
- **Department:** CSIT / Department of Cyber Security, Acropolis Institute of Technology & Research, Indore
- **Guide:** Prof. Vandana Kate

## Current Build Status: Phase 1 (Interactive Core Prototype)
- **Implemented (Local Demo State):** Role-based mobile UI (Citizen, Dispatcher, Responder), 4 core emergency categories, photo/short-video attachment picker, non-guessable Incident IDs, live in-app Report → Verify/Reject → Assign → Accept → Respond → Resolve workflow, Citizen live tracking timeline, and incident audit trail.
- **Planned (Upcoming Phases):** Persistent Supabase (PostgreSQL + PostGIS) backend, Node.js + Express + Socket.IO API, Multi-Factor Auth (OTP + Password Reset), Admin Multi-Agency Governance, Role-Scoped AI Assistant Chat, real GPS/OSM live maps, offline Room queue, and server-side Gemini API multimodal triage.

## Tech Stack
- **Mobile App:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, MVVM (`StateFlow`), Coil
- **Planned Backend:** Node.js, Express, Socket.IO, Supabase (PostgreSQL + PostGIS), Firebase Cloud Messaging, Gemini API

## Running Locally
1. Open this folder in Android Studio.
2. Let Gradle sync complete.
3. Connect an Android device (USB Debugging enabled) or start an Android Emulator (API 26+).
4. Press **Run (`Shift + F10`)**.