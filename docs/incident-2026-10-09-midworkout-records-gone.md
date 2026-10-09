# 事故记录：2026-10-09 训练中记录"消失"排查与修复

> 现象：练背约 15 分钟后，训练页"记录消失"；一段时间后数据又"回来"（草稿仍在 localStorage）。
> 结论：**不是数据丢失，是本场训练期间 Activity 被系统重建、WebView 整页重载导致的内存态丢失 + 全屏闹钟覆盖**；同时借机修复了「导出页显示所有历史日期」的口径问题。

---

## 一、数据现状（事故当时 / 修复前）

- 结构化数据全部在 WebView localStorage 的 `fitness_records_v5`（`/data/data/com.example.fitness/app_webview/Default/Local Storage/leveldb/`）。
- 兜底日志：`files/workout_log.jsonl`（append-only，仅新增/删除/墓碑，**草稿组不进日志**）。
- 热力图：`shared_prefs/fitness_heatmap.xml`（key `days`）。
- 事故时：`fitness_records_v5` 里有 11 个草稿组（16:39 引体x10 → 17:18 高位下拉x2），`records` 里没有 10-08 之后的记录；`workout_log.jsonl` 尾部停在 10-07（草稿从未保存，属正常）。
- 用户记忆中"引体 8、7、6"三组在最终数据里缺失——16:40~16:54 窗口内的组确实没保存成功（落在 Activity 重建窗口），无法找回，只能留空。

---

## 二、根因（证据链）

### 1. Activity 被系统重建 → WebView 整页重载（主因）

事件日志（`adb logcat -b events`）关键时间点：

| 时间 | 事件 | 含义 |
|---|---|---|
| 16:35:17 | MainActivity 首次启动 | 进程 23164 |
| 16:39:12 | `wm_create_activity` + `performCreate` | 开始训练前的重建 |
| **16:42:29** | **`wm_create_activity` + `performCreate`（同进程）** | **训练中途 Activity 重建** |
| 16:44:17 / 16:53:11 / 16:57:59 | AlarmActivity 弹出 3 次 | 休息倒计时到点全屏闹钟 |
| 16:54 | `app_webview/Default/` 目录 mtime = 16:54 | **WebView profile 重建时刻，对应"15 分钟后消失"** |
| 19:09:40 | lastUpdateTime | 之前调试会话安装过 debug 覆盖包 |

- AndroidManifest 原先只声明 `configChanges="orientation|screenSize|keyboardHidden"`。MIUI 上深色模式切换、字体/显示密度变化、区域语言变化等都会让 Activity 销毁重建。
- Activity 重建 → `onCreate` 重新 `new WebView()` + `loadUrl(index.html)` → JS 全部重跑 `init()` → `load()` 从 localStorage 恢复。**重建瞬间窗口里尚未 persist 的状态丢失**，页面看起来"没记录了"。
- AlarmActivity 是全屏覆盖层，弹出时把训练页整个压在下面，视觉上进一步坐实了"记录消失"。

### 2. `load()` 空吞异常 + `persist()` 可能覆盖（潜在永久丢失通道）

- 原 `load()`：`try{ ... }catch(e){}` 空吞。一旦 localStorage 临时读不到/JSON 半截，`state.records/groups` 保持默认 `[]`。
- 原 `persist()`：无条件 `localStorage.setItem(...)`。上述场景下**下一次任何操作就会用空数据覆盖真实记录**。
- 本次未触发（数据还在），但这是历史上 JSONL 里出现大量裸墓碑 + `rmuwir6*` 记录（v1.2.10 ADB 脚本迁移残留）的同类风险源。

### 3. 导出页口径不一致（顺带修）

- 训练页「记录历史」明确"只显示今天"，但「📤 导出记录」的 `collectExportItems()`（index.html:1107）**把所有日期的 `state.records` 全量导出**，用户看到"前几天记录也出现在导出页"。

---

## 三、修复内容

### 1. AndroidManifest.xml — 防 Activity 重建

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|keyboard|uiMode|density|fontScale|locale|layoutDirection"
    android:launchMode="singleTask">
```
- 补全 `configChanges` 后旋转/深色/字体/密度等变化不再销毁重建 Activity，WebView 与内存态得以保留。
- `launchMode="singleTask"` 避免多实例叠加。

### 2. index.html — `load()`/`persist()` 防覆盖

```js
let _storageHealthy = true;   // localStorage 读写是否健康：异常时暂停持久化，防止空数据覆盖旧记录

function load(){
  try{
    const raw = localStorage.getItem(LS);
    if (raw == null) { _storageHealthy = true; return; }   // 首次使用：无旧数据，正常进入空状态
    const d = JSON.parse(raw);
    _storageHealthy = true;
    /* ……原有各字段恢复逻辑不变…… */
  }catch(e){
    // 读取/解析失败：把原始内容备份到旁路 key，绝不覆盖；本次会话暂停持久化
    _storageHealthy = false;
    try{ const raw = localStorage.getItem(LS); if (raw) localStorage.setItem('fitness_records_v5_prev', raw); }catch(_){}
  }
}

