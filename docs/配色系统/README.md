# BiliTools 配色系统设计说明

本目录记录配色系统的**设计理念、参数取值与背后的约束**，供后续维护时理解「为什么是这个数」而不是照着改。

- `gen_themes.mjs` —— 配色生成器。`values/themes.xml`、`values-night/themes.xml`、`values/colors.xml` 三个文件**全部由它产出**，不要手改。
- `android16_default_monet.json` —— Android 16 模拟器实测的系统默认 Monet 色表。生成器启动时先用它自检，确认依赖的官方库与平台实现一致。
- `verify.py` —— 回归校验。直接回读出货的 XML，核对十三套配色的对比度、层次分离度与角色一致性。
- `preview.py` —— 视觉预览。渲染色板与界面 mock，改完参数先看图再编译。

生成器依赖官方 `@material/material-color-utilities`（Node），预览脚本依赖 `pillow`，校验脚本只用 Python 标准库：

```bash
cd docs/配色系统 && npm ci && cd -
pip install pillow
```

改配色的完整流程：

```bash
node docs/配色系统/gen_themes.mjs    # 自检官方库 → 重新产出三个 XML → 打印与官方方案的偏差表
python docs/配色系统/verify.py       # 回读校验，看有没有指标掉出门槛
python docs/配色系统/preview.py      # 出图目视确认（.tmp/palette.png、.tmp/mock.png）
./gradlew :app:assembleDebug
```

---

## 一、十三套配色

| 名称 | 枚举 | 色相 | 填充色（深浅共用） |
|---|---|---|---|
| 蓝紫 | `Periwinkle` | 269 | `#B1C2F2` |
| 鸢尾 | `Iris` | 252 | `#A8C9F2` |
| 天蓝 | `Sky` | 228 | `#A3D8F2` |
| 湖蓝 | `Lagoon` | 202 | `#A5E5E9` |
| 薄荷 | `Seafoam` | 178 | `#A8E7D4` |
| 青苹 | `Mint` | 152 | `#B7E6BA` |
| 抹茶 | `Matcha` | 125 | `#D0E1A1` |
| 沙金 | `Sand` | 90 | `#F2D48B` |
| 蜜杏 | `Apricot` | 62 | `#F2B781` |
| 珊瑚 | `Coral` | 32 | `#FAAF9D` |
| 樱粉 | `Sakura` | 5 | `#F5AEBD` |
| 藕荷 | `Orchid` | 332 | `#F2C0EB` |
| 丁香 | `Lilac` | 302 | `#D0BDF2` |

色相绕满一圈。顺序以默认方案居首，之后沿色环由冷到暖、最后回到紫色收尾，`AppThemeColor` 枚举、生成器与设置页的色块行都按这个顺序。

蓝紫是**默认方案**：用户从未选择过手动配色时，关掉动态取色会回落到蓝紫；选择过配色后则恢复上次的选择。它锚定在 **Android 16 系统默认 Monet 的种子色相 269°** 上——不少机型的系统动态取色并不好看，关掉之后仍能拿到那套官方默认的蓝灰观感，也是十三套里最不挑人的一套。

樱粉是 B 站的代表色，但刻意没去对齐品牌色 `#FB7299`（那个饱和度放在淡雅浅色风格里太跳），而是取了同色系里淡雅的一档。

色相不能落在 `[105, 125)`：2025 spec 把这个区间视作黄色，会换用另一套更亮的表面档位（页面 T96、卡片 T99），四层深度会贴到一起。生成器遇到这种色相会直接报错。

另有两个不属于这十三套的开关：

- **动态取色**（`AppThemeColor.Dynamic`，默认值）：API 31+ 上 Compose 主题使用 Material 动态配色主题读取系统壁纸色，Activity 启动窗口与 View 层仍由 `DynamicColors.applyToActivityIfAvailable()` 同步。此时下面所有参数都不生效，配色由系统给——但因为静态方案与系统用的是同一套规则（见第二节），两种模式的界面结构是一致的。
- **纯黑深色模式**：`ThemeOverlay.BiliTools.DarkPureBlack`，在深色配色之上再叠一层，把中性色阶压到纯黑附近。

---

