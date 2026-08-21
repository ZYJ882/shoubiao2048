# shoubiao2048

一款为 **Wear OS 智能手表**设计的离线原生 2048 游戏。项目使用 Kotlin 与 Jetpack Compose for Wear OS 编写，目标是在小尺寸圆形屏幕上保持简洁的操作、较短的启动路径和较小的运行时负担。

> An offline-native 2048 game for Wear OS smartwatches, built with Kotlin and Jetpack Compose for Wear OS.

## 功能

| 功能 | 说明 |
| --- | --- |
| 原生 Wear OS 界面 | 使用 Compose for Wear OS 构建单一游戏页面，并为圆形及方形表盘留出安全边距。 |
| 离线优先 | 不请求网络、不依赖账号、不使用广告或分析服务。 |
| 完整 2048 规则 | 支持四向滑动、合并计分、随机新方块、2048 达成提示和无可移动方块提示。 |
| 本地恢复 | 使用 DataStore 保存 16 格棋盘、当前分数、最高分和胜利确认状态。 |
| 防误触 | 可撤销一次有效移动；重新开始前要求确认。 |
| 精简发布配置 | release 构建启用 R8 代码压缩与资源压缩，且不声明网络、通知、位置或传感器权限。 |

## 技术方案

| 项目 | 选择 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose for Wear OS Material 3 |
| 存储 | Jetpack DataStore Preferences |
| 最低系统 | Wear OS 3（API 30） |
| 包名 | `com.shoubiao2048.app` |

项目刻意避免加载远程资源、后台任务、自定义字体和持续动画。每次有效滑动仅处理固定的 16 个格子，并保存一个小型本地快照。

## 在 Android Studio 中运行

请使用 Android Studio 打开本仓库根目录，等待 Gradle 同步完成后，选择 **Wear OS Small Round** 模拟器或已启用调试的 Wear OS 3+ 手表运行 `app` 模块。为准确评估启动时间和内存，请在真实设备上使用 release 变体进行测量。

### 测试

`app/src/test/java` 包含 2048 的核心规则测试，覆盖单次合并、纵向移动、无路可走判定和本地快照编解码。可通过 Android Studio 的测试运行器执行。

### 获取 release APK

仓库的 **Actions** 页面提供手动触发的 `Build release APK` 工作流。该工作流使用固定版本的 Gradle 与 JDK 17 构建 `assembleRelease`，并上传名为 `shoubiao2048-release-apk` 的 APK 构件。release 变体启用 R8 与资源压缩。本次自动构建会使用临时签名密钥，因此适合首次安装与测试；在长期分发或升级前，请替换为受控的持久发布签名密钥。

## 项目结构

```text
app/
├── src/main/java/com/shoubiao2048/app/
│   ├── GameEngine.kt       # 纯 2048 规则
│   ├── GameStore.kt        # DataStore 本地快照
│   ├── MainActivity.kt     # 应用入口
│   └── Wrist2048Screen.kt  # 圆屏游戏界面与滑动操作
├── src/main/res/           # 图标与 Android 资源
└── src/test/               # 规则单元测试
```

## 开源许可

本项目采用 [MIT License](LICENSE) 发布。欢迎提交 Issue 或 Pull Request 来改进手表交互、无障碍支持和性能测量。

---

## English summary

**shoubiao2048** is a compact, offline-first native 2048 game for Wear OS. It uses Kotlin, Compose for Wear OS, and DataStore. The app has no network, analytics, advertising, sensor, notification, or background-work dependency. Open the repository root in Android Studio, select a Wear OS 3+ emulator or device, and run the `app` module.
