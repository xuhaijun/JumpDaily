# Changelog · 爱跳绳（原 跳了么 / JumpDaily）

> 本项目功能/特性总览见 [README.md](README.md) 的「✨ 主要功能」。本文件记录每次迭代的**交付清单与工程变更**。

---

## [2026-09-04] 体验打磨五连发：挂起会话恢复 + 浮条重构 + 历史记录/缓存/间距修复（当日第 4~8 轮）

### 摄像头训练页
- 跳跳星弹跳顶部被节奏卡压住 → 节奏卡与跳跳星间距 12→20dp（实际间隙 30dp > 最大上弹 27dp）。
- 暂停/结束按钮弱化：透明度 0.72→0.55，底部 padding 64→44dp 下移。

### 传感器训练页（TrainingScreen 美化）
- 顶部副标题改为实时状态（计数中/已暂停/准备开始）；节奏/连跳/积分升级为三张马卡龙色 StatCard；暂停提示改柔和胶囊卡片；达成目标时进度环变庆祝色。

### 挂起会话跨进程恢复（新功能）
- 暂停时快照（孩子/个数/时长/方式/积分）落盘 DataStore；退出 App 重开弹「上次跳绳还没完成哦」，继续=还原挂起态（浮条显示上次信息，一键续跳），放弃=清空快照；0 个不落快照。

### 浮条（FloatingJumpBar 重构）
- 新增双形态浮条：入口「▶ 开始跳绳」/ 挂起「⏸ 已跳 X 个 · 时长 继续」，可拖动吸边、位置持久化、3s 静止变淡。
- 修复挂起态被屏幕右缘裁剪：水平定位改为「吸附边+实测宽度」实时推导，胶囊变宽位置自动跟随。

### 首页 / 我的页
- 首页：本周打卡卡片移到跳跳星卡片下方；新增返回键直接退出应用（修双首页 bug）。
- 我的页：列表 item 单行化（图标/名称/说明/箭头一行居中）；说明列右对齐贴箭头，间距两轮微调（图标-标题 4dp、说明-箭头 0dp），箭头换 KeyboardArrowRight 图标严格对齐。

### 统计页
- 历史记录只展示最近 20 条（DB 全量保留）；日期行带时刻（MM.dd HH:mm）。

### 语音 / 音效
- 系统 TTS（华为引擎）优先、离线语音包兜底；TTS 语速 1.1、离线包提速 1.15；音效与鼓励语顺序播放（先音效、停 500ms 再语音），杜绝叠音。

### 修复 / 工程
- 「清理缓存」始终 0B → 统计/清理范围扩为内外缓存目录 + codeCache + 外部诊断日志。
- 项目根目录 121 个测试截图清理；今后截图统一存项目外 `JumpDaily-backups/shots/`，每次验收前先清上一轮。
- Lint 0 错误、单测 6→15 个全绿。

---

## [2026-09-04] 设置独立成页 + 首页头部间距（当日第 3 轮）

### 首页
- 头部标题行与副标题行之间加 4dp 间距，不再挤压。
- 「今日目标」卡的「调整目标」直达新设置页。

### 设置页（新增独立页，替代老设置页）
- 重写 `SettingsScreen.kt`（签名 `SettingsScreen(container, nav?)`）：带「‹ 返回」头部 + 四组卡片——训练设置（计数方式/灵敏度/每日每周目标）、每日提醒（开关+时间，含 Android 13+ 通知权限流程）、声音与语音（音效/语音/夜间模式/语音包下载/离线优先）、恢复默认设置。
- 路由保持 `settings`，老设置页实现整体删除，功能无遗漏。

### 我的页（精简）
- 移除训练设置/每日提醒/声音与语音三张设置卡与恢复默认弹框、通知权限逻辑（全部迁至设置页）。
- 「我的数据」卡新增「⚙️ 设置（目标 / 提醒 / 声音语音）」入口。
- 清理 14 个无用 import 与 5 个不再使用的私有组件，净瘦身 229 行。

---

## [2026-09-04] 三 tab 页优化 + 童音音效 + 弹幕随机位置 + 稳定性修复

### 首页
- 问候行支持长昵称（两行省略）；「上次成绩」摘要卡（最近一次：日期/个数/时长/热量，点击跳统计页，无记录隐藏）。
- 本周打卡日历每天可点击，点某天显示当日个数；每日目标达成后按钮变「已达标！再挑战一波 🎉」，进度条达成变金色。
- （曾加积分胶囊后按需求移除，积分入口保留在「我的」页。）

### 统计页
- 头部加「🔥 连续 x 天」徽章；7 天柱状图卡显示本周总数；本周目标卡新增上周对比（上周无数据自动隐藏）。
- 历史记录删除增加确认弹框，防误触。

### 成就页
- 头部与统计页统一（头像 + 标题）；进度卡新增「下一枚」提示；已解锁卡品牌紫高亮。

### 我的页（重做）
- 去掉头部「我的」标题与设置齿轮，改为孩子彩色主页卡；设置全部整合进内容卡；补齐奖品墙/孩子管理/版本信息/恢复默认；底部 tab 图标 👤→🧒。

### 摄像头页
- 弹幕改为屏幕下方随机位置冒出（随机上升+漂移+旋转）；跳跳星半透明化；暂停/结束半透明按钮。

### 音效
- 新增 6 个童音音效（`tools/gen_sound_effects.py`：TTS+音调上移+钟声层，含时长补偿防花栗鼠效应），`res/raw/snd_*.wav`，SoundPlayer 优先播放。

### 修复
- **成就弹框误弹（回归修复）**：会话基线快照可能在 badges flow（stateIn 初始空列表）追上真实数据前执行，导致历史成就被误判为新解锁。快照前先校验 badges 与 `BadgeCatalog.evaluate(stats)` 一致。
- 我的页头部卡片 Box 布局导致昵称与统计行重叠 → 改 Column。

---

## [2026-09-03] 美化摄像头训练页（实时预览页），提升儿童乐趣与体验

### 改动
- **`PoseOverlay.kt`（骨架叠加层）**：把扁平单色骨架升级为会发光的「影子伙伴」——
  连线沿身体纵向做 **紫→粉→薄荷** 糖果渐变（`Brush.verticalGradient`），先垫一层柔光晕再画清晰渐变线；关节点改为「外圈柔光 + 白色高亮内核 + 渐变色小点」，跳起来像一串小星星。保留 mirror/cover 变换与既有入参。
- **`CameraTrainingScreen.kt`（HUD 重构）**：
  - 顶部「模式 + 计时」改为**毛玻璃小药丸**（半透明黑底圆角），浮在预览上更清爽。
  - 中部计数做成**糖果渐变 + 呼吸柔光药丸**（`PopCount` 大数字 + `SunYellow` 径向柔光）；新增**节奏脉冲指示**（🔥 节奏 X/分，随当前 cadence 快慢轻轻跳动，帮孩子卡拍子）；新增**每日目标进度条**（🎯 count/goal，糖果渐变填充）。
  - 文案「跟着影子一起跳！」→「**和影子伙伴一起跳！**」，呼应影子伙伴主题。
- **新增「看不到人」引导**：当骨架为空（孩子没站进画面）时，居中弹出跳跳星引导卡「👀 站进来一点～ 让跳跳星看到你，一起开心地跳！」，显著提升低龄儿童自检与参与感（复用 `JumpMascot`）。