## 二、与官方 Monet 的关系：官方引擎 + 六个旋钮

静态方案的骨架就是官方的 **Material 3 Expressive（2025 spec）TonalSpot** 方案，用 `@material/material-color-utilities` 0.4.0 的 `SchemeTonalSpot(seed, isDark, 0, '2025', 'phone')` 生成——这正是 Android 16 系统动态取色的规则。生成器启动时会拿 `android16_default_monet.json`（模拟器 `cmd overlay lookup` 实测值，种子 `#6476A5`）跑一遍自检，主色、次色、表面梯度共 26 个角色逐通道容差 ±2；三级色对种子色相的一位小数敏感、深色 `onSurfaceVariant` 平台取了不同档位，这两处跳过。自检不过说明库版本变了，先停下来核对。

在这个骨架上，应用只保留六个设计旋钮，全部集中在 `gen_themes.mjs` 顶部：

| 旋钮 | 取值 | 改了什么 |
|---|---|---|
| 主填充面 | `FILL_CHROMA_CAP = 30`、`FILL_TONE_CAP = 87`、`FILL_TARGET_CHANNEL = 242` | primary 的 fixed 色组四个角色，按最大 sRGB 通道对齐（第四节） |
| 浅色表面着色量 | `TINT_LIGHT = 8` | 浅色中性色板彩度，按页面底 T94 的通道差反解（第五节） |
| 深色表面着色量 | `TINT_DARK = 3` | 深色中性色板彩度，按 `surfaceContainerHigh` T12 的通道差反解（第五节） |
| 浅色正文明度 | `LIGHT_ON_SURFACE_TONE = 10`、`LIGHT_ON_SURFACE_VARIANT_TONE = 30` | 浅色 `onSurface` / `onBackground` / `onSurfaceVariant` 的明度；深色不动 |
| 浅色细线明度 | `LIGHT_OUTLINE_VARIANT_TONE = 80` | 浅色 `outlineVariant`（输入框描边、分隔线）从 2025 spec 的约 T72 回到 T80；深色不动 |
| 错误色 | `ERROR_ROLES` | 四个 error 角色固定为 M3 基线错误色板（H25 C84）的经典档位，十三套共用 |

其余全部照官方规则走：主/次/三级色板的色相与彩度、每个角色的明度、`on*` 颜色的对比度曲线。生成器最后会打印一张「与官方 2025 spec 的最大明度偏差」表，按角色列出十三套里最差的 ΔT，并标注偏差来自哪个旋钮：

- **官方规则**：primary、secondary、tertiary 各组及其容器与 `on*`，偏差 ≤ 2.4（对比度曲线针对我们更淡的表面做了微调）；
- **中性色板彩度按通道差反解**：全部表面、描边、`inverse*`，偏差 ≤ 3；
- **浅色正文明度压深**：`onSurface` / `onBackground` / `onSurfaceVariant`，ΔT 约 11~13（只在浅色）；
- **浅色细线明度调淡**：`outlineVariant` ΔT 7.7（只在浅色），2025 spec 把它压到约 T72，1dp 细线在 T98 卡片上偏重；
- **主填充面按最大通道对齐**：`primaryFixed*` 四个角色，fixedDim ΔT 5.2，onPrimaryFixed ΔT 12.9；
- **错误色固定 M3 基线档位**：2025 spec 的浅色 `errorContainer` 是 T65 的饱和红块（`#FA746F`），「删除文件」这类按钮会变成整块正红，与淡雅浅色格格不入；这里固定为主色 T40 `#BA1A1A`、容器 T90 `#FFDAD6`、容器上内容 T30 `#93000A`，深色反转。错误色板本就与种子无关，所以不随色相变化；
- **深色沿用浅色值**：secondary / tertiary 的 fixed 色组，2025 spec 深色主色板彩度是 26 而不是 32，单独算会得到另一组数，Android 系统把浅色算出来的值同时用于两个模式，这里照做。

### 为什么不直接用官方方案

官方规则是给**单一壁纸种子**设计的，从来没考虑过十三个色相并排要一样淡：

