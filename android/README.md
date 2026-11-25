# Android 客户端（android）

基于 Kotlin 2.x + Jetpack Compose + Material 3（GitHub 极客风深浅色自适应）+ Retrofit + Kotlinx Serialization + Room + Paging 3 + Hilt + Coil 构建。

## 核心约束

- **零直连 GitHub API**：代码中禁止直连 GitHub 官方接口域名，所有榜单、详情与 README 均从我方后端域名获取。
- **零凭证**：APK 内不包含任何 `GITHUB_TOKEN`。
- **请求头注入**：OkHttp `ClientVersionInterceptor` 为每个请求自动注入 `X-Client-Version: BuildConfig.VERSION_NAME`。
- **HTML 安全双保险**：服务端清洗 README 后，客户端 `WebView` 进一步关闭 JavaScript 并禁用文件/内容协议访问。

## 本地构建与联调

```bash
# 1. 如尚未生成 gradlew jar，请先执行一次：
gradle wrapper --gradle-version 8.9

# 2. 运行单元测试与 Debug 构建
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

本地调试时可在 `gradle.properties` 中临时将 `API_BASE_URL` 指向 `http://10.0.2.2:8000`（仅允许本地调试地址走明文 HTTP，生产域名强制 HTTPS）。

## 部署后回填 `API_BASE_URL` 流程

1. 收到部署 Agent（Muse AI）在交付报告中提供的线上域名 `https://<domain>`。
2. 修改 `android/gradle.properties`：
   ```properties
   API_BASE_URL=https://<domain>
   ```
3. 提交修改并打正式 tag，重新构建 APK：
   ```bash
   ./gradlew :app:assembleRelease
   ```
4. **注意**：重新打出的 APK **仅供内部验收，不对外分发**。
