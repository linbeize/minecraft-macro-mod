# Client Utilities (Fabric 26.1)

一个用于 Minecraft Fabric **26.1** 的客户端宏模组：脚本循环、游戏内编辑器、热键控制与状态 HUD。

> ⚠️ 很多服务器禁止使用自动化宏，使用前请确认服务器规则。

## 脚本语法

每行一条命令，也可以用 `;` 分隔；`#` 或 `//` 开头为注释。

| 命令 | 说明 |
| --- | --- |
| `KeyDown "W"` / `KeyUp "W"` | 按住 / 松开 |
| `KeyPress "2", 3` | 点按 N 次（默认 1） |
| `LeftDown` / `LeftUp` / `LeftClick [n]` | 鼠标左键 |
| `RightDown` / `RightUp` / `RightClick [n]` | 鼠标右键 |
| `Delay 500` | 等待（毫秒） |
| `For 9` … `Next` | 循环，可嵌套（`EndFor` 等同 `Next`） |

按键名：`W A S D`、`Space`、`Shift`、`Ctrl`、`Q`、`E`、`F`、`1`-`9`（或 `Num 1`-`Num 9`）、`LMB`、`RMB`、`MMB`。
按键按“功能”映射，玩家改了键位后脚本依然有效。

兼容旧格式：`KeyDown "W", 1,` 末尾的计数和逗号、行首行号都会被忽略。
脚本错误会在编辑器下方实时提示，并带行号。

## 运行行为

- 同一 tick 内连续执行指令，直到遇到 `Delay`；延时按理想时间累计，长时间循环不漂移。
- **暂停**会松开所有按键，继续时重新按下；暂停期间不计入 `Delay`。
- 打开任何界面（Esc、聊天、宏管理器）时宏会**冻结**，关闭后继续；打开容器界面默认直接停止。
- 切换世界自动停止。
- 视角锁定期间屏蔽鼠标转向，不会抖动。

## 热键（选项 → 控制 → 宏，可修改）

| 默认键 | 功能 |
| --- | --- |
| F6 | 打开宏管理器 |
| F7 | 切换视角锁定 |
| F8 | 开始 / 暂停 |
| F9 | 停止 |

## 宏管理器（F6 或 `/mmacro gui`）

- 左侧脚本列表（滚轮滚动），新建 / 复制 / 删除（删除需点两次确认）
- 右侧编辑器：实时语法检查、鼠标定位、滚轮滚动、Ctrl+A/C/V
  - 默认是**安全编辑器**（不开启输入法），因为开启 IME 会让部分客户端（如 Lunar）崩溃；中文可以 Ctrl+V 粘贴
  - 底栏“输入法”开关可切换到原版编辑器，直接输入中文（设置保存在 `settings.properties` 的 `imeEditor`）
- 名称框 + 保存（Ctrl+S）/ 重命名 / 还原；未保存修改有标记，关闭界面草稿也不会丢
- 底栏：开始/暂停、停止、循环、视角锁定、HUD、热键设置、帮助（`?`）
- 宏停止时，热键运行的就是编辑器里看到的脚本

## 指令

- `/mmacro start | pause | stop | status | list | where | gui`
- `/mmacro load <名称>`（别名 `import`），`/mmacro save <名称>`（别名 `export`），支持 Tab 补全
- `/mmacro set <命令; 命令; ...>` 直接设置脚本
- `/mmacro repeat on|off`、`/mmacro aimlock on|off`、`/mmacro hud on|off`

## 文件

- 脚本：`config/macro_mod/scripts/<名称>.txt`
- 设置：`config/macro_mod/settings.properties`（热键保存在原版 `options.txt`）

## 支持的版本

| Minecraft | 下载的 jar | Fabric Loader | Fabric API |
| --- | --- | --- | --- |
| 26.1 | `client-utilities-<版本>+mc26.1.jar` | ≥ 0.19.2 | 需要 |
| 26.2 | `client-utilities-<版本>+mc26.2.jar` | ≥ 0.18.4 | 需要 |
| 26.3 | `client-utilities-<版本>+mc26.3.jar` | ≥ 0.19.3 | 需要 |

每个 Minecraft 版本对应各自的 jar，不能混用（26.3 的输入系统换成了 SDL3，按键码与之前不同）。

## 构建

需要 Java 25，项目自带 Gradle Wrapper：

```bash
./gradlew build -Pmc=26.1   # 或 26.2 / 26.3，默认 26.1
```

产物在 `build/libs/`。共享代码在 `src/main`，各版本差异集中在 `src/compat/mc26_x/.../Compat.java`。