- 中性色板彩度是常数 5，但同一个彩度在不同色相上的实际浓度差着三倍，樱粉页面底会到 Δ20（`#FDE9EC`，明显发粉），蓝灰只有 Δ9；
- `primaryFixedDim` 由 `tMaxC` 推出，樱粉是 `#E99FAF`、抹茶是 `#C9DC98`，深浅不一，粉色偏艳。

这两处对应前三个旋钮。冷色相上两套规则本来就几乎重合：H269 的官方 fixedDim 是 `#ABBDF0`，我们是 `#B1C2F2`。

浅色正文与细线两个旋钮是阅读体验上的取舍：2025 spec 的 `on*` 颜色按对比度曲线对最高表面取「刚好够」的档位（正文 9:1 → 约 T21，次要文字 4.5:1 → 约 T40），在大面积浅色页面上读起来偏淡发灰；`outlineVariant` 则反过来被压到约 T72，1dp 描边在浅色卡片上显重。这两组都回到 M3 2021 的经典档位：正文 T10、次要文字 T30（对卡片底 16.3:1 与 8.9:1），`outlineVariant` T80；`outline` 仍走官方档位。深色文字本来就是亮字压暗底，官方档位不显淡，不改。

### 为什么生成器是 Node 而不是 Python

Material Color Utilities 的官方实现只有 TypeScript、Java、Dart、C++ 四种，Python 版是社区移植且停在 2021 spec，没有 `DynamicScheme` / `ContrastCurve` / 2025 spec，无法复现 Android 16 的表面梯度与 `on*` 取值。Java 版藏在 Material Components for Android 里，跑起来要过 Gradle。TypeScript 包 `@material/material-color-utilities` 是官方维护、独立发布、可以 `npm ci` 直接跑的那一个，`android16_default_monet.json` 自检也是靠它逐角色对上的。校验和预览不依赖这个库，仍是 Python。

---

## 三、核心设计：固定填充面与模式相关强调色分离

这是整套配色最重要的一条约定，写在 `AppAccents.kt` 的 KDoc 里，实现也集中在那。

| | 取哪个角色 | 浅色 | 深色 | 用在哪 |
|---|---|---|---|---|
| **固定填充面** | `primaryFixedDim` | 同一个值 | 同一个值 | 主按钮底、选中胶囊、滑条已选段、深色开关开启态轨道、深色复选框、深色悬浮按钮 |
| **压在填充面上的内容** | `onPrimaryFixed` | 同一个值 | 同一个值 | 文字、图标、滑条已选段刻度，以及深色复选框对勾、深色开关开启态滑块、浅色滑条滑块 |
| **浅色开关开启态** | `primary` + `onPrimary` | 深色轨道、近白滑块 | — | 与强调文字同色，并沿用 M3 开关的标准角色搭配 |
| **浅色复选框选中态** | `primary` + 白色 | 深色方框、白色对勾 | — | 让方框与列表高亮边框同色 |
| **模式相关强调色** | `primary` | T40（深） | T80（浅） | 正文链接、强调文字、强调图标、进度条、液态底栏选中项，以及需要更强轮廓对比的浅色控件填充 |

### 为什么要分离

M3 的 `primary` 在浅色是 T40、深色是 T80，**同一个角色在两个模式里差了 40 档明度**。如果按钮底、选中胶囊这些「色块」用 `primary`：

- 浅色模式下按钮是深色块，深色模式下是浅色块，观感完全不是一套配色，也违背「浅色背景上按钮不能是深色块」的淡雅目标；
- 设置页那十三颗色块只能挑一个模式的值来显示，跟实际界面对不上。

M3 的 **fixed 色组**正是为这种场景设计的：它的定义就是「不随明暗模式变化」。把按钮、胶囊等常规填充面迁到 fixed 色组之后，设置页的色块所见即所得，两个模式的按钮也长得一样；开关与复选框需要更强轮廓对比的浅色状态是下面写明的例外。

### 什么时候不能用填充色

填充色对页面底的对比度只有 1.2~1.6:1（浅色模式），远达不到非文字元素 3:1 的门槛。能不能用它，取决于**色块之上有没有能独立承担状态表达的文字、图标或控件形状**：

