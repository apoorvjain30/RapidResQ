# AI & Developer Coding Rules for RapidResQ

1. **Architecture:** Keep strict MVVM separation (`ui/` -> `viewmodel/` -> `data/repository/`). Never put business logic inside Composable UI functions.
2. **Repository Abstraction:** Always access data through `IncidentRepository` so local demo data swaps cleanly to remote API calls in Phase 5.
3. **Security First:** Never hardcode API keys or secrets in the Android app. Never use sequential guessable Incident IDs. Clearly label all mock/demo data.
4. **Scope Discipline:** Do not modify unrelated files when adding a new feature. Keep changes modular and small.
5. **Git Safety:** Always commit working code before running multi-file AI agent edits, and review diffs before accepting.