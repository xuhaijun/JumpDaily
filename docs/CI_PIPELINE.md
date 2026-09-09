# CI 自动化发布流水线

> 当前项目**没有**任何 CI 配置（无 `.github/`、无 GitLab CI、无本地 runner）。
> 本文给出可直接落地的 **GitHub Actions** 方案，并与现有 `tools/build_apk.sh`、多渠道 flavor、签名配置衔接。
> ⚠️ 标注「待实施」的部分需你按自己账号填密钥。

---

## 1. 现状与前提（真实盘点）

- ✅ Git 已接双远程（GitHub + Gitee，SSH），master 分支。
- ✅ `tools/build_apk.sh [all|official|huawei|xiaomi] [release|debug] [install]` 一键出包（见既有文档）。
- ✅ 签名从 `local.properties` 读（`storePassword`/`keyPassword`/`keyAlias`/`storeFile`），无硬编码。
- ✅ 多渠道 flavor：`official` / `huawei` / `xiaomi`（`build.gradle.kts`）。
- ❌ 无 CI 配置；无 AAB（Google Play 暂不能上，需补 `bundle<Flavor>Release`）。
- ❌ 无单元测试门禁（3 个单测存在但未接 CI）。

**关键事实**：构建机（x86_64 Linux/macOS）跑 ARM-only APK **没问题**——`ndk/abi` 只影响产物架构，不影响构建主机。CI 用 `ubuntu-latest` 即可。

---

## 2. 流水线设计

```
push 到 main ──► job: lint + unit-test（门禁，不过不往下）
tag v*.*.*  ──► job: build-release ──► 三渠道 APK + 签名 ──► 归档 artifacts
                                            └─► (可选) 上传到 华为/小米/应用宝 / Firebase App Distribution
```

- **main 分支**：只跑 `lintDebug` + `testDebugUnitTest`，当质量门禁，不产出包。
- **打 tag**（如 `v1.0.1`）：跑完整 release 构建 + 签名 + 归档，并触发商店上传。

---

## 3. GitHub Actions 工作流（`.github/workflows/release.yml`）

```yaml
name: Release
on:
  push:
    branches: [main]
    tags: ['v*.*.*']

jobs:
  # ---------- 质量门禁（main 与 tag 都跑） ----------
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4   # 自动缓存 ~/.gradle
      - name: Lint
        run: ./gradlew lintDebug
      - name: Unit Test
        run: ./gradlew testDebugUnitTest

  # ---------- Release 构建（仅 tag 触发） ----------
  build-release:
    needs: quality
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4

      # 1) 从 Secret 还原签名配置到 local.properties（与本地签名读取方式一致）
      - name: Decode keystore + write local.properties
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEY_ALIAS:       ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD:    ${{ secrets.KEY_PASSWORD }}
          STORE_PASSWORD:  ${{ secrets.STORE_PASSWORD }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > app/jumpdaily.jks
          cat >> local.properties <<EOF
          storeFile=jumpdaily.jks
          keyAlias=$KEY_ALIAS
          keyPassword=$KEY_PASSWORD
          storePassword=$STORE_PASSWORD
          EOF

      # 2) 三渠道 release 包（命名规则见既有文档）
      - name: Assemble channels
        run: ./gradlew assembleOfficialRelease assembleHuaweiRelease assembleXiaomiRelease

      # 3) 归档产物
      - uses: actions/upload-artifact@v4
        with:
          name: apks-${{ github.ref_name }}
          path: app/build/outputs/apk/**/release/*.apk

      # 4) （可选）发 GitHub Release + 附包
      - uses: softprops/action-gh-release@v2
        if: startsWith(github.ref, 'refs/tags/v')
        with:
          files: app/build/outputs/apk/**/release/*.apk
```

**与现有脚本的关系**：本流水线直接调 Gradle task，和 `tools/build_apk.sh` 底层一致。
若想「CI 也走 sh 脚本」，把第 2 步换成 `bash tools/build_apk.sh all release` 即可（需脚本支持非交互、自动从 env 读签名）。

---

## 4. 密钥保管（务必这么做）

| 密钥 | 存放 | 说明 |
|------|------|------|
| `KEYSTORE_BASE64` | GitHub Secrets（`settings/secrets`） | `base64 app/jumpdaily.jks` 后的字符串，**不要**提交 jks 文件（`.gitignore` 已忽略） |
| `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD` | GitHub Secrets | 签名别名与密码，明文不入库 |
| `local.properties` | **不提交**（`.gitignore` 已忽略） | CI 运行时由上面步骤生成 |

> ⚠️ `app/jumpdaily.jks` 已被 `.gitignore` 忽略（`*.jks`），本地和 CI 各自保管，**绝不上库**。
> ⚠️ 同一把 keystore 跨渠道/跨商店必须一致，否则用户无法覆盖安装、已上架应用也无法更新。

---

## 5. Gitee 同步（可选）

GitHub Actions 不能直接推 Gitee（需令牌）。两种做法：
- **A. 在 Gitee 配「镜像仓库」**：Gitee 侧设 GitHub 为镜像源，自动同步（最简单）。
- **B. Actions 里加一步** `git push` 到 `git@gitee.com:ecloudy/jump-daily.git`，用 `GITEE_SSH_KEY` secret 配 deploy key。

---

## 6. 商店自动上传（进阶，按需）

| 渠道 | 方式 | 需要的密钥 |
|------|------|-----------|
| 华为 AppGallery | `huawei/agconnect` Gradle 插件 `publishApp` task | `HUAWEI_APP_ID` / `client_id` / `client_secret`（AppGallery Connect） |
| 小米 GetApps | 开放平台 API 上传 | `XIAOMI_APP_ID` / `APP_KEY` |
| 应用宝 | 暂无官方 API，需手动或腾讯云「应用开放平台」上传 | — |
| Firebase App Distribution | `firebaseAppDistribution` Gradle 插件 | `FIREBASE_TOKEN` / `appId` |

这些接入点写在 `FLAVOR_ADVANCED.md`（厂商推送）同级，建议「发布自动化」与「推送 SDK」分开迭代，先跑通「构建+签名+归档」再接上传。

---

## 7. 本地等价命令（无 CI 也能复现）

```bash
# 质量门禁
./gradlew lintDebug testDebugUnitTest

# 三渠道出包（签名需本地 local.properties 配好）
bash tools/build_apk.sh all release

# 产物
ls app/build/outputs/apk/*/release/
```

---

## 8. 速查清单（落地时照着做）

- [ ] 在 GitHub 仓库 `Settings → Secrets` 配 `KEYSTORE_BASE64` / `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD`。
- [ ] 加 `.github/workflows/release.yml`（本文件第 3 节）。
- [ ] main 推量 → 看 `quality` job（lint + test）是否绿。
- [ ] 打 `git tag v1.0.1 && git push --tags` → `build-release` 出三渠道包 + 归档。
- [ ] 验证 artifacts 里 3 个 APK 命名形如 `JumpDaily_v1.0.1_<flavor>_release_YYYYMMDD.apk`。
- [ ] （可选）开 Gitee 镜像 / 加 deploy key 同步。
- [ ] （可选）接华为/小米上传 task。
- [ ] ⚠️ Google Play 需 AAB：补 `bundle<Flavor>Release` + 英文元数据 + 隐私政策 URL。

---

**接入**：本篇与 `STORE_PUBLISHING.md`（商店资料/步骤）、`FLAVOR_ADVANCED.md`（渠道差异化）、`TESTING.md`（测试即门禁）、`R8_RELEASE.md`（release 构建注意）强相关。
