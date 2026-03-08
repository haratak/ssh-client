---
name: release
description: Build, version bump, commit, push, and upload APK to GitHub Releases
disable-model-invocation: true
---

# Release Workflow

1. app/build.gradle.kts の versionCode を +1、versionName のパッチバージョンを +1 する (例: 0.1.1 → 0.1.2, versionCode 2 → 3)
2. 前回リリースからの変更点をまとめる（git log で確認）
3. 変更をコミットしてプッシュ:
   - 未コミットの変更があればそれも含める
   - コミットメッセージ: 変更内容の要約 + バージョン番号
4. ビルド: `./gradlew assembleDebug`
5. バージョンタグで新規リリースを作成（上書きではなく毎回新規）:
   ```
   gh release create v<VERSION> app/build/outputs/apk/debug/app-debug.apk \
     --title "v<VERSION>" \
     --notes "<変更点をmarkdownで記載>"
   ```
6. 完了後、新しいバージョン番号とリリースURLを表示する