- 按钮、胶囊、滑条 —— 上面有文字、图标或滑块表达状态，**用填充色**；
- **开关 —— 浅色用 `primary` 轨道与 `onPrimary` 滑块，深色用填充色**。浅色下强化开启态与卡片底、关闭态的层次，深色下继续保持明亮的固定填充观感；
- **勾选框 —— 浅色用 `primary`，深色用填充色**。浅色方框与列表高亮边框统一，并为白色对勾提供足够反差；
- **进度条 —— 用 `primary`**。它没有滑块，进度全靠已完成段与轨道的反差来读，填充色配淡色轨道实测不可用，所以退回 `primary` + `surfaceVariant`；
- **普通底栏 —— 沿用 Material 3 Expressive 的导航栏 token**。选中指示器用 `secondaryContainer`，选中图标用 `onSecondaryContainer`，选中文字用 `secondary`，未选中图标与文字用 `onSurfaceVariant`。文字位于指示器之外，不能与图标共用「填充面上的内容色」；
- **液态玻璃底栏 —— 用 `primary`**。它的选中气泡是透明玻璃透镜而不是色块，被着色的其实是选中项的图标与文字，属于前景语义。这一处最容易看错。

### 悬浮元素为什么要按模式取色

浅色模式下，快捷下载按钮、下载页操作菜单的主按钮与展开后的操作项都使用 `secondaryContainer`，和「复制字幕」等次级操作保持同一强调层级（M3 的 FAB 菜单默认也是主按钮与菜单项同色）。不要给菜单项换成 `primaryFixed`：它是 C14 的低彩度档，在浅色页面上读作灰块。深色模式下 `secondaryContainer`（T25）与 `primaryContainer`（T35）都容易糊进 T9 的页面底，因此悬浮主按钮和菜单项均切回深浅同值的 `primaryFixedDim`，对页面底 9.73:1。

这组模式分支集中在 `AppAccents.floatingActionContainer` / `onFloatingActionContainer`，界面组件不要自行判断系统深浅模式。

---

## 四、填充色的明度怎么定的：按最大通道对齐，不按明度

`gen_themes.mjs` 里的 `fillToneChroma()`：在 `FILL_CHROMA_CAP = 30`、`FILL_TONE_CAP = 87` 的上限内，取**最大 sRGB 通道刚够 `FILL_TARGET_CHANNEL = 242`** 的那一档明度。

### 为什么不直接用统一明度

十三套填充色若统一取 T85，绿、青、蓝这些冷色相看起来明显比暖色相**深一大截**。原因是 HCT 的明度（L\*）刻画的是相对亮度，而人眼判断一个颜色「淡不淡」，更贴近它的**最大 sRGB 通道**：

| 色相 | 统一取 T85 时的色号 | 最大通道 |
|---|---|---|
| 樱粉 H5 | 带彩度后红通道顶到 255 | 255 |
| 抹茶 H125 | 绿通道只到约 230 | 约 230 |

暖色相在带彩度时会有一个通道先撞上 255，观感就「亮」；冷色相三个通道都在中段，观感就「闷」。改成对齐最大通道之后，十三个色相摆在一起才是同一种「淡」。

### 两个上限的来历

- **`FILL_CHROMA_CAP = 30`**：再高就不是淡雅风格了。
- **`FILL_TONE_CAP = 87`**：绿─青色相若不设上限会顶到 T90-91，离浅色卡片底（T98）太近，按钮浮不起来。代价是这几个色相在色板里比暖色略深一点点，换来的是全局层次立得住。

另外 `color()` 这个取色函数会把彩度自动收敛到该明度下最大彩度的 92%，避免落在色域边界上被硬裁切——裁切会同时破坏明度和色相。

---

## 五、表面色阶：四层深度

语义层封装在 `AppSurfaces.kt`，界面代码应该使用这些语义名，不要直接取 `surfaceContainer*`。档位就是 2025 spec 的官方档位，浅色与深色取**同一组角色**，与系统动态取色下的结构一致；只有纯黑模式另取相邻档位。