### 目标
降低摄像头训练页的枯燥感、强化即时正反馈与目标感，让孩童更愿意持续跳。

---

## [2026-09-03] 还原：首页「本周打卡」/统计页「本周目标」卡片背景

> 上一版把这两张目标卡背景统一成 `Bubble` 主题色 + `BorderStroke` 描边后，真机实测观感不如改前（用户判定「没之前好」），**已还原为改前默认白底卡片**（去掉 `colors=Bubble` 与 `border=BorderStroke`，恢复默认 `Card` 外观）。
> 注：「最近 7 天」卡与首页顶部横幅仍保留 `Bubble` 主题色（属于改前即存在的设计，不受影响）。

---

## [2026-09-03] 修复 Release 摄像头姿态引擎崩溃：ExceptionInInitializerError（R8 优化）

### 现象
真机 Release 包打开摄像头训练页提示「这个设备暂时用不了摄像头姿态计数」（错误卡），Debug 包正常。最初以为是 protobuf 混淆改名，补 `com.google.protobuf.**`/`com.google.devtools.**` keep 后 offline 验证类名已恢复原名，但真机仍崩。

### 根因（最终定位）
`proguard-rules.pro` 引用的是 **`proguard-android-optimize.txt`（含代码优化）**。R8 的优化（类合并 / `allowaccessmodification` / 静态初始化块改写）会破坏 MediaPipe/protobuf 的 `<clinit>`（静态初始化块），导致 release 下抛 **`ExceptionInInitializerError`**（消息为 null），被 `PoseJumpDetector.create()` 的 `catch(Throwable)` 兜住 → 显示降级错误卡；Debug 不优化故正常。
- 关键排查手段：release 的 `proguard-android-optimize.txt` 会把 `Log.e/i/w/d/v` 整条删掉，异常被"吃掉"看似无日志 → 改用 `Log.wtf`（不被剥）+ 把完整 cause 链写到 `getExternalFilesDir/pose_error.txt`（`adb pull` 读取）才拿到真凶。

### 修复
- `app/build.gradle.kts`：release `proguardFiles` 由 `proguard-android-optimize.txt` 改为 **`proguard-android.txt`**（仅压缩+混淆，**不做代码优化**），根治优化导致的 `<clinit>` 崩溃；保留所有既有 keep 规则。
- 诊断加固（可保留，便于后续排查）：`PoseJumpDetector.create()` catch 内 `Log.wtf` 打印完整 cause 链 + 写 `pose_error.txt`；`CameraTrainingScreen` 错误卡现在显示完整 `[调试] 异常类名↳cause 链`。

### 验证
- `assembleRelease` BUILD SUCCESSFUL（1m25s）。
- 真机（2NSDU20917009254）重装 `app-release.apk` 后进入摄像头训练页：**`pose_error.txt` 未生成、无 PoseJumpDetector 错误日志、相机实时出帧正常** → `createFromOptions()` 不再抛 `ExceptionInInitializerError`，错误卡消失（骨架绘制需用户肉眼最终确认）。

---

## [2026-09-03] 设置页每日目标联动每周目标 + 首页/统计页卡片背景统一

### 1. 设置页每日目标调整 → 每周目标自动同步（每日 × 7）
- `SettingsViewModel.setDailyGoal(value)` 内联联动 `prefs.setWeeklyGoal(value * DAYS_PER_WEEK)`：调每日目标后每周目标立即按「每日 × 7」刷新，避免两者脱节（此前每周目标要手动另调）。
- `PreferencesRepository` 抽顶层常量 `DEFAULT_DAILY_GOAL = 600`、`DEFAULT_WEEKLY_GOAL = DEFAULT_DAILY_GOAL * DAYS_PER_WEEK`（4200）；`dailyGoal`/`weeklyGoal` 的 DataStore 默认值与 `SessionViewModel`/`SettingsViewModel` 的 `stateIn` 初值统一引用常量，消除多处 500/3500 硬编码漂移。
- `SettingsScreen` 每周目标滑块上限由 3500 放宽到 `350f..7000f`（= 每日上限 1000 × 7），并补说明文字「调每日目标时每周目标会自动按每日 × 7 同步；也可以单独微调每周目标」。

### 2. 首页「本周打卡」/统计页「本周目标」卡片背景与其他卡片一致
- 两个目标类卡片底色由「白底/纯色」改为与 `StatCard` 同款 `Bubble` 主题色 + `BorderStroke(1.dp, outline 半透明)`，整页卡片观感统一。
- 仅 `DailyGoalCard`（今日目标）保留 `primaryContainer` 强调色，作为首页重点目标卡突出显示，与诉求不冲突。
- 编译 `compileDebugKotlin`/`assembleDebug` 均通过，已 `adb install -r` 装真机（2NSDU20917009254）验证。

---

## [2026-09-03] 首页跳跳星点击去掉默认涟漪阴影（indication = null）

## [2026-09-03] 首页加上「今日目标」卡片：默认 600 个，可一键跳转设置调整

## [2026-09-03] 音频合成加固：改用经典 AudioTrack 构造函数 + 静态/流双路径回退（华为 EMUI 实测通过）

## [2026-09-03] 修复启动必崩：SoundPlayer AudioTrack MODE_STATIC 未设置缓冲区大小 → Invalid audio buffer size

## [2026-09-02] 成就卡标题居中修复：BadgeCard 标题补 textAlign=Center + fillMaxWidth（否则比 emoji 宽/换行时左偏）

## [2026-09-02] 「我的宝贝们」优化：成功 Toast 改顶部显示 + 选中头像始终可见（白色✓移为右下角小角标）

## [2026-09-02] 「我的宝贝们」：删顶部提示语 / 成功反馈改 Toast / 选中头像+颜色均白色圆描边+白✓ / 空态垂直居中

## [2026-09-02] 「我的宝贝们」空态引导（无宝贝时居中提示添加）+ 选中头像/颜色圆形边框改白色

## [2026-09-02] 成就页设置按钮上、右边距改 10dp（offset x=10 / y=-10）

## [2026-09-02] 「我的宝贝们」体验修复：成功提示去全屏彩带 / 弹框可滚动 / 选中色边框统一

## [2026-09-02] 首页开始按钮移至今日挑战正下方 + 训练完成弹框按钮间距调大

## [2026-09-02] 「我的宝贝们」四项增强：保存成功横幅 / 头像颜色实时预览 / 编辑+删除确认 / 切换数据同步

## [2026-09-02] 修复「我的宝贝们」添加弹框：输入框无法输入 + 保存空名字无提示

## [2026-09-02] 成就页加分段筛选（全部/已解锁/未解锁）+ 进度条卡间距收紧 + 统计页卡片改 3 列

## [2026-09-02] 训练完成弹框时长不再折行（StatCard 值单行化 + 弹框字号调小）

## [2026-09-02] 三大体验微调（首页开始按钮上移/今日挑战图标居中/成就设置按钮去边距）

## [2026-09-02] 四大 tab 补全真实数据维度（时长/卡路里/成就进度/本周汇总）

## [2026-09-02] 成就页设置按钮对称贴右上角（与标题同 20dp 边距）

