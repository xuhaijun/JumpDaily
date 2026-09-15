# Android 网络编程必备（面试精要 + 实战代码）

> 面向 **Android 高级工程师面试** 与日常工程化的网络编程知识地图。
> 从传输层 Socket 到应用层 HTTP/HTTPS，再到 OkHttp / Retrofit 主流框架与 RESTful 设计，
> 配套协程异步、安全合规、性能优化与抓包调试，最后给出 **30+ 面试高频考点速查**。
> 代码以 Kotlin 为主，关键注释中文，可直接在本项目 `app/` 中复用。

---

## 0. 为什么网络是 Android 面试的命脉

- 现在 90% 的 App 本质是「**本地壳 + 云端数据**」：社交、电商、办公、甚至本项目的跳绳排行/云端备份，都依赖网络。
- 初级只会 `Retrofit.create().getX().enqueue{ }`；**高级岗**要能回答：
  - 一次 HTTPS 请求从应用到网卡经历了什么？（应用 → OkHttp → 系统 Socket → Radio）
  - TCP 三次握手为什么是三次？HTTPS 握手怎么防中间人？
  - OkHttp 拦截器链怎么串起来的？连接池怎么复用？
  - 弱网下怎么重试、退避、缓存、离线优先？
  - 怎么防抓包、证书锁定、Token 安全存储？
- 本文档把「会用」升级到「**懂原理 + 能排错 + 知道取舍**」。

---

## 1. 网络分层与 Android 视角

### 1.1 精简分层模型（面试够用）

| 层级 | 协议/技术 | 在 Android 里的落点 |
|------|-----------|----------------------|
| 应用层 | HTTP/HTTPS、DNS、WebSocket、FTP | OkHttp / Retrofit / WebView / `URL` |
| 传输层 | **TCP**（可靠、面向连接）、**UDP**（不可靠、快） | `java.net.Socket` / `DatagramSocket` |
| 网络层 | **IP**、ICMP、路由 | 系统内核，应用一般不直接碰 |
| 链路层 | 以太网、WiFi、蜂窝（4G/5G） | 基带/Radio，厂商驱动 |

> 记忆口诀：**应用层管"说什么"，传输层管"怎么可靠送达"，网络层管"送到哪"，链路层管"怎么在线上跑"。**

### 1.2 Android 网络栈调用链（一次请求的真实路径）

```
App 代码 (Retrofit suspend)
  → Retrofit 动态代理封装 Request
    → OkHttp 拦截器链 (Application → Network)
      → 连接池取/建 RealConnection (TCP + TLS)
        → 系统 Socket (java.net，走 Linux 内核)
          → 网卡/Radio → 运营商 → 服务端
```

### 1.3 思维图

```
                ┌─────────────── Android 应用 ───────────────┐
                │  Retrofit (类型安全 REST 封装)              │
                │     ↓                                       │
                │  OkHttp  (HTTP 引擎: 拦截器/连接池/缓存)    │
                └─────┬───────────────────────────────────────┘
                      │ RealConnection
                ┌─────┴──────── 传输层 ───────────────────────┐
                │   TCP (三次握手/可靠/流式)  │  UDP (快/不可靠) │
                └─────┬─────────────────────┴─────────────────┘
                      │ IP 包
                ┌─────┴──────── 网络/链路层 ──────────────────┐
                │   WiFi / 4G-5G Radio → 互联网 → 服务端        │
                └─────────────────────────────────────────────┘
```

---

## 2. 传输层基石：Socket 编程（TCP / UDP）

### 2.1 Socket 是什么

> **Socket 是「应用进程」与「传输层协议」之间的编程接口**（IP:Port 端点）。
> 不要和「WebSocket」（应用层全双工协议）混淆——Socket 是更底层的概念。

- **TCP Socket**：像打电话，先接通（三次握手）再说话，保证顺序、不丢、不重。
- **UDP Socket**：像寄明信片，写完就扔，不保证到、可能乱序，但快、无连接开销。

### 2.2 TCP Socket 实战（原生，理解原理用）

