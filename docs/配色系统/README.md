# BiliTools 配色系统设计说明

本目录记录 2026-08-29 那次配色重构的**设计理念、参数取值与背后的约束**，供后续维护时理解「为什么是这个数」而不是照着改。

- `gen_themes.py` —— 配色生成器。`values/themes.xml`、`values-night/themes.xml`、`values/colors.xml` 三个文件**全部由它产出**，不要手改。
- `verify.py` —— 回归校验。直接回读出货的 XML，核对十三套配色的对比度、层次分离度与角色一致性。
- `preview.py` —— 视觉预览。渲染色板与界面 mock，改完参数先看图再编译。

生成器依赖 `material-color-utilities-python`，预览脚本依赖 `pillow`；校验脚本只使用 Python 标准库：

```bash
pip install material-color-utilities-python pillow
```

> Windows 上 `material-color-utilities-python` 会间接 `import curses`，而 Windows 没有这个模块。生成器开头用了一段 stub 顶掉它，不要删。

改配色的完整流程：

```bash
python docs/配色系统/gen_themes.py   # 重新产出三个 XML
python docs/配色系统/verify.py       # 回读校验，看有没有指标掉出门槛
python docs/配色系统/preview.py      # 出图目视确认
./gradlew :app:assembleDebug
```

---

## 一、十三套配色

| 名称 | 枚举 | 色相 | 填充色（深浅共用） |
|---|---|---|---|
| 樱粉 | `Sakura` | 5 | `#F5ADBC` |
| 珊瑚 | `Coral` | 32 | `#FAAF9D` |
| 蜜杏 | `Apricot` | 62 | `#F2B781` |
| 沙金 | `Sand` | 90 | `#F2D48B` |
| 抹茶 | `Matcha` | 125 | `#D0E1A1` |
| 青苹 | `Mint` | 152 | `#B7E5BA` |
| 薄荷 | `Seafoam` | 178 | `#A8E6D4` |
| 湖蓝 | `Lagoon` | 202 | `#A5E5E9` |
| 天蓝 | `Sky` | 228 | `#A4D9F2` |
| 鸢尾 | `Iris` | 252 | `#A8CAF2` |
| 蓝紫 | `Periwinkle` | 277 | `#B8C0F2` |
| 丁香 | `Lilac` | 302 | `#D0BDF2` |
| 藕荷 | `Orchid` | 332 | `#F2C0EC` |

色相之间间距 25~35 度，绕满一圈。樱粉是**默认方案**，也是关掉动态取色后的回落值——粉色是 B 站的代表色，但刻意没去对齐品牌色 `#FB7299`（那个饱和度放在淡雅浅色风格里太跳），而是取了同色系里淡雅的一档。

另有两个不属于这十三套的开关：

- **动态取色**（`AppThemeColor.Dynamic`，默认值）：API 31+ 上 Compose 主题使用 Material 动态配色主题读取系统壁纸色，Activity 启动窗口与 View 层仍由 `DynamicColors.applyToActivityIfAvailable()` 同步。此时下面所有参数都不生效，配色由系统给。
- **纯黑深色模式**：`ThemeOverlay.BiliTools.DarkPureBlack`，在深色配色之上再叠一层，把中性色阶压到纯黑附近。

---

## 二、核心设计：固定填充面与模式相关强调色分离

这是整套配色最重要的一条约定，写在 `AppAccents.kt` 的 KDoc 里，实现也集中在那。

| | 取哪个角色 | 浅色 | 深色 | 用在哪 |
|---|---|---|---|---|
| **固定填充面** | `primaryFixedDim` | 同一个值 | 同一个值 | 主按钮底、选中胶囊、滑条已选段、深色开关开启态轨道、深色复选框、深色悬浮按钮 |
| **压在填充面上的内容** | `onPrimaryFixed` | 同一个值 | 同一个值 | 文字、图标、滑条已选段刻度，以及深色复选框对勾、深色开关开启态滑块、浅色滑条滑块 |
| **浅色开关开启态** | `primary` + `onPrimary` | 深色轨道、白色滑块 | — | 与强调文字同色，并沿用 M3 开关的标准角色搭配 |
| **浅色复选框选中态** | `primary` + 白色 | 深色方框、白色对勾 | — | 让方框与列表高亮边框同色 |
| **模式相关强调色** | `primary` | T36（深） | T84（浅） | 正文链接、强调文字、强调图标、进度条、液态底栏选中项，以及需要更强轮廓对比的浅色控件填充 |

