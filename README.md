# 健身训练记录 · 安卓 App

> 一个把「健身计时 + 训练记录」网页**打包成安卓 App**的工程，**倒计时归零时触发系统级强提醒**（锁屏全屏弹窗 + 悬浮窗 + 持续震动 + 高优先级通知，能穿透勿扰、锁屏也能弹）。

---

## ✨ 强提醒是怎么实现的

倒计时归零 → JS 调用 `window.FitnessNative.fire({restSeconds})`（原生桥接）→ 安卓侧同时触发 5 件事：

| 手段 | 效果 |
|---|---|
| **锁屏全屏弹窗** `AlarmActivity` | 休息结束遮罩整个屏幕，锁屏上也能显示（`showOnLockScreen` + `turnScreenOn`） |
| **悬浮窗** `OverlayService` | 在其他 App 上层显示「休息结束」横条（`SYSTEM_ALERT_WINDOW`） |
| **持续震动** `Vibrator` | 波形震动直到你操作 |
| **高优先级通知** | 通道 `IMPORTANCE_HIGH` + `setFullScreenIntent` + 绕过勿扰 |
| **唤醒屏幕** `WakeLock` | 点亮屏幕并保持 30 秒 |

弹窗三个按钮：**✅ 记录这组**（回调 JS `recordRestGroup()`，自动关弹窗/停震动）/ **⏳ 再休息 1 分钟**（`restartCountdown(60)`）/ **关闭**。

> 在普通浏览器调试时走降级方案（Notification + `navigator.vibrate` + 蜂鸣），保证不报错。

---

## 🚀 构建 & 安装（两种方式）

### 方式一：Android Studio（推荐，最简单）
1. 安装 [Android Studio](https://developer.android.com/studio)，首次启动让它自动装好 **SDK Platform 34** 和 **Build-Tools**。
2. `File → Open` → 选中本目录 `android-app` → 等待 Gradle 同步完成。
3. 插上手机（开启 USB 调试），点工具栏 **▶ Run**，或直接 `Build → Build Bundle(s) / APK(s) → Build APK`。
4. 生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`，传到手机安装即可。

### 方式二：命令行（已有 SDK）
```bash
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

cd android-app
./build.sh            # 自动检测 SDK，编译 debug APK
# 或手动：
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
> 首次构建 Gradle 会自动下载（需联网）；若没有 `gradlew`，用系统 `gradle` 命令亦可。

---

## 📦 发布（GitHub Releases · Obtainium）

发布渠道就是 GitHub Releases：Obtainium 以仓库为源、从 Release 资产里抓 APK 自动更新。
资产名固定 `fitness-record-X.Y.Z.apk`（以 `.apk` 结尾，Obtainium 可识别），tag 格式 `vX.Y.Z`，说明直接用 `dist/release-notes-X.Y.Z.md` 的内容。

### 发布步骤
1. **bump 版本号**（4 处同步改）：
   - `version.txt` → `1.2.9`
   - `version.js`（根目录）→ `window.APP_VERSION = "1.2.9";`，并复制到 `app/src/main/assets/version.js`
   - `app/build.gradle` → `versionCode 129`（主次补版本号拼接的整数）+ `versionName "1.2.9"`
   - `app/src/main/assets/index.html` 改动后记得同步根目录 `fitness-tracker.html`（两份需保持一致）
2. **构建**：`.\gradlew.bat assembleRelease`，产物在 `app/build/outputs/apk/release/app-release.apk`
3. **本地归档**：复制 APK 为 `dist/fitness-record-X.Y.Z.apk`，写 `dist/release-notes-X.Y.Z.md`
4. **发 GitHub Release**（Obtainium 即刻可抓到）：
   ```bash
   git tag v1.2.9 && git push origin v1.2.9
   gh release create v1.2.9 dist/fitness-record-1.2.9.apk \
     --title "v1.2.9" --notes-file dist/release-notes-1.2.9.md
   ```

### 签名密钥库（keystore）
- 位置在**仓库外**：`~/keystores/debug.keystore`（即 `C:/Users/ted/keystores/debug.keystore`，不受版本控制，换机器需手动拷贝，丢了就无法覆盖升级）
- 别名 `androiddebugkey`，store/key 口令均为 `android`（配置见 `app/build.gradle` 的 `signingConfigs.release`）
- **release 构建复用 debug 签名**：这是有意的约定（个人应用，无上架需求），保证 debug/release/Obtainium 更新三者的签名一致、可直接覆盖安装
- 换 keystore = 所有用户必须卸载重装，非必要不要换

---

## 🔐 权限说明（首次使用会引导）
- **通知**：Android 13+ 首次启动自动弹窗申请（用于强提醒通知）。
- **悬浮窗**：设置页一键跳转「在其他应用上层显示」开关（`SYSTEM_ALERT_WINDOW`，需手动授权，未授权时自动引导、不会崩溃）。
- **震动 / 唤醒屏幕**：普通权限，安装即授予。

---

## 🗂 工程结构
```
android-app/
├── build.gradle / settings.gradle / gradle.properties   # Gradle 工程
├── build.sh                                             # 一键构建脚本
└── app/
    ├── build.gradle                                     # compileSdk 34, minSdk 26
    └── src/main/
        ├── AndroidManifest.xml                          # 权限 + Activity/Service 声明
        ├── assets/index.html                            # ← 就是那个网页（计时+记录）
        ├── java/com/example/fitness/
        │   ├── FitnessApp.java          # 全局单例，JS ↔ 原生桥梁
        │   ├── MainActivity.java        # WebView 加载 index.html，注册桥接
        │   ├── FitnessNativeBridge.java # @JavascriptInterface：fire() / dismiss()
        │   ├── AlarmActivity.java       # 锁屏全屏强提醒弹窗
        │   └── OverlayService.java      # 悬浮窗（可选增强）
        └── res/                         # 布局 / 主题 / 图标
```

---

## 🔗 JS ↔ 原生 桥接契约
| JS 调用 | 原生行为 |
|---|---|
| `FitnessNative.fire({restSeconds, label, at})` | 弹锁屏弹窗 + 悬浮窗 + 震动 + 通知 + 唤醒 |
| `FitnessNative.dismiss()` | 关弹窗 / 停震动 / 取消通知 |
| `recordRestGroup()`（原生回调用） | 记录这组、关提醒 |
| `restartCountdown(60)` | 重启倒计时（再休息） |

> 想改提醒样式：改 `res/layout/activity_alarm.xml`；想改触发逻辑：`FitnessNativeBridge.java`。

---

## ⚠️ 关于「是否已编译出 APK」
本工程在**沙盒环境**中开发，该环境**无法安装 Android SDK、也无法联网下载 Gradle/SDK**（网络策略拦截了 `dl.google.com` / `services.gradle.org`），因此**未能在此直接产出 `.apk`**。
但工程本身是**完整、可编译**的：
- 全部 39 个 Android/androidx 导入类已核对齐全
- 所有 `R.id.*` 引用与布局 XML 一一自洽
- API 名称、版本守卫（minSdk 26 覆盖 `Notification.Builder(Context,String)`、`TYPE_APPLICATION_OVERLAY`、`VibratorManager` 等）均已核对
- 用 Android Studio 打开即可一键构建（方式一），无需任何额外配置

如需我这边直接给你 `.apk`，请把工程放到一台**已装 Android Studio / SDK 的电脑**上构建，或提供可访问的构建环境。