| 语义层 | 角色 | 浅色 | 深色 | 纯黑 |
|---|---|---|---|---|
| `pageContainerColor` 页面底 | `surfaceContainer` | T94 Δ8 | T9 | `surface` #000000 |
| `cardContainerColor` 卡片底 | `surfaceBright` | T98 | T18 | `surfaceContainerHigh` #171717 |
| `insetContainerColor` 卡片内嵌 | 浅色 `surfaceContainerLow`/`surfaceContainer` 中点，深色 `surfaceContainerHigh` | T95 | T12 Δ3 | `surfaceContainer` #101010 |
| `insetActiveContainerColor` 内嵌激活 | `surfaceContainerHighest` | T90 | T15 | `surfaceContainerHigh` #171717 |
| `modalContainerColor` 模态容器 | `surfaceContainerHighest` | T90 | T15 | `surfaceContainerHighest` #1F1F1F |

Δ 是**通道差**（最大 sRGB 通道 − 最小），标在着色量对齐的基准层上。

### 内嵌层为什么取半档

内嵌区域（下拉框、输入框底）是唯一不落在官方整档上的一层。浅色下 2025 spec 的档距是 2 个明度：`surfaceContainerLow`（T96）对 T98 卡片只有 1.046，内嵌几乎看不出来；`surfaceContainer`（T94）1.102 又在近白卡片上压出一块明显的灰。两档之间没有官方角色，`AppSurfaces` 用 `lerp(surfaceContainerLow, surfaceContainer, 0.5f)` 取中点 T95，对卡片 1.073，读作一层轻凹陷；输入框另有 `outlineVariant` 描边勾出边界，所以这一项的门槛单列为 1.06。动态取色下两个角色同样来自系统，中点照算。

深色档距是 3 个明度，不需要取半：内嵌取 `surfaceContainerHigh`（T12），对 T18 卡片 1.167，比 T9 页面略浮、比卡片沉，方向与浅色一致；再往下的 T9/T6 会让输入框在深色卡片上变成一个黑洞。激活态两种模式都取 `surfaceContainerHighest`：浅色 T90 对内嵌 1.13，深色 T15 对 T12 是 1.076——这是 2025 spec 自己的相邻档距，且激活态总伴随展开菜单或聚焦描边，门槛放到 1.07。纯黑的 `surfaceContainerLow`（#0A0A0A）离 #000000 页面太近，内嵌取 `surfaceContainer`，激活取 `surfaceContainerHigh`。

判定深浅用的是 `ColorScheme.usesDarkSurfaces()`——比较 `surfaceContainerHighest` 与 `surface` 的相对亮度，**从最终色值反推**。不要改用 `isSystemInDarkTheme()`：应用内的主题模式设置可以覆盖系统深浅，那个 API 会判错。

### 浅色卡片为什么是 T98 而不是纯白

纯白卡片比 T98 更亮一截，能给页面多留一档层次，但大面积纯白刺眼，而且与动态取色下系统给的 `surfaceBright`（T98）不一致。卡片跟官方走 T98，页面 T94，分离度 1.102，在 1.09 的门槛之上——门槛本来就是照着「系统动态取色下卡片对页面」这个官方观感定的。

### 着色量按通道差对齐，不按彩度

页面底的浓淡由 `TINT_LIGHT` / `TINT_DARK` 控制，单位是**通道差**，由 `solveNeutralChroma()` 反解出每个色相各自的中性色板彩度。

不能直接写彩度值，因为**同一个彩度在十三个色相上的实际浓度差着三倍**（樱粉在 T94 被色域裁到 C8 封顶，抹茶却能到 C69），照数字给必然一半发灰、一半发腻。2025 spec 还会在色板彩度上再乘一个随层级递增的倍率（TonalSpot：`surfaceContainerLow` ×1.25、`surfaceContainer` ×1.4、`surfaceContainerHigh` ×1.5、`surfaceContainerHighest` ×1.7），所以反解必须对着**角色的最终输出**做，而不是对着色板做。

**调浓淡是免费的**：对比度公式只看相对亮度，`TINT_*` 取多少都不影响任何分离度门槛。浅色当前取 8——大面积页面底长时间看不累，色调只在与卡片并排时才读得出来；13 读作微微上色的纸，18 色调明确。

### 深色为什么要单独给一个更小的着色量