### 为什么要分离

重构前整套配色是 Material Theme Builder 的 Tonal Spot 输出，`primary` 在浅色是 T40、深色是 T80，**同一个角色在两个模式里差了 40 档明度**。而按钮底、选中胶囊这些「色块」用的就是 `primary`，于是：

- 浅色模式下按钮是深色块，深色模式下是浅色块，观感完全不是一套配色；
- 设置页那十三颗色块只能挑一个模式的值来显示，跟实际界面对不上。

M3 的 **fixed 色组**正是为这种场景设计的：它的定义就是「不随明暗模式变化」。把按钮、胶囊等常规填充面迁到 fixed 色组之后，设置页的色块所见即所得，两个模式的按钮也长得一样；开关与复选框需要更强轮廓对比的浅色状态是下面写明的例外。

### 什么时候不能用填充色

填充色对页面底的对比度只有 1.23~1.60:1（浅色模式），远达不到非文字元素 3:1 的门槛。能不能用它，取决于**色块之上有没有能独立承担状态表达的文字、图标或控件形状**：

- 按钮、胶囊、滑条 —— 上面有文字、图标或滑块表达状态，**用填充色**；
- **开关 —— 浅色用 `primary` 轨道与 `onPrimary` 滑块，深色用填充色**。浅色下强化开启态与卡片底、关闭态的层次，深色下继续保持明亮的固定填充观感；
- **勾选框 —— 浅色用 `primary`，深色用填充色**。浅色方框与列表高亮边框统一，并为白色对勾提供足够反差；深色继续使用 `primaryFixedDim` + `onPrimaryFixed`，保持原有观感；
- **进度条 —— 用 `primary`**。它没有滑块，进度全靠已完成段与轨道的反差来读，填充色配 `primaryFixed` 轨道只有 1.35:1，实测不可用，所以退回 `primary` + `surfaceVariant`；
- **普通底栏 —— 沿用 Material 3 Expressive 的导航栏 token**。选中指示器用 `secondaryContainer`，选中图标用 `onSecondaryContainer`，选中文字用 `secondary`，未选中图标与文字用 `onSurfaceVariant`。文字位于指示器之外，不能与图标共用“填充面上的内容色”；
- **液态玻璃底栏 —— 用 `primary`**。它的选中气泡是透明玻璃透镜而不是色块，被着色的其实是选中项的图标与文字，属于前景语义。这一处最容易看错。

### 悬浮元素为什么要按模式取色

浅色模式下，快捷下载按钮与下载页操作菜单使用 `secondaryContainer`，和“复制字幕”等次级操作保持同一强调层级。深色模式下 `secondaryContainer` 会变成暗色，容易糊进页面底，因此切回深浅同值的 `primaryFixedDim`：

| 下载页汉堡按钮（深色模式） | 按钮/页底 | 图标/按钮 |
|---|---|---|
| 用 `primaryContainer`（M3 默认） | 1.88:1 | 2.41:1 |
| 用 `primaryFixedDim` | 9.07:1 | 8.59:1 |

这组模式分支集中在 `AppAccents.floatingActionContainer` / `onFloatingActionContainer`，界面组件不要自行判断系统深浅模式。

---

## 三、填充色的明度怎么定的：按最大通道对齐，不按明度

`gen_themes.py` 里的 `fill_tone_chroma()`：在 `C_CAP = 30`、`T_CAP = 87` 的上限内，取**最大 sRGB 通道刚够 `TARGET_CHANNEL = 242`** 的那一档明度。

### 为什么不直接用统一明度

最初十三套填充色统一取 T85，结果绿、青、蓝这些冷色相看起来明显比暖色相**深一大截**。原因是 HCT 的明度（L\*）刻画的是相对亮度，而人眼判断一个颜色「淡不淡」，更贴近它的**最大 sRGB 通道**：