```kotlin
// ⚠️ 必须在 IO 线程！主线程联网会抛 NetworkOnMainThreadException
// ⚠️ 生产环境几乎不直接写原生 TCP，但面试常考其上的 HTTP 封装原理
suspend fun tcpEcho(host: String, port: Int, msg: String): String = withContext(Dispatchers.IO) {
    Socket(host, port).use { socket ->           // use{} 自动关流，防泄漏
        socket.soTimeout = 5000                    // 读超时 5s，防永久阻塞
        val out = socket.getOutputStream().bufferedWriter()
        val input = socket.getInputStream().bufferedReader()
        out.write(msg); out.newLine(); out.flush() // 写完必须 flush
        input.readLine() ?: throw IOException("空响应")
    }
}
```

**三次握手（建立连接）**：`SYN → SYN+ACK → ACK`
- 为什么不是两次？防止已失效的连接请求突然又传到服务端，造成服务端空等资源。

**四次挥手（断开连接）**：`FIN → ACK → FIN → ACK`
- 为什么不是三次？TCP 全双工，关闭需双向各发一次 FIN，服务端可能还有数据要发。

### 2.3 UDP Socket 实战（实时场景）

```kotlin
// 适用：实时音视频、游戏帧同步、局域网发现；不保证到达
suspend fun udpSend(broadcastIp: String, port: Int, payload: ByteArray) = withContext(Dispatchers.IO) {
    DatagramSocket().use { ds ->
        ds.broadcast = true
        val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(broadcastIp), port)
        ds.send(packet)                            // 发出即忘，无确认
    }
}
```

### 2.4 TCP vs UDP 对比

| 维度 | TCP | UDP |
|------|-----|-----|
| 连接 | 面向连接（握手） | 无连接 |
| 可靠性 | 可靠（确认/重传/排序） | 不可靠（可能丢/乱序/重复） |
| 速度 | 较慢（有开销） | 快 |
| 流量控制 | 有（滑动窗口） | 无 |
| 典型应用 | HTTP/HTTPS、数据库、文件 | 视频通话、DNS、游戏、广播 |

> **TCP 粘包/拆包**：TCP 是字节流，无消息边界。应用层需自定义边界（固定长度 / 分隔符 / 长度前缀）。这是面试高频。

---

## 3. 应用层协议：HTTP / HTTPS 精解

### 3.1 HTTP 报文结构

```
请求：  METHOD  PATH  HTTP/1.1
        Header: value
        (空行)
        Body（可选）

响应：  HTTP/1.1  STATUS  Reason
        Header: value
        (空行)
        Body
```

### 3.2 请求方法语义（RESTful 核心）

| 方法 | 语义 | 幂等 | 安全 |
|------|------|------|------|
| GET | 获取资源 | ✅ | ✅ |
| POST | 新建资源 | ❌ | ❌ |
| PUT | 整体替换 | ✅ | ❌ |
| PATCH | 局部更新 | ❌(通常) | ❌ |
| DELETE | 删除 | ✅ | ❌ |
| HEAD | 取头部 | ✅ | ✅ |
| OPTIONS | 预检(CORS) | ✅ | ✅ |

> **幂等** = 多次相同请求效果与一次相同。这决定了"弱网重试是否安全"（见 §8.6、§11.3）。

### 3.3 高频状态码

- `2xx` 成功：`200`(OK)、`201`(已创建)、`204`(无内容)
- `3xx` 重定向：`301`(永久)、`302`(临时)、`304`(缓存命中，Not Modified)
- `4xx` 客户端错：`400`(参数错)、`401`(未认证)、`403`(无权限)、`404`(不存在)、`429`(限流)
- `5xx` 服务端错：`500`、`502`(网关坏)、`503`(过载)、`504`(网关超时)

### 3.4 关键 Header