## [2026-09-02] 成就页设置图标与标题顶部对齐、间距再收紧

## [2026-09-02] 三项微调（跳跳星动画温柔/首页卡片换位/每日目标默认600）

## [2026-09-02] 四项体验优化（成就图标放大/历史页空态/报告页提示居中/首页隐藏更多）

## [2026-09-02] 成就页右上角设置入口改为纯图标 + 放大

## [2026-09-02] 设置页开关关闭态边框改为品牌紫（与开启填充色相反）

## [2026-09-02] 首页开始跳绳按钮移至本周打卡下方

## [2026-09-02] 启动图标放大人物/缩小绳子/加内边距 + 启动页动效换小人跳绳

- 启动图标 `ic_launcher_foreground.xml` 重画：放大人物（头半径 8→10、身体/四肢加粗 strokeWidth 5→6、整体更居中），跳绳由占满画面的巨型弧（x 15~93）收缩为环绕脚下的小环（x 30~78、下探 y 78），四周留约 30dp 内边距，不再贴边。手与绳端坐标对齐（38/70,42）保持"握住绳"。
- 启动页 `SplashScreen` 旋转五角星 `LottieRes(R.raw.star_spin)` 改为白线稿「小人跳绳」循环动效：新增 `ui/components/RopeSkipper.kt`（Canvas 画火柴人 + 跳绳，`rememberInfiniteTransition` 700ms 周期，小人随相位上下跳、绳在头顶↔脚下循环绕过，与启动图标同风格、紫底白线）。移除 LottieRes import，加 RopeSkipper import。编译 `compileDebugKotlin` BUILD SUCCESSFUL。
- 踩坑：①`RopeSkipper` 初版漏 `animateFloat`/`drawscope.translate` import → 连锁 `.y` 解析失败；②`kotlin.math.PI` 是 Double 致 `phase2` 推断为 Double 传 `translate` 第二参报错 → `PI.toFloat()`；③`bodyBot` 定义为 y 坐标 Float 却误用 `bodyBot.y` → 去掉 `.y`；④`quadraticBezierTo` 已废弃改 `quadraticTo`。

---

## [2026-09-02] 成就页右上角加设置入口 + 首页开始按钮上移

- 成就页 `AchievementsScreen` 新增 `nav: NavHostController` 参数（`AppRoot` 调用处补传 `nav`）；顶部标题行由纯 `Column` 改为 `Row`：左侧标题+副标题，右侧 `TextButton("⚙️ 设置")` 点击跳 `Screen.Settings`，方便在成就墙直接调音效/提醒/计数方式等。
- 首页「开始跳绳 🏃」从底部「快捷入口」之前上移至「跳跳星」卡片之后、「本周打卡」卡片之前，进首页一眼可开跳；底部原按钮删除（避免重复）。编译 `compileDebugKotlin` BUILD SUCCESSFUL。

---

## [2026-09-02] 首页「本周打卡」改为从星期一开始排列

- 原 `RecordsViewModel.weekly` 是「最近 7 天滚动窗口」（从 6 天前排到今天），并非以周一为起点。改为**本周周一 ~ 周日**的固定窗口：先算出本周一 0 点（`(DAY_OF_WEEK 转距周一天数) * DAY_MS`），再生成周一…周日 7 格；今天的格子标签仍为「今天」并保留高亮环，其余显示「周一」…「周日」。
- `WeeklyCheckIn` UI 无需改动（`label == "今天"` 高亮判断依旧成立），横排自然从周一开始。编译 `compileDebugKotlin` BUILD SUCCESSFUL。

---

## [2026-09-02] 弱化卡片弹簧缩放 + 反馈横幅容器再缩小/文字调大

- 卡片点击弹簧缩放太明显（峰值 1.18f + 低阻尼 0.35 强回弹）→ 在 `StatCard` 统一弱化：峰值 1.18f→**1.08f**、首段 `spring(700,0.35)`→`spring(420,0.62)`、回弹 `spring(450,0.55)`→`spring(380,0.72)`。统计页 4 卡与首页 3 卡（今日个数/连续天数/最佳单次）共用同一组件，一处改动同步生效，变为轻微"点一下"手感。
- `FunBanner` 容器再缩小、文字调大：最小高 44→**36dp**、padding 10→**8dp**、圆角 16→**14dp**；文案 13→**14sp**（更醒目），emoji 维持 18sp。统计页/成就页点击反馈横幅都更紧凑但不糊。编译 BUILD SUCCESSFUL。

---

## [2026-09-02] 成就未解锁提示横幅换 💡 + 反馈横幅整体调小

- 成就页未解锁卡点击反馈图标 `🔒 → 💡`，语义从「锁」改为「提示」，与卡片右上角常驻 🔒 角标区分（角标表状态、横幅给解锁提示）。
- 共用 `FunBanner` 整体调小：emoji 22sp→18sp、最小高 56dp→44dp、padding 14dp→10dp、圆角 18dp→16dp、文案 14sp→13sp。统计页/成就页点击反馈均更紧凑（用户反馈原横幅过大）。

---

## [2026-09-02] 默认计数方式改摄像头 + 设置页开关未开启态可见性优化

- 默认计数方式改为**摄像头**：`PreferencesRepository.countMode` 的 `CountMode.fromKey(it[COUNT_MODE] ?: CountMode.CAMERA.key)`，首次未设置即默认 `CAMERA`（更准更安全）；已显式选过 sensor 的仍保留。
- 设置页三个 `Switch`（每日提醒/反馈音效/夜间模式）未开启态默认边框透明、浅 lavender 几乎不可见 → 新增 `@Composable switchColors(dark)`：`SwitchDefaults.colors(checkedThumb/Track/Border=PurplePrimary, uncheckedThumb=White, uncheckedTrack=深薰衣草, uncheckedBorder=InkSoft[亮]/DarkOutline[暗])`，未开启态一眼可辨。三个开关均接 `colors = switchColors(darkOn)`。编译 BUILD SUCCESSFUL。

## [2026-09-02] 成就页九宫格角标被遮挡修复（contentPadding 代替外边距）

- 角标用 `.offset(9.dp,(-9).dp)` 向卡片右上**外溢**，而 `LazyVerticalGrid` 按自身边界裁剪子项 → 首行角标被网格上边界裁掉（"被上方遮挡"），最右列角标同理向右裁。
- 给 `LazyVerticalGrid` 加 `contentPadding = PaddingValues(top=14.dp, end=14.dp, bottom=16.dp)`：上/右内边距容纳外溢角标不再裁切；底部内边距取代原外部下边距（满足"外边距改内边距"）。编译 `compileDebugKotlin` BUILD SUCCESSFUL。

## [2026-09-02] 首页去掉重复的「今日目标」卡（保留更游戏的「今日挑战」）

- 首页移除「🏁 今日目标」进度卡（与「🎯 今日挑战」目标值/进度条高度重复，新用户时两者目标都退化成 dailyGoal 完全雷同）。保留「今日挑战」（3 星评级 + 超越个人最佳，更吸引孩子）。
- `dailyGoal` 仍由 `TodayChallenge` 使用；其"保底目标"语义仍体现在设置页（可改）、训练页环形进度 + 跨目标彩带庆祝中，未丢失功能。编译 `compileDebugKotlin` BUILD SUCCESSFUL。

