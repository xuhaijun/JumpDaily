# TTS 语音播报全局仲裁实战

> 适用：本项目训练时中文鼓励语播报（`audio/VoiceSpeaker.kt` + `audio/Encouragements.kt` + `TrainingViewModel` 调用方）。
> 文档全部基于真实代码，行号见引用。

---

## 1. 问题：30 秒 13 句，话都被掐断

2026-09-07 之前，各调用方**各自节流、互不知情**：报数、节奏鼓励、连击、目标、开场、未发现人引导…… 30 秒内会冒出 13 句语音（平均 2.3 秒一句）。

而底层用的是 `TextToSpeech.QUEUE_FLUSH`——新一句会**直接掐断**正在播的前一句。听感就是「一句话说到一半突然断了，接上一句别的」。

根因：TTS 是**全局单声道资源**，但调用方是**多个并发通道**。每个通道只管自己「隔多久说一次」，彼此不知道对方正在说 → 同帧撞车、互相打断。

---

## 2. 解决方案：统一仲裁入口

所有 `speak` 调用**必须经过 `VoiceSpeaker` 一个出口**，由它按优先级 + 静默窗口统一决定「这句该不该播」。

### 2.1 优先级枚举（`VoiceSpeaker.kt` 第 25–43 行）

```kotlin
enum class VoicePriority(val level: Int) {
    HINT(0),       // 引导提示：找不到人、开灯提醒。随时可丢，最不重要。
    COACH(1),      // 常规教练反馈：节奏鼓励、动作纠正。默认。
    COUNT(2),      // 报数：整 N 个时念一下数字。
    MILESTONE(3),  // 阶段性成就：连击奖励。
    CELEBRATE(4),  // 重要庆祝：目标达成、奖品解锁、训练完成。几乎总该播。
    START(5),      // 开场语：进入训练第一句，不应被任何东西挡掉。
}
```

数值越大越重要。**所有 `speak` 调用必须显式传 `VoicePriority`**（默认 `COACH`）。

### 2.2 仲裁规则（`VoiceSpeaker.kt` 第 306–310 行）

```kotlin
private fun shouldSpeak(level: Int): Boolean {
    val gap = System.currentTimeMillis() - lastStartTs
    if (!speaking && gap >= MIN_GAP_MS) return true          // 空闲且隔够 → 放
    return level > lastLevel && gap >= PREEMPT_MIN_GAP_MS    // 否则只有更高优先级且隔够 → 抢占
}
```

两个关键常量（第 106–111 行）：

```kotlin
const val MIN_GAP_MS = 4000L          // 全局最小发声间隔：一句中文约 4 秒，保证孩子听清
const val PREEMPT_MIN_GAP_MS = 1200L  // 抢占最小间隔：即便更高优先级要打断，也至少离上一句 1.2 秒
```

规则含义：

| 场景 | 结果 |
|------|------|
| 当前空闲，距上次发声 ≥ 4s | ✅ 播 |
| 正在播 / 距上次太近 | ❌ 直接丢弃（除非优先级**更高**且距上次 ≥ 1.2s 才允许抢占） |
| 同优先级或更低，在静默窗口内 | ❌ 直接丢弃——**宁可少说，也别把话掐断一半** |

被丢弃时在 `tts_init.txt` 落一行诊断（`VoiceSpeaker.kt` 第 322–324 行）：

```
voice_drop level=N last=M speaking=true
```

---

## 3. 统一出口（调用方必须遵守）

`speak` 三个重载（`VoiceSpeaker.kt` 第 350–367 行），**全部经 `speakInternal` 这一个出口做仲裁**：

```kotlin
fun speak(text: String, priority: VoicePriority = VoicePriority.COACH) =
    speakInternal(text, priority.level) { lvl -> playAsset(text, lvl) }

fun speak(template: String, number: Int, priority: VoicePriority = VoicePriority.COACH) {
    val finalText = template.replace("{n}", number.toString())
    speakInternal(finalText, priority.level) { lvl -> playTemplateAsset(template, number, lvl) }
}

fun speakNumber(number: Int, priority: VoicePriority = VoicePriority.COUNT) =
    speakInternal(number.toString(), priority.level) { lvl -> playNumberAsset(number, lvl) }
```

`VoiceSpeaker.kt` 第 318–344 行 `speakInternal` 在 `executor`（单线程）里先 `shouldSpeak(level)` 判定，丢弃则 `return`，否则才真正播。

---

## 4. 调用方如何接（以 `TrainingViewModel` 为例）

### 4.1 所有节奏反馈「共用一个时间戳」

`TrainingViewModel.onCadence` 第 490–524 行，三条分支（节奏好有礼物 / 偏慢加油 / 很稳喝彩）**必须共用 `lastFeedbackTs`**：

```kotlin
// ⚠️ 三条节奏反馈必须共用 lastFeedbackTs（2026-09-07）：
// 此前礼物语用独立 lastGiftTs，而节奏 120 同时落在 GOOD_CADENCE_RANGE 与 steady 两个区间，
// 两个计时器互不知情 → 每 12 秒必然两句同帧撞车，一句把另一句掐断。
// 共用同一时间戳后三分支天然互斥，同一时刻只说一句。
when {
    c in Rewards.GOOD_CADENCE_RANGE && now - lastFeedbackTs > Rewards.GOOD_CADENCE_INTERVAL_MS -> {
        lastFeedbackTs = now
        ...
        voiceSpeaker.speak(pick("prize", prizePool).display, VoicePriority.HINT)  // 最低优先级
    }
    c in 1..45 && now - lastFeedbackTs > CHEER_INTERVAL_MS -> {
        lastFeedbackTs = now
        ...
        voiceSpeaker.speak(msg.display, VoicePriority.COACH)
    }
    c >= 110 && now - lastFeedbackTs > STEADY_INTERVAL_MS -> {
        lastFeedbackTs = now
        ...
        voiceSpeaker.speak(msg.display, VoicePriority.COACH)
    }
}
```