| 色相 | 统一取 T85 时的色号 | 最大通道 |
|---|---|---|
| 樱粉 H5 | 带彩度后红通道顶到 255 | 255 |
| 抹茶 H125 | 绿通道只到 约 230 | 约 230 |

暖色相在带彩度时会有一个通道先撞上 255，观感就「亮」；冷色相三个通道都在中段，观感就「闷」。改成对齐最大通道之后，十三个色相摆在一起才是同一种「淡」。

### 两个上限的来历

- **`C_CAP = 30`**：再高就不是淡雅风格了。
- **`T_CAP = 87`**：绿─青色相若不设上限会顶到 T90-91，离浅色卡片底（纯白）太近，按钮浮不起来。代价是这几个色相在色板里比暖色略深一点点，换来的是全局层次立得住。

另外 `c()` 这个取色函数会把彩度自动收敛到该明度下最大彩度的 92%，避免落在色域边界上被硬裁切——裁切会同时破坏明度和色相。

---

## 四、表面色阶：三层深度

语义层封装在 `AppSurfaces.kt`，界面代码只应该用这三个名字，不要直接取 `surfaceContainer*`。

| 语义层 | 浅色 | 深色 | 纯黑 |
|---|---|---|---|
| `pageContainerColor` 页面底 | `surfaceContainer` T95 Δ8 | `surfaceContainer` T12 | `surface` #000000 |
| `cardContainerColor` 卡片底 | `surfaceBright` #FFFFFF | `surfaceBright` T24 | `surfaceContainerHigh` |
| `insetContainerColor` 卡片内嵌 | `surfaceContainerLow` T94 Δ9 | `surfaceContainerHigh` T17 | `surfaceContainer` |
| `insetActiveContainerColor` 内嵌激活 | `surfaceContainerHigh` T89 Δ12 | `surfaceContainerHighest` T21 | `surfaceContainerHigh` |

浅色的 Δ 是**通道差**（最大 sRGB 通道 − 最小），不是彩度，理由见下面第二小节。

深色模式的色阶方向与浅色相反（层级越高越亮），所以每层都得按模式分别取档，不能共用一套档位。判定深浅用的是 `ColorScheme.usesDarkSurfaces()`——比较 `surfaceContainerHighest` 与 `surface` 的相对亮度，**从最终色值反推**。不要改用 `isSystemInDarkTheme()`：应用内的主题模式设置可以覆盖系统深浅，那个 API 会判错。

### 浅色的核心取舍：卡片放弃色相，换页面的亮度

**带色相的近白色有个硬天花板**：到 T98 就到顶，再往上红通道先撞满 255，只能靠压低绿蓝通道凑明度，T99 算出来是 `#FCFCFC`——色相被彻底挤掉。也就是说，一张「带色相的卡片」最亮只能到 `#FFF7F7`。

以前卡片占着这个位置，页面底就只能靠压暗自己来拉开层次，越压越闷。现在反过来：**卡片放弃色相改用纯白**，白比 `#FFF7F7` 还亮一截，省下的亮度全部让给页面——页面因此能抬到明亮的 T95，层次反而比旧方案更好。

| | 卡片/页面 | 卡片/内嵌 | 内嵌/激活 |
|---|---|---|---|
| 卡片 T98 / 页面 T94 / 内嵌 T93 / 激活 T88（旧，卡片带色相） | 1.099 | 1.129 | 1.133 |
| **卡片 #FFFFFF / 页面 T95 / 内嵌 T94 / 激活 T89（当前）** | **1.128** | **1.159** | **1.134** |

内嵌层比页面底还深一档，是因为二者隔着一整张卡片、从不相邻，谁深谁浅看不出来，这一档余量拿来喂给「卡片/内嵌」更划算。

**页面底再往上就到头了**，两条限制同时收紧：

| 页面明度 | 卡片/页面 | 暖色相能达到的最大 Δ |
|---|---|---|
| T94 | 1.160 | 18 |
| **T95（当前）** | **1.129** | **15** |
| T96 | 1.100 | 12 |
| T97 | 1.074 ❌ 跌破门槛 | 9 |