| Header | 作用 |
|--------|------|
| `Content-Type` | 请求/响应体的媒体类型（`application/json`） |
| `Accept` | 客户端能接收的类型 |
| `Authorization` | 凭证（`Bearer <jwt>`） |
| `Cache-Control` | 缓存策略（`max-age=300`、`no-cache`） |
| `User-Agent` | 客户端标识（后端统计/灰度） |
| `Cookie` / `Set-Cookie` | 会话 |

### 3.5 HTTPS = HTTP + TLS（防窃听/篡改/冒充）

- **对称加密**（AES）：快，但密钥如何安全发给对方？→ 用非对称加密传密钥。
- **非对称加密**（RSA/ECDHE）：公钥加密、私钥解密；慢，仅用于握手阶段交换「会话密钥」。
- **数字证书 / CA**：服务端出示由可信 CA 签名的证书，客户端用内置 CA 公钥验签，确认"对方真是它声称的域名"。
- **TLS 握手（ECDHE 为例，前向保密）**：
  1. ClientHello（支持的套件、随机数）
  2. ServerHello（选定套件、随机数）+ 证书
  3. 客户端验证书 → 用服务器公钥加密「预备主密钥」/ 或 ECDHE 交换公钥
  4. 双方算出相同**会话密钥** → 之后全用对称加密
  5. Finished 校验完整性
- **为什么能防中间人**：攻击者没有服务端私钥，无法伪造被 CA 信任的证书（除非装了假根证书——这正是抓包原理，见 §14.2）。

### 3.6 HTTP/1.1 → 2 → 3 演进

| 特性 | HTTP/1.1 | HTTP/2 | HTTP/3(QUIC) |
|------|----------|--------|--------------|
| 并发 | 同域 6 个 TCP，队头阻塞 | **多路复用**（单连接多流） | 基于 **UDP**，无 TCP 队头阻塞 |
| 头部 | 文本、重复 | **HPACK 二进制压缩** | QPACK |
| 服务器推送 | 无 | 有（已少用） | 有 |
| 传输 | TCP+TLS | TCP+TLS | **UDP+QUIC**（0-RTT 建连） |

> OkHttp 默认协商 HTTP/2（多路复用，一个 TCP 复用多个请求），这是它比原生 `HttpURLConnection` 快的关键之一。

### 3.7 明文流量限制（Android 9+ 默认禁止）

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">  <!-- 禁止 http 明文 -->
        <domain includeSubdomains="true">api.example.com</domain>
    </domain-config>
    <debug-overrides>                                  <!-- 仅 debug 允许抓包证书 -->
        <trust-anchors>
            <certificates src="@raw/charles_cert" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

---

## 4. 原生 HTTP：HttpURLConnection（了解原理，已淘汰）

```kotlin
// 老旧写法，仅用于理解"框架帮我们做了什么"；新项目不要用
val url = URL("https://api.example.com/users")
val conn = url.openConnection() as HttpURLConnection
conn.requestMethod = "GET"
conn.connectTimeout = 10_000
conn.readTimeout = 10_000
val code = conn.responseCode
conn.disconnect()
```

**为什么被 OkHttp 取代**：无连接池（每次新建 TCP）、不支持 HTTP/2、拦截器/缓存/超时控制弱、API 反人类。

---

## 5. 主流网络框架总览与选型

| 框架 | 定位 | 现状 |
|------|------|------|
| `HttpURLConnection` | 系统原生 | 仅历史/原理，新项目弃用 |
| **Volley** | Google 出品，适合小请求+图片 | 已不活跃，仅旧项目 |
| **OkHttp** | **HTTP 引擎事实标准** | 必学，Retrofit 的底层 |
| **Retrofit** | **RESTful 类型安全封装** | 必学，CV 工程师标配 |
| WebSocket | 长连接全双工 | OkHttp 内置 `ws://` |
| Ktor Client | Kotlin 多平台 | 跨端项目可选 |

**结论（面试标准答案）**：
> 用 **OkHttp 做 HTTP 引擎**（连接池、拦截器、HTTP/2、缓存），
> 用 **Retrofit 做类型安全的 REST 封装**（注解 → 动态代理 → OkHttp Call），
> 二者是 Android 网络层的"黄金组合"。

---