官方深色表面的通道差约 Δ8，和浅色差不多，但深色底的通道值本来就只有 20~50，同样的 Δ8 相对着色高达 25%，而浅色只有 3%。蓝紫色相在低明度读作「冷黑」还能接受，樱粉、珊瑚、蜜杏、沙金这些暖色相在低明度会直接变成酱色、棕色，长时间看发脏、视觉疲劳。深色着色量因此单独压到 Δ3（约 8%~10%），仍留一点与浅色呼应的色相，不再发脏。纯灰（Δ0）是备选，但那样深色就和配色方案完全脱钩了。

### 模态容器

模态容器统一取 `surfaceContainerHighest`，`AppDialogs.kt` 再补上窗口遮罩和细边缘：浅色 32% 黑色遮罩且不画边；深色（含纯黑）48% 遮罩，普通深色 30% `outlineVariant` 边缘、纯黑 50%。深色卡片 T18 与模态底 T15 只差三档，遮罩必须压到 48% 才能把遮罩后的卡片和模态底拉开到 1.176:1；黑色遮罩无法让已经是 #000000 的区域继续变暗，因此纯黑模式还必须靠边缘勾出轮廓。

---

## 六、按钮的三档强调梯度

同屏会出现三种明显不同深浅的按钮底，这是刻意设计的，不是配色不统一：

| 档位 | 角色 | 浅色 | 例子 |
|---|---|---|---|
| 主操作 | `primaryFixedDim` | 约 T80 C30（顶格） | 解析、登录、确定 |
| 次级操作 | `secondaryContainer` | T90 C16（官方） | 复制字幕、浅色悬浮按钮、批量面板里的清除记录 |
| 中性 | `surfaceContainer` | T94 Δ8 | 未选中的分段按钮（M3 当前默认） |
| 中性强调 | `AppAccents.inactiveTrackColor` | T90（浅色 `surfaceContainerHighest`） | 关闭态开关轨道、滑条未选段；深色取 `surfaceContainerHigh` T12 |

---

## 七、开屏页：平台限制，不跟随配色

开屏窗口由系统在**应用进程启动之前**绘制，那一刻读不到用户选的配色方案，`windowSplashScreenBackground` 只能是一个静态色号。所以它**不可能跟随配色变化**。

这里用专用的零彩度中性灰 `splash_background_light/dark`，明度对齐页面底色（浅色 T94 `#EEEEEE` / 深色 T9 `#191919`），十三套配色下都读作干净的加载底。开屏的中性灰与首帧的带色页面底之间只有一点色相差，表现为一次很轻的「上色」，不会有明暗跳变。**调整页面底档位时生成器会同步这两个色号**。

`MainActivity` 会在 `installSplashScreen()` 之前把这个色号读出来做防闪帧遮罩，改色号不影响那段逻辑。

---

## 八、组件着色上的坑

这几处都是实际踩过的，改动相关代码时留意。

**`ToggleFloatingActionButton` 不向子内容提供内容色。** 它的实现只做了加阴影、画容器、调 `content()` 三件事，既没有 `CompositionLocalProvider`，也不会自动套 `ToggleFloatingActionButtonDefaults.animateIcon`。里面的 `Icon` 如果不写 `tint`，会回落到 Compose 库的默认值 **`Color.Black`**，和 colorScheme 完全脱钩。`DownloadsScreenContent.kt` 的汉堡按钮就是这样显式着色的。

**`BasicTextField` 的光标默认是纯黑。** 不写 `cursorBrush` 时它回落到 Compose 库的默认值 `SolidColor(Color.Black)`，深色模式下光标直接看不见。项目里的三处 `BasicTextField`（解析输入框、分页跳转框、历史搜索框）都显式给 `SolidColor(colorScheme.primary)`。用 `OutlinedTextField` / `TextField` 不会踩到，它们的 `cursorColor` 默认就是 `primary`。

**`tonalElevation` 会叠 `surfaceTint`。** 全局主题构建器把 `surfaceTint` 赋成了 `primary`，浅色模式下那是 T40 的深色，给 `Surface` 设非零 `tonalElevation` 会让底色明显偏色偏暗、偏离表面色阶。层次一律用 `shadowElevation` 表达，全项目不使用 `tonalElevation`。