明度越高，色域留给色调的空间越小——抬到 T97 时分离度已经跌破 1.09，页面也只剩下几乎看不出的一点色。真要更亮，只能给卡片加描边，把层次从「明暗差」改由轮廓承担。

### 着色量按通道差对齐，不按彩度

页面底的浓淡由 `gen_themes.py` 顶部的 `TINT` 一个常量控制，单位是**通道差**（最大 sRGB 通道 − 最小通道），由 `tint()` 反解出每个色相各自的彩度。

不能直接写彩度值，因为**同一个彩度在十三个色相上的实际浓度差着三倍**：

| 色相 | T94 的彩度上限 | 给 C14 实际得到 |
|---|---|---|
| 樱粉 H5 | 8.1（红通道先撞满 255） | 只有 C8.1，Δ23 |
| 抹茶 H125 | 68.9 | 足额 C14，Δ30 |

照数字给必然一半发灰、一半发腻——这正是「有的配色死气沉沉、有的又太浓」的根源。对齐通道差之后，十三套摆在一起才是同一种淡。这和填充色按最大通道对齐（第三节）是同一套思路。

**调浓淡是免费的**：对比度公式只看相对亮度，`TINT` 取多少都不影响上面那三档分离度，也不牵动任何门槛。当前取 8——大面积页面底长时间看不累，色调只在与纯白卡片并排时才读得出来；13 读作微微上色的纸，18 色调明确，23 已偏明快糖果色。改完跑一遍 `verify.py` 即可。注意页面停在 T95 时暖色相最多只能到 Δ15，`TINT` 调过这个数也拿不到。

改页面底档位时，压在它上面的 `secondaryContainer`（浅色悬浮按钮、普通底栏选中指示器）要一起看：二者贴得太近，按钮就会糊进页面底。当前页面 T95、`secondaryContainer` T90，对比 1.13:1。

---

## 五、按钮的三档强调梯度

同屏会出现三种明显不同深浅的按钮底，这是刻意设计的，不是配色不统一：

| 档位 | 角色 | 浅色彩度 | 例子 |
|---|---|---|---|
| 主操作 | `primaryFixedDim` | 约 C30（顶格） | 解析、登录、确定 |
| 次级操作 | `secondaryContainer` | T90 C22 | 复制字幕、浅色悬浮按钮、批量面板里的清除记录 |
| 中性 | `surfaceContainer` | T95 Δ8 | 未选中的分段按钮（M3 当前默认） |
| 中性强调 | `surfaceContainerHigh` | T89 Δ12 | 普通浅色/深色的关闭态开关轨道、滑条未选段 |

`secondaryContainer` 的彩度是从 C15 提到 C22 的——原值离中性容器太近，次级按钮看着像禁用态；提上来之后读得出它和主按钮是同一色族的低一档，同时仍明显更淡。

---

## 六、开屏页：平台限制，不跟随配色

开屏窗口由系统在**应用进程启动之前**绘制，那一刻读不到用户选的配色方案，`windowSplashScreenBackground` 只能是一个静态色号。所以它**不可能跟随配色变化**。

原来这里指向 `@color/md_theme_dark_background`，也就是基线配色（樱粉）的深色底 `#1C1315`——用户选了别的方案时就会看到一块与当前配色无关的深红褐色。现在改成专用的零彩度中性灰 `splash_background_light/dark`，明度对齐页面底色（浅色 T95 `#F1F1F1` / 深色 T12 `#1F1F1F`），十三套配色下都读作干净的加载底。**调整页面底档位时记得同步这两个色号**，否则开屏到首帧会闪一级明度。

开屏的中性灰与首帧的带色页面底之间会有一点色相差。这仍是十三套配色下的最优解——换成任何一套的实际底色，另外十二套都会闪一下明显异色；而明度对齐之后，色相差只表现为一次很轻的「上色」，不会有明暗跳变。

顺带修掉一个隐藏落差：原来取的是 `background`（深色 T7），而应用首帧的页面底是 `surfaceContainer`（T12），启动瞬间会有一级明度跳变。