## 6. OkHttp 深度（原理 + 实战）

### 6.1 接入与单例

```kotlin
// 单例！不要每次请求 new（否则连接池/缓存失效）
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)        // WebSocket 心跳
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY   // 注意脱敏！生产用 BASIC
    })
    .build()
```

### 6.2 请求/响应模型

```kotlin
val request = Request.Builder()
    .url("https://api.example.com/users/1")
    .header("Authorization", "Bearer $token")
    .get()
    .build()
// 同步（需 IO 线程）：client.newCall(request).execute()
// 异步：client.newCall(request).enqueue(object : Callback { ... })
```

### 6.3 拦截器链（面试核心：责任链模式）

```
Application 拦截器（用户添加，不关心重定向/重试）
   ↓
RetryAndFollowUpInterceptor  （失败重试/重定向）
   ↓
BridgeInterceptor           （加 Host/Content-Length/ gzip 解压）
   ↓
CacheInterceptor            （命中缓存直接返回）
   ↓
ConnectInterceptor          （从连接池取/建 TCP+TLS 连接）
   ↓
Network 拦截器（能看到真实网络请求，含代理）
   ↓
CallServerInterceptor       （写请求体、读响应）
```

```kotlin
// 自定义：统一注入鉴权 Header（Application 拦截器）
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val req = chain.request().newBuilder().apply {
            tokenProvider()?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        return chain.proceed(req)              // 责任链：必须 proceed 才往下走
    }
}
```

> **Application vs Network 拦截器区别**：
> - `addInterceptor`：每个请求只走一次，看不到重定向后的真实请求。
> - `addNetworkInterceptor`：每次网络尝试都走（重定向/重试会走多次），能看到最终字节流。

### 6.4 连接池（ConnectionPool）

- 默认最多 **5 个空闲连接 / 同路由，保活 5 分钟**。
- HTTP/1.1 `keep-alive` + HTTP/2 多路复用 → 复用同一 TCP，省去握手开销。
- 这是"为什么 OkHttp 比原生快"的核心之一。

### 6.5 缓存

```kotlin
.cache(Cache(File(context.cacheDir, "http_cache"), 10L * 1024 * 1024))  // 10MB
// 配合服务端 Cache-Control，或自定义 CacheInterceptor 实现离线优先
```

### 6.6 证书锁定（防中间人，见 §12.1）

```kotlin
.certificatePinner(
    CertificatePinner.Builder()
        .add("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .build()
)
```

### 6.7 事件监听（监控每次请求耗时）

```kotlin
.eventListenerFactory(EventListener.NONE) // 可自定义统计 dns/connect/request/response 各阶段耗时
```

---

## 7. Retrofit 深度（RESTful 类型安全封装）

### 7.1 接口定义（注解驱动）

```kotlin
interface UserApi {
    @GET("users/{id}")                       // 路径参数
    suspend fun getUser(@Path("id") id: Long): User

    @GET("users")                            // 查询参数
    suspend fun list(@Query("page") page: Int, @Query("size") size: Int): Page<User>

    @POST("users")
    @Headers("Content-Type: application/json")
    suspend fun create(@Body user: User): User

    @FormUrlEncoded                         // 表单
    @POST("login")
    suspend fun login(@Field("name") name: String, @Field("pwd") pwd: String): Token

    @Multipart                              // 文件上传
    @POST("avatar")
    suspend fun upload(@Part file: MultipartBody.Part): ApiResp<Unit>

    @Headers("Cache-Control: max-age=300")  // 静态 Header
    @GET("config")
    suspend fun config(): Config
}
```

### 7.2 Converter（JSON 解析）

```kotlin
Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .client(okHttpClient)
    .addConverterFactory(MoshiConverterFactory.create())      // 或 Gson / kotlinx-serialization
    .addCallAdapterFactory()                                  // 默认已支持 suspend
    .build()
```

### 7.3 协程 + Retrofit（最常用形态）

