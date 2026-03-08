---
name: release
description: Build, version bump, commit, push, and upload APK to GitHub Releases
disable-model-invocation: true
---

# Release Workflow

1. app/build.gradle.kts の versionCode を +1、versionName のパッチバージョンを +1 する (例: 0.1.1 → 0.1.2, versionCode 2 → 3)
2. 変更をコミットしてプッシュ:
   - 未コミットの変更があればそれも含める
   - コミットメッセージ: 変更内容の要約 + バージョン番号
3. ビルド: `./gradlew assembleDebug`
4. APK を GitHub Releases にアップロード: `gh release upload dev app/build/outputs/apk/debug/app-debug.apk --clobber`
5. 完了後、新しいバージョン番号とリリースURLを表示する
