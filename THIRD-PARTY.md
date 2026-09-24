# 第三方组件与开源许可

本仓库发布的可执行产物（APK）中包含以下第三方组件。为了让每一位拿到二进制的人
都能获得对应的源代码，这里列出全部组件、许可与源码地址。

---

## 一、本项目自身的许可：GPL-3.0

本项目的整体许可为 **GNU General Public License v3.0**，全文见 [LICENSE](LICENSE)。

选择 GPL-3.0 不是随意决定的，而是**被依赖关系决定的**：

本项目的 APK 里内置了 **LSPatch** 的引擎代码（`app/libs/lspatch-classes.jar`，
以及 `app/src/main/assets/lspatch/` 下的 `loader.dex`、`metaloader.dex` 与各架构的
`liblspatch.so`）。LSPatch 以 GPL-3.0 授权，因此把它的代码打包进本项目的产物后，
本项目整体也必须以 GPL-3.0 发布，并且在分发二进制时提供完整源代码。

> 也就是说：**你可以自由使用、修改、再分发这个项目，但再分发的产物也必须开源，
> 并且同样使用 GPL-3.0。**

---

## 二、组件清单

| 组件 | 版本 | 用途 | 许可 | 在本仓库中的位置 |
| --- | --- | --- | --- | --- |
| [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch) | v1.2（487） | APK 注入引擎 | GPL-3.0 | `app/libs/lspatch-classes.jar`、`app/src/main/assets/lspatch/` |
| [jiwangyihao/app_config](https://github.com/jiwangyihao/app_config) | v1.2 | 注入模块（运行时改 density / minWidth） | MPL-2.0 | `app/src/main/assets/module.apk` |
| [HighCapable/YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) | 随模块分发 | 模块侧 Hook 框架 | Apache-2.0 | 已编译进 `module.apk` |
| [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) | 0.9.4 | Compose 组件库 | Apache-2.0 | Gradle 依赖 |
| AndroidX（Core / Activity / Lifecycle） | — | 基础框架 | Apache-2.0 | Gradle 依赖 |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | — | 声明式界面框架 | Apache-2.0 | Gradle 依赖 |

---

## 三、各组件说明

### 1. LSPatch（GPL-3.0）

- 上游：<https://github.com/JingMatrix/LSPatch>
- 本项目使用的是 **JingMatrix** 维护的分支（LSPosed 组织下的 LSPatch 分支），
  官方仓库 <https://github.com/LSPosed/LSPatch> 已归档。
- 使用方式：把官方发布物中的引擎类、`loader.dex`、`metaloader.dex` 与各架构
  `liblspatch.so` 作为资源内置进本应用，运行时由本应用调用它完成注入与会话签名。
- **本仓库中的 LSPatch 部分未做源码级修改**，如需 LSPatch 的源代码，
  请前往上面的上游地址获取。
- Apache-2.0 / MIT 等宽松许可的代码混入 GPL-3.0 是允许的；反过来则不成立，
  这就是本项目必须整体采用 GPL-3.0 的原因。

### 2. app_config（MPL-2.0）

- 上游：<https://github.com/jiwangyihao/app_config>
- 以 `app/src/main/assets/module.apk` 的形式随本应用分发（预编译模块）。
- 本项目**没有修改该模块的源代码**。运行时会往模块 APK 里写入
  `assets/config.xml`（这是配置数据，不是源代码），用来把用户选择的参数传给模块。
- MPL-2.0 是「文件级」的弱著佐权许可：只要被 MPL 覆盖的文件保持 MPL，
  就可以把它与其他许可的代码一起分发。本项目的做法满足这一要求。
- 如需该模块的源代码，请前往上面的上游地址获取。

### 3. Miuix 与 AndroidX / Compose（Apache-2.0）

以普通 Gradle 依赖引入，未做修改。Apache-2.0 要求保留版权与许可声明，
本项目通过 `THIRD-PARTY.md` 与本文件履行该义务。

---

## 四、如果你想再分发

1. 保留 [LICENSE](LICENSE)（GPL-3.0）与 `THIRD-PARTY.md`。
2. 公开你修改后的完整源代码。
3. 不要移除应用中「关于」页对上游项目的致谢。
4. 不要用本项目的名字或作者名义为自己的分支背书。
