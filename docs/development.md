# 开发指南

## 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Ladybug (2024.2.1) 或更高 |
| JDK | 17 |
| Android SDK | 34+ |
| Gradle | 8.x（项目自带 Wrapper） |
| Kotlin | 2.x |

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/KIPPUDESU/KIPPU_Trace.git
cd KIPPU_Trace
```

### 2. 用 Android Studio 打开项目

直接打开项目根目录，Android Studio 会自动识别 Gradle 项目结构。

### 3. 等待 Gradle 同步

首次打开会下载依赖，需要保持网络通畅。同步完成后 Build 窗口显示 "BUILD SUCCESSFUL"。

### 4. 运行

连接 Android 设备（或启动模拟器），点击 Android Studio 工具栏的 **Run** 按钮（绿色三角形），或使用命令行：

```bash
./gradlew installDebug
```

## 项目配置

### build.gradle.kts（应用模块）

关键配置项：

```kotlin
android {
    namespace = "com.kippu.trace"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.kippu.trace"
        minSdk = 26          // Android 8.0
        targetSdk = 34
        versionCode = 7
        versionName = "2.2.0"
    }
}
```

### 版本号管理

- `versionCode`：整数，每次发布递增
- `versionName`：语义化版本号，如 `2.2.0`

## 依赖说明

| 依赖 | 用途 |
|------|------|
| **Jetpack Compose** | UI 框架，Material 3 设计 |
| **Compose BOM** | 统一管理 Compose 相关库版本 |
| **Navigation Compose** | 页面路由导航 |
| **Room** | SQLite 数据库 ORM |
| **Coil** | 图片加载（支持 Compose） |
| **KSP** | Room 编译期注解处理（替代 kapt） |

## 代码规范

### 命名约定

- **文件名**：PascalCase，如 `HomeScreen.kt`
- **类名**：PascalCase，如 `EventViewModel`
- **函数/变量**：camelCase，如 `addEvent()`
- **常量**：UPPER_SNAKE_CASE，如 `COUNT_DOWN`
- **包名**：全小写，如 `com.kippu.trace.viewmodel`

### 架构约定

1. **UI 组件放在 `ui/components/`**：可复用的 Compose 组件
2. **页面放在 `ui/screens/`**：完整的页面级 Composable
3. **数据操作通过 Repository**：不直接在 ViewModel 中操作 DAO
4. **协程通过 viewModelScope 管理**：避免手动管理 Job 生命周期

### Compose 规范

- 预览函数使用 `@Preview` 注解，命名以 `Preview` 结尾
- 状态提升：将状态和事件回调作为参数传入 Composable
- 避免在 Composable 中直接执行副作用，使用 `LaunchedEffect` 或 `SideEffect`

## 构建变体

### Debug

- 启用 Compose UI Tooling
- 启用 UI Test Manifest
- 未混淆

### Release

- 开启代码混淆（ProGuard）
- 使用 `proguard-android-optimize.txt` 优化规则

## 测试

项目使用标准 Android 测试框架：

```bash
# 单元测试
./gradlew test

# 仪器化测试（需要设备/模拟器）
./gradlew connectedAndroidTest
```

测试依赖：
- **JUnit** — 单元测试
- **Espresso** — UI 测试
- **Compose UI Test** — Compose 专用测试

## 常见问题

### Gradle 同步失败

1. 检查网络连接，确保能访问 `google()` 和 `mavenCentral()` 仓库
2. 在 Android Studio 中：File → Invalidate Caches → Invalidate and Restart
3. 删除 `~/.gradle/caches/` 后重试

### 编译错误：SDK 版本

确保 SDK Manager 中已安装：
- Android SDK Platform 34
- Android SDK Build-Tools 34+

### Room 编译错误

Room 使用 KSP（Kotlin Symbol Processing）生成代码。如果出现符号找不到的错误：
1. 执行 `./gradlew clean` 后重新编译
2. 确认 `ksp` 插件已在 `build.gradle.kts` 中声明
