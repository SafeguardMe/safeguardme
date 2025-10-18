# AI Agent Expansion & Quality Playbook

## Current Objectives
- Stabilise Live AI responses by ensuring OpenAI credentials, serialization, and Gradle sync are in place.
- Extend the assistant so it can detect and trigger emergency workflows, personalise guidance, and compile shareable reports.
- Outline advanced, trauma-informed capabilities that reinforce user safety and trust.
- Provide a repeatable checklist for collecting code-quality metrics inside Android Studio and via Gradle.

## AI Agent Capability Blueprint
1. **Safety Trigger Detection & Response**
   - Subscribe to the existing `SafetyMonitoringService` gesture events (shake, power, volume).
   - Surface high-risk signals to the assistant view model; auto-inject a "panic" message when thresholds are met.
   - Allow the assistant to queue follow-up steps (notify contact, start recording, escalate to 112/10111) using repository callbacks.

2. **Personalised Guidance from User Profiles**
   - Pull `User` profile, safety plans, and preferred contacts from Firestore on assistant launch.
   - Pass a redacted context block into the OpenAI system prompt (never include raw identifiers without consent).
   - Cache opt-in preferences (language, trusted terms, accessibility needs) and mirror them in assistant replies.

3. **Evidence & Report Generation**
   - Aggregate `SafetySession` history, risk scores, and evidence attachments.
   - Offer export presets: quick summary (SMS/email) and detailed PDF for case workers.
   - Gate downloads behind a biometric/pin check and encrypt generated files at rest.

4. **Additional Nice-to-Haves**
   - Proactive wellness check-ins driven by calendar/usage patterns.
   - Multi-lingual (isiZulu, isiXhosa, Sesotho) support with offline fallbacks.
   - Incident timeline visualisation combining map pins, audio transcripts, and chat snippets.
   - Auto-triage inbox where professionals can leave feedback or resources for the user.

## Architecture Notes
- Keep AI orchestration inside `AIAssistantRepository`; expose sealed `AssistantAction` objects so the UI/service layer can execute triggers without parsing text.
- Centralise sensitive operations (location, contacts, recordings) behind a `SafetyOrchestrator` that enforces permission checks and auditing.
- Retain an offline-first fallback so every AI-driven action has a deterministic alternative in case the model is unreachable.
- Log only anonymised analytics; never persist raw prompts/responses without explicit user consent.

## Code Quality & Metrics Workflow
### Android Studio (Hedge UI)
- `Analyze ▸ Inspect Code…` → scope `app` module → run default profile for lint, code style, and performance suggestions.
- `Code ▸ Analyze Code Cleanup…` to auto-apply safe formatting fixes.
- `Build ▸ Analyze APK` after a release build to review baseline size and ProGuard mappings.
- `View ▸ Tool Windows ▸ App Quality Insights` to pull Firebase Crashlytics / Android Vitals once linked.

### Gradle Tasks (CLI)
- `./gradlew lint` – runs Android + Compose lint checks, outputs HTML at `app/build/reports/lint/lint.html`.
- `./gradlew testDebugUnitTest` – executes JVM unit tests; merge with `connectedDebugAndroidTest` for instrumentation when a device/emulator is attached.
- `./gradlew ktlintCheck` (add `org.jlleitschuh.gradle.ktlint` plugin if not yet applied) – enforces Kotlin style.
- `./gradlew detekt` (after applying the Detekt plugin) – deeper static analysis; configure baseline in `config/detekt/detekt.yml`.
- `./gradlew jacocoTestReport` – collect code coverage; wire into CI for gating thresholds (e.g., 70%).

### Metrics Dashboarding
- Export lint/detekt/Jacoco XML artefacts into your CI (GitHub Actions, Bitrise) and archive with each build.
- Use Android Studio `Profiler` and `Layout Inspector` during manual runs to capture performance snapshots before/after AI changes.
- Track bundle size, cold-start time, and memory footprint in a release checklist to avoid regressions from AI dependencies.

## Review Checklist Before Shipping
- [ ] AI flows succeed offline with cached FAQs when OpenAI is unavailable.
- [ ] Emergency actions respect runtime permissions and fail gracefully with user-visible messaging.
- [ ] Sensitive transcripts and reports are encrypted, redacted, and auto-expire if unused.
- [ ] Lint, tests, Detekt/Ktlint, and coverage gates all pass in CI and locally.
- [ ] Documentation and training materials are updated for support staff and end users.

---
**Next Steps:** finish wiring `AssistantAction` handling in the service layer, verify gesture observers on-device (Android 13 & 14), and pilot the report export workflow with a trusted tester group.
