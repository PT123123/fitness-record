# Windows 上 just 会逐行执行 recipe（每行独立 bash -cu），因此 recipe 必须写成单行
# -c 必需：just 把行内容作为参数传给 shell，没有 -c 时 bash 会把它当文件名
# -e(出错即停) -u(未定义变量报错)
# Windows 下必须用 Git Bash（system32 的 WSL bash 无法执行 just 生成的临时脚本）
set shell := ["C:/Program Files/Git/bin/bash.exe", "-cu"]

# Auto-detect ANDROID_HOME if not set
ANDROID_HOME := env_var_or_default("ANDROID_HOME", env_var_or_default("ANDROID_SDK_ROOT", ""))

_detect_sdk:
    if [ -z "{{ANDROID_HOME}}" ]; then for p in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk" "/opt/homebrew/share/android-sdk"; do if [ -d "$p" ]; then echo "$p"; exit 0; fi; done; echo "❌ 未检测到 Android SDK" >&2; exit 1; else echo "{{ANDROID_HOME}}"; fi

# Build the debug APK（每次构建 patch +1，版本号写入 version.js 并在页面顶部显示）
build:
    set -euo pipefail; AH="$(just _detect_sdk)"; AH="${AH//\\//}"; if [ ! -d "${AH}" ]; then echo "❌ ANDROID_HOME 目录不存在: ${AH}"; exit 1; fi; echo "✅ ANDROID_HOME=${AH}"; SRC="{{source_dir()}}"; SRC="${SRC//\\//}"; cd "${SRC}"; VER=$(cat version.txt 2>/dev/null || echo "1.0.0"); IFS=. read -r MAJ MIN PAT <<< "${VER}"; PAT=$(( ${PAT:-0} + 1 )); NEW="${MAJ}.${MIN}.${PAT}"; echo "${NEW}" > version.txt; printf 'window.APP_VERSION = "%s";\n' "${NEW}" > version.js; cp version.js app/src/main/assets/version.js; echo "🔖 版本号 → v${NEW}"; if [ -x "./gradlew" ]; then GRADLE="./gradlew"; else GRADLE="$(command -v gradle || true)"; fi; if [ -z "${GRADLE}" ]; then echo "❌ 未找到 gradle。"; exit 1; fi; echo "🔨 构建 debug APK ..."; ${GRADLE} assembleDebug; echo ""; echo "🎉 构建成功！APK: app/build/outputs/apk/debug/app-debug.apk"

# Install the debug APK to a connected device
install:
    set -euo pipefail; SRC="{{source_dir()}}"; SRC="${SRC//\\//}"; APK="${SRC}/app/build/outputs/apk/debug/app-debug.apk"; if [ ! -f "${APK}" ]; then echo "❌ 未找到 APK，请先运行 just build"; exit 1; fi; echo "📲 安装 ${APK} ..."; adb install -r "${APK}"


# Clean build outputs
clean:
    set -euo pipefail; SRC="{{source_dir()}}"; SRC="${SRC//\\//}"; cd "${SRC}"; if [ -x "./gradlew" ]; then GRADLE="./gradlew"; else GRADLE="$(command -v gradle || true)"; fi; if [ -z "${GRADLE}" ]; then echo "❌ 未找到 gradle。"; exit 1; fi; echo "🧹 清理构建产物 ..."; ${GRADLE} clean; echo "✅ 清理完成"