## [2026-09-02] 成就页未解锁卡常驻 🔒 角标（一眼区分未解锁）

- 成就页未解锁卡：右上角新增**常驻** 🔒 圆形角标（主色底盘 + 圆角裁切、挂角溢出更醒目），一眼区分未解锁状态；中心恢复显示真实 emoji（整体 0.5 透明度表示上锁），不再用 🔒 覆盖中心。
- 配套（本轮早先）：新增共用组件 `ui/components/FunBanner.kt` 让点击反馈横幅带彩带更花哨；未解锁卡点击保留「摇头式摇晃」+ `correction()` 音效 + 顶部「解锁条件」横幅。
- 编译 `compileDebugKotlin` BUILD SUCCESSFUL。

## [2026-09-02] App 名称改为「爱跳绳」并同步所有相关文案

- 显示名 `app/src/main/res/values/strings.xml` 的 `app_name` 由「跳了么」改为 **「爱跳绳」**（桌面图标 / 设置 / 通知栏展示名统一生效）。
- 同步所有用户可见的「跳了么」硬编码文案：`SplashScreen`（闪屏标题 + 欢迎使用弹框）、`SettingsScreen`（关于版本 + 通知权限说明/引导）、`PrivacyScreen`（导言）、`CameraTrainingScreen`（摄像头权限说明/引导）、`ReminderWorker`（通知标题「爱跳绳 · 该运动啦！」）。
- 模型下载 `User-Agent` 由 `JumpDaily/1.0` 改为 `AiTiaoSheng/1.0`（ASCII 标识）。
- 文档标题同步：`README.md` 与 `CHANGELOG.md` 顶部由「跳了么 / 跳一跳」改为「爱跳绳」。
- **未改动**：包名 `com.jumpdaily.jump`、类名 `JumpDailyApplication`、主题 `Theme.JumpDaily`、项目目录名等内部标识符（重命名风险高、非「app 名称」范畴，保持兼容）。

---

## [2026-09-02] 统计页 / 成就页卡片点击动效与反馈

- 统计页四张卡片（累计个数 / 最佳单次 / 打卡天数 / 连续天数）通过 `StatCard(onClick=...)` 启用弹簧弹跳（复用 `StatCard` 既有 `Animatable`+`spring` 实现）。
- 点击时播放可爱音效 `soundPlayer.star()`，并在顶部弹出 1.6s 自动消失的鼓励横幅（按卡片给不同文案，如「你已经跳了 N 个啦，太厉害了！💪」）。
- 成就页卡片抽成独立 `BadgeCard` 组件：自带弹簧缩放动效 + `clickable`，点击播放 `star()` 音效；已解锁显示「🏆 已解锁「标题」：说明」，未解锁显示「🔒 解锁条件：说明」的临时反馈横幅。
- 两页均新增 `soundPlayer: SoundPlayer` 参数，由 `AppRoot` 传入 `container.soundPlayer`。
- 顺手把统计页「本周目标」`LinearProgressIndicator` 升级为 lambda 重载，消除 deprecation 警告。
- 编译 `compileDebugKotlin` BUILD SUCCESSFUL。

---

## [2026-09-02] 成就解锁专属旋律 + 连续打卡 7 天大奖章动画

- **成就解锁专属旋律**：`SoundPlayer` 新增 `badge()`，上行大调琶音 + 高音闪光（C5→E5→G5→C6→E6），比普通 `star()` 更隆重；触发点在 `HomeScreen` 新成就 `BadgeCelebration` 弹出时 `sound.badge()`（含震动）。无音频素材时走 `AudioTrack` 实时合成兜底，永不中断。
- **连续打卡里程碑大奖章**：新增 7 的倍数（7/14/21…）里程碑庆祝。
  - `PreferencesRepository` 新增 `lastStreakMedal`（Int）标记「已庆祝过的最高里程碑」；首次基线种子化时把当前 `streak/7*7` 记为基线，避免老用户/已连续多天者一打开就误弹。
  - `HomeScreen` 新增 `StreakMedalCelebration`：旋转金光环(✨ 无限旋转) + 奖章 🏅 弹入(spring 缩放) + 彩带 + 奖励上浮 + 「连续打卡 N 天！坚持大奖章解锁啦」祝贺卡，并播放专属号角 `sound.fanfare()`（更长更辉煌的胜利旋律）。
  - 仅在「本次跨过的里程碑 > 已庆祝最高里程碑」时触发，每个里程碑只庆祝一次；关闭后持久化里程碑值。
- 编译 `compileDebugKotlin` BUILD SUCCESSFUL。

---

## [2026-09-02] 横幅花哨化（带彩带）+ 成就未解锁卡「摇晃」提示

- **花哨鼓励横幅**：新增共用组件 `ui/components/FunBanner.kt`——主色底 + 内嵌 `ConfettiBurst` 彩带喷洒（文案变化 `key(text)` 时重新喷一次）+ emoji + 缩放淡入淡出（`scaleIn(0.85)`/`scaleOut`）。统计页、成就页点击卡片的反馈横幅统一替换为 `FunBanner`（取代原 `primaryContainer` 纯色卡）。
- **成就未解锁卡「摇晃」提示不可点**：`BadgeCard` 新增 `shake`(rotation) `Animatable`；点击未解锁卡时播放摇头式摇晃（±9° 弹簧来回 3 次回正），并改播 `soundPlayer.correction()`（下行双音「还不行」）+ 横幅「🔒 解锁条件：…」；已解锁卡保持原弹簧弹跳 + `star()` 音效。两者动画各用独立 Animatable，互不干扰。
- 反馈状态由 `String?` 改为 `Pair<String,String>?`（文案 + 配图 emoji），统计页四卡各配专属 emoji（💯🏆🌱🔥）。
- 清理：成就页移除已不用的 `Card`/`CardDefaults` 导入。`compileDebugKotlin` BUILD SUCCESSFUL。

---

## [2026-09-02] 让孩子更爱跳绳：打卡日历 / 今日挑战 / 训练中达标庆祝 / 新成就解锁

### 1. 首页·本周打卡日历（连续打卡可视化）
- `HomeScreen` 新增 `WeeklyCheckIn`：用 `records.weekly`（最近 7 天）渲染 7 个圆点，已打卡填主色 + ✓，今天加 tertiary 高亮环，并显示「连续 N 天 🔥」。把抽象的「连续天数」变成看得见的小格子，强化坚持习惯。

### 2. 首页·今日挑战（超越自己的最佳纪录）
- `HomeScreen` 新增 `TodayChallenge`：以孩子的**个人最佳单次** `bestCount` 为挑战目标（无纪录时降级为「迈出第一步」= max(每日目标,30)）。三星进度条 + 动态进度，达成显示「已达成 🏆」；鼓励「做更厉害的自己」，比单纯堆目标更有挑战趣味。

### 3. 训练页·环形目标进度 + 中途达标庆祝
- `TrainingViewModel` 新增 `dailyGoal` 状态（`init` 订阅 `prefs.dailyGoal`），`onJump` 中跨过每日目标线时**仅庆祝一次**（`goalCelebrated` 标记），触发专属 `celebrateGoal()`：彩带 + 吉祥物 HAPPY + 奖励音 + 反馈「🎯 达成今日目标啦！」。
- `TrainingScreen` 大数字外套新增 `GoalRing` 环形进度（底环 + 主色进度弧，平滑动画），下方显示「还差 N 个达成目标 / 已达成 🎉」。目标离得有多远一目了然。

