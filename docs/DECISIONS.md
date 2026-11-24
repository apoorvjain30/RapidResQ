# Architecture Decision Records (ADR)

* **ADR-01:** Native Android (Kotlin + Jetpack Compose + MVVM Repository Pattern) over web-only or multi-codebase apps for optimal performance, smooth camera/location integrations, and better offline capabilities.
* **ADR-02:** Dispatcher-led incident verification/rejection instead of an IT Admin bottleneck. Dispatchers possess the domain expertise needed to triage live emergencies accurately in real-time.
* **ADR-03:** 4 Core Roles + Multi-Agency Departments/Specializations, with mandatory Staff MFA (Password + 6-Digit OTP + Password Reset) and Citizen Guest SOS bypass. This ensures low friction for emergencies alongside high security and accountability for responders and dispatchers.
* **ADR-04:** Server-side, RBAC-scoped Gemini AI Chat & Multimodal Triage scheduled for Phase 8 (after cloud persistence and auth are finalized). Never call Gemini directly from the Android client or embed API keys in the APK.