```kotlin
// suspend 函数 = Retrofit 内部用 OkHttp 异步 + 挂起，调用方在 viewModelScope 直接 await
class UserRepository(private val api: UserApi) {
    suspend fun profile(id: Long): Result<User> = runCatching { api.getUser(id) }
}
```

### 7.4 动态 baseUrl / 多域名

```kotlin
@GET                                                               // 配合 @Url 传完整地址
suspend fun proxy(@Url url: String): String

// 或自定义给 OkHttp 的拦截器根据 host 选不同 baseUrl（多域名网关场景）
```

### 7.5 统一错误响应（密封类 + 拦截器）

```kotlin
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val code: Int, val msg: String) : ApiResult<Nothing>
    data class NetworkError(val e: Throwable) : ApiResult<Nothing>
}
// 在拦截器/CallAdapter 里把 HTTP 非 2xx 与 IOException 映射成对应分支
```

### 7.6 文件上传/下载 + 进度

```kotlin
// 进度监听：用 OkHttp 的 ProgressRequestBody 包装，拦截 writeTo 回调百分比
val body = ProgressRequestBody(file.asRequestBody("image/*")) { percent ->
    _progress.tryEmit(percent)
}
api.upload(MultipartBody.Part.createFormData("file", file.name, body))
```

### 7.7 最佳实践清单

- ✅ **单例** OkHttpClient + Retrofit（不要每次 new）
- ✅ 统一超时 + 日志拦截器（生产用 `BASIC` 并脱敏）
- ✅ 统一错误处理（密封类 `ApiResult`）
- ✅ `proguard-rules.pro` 保留模型类与 `Service` 接口（R8 混淆会改坏反射）
- ✅ 用 `kotlinx.serialization`/`Moshi` 而非 `Gson`（Kotlin 空安全友好）

---

## 8. RESTful API 设计规范

### 8.1 资源即 URL，动词由 HTTP Method 表达

```
❌ POST /getUser?id=1        ✅ GET  /users/1
❌ POST /deleteUser?id=1     ✅ DELETE /users/1
❌ GET  /users/getAll        ✅ GET /users
```

### 8.2 命名规范

- 资源用**名词复数**：`/users`、`/orders`
- 小写、连字符：`/training-records`
- 层级：`/users/{uid}/orders`
- 过滤/分页/排序用 query：`?status=done&page=2&sort=-createdAt`

### 8.3 状态码语义化

- 创建成功返回 `201` + `Location` 头
- 删除成功返回 `204`（无体）
- 校验失败 `400`、未登录 `401`、无权限 `403`、不存在 `404`

### 8.4 版本管理

```
URL 版本：  https://api.example.com/v1/users     （简单，但 URL 污染）
Header 版本：Accept: application/vnd.myapi.v1+json（优雅）
```

### 8.5 认证与授权

- **JWT**：服务端签发带签名 token，客户端存本地，每次 `Authorization: Bearer <jwt>`。无状态、易扩展。
- **OAuth2.0**：第三方授权（微信/Google 登录）。四种授权模式：授权码（最安全）、隐藏式、密码式、客户端凭证。
- **区别**：认证（你是谁 Authentication）≠ 授权（你能干啥 Authorization）。

### 8.6 幂等性（决定弱网能否重试）

| 方法 | 幂等 | 重试安全？ |
|------|------|-----------|
| GET | ✅ | 安全 |
| PUT | ✅ | 安全 |
| DELETE | ✅ | 安全 |
| POST | ❌ | **不安全**（可能重复创建）→ 需客户端幂等键 `Idempotency-Key` |

### 8.7 统一响应体

```json
{ "code": 0, "message": "ok", "data": { "id": 1, "name": "小明" } }
// code=0 业务成功，非 0 业务错误；HTTP 状态码仍应语义正确
```

---

## 9. 数据序列化：JSON / Protobuf

| 方案 | 优点 | 缺点 | 场景 |
|------|------|------|------|
| Gson | 老牌、兼容好 | 反射慢、Kotlin 空安全差 | 旧项目 |
| **Moshi** | 无反射(代码生成)、Kotlin 友好 | 需 kapt/KSP | 推荐 |
| **kotlinx.serialization** | Kotlin 官方、多格式 | 生态较新 | 新项目首选 |
| **Protobuf** | 二进制、体积极小、跨语言、快 | 不可读、需 schema | 实时/高频/跨端 |