### 4. 新成就解锁全屏庆祝（即时正反馈）
- `PreferencesRepository` 新增 `seenBadgeIds`（已看过的徽章集合）+ `celebrationSeeded`（首次基线种子化标记）。
- `HomeScreen` 首次进入把孩子已解锁成就标记为「已看」（老用户升级后不弹历史成就）；之后**真正新增的成就**解锁即弹全屏 `BadgeCelebration`（彩带 + 奖励上浮 + 祝贺卡「🏆 解锁新成就啦！」），点「太棒了」收下并持久化，避免重复弹。

### 5. 吉祥物待机浮动 + 首页按时段问候
- `JumpMascot` 在 `IDLE` 态增加 `rememberInfiniteTransition` 轻微上下浮动（-7dp），始终「活」着更可爱。
- 首页问候由固定「嗨」改为按时段（早上好/中午好/下午好/晚上好）+ 星期，更亲切。

### 编译
- `compileDebugKotlin` BUILD SUCCESSFUL（无错误；`LinearProgressIndicator` 已升级为 lambda 重载消除 deprecation 警告）。

---

## [2026-09-02] 体验修复：隐私同意可看全文 / 目标默认 500 / 7 天空态美化

### 1. 隐私同意框可查看协议内容
- `SplashScreen` 首启同意框正文下方新增可点击的「查看完整《隐私政策》」（主色、加粗），点击 `nav.navigate(Screen.Privacy)` 进入隐私政策页查看十节正式正文；`PrivacyScreen` 底部「我已阅读并同意」返回后同意框仍显示，不破坏首启强制同意流程。

### 2. 今日目标默认改为 500 个
- `PreferencesRepository`：`dailyGoal` 默认 `100 → 500`，`weeklyGoal` 默认 `700 → 3500`（约每天 500）。
- `SessionViewModel` / `SettingsViewModel`：`dailyGoal` / `weeklyGoal` 的 `stateIn` 初始值同步改为 `500 / 3500`。
- `SettingsScreen`：每日目标滑块范围 `30..500 → 50..1000`，每周目标滑块范围 `100..3500 → 350..3500`，使新默认值合理落在滑动区间内。
- 首页进度、训练完成「达成目标」判定、统计页周进度均动态读取该默认值，自动同步。

### 3. 统计页「最近 7 天」空态美化
- `WeeklyBarChart`：柱子增加最小基准高度（无数据时 6.dp），7 根柱子结构可见，不再整片空白。
- `StatsScreen`：当 7 天累计为 0 时，卡片内图表下方显示居中提示「还没有跳绳记录哦，今天跳一跳，点亮这 7 根小柱子吧 💪」。

---

## [2026-09-02] UI 优化：照镜子背景 / 弹框上移 / 卡片图标对齐 / 按钮文字完整

### 1. 照镜子模式背景焕新
- `CameraTrainingScreen` 整体背景由 `MaterialTheme.colorScheme.background`（浅色主题下为 Mint 浅绿，白字 HUD 对比差）改为深空紫竖向渐变 `Brush.verticalGradient(0xFF3A2A6B → 0xFF1A1430)`。相机预览全屏覆盖时不透出；无预览（授权/加载/降级）时深紫衬白字更协调好看。

### 2. 训练完成弹框内容上移
- `ResultDialog` 改用 `DialogProperties(usePlatformDefaultWidth = false)` + 外层 `Column(fillMaxSize, verticalArrangement = Top, padding(top=56.dp))`，弹框整体由居中偏下改为顶部对齐、上移；内部 `LottieRes` 140→120dp、`spacedBy` 12→10dp、Card padding 24→20dp 更紧凑。

### 3. 三卡片图标水平对齐
- `StatCard` 的 emoji 改用固定 `Box(Modifier.size(32.dp), contentAlignment=Center)` 包裹，消除 ⏱️(带组合符)/🔥/🍎 字形基线差异导致的视觉不对齐，三卡片图标严格同一水平线。

### 4. 按钮文字显示不全修复
- `CuteButton` 水平 padding 24→16dp（释放文字空间），内部文字加 `maxLines=1, softWrap=false`；配合弹框按钮 `weight(1f)` 等宽后「再跳一次」不再被圆角 `clip()` 裁掉两端。
- 编译 `compileDebugKotlin` BUILD SUCCESSFUL（仅遗留无关 deprecation 警告）。

---

## [2026-09-02] 隐私政策正文润色为可上架正式版本
- `ui/screens/PrivacyScreen.kt` 正文由口语版重写为**十节正式合规版**，覆盖：导言与适用范围（含监护人同意）、信息收集及用途、权限调用说明、存储与安全措施、共享/转让/公开披露、儿童个人信息保护、用户权利、第三方 SDK 清单、联系我们、政策更新。
- 措辞对齐《个人信息保护法》《儿童个人信息网络保护规定》：明确「最小必要」、不上传服务器、无广告/无统计上报 SDK、摄像头画面仅本地处理不留存、监护人可撤回同意等。
- 内部强调一律改用中文引号「」包裹，消除先前误用 ASCII 双引号破坏 Kotlin 字符串的编译错误。`compileDebugKotlin` BUILD SUCCESSFUL。
- ⚠️ 发布前需将「九、联系我们」中的占位 `[请替换为贵方联系邮箱]` 替换为真实可接收投诉的邮箱。

---

## [2026-09-02] 体验优化：首页可爱交互 + 底部导航修复 + 提醒开关崩溃修复

### 1. 首页表情卡片可爱交互
- `StatCard` 新增可选 `onClick` 参数：点击触发弹簧式「弹跳」动效（`Animatable` + `spring`），并回调业务逻辑。
- 首页三个数据卡可点：⭐今日个数 / 🔥连续天数 / 🏆最佳单次，点击各自播放卡通音效（`reward`/`cheer`/`star`）+ 吉祥物切到 `CHEER` 欢呼态 + 冒出逗比气泡文案（如「连续 X 天没偷懒，坚持下去你就是最棒的！🔥」），1.6s 后自动恢复。
- 吉祥物「跳跳星」自身也可点：切到 `JUMPING` 蹦跳态 + 欢呼音效 + 喊话「跳跳星陪你一起跳！🤸」。
- 顺手把设置页「反馈音效」总开关接到 `SoundPlayer.setEnabled`，音效开关真正生效（此前只存偏好未实际静音）。

### 2. 底部导航「点首页无法回到首页」修复
- 根因：tab 切换原用 `popUpTo(Home){saveState} + launchSingleTop + restoreState`，nav-compose 中 **launchSingleTop + restoreState 指向同一目标**时存在「点击无反应」的已知问题，导致有时点首页 tab 不切换。
- 修复：去掉 `restoreState`；底部栏**仅 4 个主 tab 屏（首页/统计/历史/成就）显示**，设置/孩子/隐私等二级页不再显示底部栏，消除「无高亮 tab」的困惑态。

