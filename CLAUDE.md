# SSH Client - Claude Code Instructions

## Project Overview
Android SSH client with tmux session support. Kotlin + Jetpack Compose.

## Version Management
- Current version: `0.1.75` (app/build.gradle.kts の versionCode / versionName)
- **修正依頼のたびにパッチバージョンを上げる** (0.1.0 → 0.1.1 → 0.1.2 ...)
- versionCode もインクリメントする (1 → 2 → 3 ...)
- ビルド成功後に `/release` スキルでリリースする

## Build
```bash
./gradlew assembleDebug
```

## Project Structure
- `app/` - Main Android app (Kotlin, Jetpack Compose)
- `terminal-emulator/` - Local module copied from termux-app (Java)
- SSH: `app/.../ssh/` - SshSessionManager, ConnectionStore, SshConnectionConfig
- Terminal: `app/.../terminal/` - TerminalSession (bridges SSH I/O to emulator)
- UI: `app/.../ui/terminal/` - TerminalView (custom View), TerminalScreen, ModifierKeyBar
- UI: `app/.../ui/connect/` - ConnectScreen, ConnectViewModel
- UI: `app/.../ui/tmux/` - TmuxSessionListScreen
- Navigation: `app/.../navigation/AppNavigation.kt`
- tmux: `app/.../tmux/` - TmuxSessionInfo (data class for session listing)
- Updater: `app/.../updater/` - AppUpdater (in-app update from GitHub Releases)

## Key Architecture Decisions
- TerminalView is a custom Android View (not Compose) for soft keyboard via onCreateInputConnection
- Thread sync: `synchronized(session.lock)` between IO read thread and UI draw thread
- PTY resize deferred until View is measured (onSizeChanged)
- tmux sessions managed via exec channels; terminal uses plain `tmux attach-session` (not -CC control mode)
- Terminal layout: Column (tab bar → terminal → shortcut bar) to avoid overlap
- BouncyCastle provider registered in SshClientApp.onCreate()

## Tests
```bash
./gradlew test
```
- (currently no unit tests)

## Release
`/release` スキルを使用する。