> 本项目的 MediaPipe 模型、DataStore 内部都已用 protobuf（`jump_prefs.preferences_pb`），可见二进制协议在端侧的成熟应用。

---

## 10. 长连接：WebSocket / SSE

### 10.1 何时用

- 实时聊天、行情推送、多人游戏、训练实时对战。
- HTTP 轮询浪费连接；WebSocket 一次握手后**全双工长连**。

### 10.2 OkHttp WebSocket 实战

```kotlin
val ws = okHttpClient.newWebSocket(
    Request.Builder().url("wss://api.example.com/ws").build(),
    object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            ws.send("hello")                       // 发文本/字节
        }
        override fun onMessage(ws: WebSocket, text: String) { /* 收消息，切主线程刷新 UI */ }
        override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) { /* 触发重连 */ }
    }
)
// 重连 + 心跳：用 pingInterval + 监听 onFailure 指数退避重连
// 必须在 viewModelScope 外持有引用或用 Service，否则被回收
```

### 10.3 SSE（Server-Sent Events）

- 服务端单向推送（文本流），基于普通 HTTP，比 WebSocket 轻。
- 适合通知、直播弹幕。Android 可用 `EventSource`（okhttp-sse 或第三方）。

---

## 11. 协程 + 网络：异步与错误处理的工程化

> 与本项目 `COROUTINE_FLOW_VM.md` / `COROUTINE_EXCEPTION_TIMEOUT.md` 一脉相承。

### 11.1 网络必须在 IO 调度器

```kotlin
viewModelScope.launch {
    _uiState.value = UiState.Loading
    val result = withContext(Dispatchers.IO) { repo.profile(id) }  // 别在主线程
    _uiState.value = when (result) {
        is ApiResult.Success -> UiState.Data(result.data)
        is ApiResult.Error   -> UiState.Error(result.msg)
        is ApiResult.NetworkError -> UiState.Error("网络开小差了")
    }
}
```

### 11.2 统一封装（Result / 密封类 + 异常映射）

```kotlin
suspend fun <T> safeApi(call: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(call())
} catch (e: HttpException) {            // Retrofit 非 2xx 抛这个
    ApiResult.Error(e.code(), e.message())
} catch (e: IOException) {              // 断网/超时
    ApiResult.NetworkError(e)
} catch (e: Exception) {
    ApiResult.Error(-1, e.message ?: "unknown")
}
```

### 11.3 超时与重试（弱网对抗，指数退避）

```kotlin
suspend fun <T> retryIO(
    times: Int = 3,
    initialDelay: Long = 500,
    block: suspend () -> T
): T {
    require(times > 0)
    var current = initialDelay
    repeat(times - 1) {
        try { return block() }
        catch (e: IOException) {
            delay(current)                     // 退避
            current *= 2                       // 指数增长：500→1s→2s
        }
    }
    return block()                            // 最后一次不再吞异常
}
// ⚠️ 只有幂等请求(POST 除外)才盲目重试，非幂等需 Idempotency-Key
```

### 11.4 与 ViewModel / StateFlow 集成

- `_state: MutableStateFlow` + 只读出口（见 `COROUTINE_FLOW_VM.md`）
- `stateIn(viewModelScope, WhileSubscribed(5000), initial)` 让 UI 自动订阅/取消
- 一次性事件（toast）用 `Channel` 或 `SharedFlow`

### 11.5 取消传播

- `viewModelScope` 在 `onCleared` 自动取消，挂起的网络请求随之取消。
- ⚠️ 自定义 `CoroutineScope` 必须 `cancel()`，否则泄漏（参考 `COROUTINE_EXCEPTION_TIMEOUT.md`）。

---

## 12. 安全与隐私合规

### 12.1 HTTPS 强制 + 证书锁定