### 3. 设置页「每日提醒」开关点击崩溃退出修复（关键）
- 根因：`AndroidManifest` 用 `tools:node="remove"` 移除了 WorkManager 默认 App Startup 初始化器，但 `JumpDailyApplication` 未实现 `Configuration.Provider`；拨动开关触发 `setReminder` → `WorkManager.getInstance()` 抛 `IllegalStateException("WorkManager is not initialized properly")`，且异常在 `viewModelScope.launch` 内未被捕获 → 直接崩溃退出。
- 修复：`JumpDailyApplication` 实现 `androidx.work.Configuration.Provider` 显式提供默认配置（与 manifest 移除默认初始化器配合使用，是 WorkManager 官方推荐做法）；并在 `SettingsViewModel.setReminder` 内对调度调用加 `try/catch` 兜底，极端情况下也不致崩溃。

---

## [2026-09-02] 优化训练完成弹框：三卡片对齐 + 按钮去图标

### 1. 三个统计卡片内容错位修复
- `ResultDialog` 中承载「时长 / 最长连跳 / 千卡」的三个 `StatCard` 原用普通 `Row` 平铺，因默认 `verticalAlignment = Top` 且各卡片高度随内容（emoji 行高、数值位数）略有差异，会出现高度不齐、视觉错位的观感。
- 修复：外层 `Row` 加 `Modifier.height(IntrinsicSize.Min)`，`StatCard` 各自加 `Modifier.weight(1f).fillMaxHeight()`，使三张卡片**宽度与高度完全一致**，内容在卡片内垂直居中，整齐对齐。

### 2. 删除按钮中的图标
- 「再跳一次」「完成」两个 `CuteButton` 原先各带 emoji 图标（`🔁` / `✅`），与文字语义重复、略显杂乱。
- 移除 `emoji` 参数，只保留纯文字按钮；两个按钮仍保持 `weight(1f)` + `height(52.dp)` 等宽等高。

### 编译
- `compileDebugKotlin` BUILD SUCCESSFUL。

---

## [2026-09-02] 权限授权弹框 + 隐私协议 + 启动/包体积/性能优化

### 1. 系统权限授权弹框（带说明，合规且体验友好）
- 新增 `ui/components/PermissionDialogs.kt`：`PermissionRationaleDialog`（申请前用大白话说明用途）+ `PermissionDeniedDialog`（被永久拒绝时引导去系统设置）。
- **摄像头权限**（`CameraTrainingScreen`）：进入页不再硬弹系统框，先弹「为什么需要摄像头」说明框；用户点「授予权限」才真正申请。若曾被勾选「不再询问」，弹「去设置」框跳转应用详情页。删除了原来「需要权限」占位按钮的直接 `launch`。
- **通知权限**（`SettingsScreen` 开启每日提醒时，Android 13+）：拨动开关若为开启且未授权，先弹通知说明框再申请；未授权则回退开关避免「开着却不提醒」；永久拒绝引导去设置。
- **移除 `MainActivity` 启动即盲申请 `POST_NOTIFICATIONS`**：改为功能处按需申请，避免首启突兀弹系统框。

### 2. 用户隐私协议与首启同意
- `PreferencesRepository` 新增 `privacyAccepted`（DataStore）标志。
- 新增 `PrivacyScreen`（设置页「📄 隐私政策」入口，或首启同意框「查看」进入）：分八节说明收集信息/存储保护/权限/第三方 SDK/儿童隐私/用户权利等，明确「数据仅存本机、不上传」。
- `SplashScreen` 首启弹「欢迎使用跳了么」同意框：未同意不进首页；「同意并继续」写入标志并进入，「不同意」`finishAffinity()` 退出。

### 3. 启动 / 包体积 / 性能优化
- **Release 开启 R8**：`isMinifyEnabled=true` + `isShrinkResources=true`；补齐 `proguard-rules.pro`（Room / MediaPipe / CameraX / Lottie / Kotlin / Coroutines / Compose / 应用枚举与数据模型），并 `-dontwarn` MediaPipe 传递的 auto-value 编译期类（javax.annotation.processing / javax.lang.model）。
- **仅打包中文资源**：`resourceConfigurations += zh/zh-rCN/zh-rTW`，剔除依赖库其它语言字符串。
- **Release 仅 ARM ABI**：`ndk.abiFilters = arm64-v8a, armeabi-v7a`（MediaPipe/CameraX 本就不支持 x86），release APK 不再含 x86 原生库。
- **图标依赖瘦身**：移除 `material-icons-extended`（数十 MB 图标资源），改用 `material-icons-core`（本应用仅用 `Icons.Filled.Add/Delete`，均在 core 内）。
- **隐私安全**：`android:allowBackup=false` + `fullBackupContent=false`，避免儿童本地数据被云备份；新增 `enableOnBackInvokedCallback=true`（预测返回）。
- **性能**：新增 `androidx.profileinstaller`，支持 Play 云基线配置（Baseline Profile）下发，提升冷启动/滚动流畅度。
- **修复 release 签名路径 bug**：`local.properties` 的 `KEYSTORE_FILE=app/jumpdaily-release.keystore` 原被按模块目录解析成 `app/app/...`，改为 `rootProject.file(storePath)`，release 现在可正常签名出包。

### 编译
- `compileDebugKotlin` 与 `assembleRelease` 均 BUILD SUCCESSFUL；release APK 32MB、仅 arm ABI。

---

## [2026-09-02] 修复摄像头姿态引擎崩溃（MediaPipe 原生库 ABI 限制）

### 现象
在 **x86_64 模拟器**打开摄像头训练页即闪退：`UnsatisfiedLinkError: couldn't find "libmediapipe_tasks_vision_jni.so"`，且异常在相机线程未被捕获，直接杀进程。

### 根因
`com.google.mediapipe:tasks-vision` 的 AAR 只内置 **ARM** 架构（`arm64-v8a`/`armeabi-v7a`）原生库，无 `x86`/`x86_64`。`PoseLandmarker.<clinit>` 调 `System.loadLibrary` 抛 `UnsatisfiedLinkError`（继承 `Error` 而非 `Exception`），而原 `create()` 的 `catch (Exception)` **兜不住**，异常上抛致 App 崩溃。

### 修复
- `PoseJumpDetector.create()` 的 catch 由 `Exception` 改为 `Throwable`，并新增 `onInitError` 回调；原生库加载失败时不再崩溃，仅通知 UI。
- `CameraTrainingScreen` 监听 `onInitError`，弹出友好提示卡片（说明 MediaPipe 仅支持 ARM、建议真机/ARM 模拟器），**保留摄像头预览**（"看到自己"仍可用），不再计次。

### 备注
- 想完整体验姿态计数，请用 **真机** 或 **arm64-v8a 模拟器镜像**（x86_64 模拟器因缺原生库无法跑 MediaPipe）。
- 未加 `ndk.abiFilters` 限制，避免在本机 x86_64 模拟器上连其他页面都装不上（INSTALL_FAILED_NO_MATCHING_ABIS）。

---

## [2026-09-02] 品牌焕新 + 启动体验 + 训练页按钮修复

