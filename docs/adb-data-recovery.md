# 用相同 keystore 捞取应用数据（无 root / 非 debug 构建场景）

适用场景：应用为 release 构建（`run-as` 不可用），设备无 root，`adb backup` 在 Android 12+ 上基本失效且用户不想全量备份。此时可以用**相同 keystore 签名的 debug 包覆盖安装**的方式，既保留应用数据，又获得 `run-as` 权限直接读取私有目录。

## 原理

- `adb install -r` 覆盖安装时，系统校验签名一致才允许替换，数据（localStorage、files/ 等）全部保留。
- debug 构建默认 `debuggable=true`，装上后 `run-as <包名>` 可直接访问 `/data/data/<包名>/`。
- 本项目 release 已统一使用共用密钥库 `~/keystores/debug.keystore`（别名 `androiddebugkey` / 口令 `android`），见 `app/build.gradle` 的 `signingConfigs.release`。

## 步骤

### 1. 让 debug 构建使用同一 keystore

在 `app/build.gradle` 的 `buildTypes` 中加上：

```groovy
debug {
    signingConfig signingConfigs.release   // debug 包也用同一 keystore，可覆盖安装 release 包且保留数据
}
```

### 2. 构建 debug APK

```bash
gradlew.bat assembleDebug --console=plain
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

### 3. 覆盖安装（保留数据）

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

注意：versionCode 不能低于已安装版本，否则需要先改版本号；安装失败（签名不一致）是无害的，不会丢数据，**切勿 uninstall**。

### 4. 用 run-as 读取数据

```bash
# 结构化数据完整快照（v1.2.19 起每次持久化全量写入：记录/笔记/HIIT/爬楼，优先看这个）
adb shell run-as com.example.fitness cat files/fitness_data.json

# 追加式训练日志（每次新增记录立刻落盘，误删兜底）
adb shell run-as com.example.fitness cat files/workout_log.jsonl

# WebView localStorage（结构化训练记录 fitness_records_v5 等都在这里）
adb shell run-as com.example.fitness ls files app_webview
adb shell "run-as com.example.fitness tar -cf - app_webview/Default/Local\ Storage" > local_storage.tar

# SharedPreferences（热力图 fitness_heatmap、加密密钥 fitness_secure 等）
adb shell run-as com.example.fitness cat shared_prefs/fitness_heatmap.xml
```

拉到电脑后解包 leveldb 即可分析 localStorage 内容。

### 5. 恢复数据

优先走应用自身通道（云备份恢复 / 日志找回 / 导入），避免直接改 leveldb（有损坏风险）。若确认日志里有丢失记录，重新构建带自动恢复逻辑的包覆盖安装，打开应用即自动补回。

## 注意事项

- 本项目数据分三层：结构化记录在 WebView localStorage（`fitness_records_v5`）、原生层全量快照 `files/fitness_data.json`（v1.2.19 起每次持久化原子写入）、append-only 兜底日志 `files/workout_log.jsonl`（原生层，不经 WebView）。
- v1.2.19 起启动时若发现 localStorage 缺 key（WebView profile 被清/重建），会自动从 `files/fitness_data.json` 回填，通常不需要手工恢复。
- 日志里故意删除的记录带 `tomb` 字段（墓碑），找回比对时要排除。
- 上述 `run-as` 命令只在 debuggable 构建上可用；手机装的是正式包时，按第 1~3 步用同 keystore 的 debug 包覆盖安装再捞。