`MainActivity` 会在 `installSplashScreen()` 之前把这个色号读出来做防闪帧遮罩，改色号不影响那段逻辑。

---

## 七、组件着色上的坑

这几处都是实际踩过的，改动相关代码时留意。

**`ToggleFloatingActionButton` 不向子内容提供内容色。** 它的实现只做了加阴影、画容器、调 `content()` 三件事，既没有 `CompositionLocalProvider`，也不会自动套 `ToggleFloatingActionButtonDefaults.animateIcon`。里面的 `Icon` 如果不写 `tint`，会回落到 Compose 库的默认值 **`Color.Black`**，和 colorScheme 完全脱钩。`DownloadsScreenContent.kt` 的汉堡按钮曾长期是纯黑图标。

**`tonalElevation` 会叠 `surfaceTint`。** 全局主题构建器把 `surfaceTint` 赋成了 `primary`，浅色模式下那是 T36 的深色，给 `Surface` 设非零 `tonalElevation` 会让底色明显偏色偏暗、偏离表面色阶。层次一律用 `shadowElevation` 表达，全项目不使用 `tonalElevation`。

**`AndroidView.factory` 只负责创建，不会随 Compose 配色重建。** 主界面不再依赖 Activity `recreate()` 后，嵌在 Compose 中且从 View theme 取色的控件必须在 `update` 中把当前 `MaterialTheme.colorScheme` 同步回 View，或直接改用等价的 Compose 组件。下载进度条按前一种方式处理；“我”页加载动画使用 Compose `LoadingIndicator`，两者都能随配色即时刷新。

**开关与滑条的滑块都要按模式取色。** 开关不在滑块里放状态图标。启用时，浅色模式的开启态使用 `primary` 轨道与 `onPrimary` 滑块，关闭态也使用 `onPrimary` 滑块；深色模式开启态使用 `fill` 轨道与 `onFill` 滑块，关闭态滑块使用 `outline`，与 Material 默认的未选中滑块角色一致。普通浅色和深色模式的关闭态轨道使用 `surfaceContainerHigh`，不画描边；纯黑模式的卡片底本身就是 `surfaceContainerHigh`，所以关闭态必须升到 `surfaceContainerHighest` 并恢复 `outline` 描边，避免轨道与卡片重合。禁用关闭态仍不画描边。滑条未选段也使用 `surfaceContainerHigh`，让细轨道在卡片上仍清晰可见；浅色模式用 `onFill` 深色滑块，避免糊进近白卡片，深色模式用 `fill` 浅色滑块，与已选轨道保持连续的强调色。

**复选框的启用选中态要按模式取色。** 浅色模式使用 `primary` 方框配白色对勾，使方框与列表高亮边框同色；深色模式保留 `fill` 方框与 `onFill` 对勾，避免亮色对勾和浅色方框连成亮斑。未选中与禁用态沿用 Material 默认配色。模式判断集中在 `AppAccents.checkboxColors()`，界面组件不要各自分支。

**色选择器的色块直接从 overlay 解析。** `Context.resolveOverlaySwatch()` 用 `obtainStyledAttributes` 从每套 overlay 里读 `colorPrimaryFixedDim` 与 `colorOnPrimaryFixed`，所以改配色表时设置页无需同步。注意 `obtainStyledAttributes` 要求属性数组按 ID 升序，那里排序后再按 ID 反查下标。

---

## 八、代码落点

| 文件 | 职责 |
|---|---|
| `res/values/themes.xml` | 基础主题 + 十三套 overlay（浅色）+ 纯黑 overlay，**生成器产出** |
| `res/values-night/themes.xml` | 同上（深色），**生成器产出** |
| `res/values/colors.xml` | 基线（樱粉）色号 + 开屏页色号，**生成器产出** |
| `res/values/styles.xml` | 仍保留的 View 层控件样式 |
| `ui/theme/AppAccents.kt` | 填充面 / 前景色的语义封装，各控件的 `*Colors()` 都在这 |
| `ui/theme/AppSurfaces.kt` | 三层表面语义 + 深浅/纯黑判定 |
| `ui/ThemeColorOverlay.kt` | 枚举 → overlay 样式、枚举 → 中文名、overlay 取色，以及启动窗口 / View 层的 Activity 主题叠加 |
| `ui/theme/BiliToolsTheme.kt` | 全应用唯一 Compose 主题入口，由 `AppSettings` 驱动并完整解析 XML 颜色角色 |
| `data/SettingsRepository.kt` | `AppThemeColor` 枚举与旧值迁移表 |