### 1. 桌面图标与名称（更主流）
- 应用显示名 `app_name` 由「跳一跳」改为 **「跳了么」**（strings.xml），同步 SplashScreen、通知标题、设置页版本文案。
- 启动图标 foreground 重画为**白色跳绳人物线稿**（头/身/双臂握绳/腾空双腿 + 环绕跳绳），比原星形吉祥物更直观贴合跳绳主题；background 主色对齐新主色 `#6A3FE8`（ic_launcher_background.xml）。

### 2. 消除启动白屏
- 新增 `res/drawable/splash_background.xml`（品牌紫 135° 渐变），`themes.xml` 的 `Theme.JumpDaily` 加 `android:windowBackground`，冷启动窗口首帧即为品牌色，不再白闪。
- SplashScreen 背景与文字改为品牌紫 + 白字，与窗口底色一致，避免紫底紫字不可见。

### 3. 跳绳界面布局与按钮修复
- **去除按钮图标重复**：`CuteButton("暂停 ⏸", emoji="⏸")` 文字与 emoji 参数重复渲染 ⏸ → 改为文字不含 emoji、统一由 `emoji` 参数传入（暂停/继续/结束/再跳一次/完成，训练页与相机页一致）。
- **结束弹框按钮等宽等高**：`ResultDialog` 两个按钮加 `weight(1f)` + 固定 `height(52.dp)`，配色保留主/次强调但边界框完全一致，解决「大小不一」。
- 训练页主容器加 `navigationBarsPadding()`，避免底部按钮贴住手势栏。

---

## [2026-09-02] 跳绳界面优化：孩子看见自己跳 · 上浮奖励

参考 Apple Fitness+ / Just Dance / Duolingo 等儿童向健身产品设计，优化训练页沉浸感与正向反馈。

### 看见自己的跳绳动作（CameraTrainingScreen）
- **前置自拍镜像**：摄像头由后置 `DEFAULT_BACK_CAMERA` 改为 `DEFAULT_FRONT_CAMERA`，`PreviewView.scaleX = -1f` 做镜像，孩子看到「镜子里的自己」。
- **实时骨架叠加**：新增 `camera/PoseSkeleton.kt`（归一化关键点 + BlazePose 连线拓扑）与 `ui/components/PoseOverlay.kt`（cover 变换 + 镜像映射，Canvas 画连线/关节点）。
  - `PoseJumpDetector` 新增 `onLandmarks` 回调，每帧把 33 关键点（归一化 x,y）回传给 UI；骨架 state 独立持有，**仅 PoseOverlay 每帧重组**，不影响大数字等 HUD。
  - 对齐前提：PreviewView 默认 FILL_CENTER（cover），`srcAspect` 取送入检测器的竖屏位图 3:4；前置预览已镜像故骨架同步 `mirror=true`。

### 向上浮的奖励元素（全训练页通用）
- 新增 `ui/components/FloatingRewards.kt`：
  - `FloatingRewards(count)`——每次计数 +1 时从画面下方中央冒出 ⭐✨💖🌟🏅🎈👏，向上飘升 240dp、放大并淡出（并发上限 24，自动回收）。
  - `PopCount(count)`——大数字每次变化瞬间放大回弹（spring stiffness 350 / damping 0.55）。
- 两个训练页（CameraTrainingScreen / TrainingScreen）均接入 `FloatingRewards` + `PopCount`。

### 头图与文案
- 摄像头页顶部标识改为「🤳 照镜子模式」，中部加提示「跟着影子一起跳！」。

### 编译
- `compileDebugKotlin` BUILD SUCCESSFUL（仅遗留 CameraX/Material3/LocalLifecycleOwner 等 deprecation 警告，与本次无关）。

---

## [2026-09-01] 三大方向交付：音效+Lottie · 摄像头姿态计数 · 单测+发布签名

本日按编号顺序完成三个方向，并最终打通发布链路。

### ① 真人/卡通音效 + Lottie 动效
- **资产生成**：`tools/gen_assets.py`（纯 Python 标准库，无第三方依赖）生成
  - 6 个卡通音效 WAV → `app/src/main/res/raw/snd_reward.wav` `snd_star.wav` `snd_correction.wav` `snd_cheer.wav` `snd_tick.wav` `snd_complete.wav`
  - 2 个 Lottie JSON → `app/src/main/res/raw/celebration.json`（彩带爆发）`star_spin.json`（五角星旋转）
- **音效**：`app/.../audio/SoundPlayer.kt` 重写——优先 `MediaPlayer` 播放素材，失败/异常回退 `AudioTrack` 实时合成（**零素材也能跑**）。接口 `reward/star/correction/cheer/tick/complete` 不变；`reward/complete` 带振动。
- **动效**：`app/.../ui/components/LottiePlayer.kt` 新增 `LottieRes` 封装；`SplashScreen` 用 `star_spin` 旋转星、`TrainingScreen` 结算弹窗用 `celebration` 彩带替代原 emoji。
- **依赖**：`com.airbnb.android:lottie-compose:6.5.2`（阿里云 public 镜像可用）。
- 设置页加说明：已采用卡通音效素材，可替换为真人录音。

### ② 摄像头姿态估计计数（MediaPipe）
- **计数模式**：`app/.../data/model/CountMode.kt` 枚举（sensor / camera），设置页「📷 计数方式」可切换。
- **纯算法**：`app/.../camera/PoseCounter.kt`——髋中心归一化 y 滞回计次（指数滑动基线 + 灵敏度阈值 + 最小间隔 300ms），`estimateCadence()` 估频率；**便于单测**。
- **封装**：`app/.../camera/PoseJumpDetector.kt`（MediaPipe PoseLandmarker `LIVE_STREAM`，取 LEFT_HIP=23/RIGHT_HIP=24 髋中心 y 喂 `PoseCounter`）+ `app/.../camera/PoseModelProvider.kt`（`assets/pose_landmarker.task` 优先，否则下载官方 lite/float16 模型）。
- **页面**：新增 `CameraTrainingScreen`（CameraX `Preview` + `ImageAnalysis` 640×480 → Bitmap → `detector.processFrame`），与传感器页共享 `TrainingViewModel` 计数/反馈/庆祝流程。
- **依赖**：`camera-core/camera2/camera-lifecycle/camera-view:1.3.1` + `com.google.mediapipe:mediapipe_tasks_vision:0.10.14`；`AndroidManifest.xml` 加 `CAMERA` 权限。

### ③ 单元测试 + 发布签名
- **单测**（JVM，不依赖 Android 运行时）：
  - `app/src/test/java/.../camera/PoseCounterTest.kt`（3 用例：每完整落地计 1 次 / 过快不计 / 灵敏度降阈值）
  - `app/src/test/java/.../util/DateUtilsTest.kt`（3 用例：`dayStart` 清零时分秒 / `computeStreak` 连续天数 / `formatDuration` 分秒格式化）
  - `build.gradle.kts` 加 `testImplementation("junit:junit:4.13.2")`
- **发布签名**：`app/build.gradle.kts` 配 `signingConfigs.release`（从根 `local.properties` 读密钥，缺失则跳过、不影响 debug）；`.gitignore` 忽略 `*.keystore` / `local.properties` / `keystore.properties`。
- **文档**：README 新增「📦 发布签名（Release）」「✅ 单元测试」两章，含完整出包 / 校验签名 / 备份安全步骤。