**`AndroidView.factory` 只负责创建，不会随 Compose 配色重建。** 主界面不再依赖 Activity `recreate()` 后，嵌在 Compose 中且从 View theme 取色的控件必须在 `update` 中把当前 `MaterialTheme.colorScheme` 同步回 View，或直接改用等价的 Compose 组件。下载进度条按前一种方式处理；「我」页加载动画使用 Compose `LoadingIndicator`，两者都能随配色即时刷新。

**开关与滑条的滑块都要按模式取色。** 开关不在滑块里放状态图标。启用时，浅色模式的开启态使用 `primary` 轨道与 `onPrimary` 滑块，深色模式使用 `fill` 轨道与 `onFill` 滑块；关闭态滑块两种模式都用 `outline`，与 Material 默认的未选中滑块角色一致——近白滑块放在近白轨道上会整颗糊掉。关闭态轨道与滑条未选段共用 `AppAccents.inactiveTrackColor`，取**离卡片底最远**的容器档位：浅色卡片 T98 下是 `surfaceContainerHighest`（T90，1.22:1），`surfaceContainerHigh` 只有 1.15:1，细轨道读不出来；深色卡片 T18 下反过来是 `surfaceContainerHigh`（T12，1.17:1）。普通模式不画描边；纯黑模式的卡片底本身就是 `surfaceContainerHigh`，只能升到 `surfaceContainerHighest` 并恢复 `outline` 描边，避免轨道与卡片重合。禁用关闭态仍不画描边。滑条滑块浅色模式用 `onFill` 深色，避免糊进近白卡片，深色模式用 `fill` 浅色，与已选轨道保持连续的强调色。

**复选框的启用选中态要按模式取色。** 浅色模式使用 `primary` 方框配白色对勾，使方框与列表高亮边框同色；深色模式保留 `fill` 方框与 `onFill` 对勾，避免亮色对勾和浅色方框连成亮斑。未选中与禁用态沿用 Material 默认配色。模式判断集中在 `AppAccents.checkboxColors()`，界面组件不要各自分支。

**色选择器的色块直接从 overlay 解析。** `Context.resolveOverlaySwatch()` 用 `obtainStyledAttributes` 从每套 overlay 里读 `colorPrimaryFixedDim` 与 `colorOnPrimaryFixed`，所以改配色表时设置页无需同步。注意 `obtainStyledAttributes` 要求属性数组按 ID 升序，那里排序后再按 ID 反查下标。

---

## 九、代码落点

| 文件 | 职责 |
|---|---|
| `res/values/themes.xml` | 基础主题 + 十三套 overlay（浅色）+ 纯黑 overlay，**生成器产出** |
| `res/values-night/themes.xml` | 同上（深色），**生成器产出** |
| `res/values/colors.xml` | 基线（蓝紫）色号 + 开屏页色号，**生成器产出** |
| `res/values/styles.xml` | 仍保留的 View 层控件样式 |
| `ui/theme/AppAccents.kt` | 填充面 / 前景色的语义封装，各控件的 `*Colors()` 都在这 |
| `ui/theme/AppSurfaces.kt` | 四层表面语义 + 深浅/纯黑判定 |
| `ui/AppDialogs.kt` | 标准/日期/自定义对话框入口 + 深色边缘与动态遮罩 |
| `ui/ThemeColorOverlay.kt` | 枚举 → overlay 样式、枚举 → 中文名、overlay 取色，以及启动窗口 / View 层的 Activity 主题叠加 |
| `ui/theme/BiliToolsTheme.kt` | 全应用唯一 Compose 主题入口，由 `AppSettings` 驱动并完整解析 XML 颜色角色 |
| `data/SettingsRepository.kt` | `AppThemeColor` 枚举与旧值迁移表 |
| `docs/配色系统/package.json` | 生成器的 Node 依赖声明，`node_modules/` 已忽略 |

所有 Compose 根都使用 `BiliToolsTheme`：配色与纯黑开关直接随 `AppSettings` 重组，深浅模式由 `Resources.getSystem()` 判断，并订阅 `LocalConfiguration` 接收系统 `uiMode` 变化。`MainActivity` 在 Manifest 中自行处理 `uiMode`，因此主题变化不再通过快照比对、延迟 `recreate()` 刷新；Activity theme overlay 只负责启动窗口和仍存在的 View 层组件。

