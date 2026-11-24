# AI & Developer Coding Rules for RapidResQ

1. **Architecture:** Keep strict MVVM separation (`ui/` -> `viewmodel/` -> `data/repository/`). Never put business logic inside Composable UI functions.
2. **Repository Abstraction:** Always access data through `IncidentRepository` so local demo data swaps cleanly to remote API calls in Phase 5.
3. **Security First:** Never hardcode API keys or secrets in the Android app. Never use sequential guessable Incident IDs. Clearly label all mock/demo data.
4. **Scope Discipline:** Do not modify unrelated files when adding a new feature. Keep changes modular and small.
5. **Git Safety:** Always commit working code before running multi-file AI agent edits, and review diffs before accepting.
6. **Emergency-First Access:** Never gate Citizen emergency reporting behind mandatory login or OTP; always preserve a 1-tap Guest SOS path.
7. **Role Discipline:** Maintain 4 core login roles (Citizen, Dispatcher, Responder, Admin). Represent multi-agency entities (EMS, Fire, Traffic, Security, Campus Guard) as Unit Specializations and Departments managed by Admin rather than creating fragmented login roles.
8. **Role-Scoped Server-Side AI:** All AI triage and per-user AI chat features execute exclusively on the backend server (Phase 8+) with strict RBAC context filtering—never call Gemini directly from the Android client or embed API keys in the APK.