- 生产 `cleartextTrafficPermitted=false`（§3.7）。
- 关键接口用 `CertificatePinner`（§6.6）防伪造证书中间人。
- ⚠️ 证书轮换要预留旧 hash，否则更新证书会全体无法联网。

### 12.2 防抓包

- 证书锁定让 Charles/Fiddler 无法解密（除非 root + 改包）。
-  release 包移除 `BODY` 级日志拦截器、混淆、检测 `Build.isUserBuild`/root。
- 敏感字段（密码、Token）绝不打日志。

### 12.3 Token 安全存储

```kotlin
// 用 EncryptedSharedPreferences（基于 Android Keystore，主密钥硬件隔离）
// 或本项目的 DataStore 存放非敏感偏好；Token 类敏感数据走加密存储
```

### 12.4 儿童/国内合规（本项目相关）

- 权限最小化（见 `PERMISSION_PRIVACY.md`）。
- 隐私政策公网 URL（见 `STORE_PUBLISHING.md`）。
- 未成年人数据本地优先、上云需监护人同意。

---

## 13. 性能优化与弱网对抗

| 手段 | 说明 |
|------|------|
| 连接复用 | OkHttp 连接池 + HTTP/2 多路复用 |
| **HttpDNS** | 绕过运营商 LocalDNS 劫持/污染，直接拿 IP，加快解析、防域名劫持 |
| 超时分级 | 连/读/写分别设；上传大文件加长写超时 |
| 重试+退避 | 仅幂等请求；指数退避（§11.3） |
| 缓存策略 | `Cache-Control` + 拦截器实现「有网取新、无网用旧」离线优先 |
| 断点续传 | Range 头 `bytes=1000-` 分片下载/上传 |
| 图片优化 | Coil/Glide 自动缩放、WebP、HEIC |
| 流量节省 | 合并请求、差量同步、压缩(gzip/br) |
| 电量 | 批量上报（本项目积分每 3s flush）、避免后台频繁唤醒 |

> **离线优先呼应**：本项目 `Glance_WIDGET.md` 的 `WidgetRepository` 就是「失败了兜底空快照」的离线优先思路；网络层同理。

---

## 14. 调试与抓包

### 14.1 OkHttp 日志拦截器

```kotlin
HttpLoggingInterceptor().apply { level = Level.BODY }  // debug 看完整报文；生产改 BASIC 并脱敏
```

### 14.2 Charles / Fiddler 抓 HTTPS 原理

1. 电脑装 Charles，手机 WiFi 设代理指向电脑 IP:8888。
2. 手机浏览器下载并安装 Charles **根证书**。
3. **Android 7+**：用户证书默认不信任给 App，需在 `network-security-config` 的 `debug-overrides` 配置信任锚（见 §3.7），或 root 后装系统证书。
4. Charles 用根证书动态签发「假证书」解密 HTTPS（中间人），所以**证书锁定能挡住它**。

### 14.3 其他工具

- **Chucker**：OkHttp 拦截器，App 内查看请求（无需电脑）。
- **Stetho**：Facebook，Chrome DevTools 看网络。
- **adb**：`adb shell dumpsys connectivity` 查网络状态；`adb logcat` 看网络异常。

### 14.4 真机排查清单

- 断网 → `IOException`；超时 → 调大 readTimeout / 查弱网。
- `SSLHandshakeException` → 证书/系统时间/域名不匹配。
- `CertificatePinner` 配错 → 全体 4xx/网络错，先注释定位。
- 抓不到包 → 检查代理、证书信任、`cleartextTrafficPermitted`。

---

## 15. 面试高频考点速查（30+）

**TCP/HTTP 基础**
1. TCP 三次握手 / 四次挥手流程与原因
2. TCP 为什么可靠（确认/重传/滑动窗口/拥塞控制）
3. TCP 粘包/拆包原因与解决（长度前缀/分隔符）
4. UDP 与 TCP 区别与适用场景
5. HTTP 与 HTTPS 区别；HTTPS 怎么防中间人
6. HTTP 常见状态码（尤其 301/302/304/401/403/429/502/504）
7. GET 与 POST 区别（语义/幂等/缓存/ body）
8. HTTP/1.1 vs 2 vs 3（多路复用/队头阻塞/QUIC）