function persist(){
  if (!_storageHealthy) return;   // 存储异常时放弃写回，静默保留旧记录
  localStorage.setItem(LS, JSON.stringify({ ... }));
  syncHeatmap(); syncWorkoutLog(); scheduleCloudBackup();
}
```
- 读失败/解析失败 → 原始值备份到 `fitness_records_v5_prev`（排查用），并暂停本会话的所有写回，杜绝空数据覆盖。

### 3. index.html — 导出页只导出今天

```js
function collectExportItems(){
  const items=[];
  state.groups.forEach(/* 草稿组，date=trainToday() 不变 */);
  // 只含今天已保存的记录；历史请在「结构化数据」查看
  state.records.filter(r=>r && r.date===trainToday()).forEach(/* push 与原来相同 */);
  return items;
}
```
- 导出文案同步改为"仅导出今天的记录（本次组别 + 今日已保存），更早的记录请去「🗂 结构化数据」查看"。
- 注意：`endTraining()` 的"导出今天的全部结构化记录"本来就是按 `date===trainToday()` 过滤的，无此问题。

### 4. 今日数据固化（手机端，一次性操作）

通过 DevTools（`adb forward` + CDP `Runtime.evaluate`）调用应用自身逻辑：

```js
// 在页面上下文执行
saveGroupsToHistory();
```

- 结果：今天 11 个草稿组 → 11 条结构化 `records`（src=timer），并同步追加进 `workout_log.jsonl`（日志尾部可见 `2026-10-09` 11 条）、热力图（`2026-10-09:{"背":1}`）。
- 验证：`records.v5` records=273、导出的 `collectExportItems()` 仅返回 `["2026-10-09"]`。

### 5. 结构化数据落盘镜像（v1.2.19 追加）

`persist()` 写 localStorage 的同时，把完整快照原子写入原生文件 `files/fitness_data.json`，数据与 WebView 彻底解耦：

- **格式**：`{"app","ver","ts","keys":{fitness_records_v5 / fitness_notes_v1 / fitness_hiit_v2 / fitness_stair_v2}}`，一次 `run-as cat` 拿到记录 + 笔记 + HIIT + 爬楼的完整 JSON。
- **写入**：原生 `FitnessNativeBridge.saveDataFile()` 走 tmp + fsync + rename 原子替换；JS 侧 300ms 防抖合并（输入框每键会触发 `persist()`），`beforeunload` 同步落盘不等防抖。
- **写前合并**：先读旧文件，localStorage 里缺失的 key 沿用文件旧值，局部快照不会把别的数据抹掉（实测：只删一个 key 再落盘，文件里其它 key 不丢）。
- **启动回填**：`init()` 先 `restoreFromDataFile()`，localStorage 缺 key（WebView profile 被清/重建）时自动从该文件回填，**只补不覆盖**；`load()` 解析失败仍走旁路备份 + 暂停写回的老逻辑，两层防护不冲突。
- **验证**：CDP 清空四个 key 后 `Page.reload`，records 273 / notes 18 / 爬楼全部自动找回，`_storageHealthy=true`；`run-as cat` 出的文件 66927 字节、records 数与 localStorage 逐条一致。

---

## 四、复盘 / 遗留

- **草稿组没有实时落盘到 JSONL**：训练中的"本次组别"只在 localStorage。重建窗口内的组（本次 16:40~16:54 的引体 8/7/6 等 5 组）一旦被清就没有兜底。v1.2.19 的落盘镜像缓解了一部分：草稿在每次 `persist()` 时会进 `fitness_data.json`，残留窗口只剩 300ms 防抖与未触发 persist 的空档。彻底方案（未做）：把草稿组也 append 进日志。
- **Android 12+ WebView localStorage 异步落盘**：正常 flush 很快，但进程被 kill/Activity 重建瞬间的差异仍存在；本修复已尽量消除重建路径，且即使 profile 被清也能从落盘镜像回填。
- 排查工具沉淀：`docs/adb-data-recovery.md`（覆盖安装 debug 包 + run-as 捞数据）、`_adb_tmp/*`（CDP dump/注入脚本、leveldb 提取脚本）。

## 五、相关脚本速查

```bash
# 结构化数据完整快照（v1.2.19 起，优先看这个：记录+笔记+HIIT+爬楼全在里面）
adb shell run-as com.example.fitness cat files/fitness_data.json

# 落盘日志
adb shell run-as com.example.fitness cat files/workout_log.jsonl

# 拉 leveldb（注意 Windows 下用 exec-out，普通 > 重定向会变 UTF-16）
adb exec-out run-as com.example.fitness tar -cf - "app_webview/Default/Local Storage" > local_storage.tar

# 热力图
adb shell run-as com.example.fitness cat shared_prefs/fitness_heatmap.xml

# DevTools 直读/注入（debug 构建）
adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>
node _adb_tmp/cdp_dump_state.js      # 读当前状态
node _adb_tmp/cdp_fix_today.js       # 示例：saveGroupsToHistory() 固化草稿
```