**教训**：新加一个语音通道前，先确认它和已有通道会不会区间重叠。能共用 `lastFeedbackTs` 就共用，**绝不要另起一套独立节流变量**（礼物语 `lastGiftTs` 就是反面教材）。

### 4.2 各场景选用优先级（本项目现状）

| 场景 | 调用 | 优先级 | 说明 |
|------|------|--------|------|
| 开场 | `vm.start` 第 202 行 | `START` | 不应被挡 |
| 满整十报数 | `onJump` 第 378 行（**每 20 个一次**，原每 10 个太密） | `COUNT` | 静默窗口内被弃，绝不抢鼓励语 |
| 连击 20/50/100 | `onJump` 第 391 行 | `MILESTONE` | 喝彩 |
| 目标达成 | `celebrateGoal`/`stop` | `CELEBRATE` | 重要庆祝 |
| 奖品解锁 | `checkPrizeUnlock` 第 452 行 | `CELEBRATE` | 重要庆祝 |
| 节奏偏慢加油 | `onCadence` 第 514 行 | `COACH` | 默认 |
| 节奏很稳喝彩 | `onCadence` 第 521 行 | `COACH` | 默认 |
| 动作纠正 | `onFormIssue` 第 533 行 | `COACH` | 默认 |
| 未发现人引导 | `CameraTrainingScreen` 第 313 行 | `HINT` | 最低，正在播别的时直接丢 |

### 4.3 音效与语音「错峰」

`TrainingViewModel.fxThenVoice`（第 471–477 行）：先响短音效、停顿 `delayMs` 再播语音，避免 SoundPool 音效叠在长鼓励语上混作一团：

```kotlin
private fun fxThenVoice(delayMs: Long = 500, fx: (() -> Unit)? = null, voice: () -> Unit) {
    fx?.invoke()
    viewModelScope.launch { delay(delayMs); voice() }
}
```

---

## 5. 离线语音包 vs 系统 TTS（两条音色来源）

`VoiceSpeaker` 默认**系统 TTS 优先**（用户反馈厂商引擎更自然），离线 `assets/voice/<sha1>.wav` 作兜底（任意手机音色一致）。`speakInternal` 第 318–344 行决定走哪条（见源码注释）。语料集中在 `audio/Encouragements.kt`，生成脚本见 `tools/gen_voice_pack.py`。

---

## 6. 诊断：怎么确认仲裁生效 / TTS 真的就绪

`release` 包 logcat 在华为 EMUI 上常被吞，故所有关键状态**落盘到外部文件**：

- `tts_init.txt`（`VoiceSpeaker.kt` 第 248 行）：`oninit status=`、`engine_info=`、`voice_drop ...`、`play_asset/play_seq/play_num` 行。
- 真机验收必看 `tts_init.txt` 的 `play_asset`/`play_seq`/`play_num`——`asset_voice_count` 只代表「可读」不代表「会播」。
- 出现 `voice_drop level=N last=M speaking=true` 即仲裁生效（该句被丢弃，正常行为）。

读取：`adb pull /sdcard/Android/data/com.jumpdaily.jump/files/tts_init.txt <本地路径>`

---

## 7. 华为 EMUI 的 TTS 坑（必须知道）

`TextToSpeech.OnInitListener` **不能存成类的 `private val` 属性再传给构造器**——在华为 EMUI + D8 下回调**不会触发**（无崩溃、无声），TTS 永远不就绪。必须**每次 `new` 一个实例**（内联 lambda 或工厂方法）：

```kotlin
// VoiceSpeaker.kt 第 195 行（正确写法）
private fun onInit() = TextToSpeech.OnInitListener { status -> ... }
// 用法：TextToSpeech(appContext, onInit())
```

定位靠 `tts_init.txt` 诊断（看有没有 `oninit status=` 行）。另：`(Int)->Unit` 属性当 SAM 传也会在回调里 NPE，同样要显式写 `OnInitListener`。

详见 [R8_RELEASE.md](R8_RELEASE.md)。

---

## 8. 加新语音前自查清单

- [ ] 走 `VoiceSpeaker.speak(...)` 统一出口，显式传 `VoicePriority`。
- [ ] 确认它与已有通道**区间是否重叠**；重叠就共用 `lastFeedbackTs`（不要新起节流变量）。
- [ ] 默认优先级用 `COACH`；打扰性强的用 `HINT`；重要庆祝用 `CELEBRATE`/`START`。
- [ ] 需要音效时，用 `fxThenVoice` 错峰，不要同帧硬叠。
- [ ] 真机验收看 `tts_init.txt` 的 `play_*` 行确认确实播了。

---

## 9. 相关文档

- Compose 重组优化见 [COMPOSE_RECOMPOSITION.md](COMPOSE_RECOMPOSITION.md)
- Release 包调试（文件落盘、华为坑）见 [R8_RELEASE.md](R8_RELEASE.md)
