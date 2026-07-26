# AGENTS.md

## 项目概览

本项目是一个**安卓 APP**：哔哩哔哩视频解析与下载工具，复刻自桌面端项目 `../BiliTools_Desktop`，并在其基础上新增了大量特性、修复了若干 Bug。

- 包名：`com.happycola233.bilitools`
- 语言 / 构建：Kotlin + Gradle（Kotlin DSL），JDK 17+
- SDK：`minSdk 29` / `targetSdk 36`
- UI：Jetpack Compose + Material 3 Expressive（`ui/` 下按 `parse` / `downloads` / `history` / `login` / `me` / `settings` / `theme` / `widget` 分包），仅少量 XML layout 残留
- 关键依赖：OkHttp（网络）、Coil（图片）、ffmpeg-kit（音视频合并）、JAudiotagger（元数据写入）
- 依赖注入：手写容器 `core/AppContainer.kt`，无 DI 框架

目录职责：`core/`（HTTP 客户端、WBI 签名、Cookie、合并引擎、NFO、弹幕解析等基础设施）、`data/`（各 Repository 与 `data/model`）、`download/`、`notification/`、`update/`、`ui/`。

## 参考资料

- 桌面端原项目：`../BiliTools_Desktop` —— 对齐功能行为时参考
- 哔哩哔哩 API 文档：`../bilibili-API-collect` —— **凡涉及 B 站接口，一律先查此文档**，不要凭记忆猜测字段与参数
- `.devfiles/`：**只读**，仅供参考，禁止做任何修改

## 开发准则

- 保持深度思考；主动查阅最新官方文档与行业最佳实践，不要停留在过时写法上。
- 代码可维护性优先：**严防功能回归**，拒绝冗余代码与"屎山"堆积。
- 命名语义清晰；修改代码时顺手重构掉相关的旧变量名，不要新旧命名并存。
- 遇到复杂业务逻辑、非显而易见的算法、B 站接口特殊规则、兼容性处理、容易误解的状态流转或必须保留的权宜方案时，应编写清晰、准确的中文注释，说明原因、约束或设计意图；不要为代码已经清楚表达的行为添加重复注释。
- 需要任何工具或依赖时，自行安装，不必先询问。

## Commit 规范

被要求写 commit 时：

- 使用**中文 Conventional Commit**。
- **只写 commit，不要执行提交**（不 commit、不 push）。
- **描述对象是"最后一个 commit → 当前工作区"的整体 diff，不是本次对话的迭代过程。**
  典型场景：一个会话里实现新功能时，中途可能引入过回归、出过 Bug、又反复改进重构。这些中间状态对 Git 历史毫无意义——只要它在最终 diff 里看不出来，就不要写进 commit message。
  所以：先 `git diff HEAD`（含未跟踪文件）通读一遍**最终结果**，再据此下笔；不要凭对话记忆罗列"修了什么、改了几版"。
- 第一行 summary 必须覆盖该整体 diff 的**全部**内容：把所有改动合并概括，而不是从最大的那份 diff 里提炼标题。
- 主题本就不一致时可以并列写多个，不必强行归纳成单一主题。