主题构建器必须覆盖**全部**角色。漏掉的角色会停在 Compose 基线的紫色，平时没有组件用到就不会暴露，一旦加 `Snackbar` 或 `RichTooltip` 这类用 `inverseSurface` 的组件就会蹦出一块与配色无关的紫。新增角色时记得同步 `BiliToolsTheme.kt` 和生成器的 `COLOR_REF`。

### 旧值迁移

配色方案重命名后，`AppThemeColor.fromValue()` 里有一张 `LEGACY_VALUES` 表按色相就近迁移，避免用户升级后配色被重置：

```
rose → Sakura    gold → Sand    olive/lime → Matcha
leaf → Mint      turquoise → Seafoam    cyan → Lagoon
```

再次重命名时记得往这张表里追加，不要替换。

---

## 十、当前指标

`verify.py` 的门槛与实测（十三套配色里的最差值，直接回读出货 XML 算出）：

| 指标 | 门槛 | 浅色 | 深色 |
|---|---|---|---|
| 正文 `onSurface` / 卡片底 | 4.5:1 | 16.27:1 | 11.07:1 |
| 次要文字 `onSurfaceVariant` / 卡片底 | 4.5:1 | 8.85:1 | 6.02:1 |
| 描边 `outline` / 卡片底 | 3:1 | 4.03:1 | 3.03:1 |
| 填充面上的内容 `onFill` / `fill` | 4.5:1 | 8.66:1 | 8.66:1 |
| 普通底栏选中图标 / 指示器 | 4.5:1 | 6.05:1 | 6.02:1 |
| 普通底栏选中文字 / 底栏底 | 4.5:1 | 5.48:1 | 10.24:1 |
| 普通底栏未选中内容 / 底栏底 | 4.5:1 | 7.98:1 | 7.57:1 |
| 开关开启轨道 / 卡片底 | 3:1 | 6.06:1 | 7.79:1 |
| 开关开启滑块 / 轨道 | 3:1 | 6.04:1 | 8.66:1 |
| 开关开启 / 关闭轨道 | 3:1 | 4.92:1 | 9.09:1 |
| 开关关闭轨道 / 卡片底 | 1.15 | 1.222 | 1.167 |
| 开关关闭滑块 `outline` / 轨道 | 3:1 | 3.28:1 | 3.54:1 |
| 悬浮按钮 / 页面底 | 深色 3:1 | 1.10:1（靠投影） | 9.73:1 |
| 悬浮按钮内容 / 按钮 | 4.5:1 | 6.05:1 | 8.66:1 |
| 卡片对页面 | 1.09 | 1.102 | 1.246 |
| 卡片对内嵌 | 浅色 1.06 / 深色 1.09 | 1.073 | 1.167 |
| 内嵌激活对内嵌 | 浅色 1.09 / 深色 1.07 | 1.132 | 1.076 |
| 模态底 / 遮罩后卡片 | 深色 1.15 | Material 3 默认 | 1.176 |
| 填充色最大通道 | 220 ~ 252 | 225 ~ 250 | 同浅色 |

深色的描边 3.03:1 与次要文字 6.02:1 是 2025 spec 对比度曲线给出的「刚好够」档位（描边 3:1、次要文字 4.5:1 对最高表面），与系统动态取色下的观感一致；浅色正文与次要文字由旋钮压深，所以高出一截。

纯黑模式另有一组关闭态约束：`surfaceContainerHighest` 轨道与 `surfaceContainerHigh` 卡片底至少保持 1.08:1 的细微层次（当前 1.088:1），组件边界由 `outline` 描边承担并至少达到 3:1（当前最差 3.89:1），同为 `outline` 的滑块与轨道当前最差 3.58:1。纯黑模态底与 48% 遮罩后卡片的分离度门槛为 1.17:1，当前 1.187:1，并由半透明 `outlineVariant` 边缘进一步界定轮廓。

浅色模式下淡色按钮容器对页面底达不到 3:1 是**设计取舍**，不是缺陷：淡雅浅色风格要求色块本身很淡，状态表达交给压在上面的深色内容与投影。详见第三节。