### 工程修复（本轮构建报错）
- `gradle-wrapper.properties`：**Gradle 9.0.0 → 8.9**。AGP 8.5.2 不支持 Gradle 9（9.0 移除 AGP 仍调用的 `Configuration.fileCollection(Spec)` 废弃 API），导致 Sync 报「找不到该方法」。
- `app/build.gradle.kts`：修复 `e: ... Unresolved reference 'util'`——顶部加 `import java.util.Properties`，`signingConfigs` 内 `java.util.Properties()` 改为 `Properties()`。

### 发布签名链路（已就绪）
- 密钥库 `app/jumpdaily-release.keystore`（PKCS12，alias=`jumpdaily`，RSA 2048，validity 10000 天）已生成。
- `local.properties` 已填 `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 四项（文件被 `.gitignore` 忽略，不入库）。
- 弱密码已升级为 32 位随机强密码（PKCS12 改 `storepass` 即同步 `keypass`，已用 `keytool -importkeystore` 导出校验成功）。

### 待验证（本机代理慢，未跑 `assembleDebug` / `testDebugUnitTest`）
- [ ] Android Studio **Sync Project with Gradle Files** + 真机运行（传感器计数正常）
- [ ] 摄像头模式：首次构建联网拉取 `mediapipe_tasks_vision`，并放置 `pose_landmarker.task`（`app/src/main/assets/` 或首次运行自动下载）
- [ ] `./gradlew testDebugUnitTest` 单测全绿
- [ ] `./gradlew assembleRelease` 出已签名 APK + `apksigner verify --print-certs` 校验签名

---

## [2026-09-01 补充] UI 元素优化 + 架构设计文档

### UI 元素优化（修复按钮/背景对比度）
- **根因修复**：`ui/components/CuteButton.kt` 仅做 `clip` 漏了 `.background(containerColor)`——按钮背景透明与页面背景融为一体、白字不可见（「按钮和背景太接近」的根因）。已补背景填充 + 阴影 + 按下反馈。
- **自适应文字色**：`CuteButton` 按底色明暗自动选字色（深底白字 / 浅底深紫字），解决浅粉/浅红按钮配白字看不清。
- `ui/theme/Color.kt`：主色 `PurplePrimary` 加深（`7C5CFF→6A3FE8`），新增高对比 `ErrorDeep`（深玫红 `D6336E`）。
- `ui/theme/Theme.kt`：浅/夜间 `error` 用 `ErrorDeep`；`Typography` 去掉固定 `color=Ink`（否则夜间深紫字落深紫底不可见），改跟随 `onSurface`。
- `ui/components/StatCard.kt`：卡片加 `1.dp` 描边，浅紫卡在奶油底边界更清晰。
- `ui/screens/ChildrenScreen.kt`：孩子选色同步新主色。
- 验证：`./gradlew compileDebugKotlin` 首次 BUILD SUCCESSFUL（仅 CameraX/Material3 deprecation 警告）。

### 架构设计文档
- 新增 `docs/ARCHITECTURE.md`：分层架构（表现层 / VM 层 / 数据层 / 平台能力 / 手动 DI）、Mermaid 依赖图与流程图、双计数算法统一 `JumpListener` 接口、数据模型 ER 图、关键流程（计数/落库/主题/提醒/种子）、工程配置、测试策略、已知限制与演进路线、目录速查。
- 新增 `docs/TECH_STACK.md`：逐项技术详解（Kotlin/Compose/Material3/Navigation/Room/DataStore/SensorManager/CameraX/MediaPipe/Lottie/WorkManager/音效/手动DI/Gradle），每节「核心思想 → 本项目真实用法(含文件路径与代码) → 关键 API → 踩坑注意」四段式，并与 ARCHITECTURE.md 互链。

---

## [2026-09-07] 训练退出保护 + 缓存清理确认 + 多渠道打包 + 一键脚本

### 摄像头训练页「退出保护」（新交互）
- 训练中（`running`/`paused` 且 `count>0` 且未出结果页）点返回键不再直接退出：`CameraTrainingScreen` 用 `BackHandler` 先 `pause()` 再弹三选框——
  - **继续**：留在当前页恢复训练；
  - **挂起**：自动暂停并挂起会话（浮条显示「⏸ 已跳 X 个」，下次进训练页可续跳，逻辑复用既有挂起快照）；
  - **清零**：调 `TrainingViewModel.discard()` 清空内存会话与挂起快照（不落库、不攒积分），首页浮条因 `tCount>0` 守卫不再显示。
- `AppRoot` 浮条可见性新增 `tCount > 0` 条件，避免清零后残留浮条。

### 清理缓存（确认 + 空态友好）
- `ProfileScreen`「清除缓存」改为先弹确认框（显示将释放的缓存大小）；`cacheBytes==0` 时改弹「缓存很干净 ✨」友好提示，不再出现「将删除 0 B」的违和文案。
- 箭头图标 `Icons.Filled.KeyboardArrowRight` → `Icons.AutoMirrored.Filled.KeyboardArrowRight`（RTL 兼容）。

### 多渠道打包（新功能）
- `app/build.gradle.kts` 新增 `flavorDimensions("channel")` + 三个 productFlavor：`official`（官方通用）/ `huawei`（华为应用市场）/ `xiaomi`（小米商店），各组合 debug/release 共 6 个变体。
- `AndroidManifest.xml` 注入 `<meta-data android:name="CHANNEL" android:value="${CHANNEL_VALUE}" />`；`defaultConfig` 兜底 `official`。运行时读取：`packageManager.getApplicationInfo(...).metaData.getString("CHANNEL")` 或 `BuildConfig.FLAVOR`。
- 新增渠道：在 `productFlavors` 照抄 `create("渠道名")` 块即可，无需改 Manifest。

### 安装包自动命名 + 一键打包脚本
- `app/build.gradle.kts` `applicationVariants.all` 重命名产物：`JumpDaily_v<版本>_<渠道>_<类型>_<日期>.apk`（例 `JumpDaily_v1.0.0_huawei_release_20260907.apk`）。
- 新增 `tools/build_apk.bat`（Windows cmd/双击）与 `tools/build_apk.sh`（Git Bash / Linux / macOS）：参数 `[all|official|huawei|xiaomi] [release|debug] [install]`，自动设 `JAVA_HOME`/`ANDROID_HOME` → Gradle 构建 → 归档 `dist\<日期>\` → 列清单；`install` 末尾自动 `adb install`。
- `.gitignore` 加 `dist/`；README「📦 发布签名」章补充多渠道与一键脚本完整用法。

### 工程清理 / 修复
- `tools/` 删除与打包无关的调试临时文件；项目根目录清理调试残留（`_d.log`/`_gv.txt`/根 `build/`），零风险（均已被 `.gitignore` 忽略）。
- PageHeader 统一收尾：修复并行编辑竞态导致的错误 import（`BoxScope.align` 免导入）、补齐 `PageHeader`/`Dimens` 导入、清理 `AchievementsScreen` 未用 `nav` 参数；达成 Kotlin 编译 0 警告、单测通过、debug/release 双构建通过、模拟器验收通过。

---

## 历史基线

- 应用主体（多孩子档案、传感器计数、统计/历史/成就、目标+达成庆祝、夜间模式、卡哇伊 UI）与上一轮迭代的功能详见 [README.md](README.md)。