**TLS 安全**
9. 对称 vs 非对称加密；HTTPS 握手流程（ECDHE 前向保密）
10. 数字证书 / CA / 证书链作用
11. 证书锁定（Certificate Pinning）原理与风险
12. Android 9+ 明文流量限制与 `network-security-config`

**OkHttp / Retrofit**
13. OkHttp 拦截器链顺序与责任链模式
14. Application 与 Network 拦截器区别
15. 连接池原理与 HTTP/2 多路复用
16. Retrofit 怎么把接口变成实现（动态代理 + 注解解析）
17. Converter / CallAdapter 分别干什么
18. Retrofit 如何支持 `suspend`（底层还是 Callback/Call）
19. 为什么 OkHttp/Retrofit 必须单例
20. 文件上传/下载进度怎么监听

**RESTful / 设计**
21. RESTful 资源命名与动词语义
22. 幂等性定义 + 哪些方法幂等 + 重试风险
23. JWT vs Session 认证；OAuth2 四种模式
24. API 版本管理方案
25. 统一响应体设计

**协程/工程**
26. 网络请求为什么放 `Dispatchers.IO`
27. 弱网重试为什么只用幂等请求；指数退避
28. `viewModelScope` 如何自动取消网络请求
29. 统一错误处理：密封类 `ApiResult` 设计
30. 超时 `withTimeoutOrNull` vs OkHttp 原生超时区别

**长连接/优化**
31. WebSocket 与 HTTP 轮询区别；心跳与重连
32. HttpDNS 解决什么问题（劫持/解析慢）
33. 离线优先 + 缓存策略怎么落地
34. 断点续传（`Range` 头）实现要点

---

## 16. 本项目（JumpDaily）网络现状与建议

### 16.1 现状

- 当前项目**唯一的联网点是 MediaPipe 模型下载**：`PoseModelProvider` 三级加载（预置 → assets → 联网下载 `.task`），见 `MEDIAPIPE_POSE.md`。
- 业务数据（跳绳记录、积分、孩子档案）**全部本地**（Room + DataStore），无云端同步/排行。
- 网络相关工程能力已具备：`Dispatchers.IO` 异步（见 `COROUTINE_FLOW_VM.md`）、`withTimeoutOrNull` 超时（见 `COROUTINE_EXCEPTION_TIMEOUT.md`）。

### 16.2 若后续引入云端（建议栈）

```
云端排行/多设备同步：
  OkHttp(单例, 拦截器: 鉴权/日志/证书锁定)
    + Retrofit(suspend 接口, Moshi/kotlinx-serialization)
    + 统一 ApiResult 密封类
    + 协程 retryIO(幂等)+ 离线优先缓存
    + Token 存 EncryptedSharedPreferences
```

- 与现有架构无缝衔接：`AppContainer` 注入 `UserApi`/`TrainingApi`，`Repository` 层加网络源 + 本地 Room 兜底（离线优先，呼应 `WidgetRepository` 思路）。

---

## 17. 一句话总结

> **Socket 是地基（TCP/UDP），HTTP/HTTPS 是语言，OkHttp 是引擎，Retrofit 是类型安全的翻译官，RESTful 是约定，协程是编排，安全与缓存是护城河。**
> 高级工程师 = 不止会调接口，更要懂"请求从应用到网卡"的每一层，以及弱网下如何不崩、不卡、不泄密。

---

*相关文档：协程 VM 实战 `COROUTINE_FLOW_VM.md`、协程异常/超时 `COROUTINE_EXCEPTION_TIMEOUT.md`、桌面小组件离线优先 `GLANCE_WIDGET.md`、权限隐私 `PERMISSION_PRIVACY.md`、发布合规 `STORE_PUBLISHING.md`、MediaPipe 联网下载 `MEDIAPIPE_POSE.md`。*
