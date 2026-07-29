# Telegram Bot Builder — Starter Project

This is a real, working Android Studio project skeleton implementing the CORE
engine described in the build spec: Telegram token setup, keyword-based
auto-replies (multi-keyword per rule), AI fallback (OpenAI/Gemini/Anthropic/
OpenRouter/Custom) with auto-injected reply data into the system prompt,
encrypted secret storage, Room database, and a foreground polling service.

## What's implemented (real, compiling code)
- `data/local/` — Room entities (ReplyRule, ConversationLog, KnownContact), DAOs, database, encrypted SecureStore
- `data/remote/` — Telegram Retrofit API client, multi-provider AI adapter (OpenAI/Gemini/Anthropic/OpenRouter/Custom)
- `data/repository/BotRepository.kt` — the full message pipeline: match rules → AI fallback → send → log, plus system-prompt auto-injection
- `service/BotPollingService.kt` — foreground service running the long-polling loop with backoff
- `ui/MainActivity.kt` + layout — minimal working dashboard: paste token, save, start/stop bot

## What's NOT yet built out (per the original spec — you or Claude Code should extend this)
- Auto Replies screen UI (add/edit/delete rules) — the data layer is ready, needs a RecyclerView screen
- AI Settings screen UI — SecureStore fields already exist, needs a form screen
- Conversation Logs screen UI — DAO ready, needs a RecyclerView + search
- Broadcast screen — KnownContactDao ready, needs compose/send UI
- Settings screen (backup/restore, theme toggle)
- Play Billing / freemium tier enforcement
- App icon, launcher mipmap assets

## How to open and build
1. Install Android Studio (Hedgehog or newer)
2. File → Open → select the `TelegramBotBuilder` folder
3. Let Gradle sync (it will download dependencies — needs internet)
4. Run on an emulator or physical device (Build → Build Bundle/APK → Build APK(s) for a standalone .apk)

## Recommended next step
Feed this project + the original build spec into Claude Code — it can run the
actual Gradle build, catch compile errors, and finish the remaining screens
listed above end-to-end.
