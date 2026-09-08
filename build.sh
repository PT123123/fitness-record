#!/usr/bin/env bash
# 一键构建 debug APK。需已安装 Android SDK（或 Android Studio 自带的 SDK）。
set -e
cd "$(dirname "$0")"

# 自动探测 SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  for p in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk" "/opt/homebrew/share/android-sdk"; do
    [ -d "$p" ] && export ANDROID_HOME="$p" && break
  done
fi
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
export PATH="$PATH:$ANDROID_HOME/platform-tools"

if [ -z "$ANDROID_HOME" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "❌ 未检测到 Android SDK。"
  echo "   请先安装 Android Studio 并配置 ANDROID_HOME，或在 Android Studio 中打开本目录直接 Run。"
  exit 1
fi

echo "✅ ANDROID_HOME=$ANDROID_HOME"

# 优先用 wrapper，否则用系统 gradle
if [ -x "./gradlew" ]; then GRADLE="./gradlew"; else GRADLE="$(command -v gradle || true)"; fi
if [ -z "$GRADLE" ]; then
  echo "❌ 未找到 gradle。请先运行一次 Android Studio 让它生成 gradle wrapper，或用系统 gradle。"
  exit 1
fi

echo "▶ 构建 debug APK ..."
$GRADLE assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "🎉 构建成功：$APK"
  echo "   安装到已连接的设备： adb install -r $APK"
else
  echo "❌ 未找到产物，请检查上方日志。"
  exit 1
fi