所有 Compose 根都使用 `BiliToolsTheme`：配色与纯黑开关直接随 `AppSettings` 重组，深浅模式由 `Resources.getSystem()` 判断，并订阅 `LocalConfiguration` 接收系统 `uiMode` 变化。`MainActivity` 在 Manifest 中自行处理 `uiMode`，因此主题变化不再通过快照比对、延迟 `recreate()` 刷新；Activity theme overlay 只负责启动窗口和仍存在的 View 层组件。

主题构建器必须覆盖**全部**角色。曾经漏掉 `inverseSurface` / `inverseOnSurface` / `inversePrimary`，它们一直停在 Compose 基线的紫色——当时没有组件用到所以没暴露，一旦加 `Snackbar` 或 `RichTooltip` 就会蹦出一块与配色无关的紫。新增角色时记得同步 `BiliToolsTheme.kt` 和生成器的 `COLOR_REF`。

### 旧值迁移

配色方案重命名后，`AppThemeColor.fromValue()` 里有一张 `LEGACY_VALUES` 表按色相就近迁移，避免用户升级后配色被重置：

```
rose → Sakura    gold → Sand    olive/lime → Matcha
leaf → Mint      turquoise → Seafoam    cyan → Lagoon
```

再次重命名时记得往这张表里追加，不要替换。

---

## 九、当前指标

`verify.py` 的门槛与实测（十三套配色里的最差值，直接回读出货 XML 算出）：

| 指标 | 门槛 | 浅色 | 深色 |
|---|---|---|---|
| 正文 `onSurface` / 卡片底 | 4.5:1 | 15.93:1 | 9.33:1 |
| 次要文字 `onSurfaceVariant` / 卡片底 | 4.5:1 | 8.64:1 | 6.72:1 |
| 描边 `outline` / 卡片底 | 3:1 | 4.78:1 | 3.86:1 |
| 填充面上的内容 `onFill` / `fill` | 4.5:1 | 8.59:1 | 8.59:1 |
| 普通底栏选中图标 / 指示器 | 4.5:1 | 7.74:1 | 6.53:1 |
| 普通底栏选中文字 / 底栏底 | 4.5:1 | 6.08:1 | 10.17:1 |
| 普通底栏未选中内容 / 底栏底 | 4.5:1 | 7.58:1 | 9.55:1 |
| 开关开启轨道 / 卡片底 | 3:1 | 7.45:1 | 6.38:1 |
| 开关开启滑块 / 轨道 | 3:1 | 7.45:1 | 8.59:1 |
| 开关开启 / 关闭轨道 | 3:1 | 5.61:1 | 7.95:1 |
| 悬浮按钮 / 页面底 | 深色 3:1 | 1.13:1（靠投影） | 9.07:1 |
| 悬浮按钮内容 / 按钮 | 4.5:1 | 7.74:1 | 8.59:1 |
| 卡片对页面 / 对内嵌 | 1.09 | 1.128 / 1.159 | 1.408 / 1.241 |
| 填充色最大通道 | 220 ~ 252 | 225 ~ 250 | 同浅色 |

纯黑模式另有一组关闭态约束：`surfaceContainerHighest` 轨道与 `surfaceContainerHigh` 卡片底至少保持 1.08:1 的细微层次（当前 1.088:1），组件边界由 `outline` 描边承担并至少达到 3:1（当前最差 6.01:1），同为 `outline` 的滑块与轨道当前最差 5.53:1。

浅色模式下淡色按钮容器对页面底达不到 3:1 是**设计取舍**，不是缺陷：淡雅浅色风格要求色块本身很淡，状态表达交给压在上面的深色内容与投影。详见第二节。
