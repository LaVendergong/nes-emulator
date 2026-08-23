# ADR 0003: Android 作为第二 Host

- 状态：已接受
- 日期：2026-08-22

## 背景

核已经只通过 `nes.console.Console` 对外。Windows Host（AWT）不能上手机。用户要移动端，完成定义是「先能玩」：选 `.nes`、约 60 帧、虚拟手柄、有声。存档/换绑不做。

## 决策

加 **Android Host**（`android/` Gradle 工程）。NES 核仍是现有 Java 源码，打进 APK。窗口用系统 `View`，音频用 `AudioTrack`，选盘用存储访问框架（SAF）。不抽 Emulator 接口，不改 `console` 契约。

## 没选

- **Flutter / React Native**：核是 Java；跨端 UI 换不到 60 帧，还要桥一层。
- **libGDX / 一套代码打 iOS**：新依赖换不到 Android 系统 API 做不到的事；iOS 仍要另条工具链。
- **先做 iOS**：JVM 上不了，这一刀就会改语言或上 JNI。
- **把核迁到 Kotlin / C**：不可逆重写，和「Host 只贴图层」相反。

## 后果

- 从此必须遵守：Android 只 blit / 播采样 / 收按钮 bit；禁止在 `android/` 解析 iNES 或实现 mapper。
- 从此变贵的事：两套 Host（Windows Maven + Android Gradle）要各自打包。
- 推翻阈值：目标真机上 NROM 稳不住约 60 帧（已按周期模型），或必须上架 iOS。此时再重开：native 层或第二条 Host 工具链。
