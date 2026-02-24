# Client Utilities (Fabric 1.21.11)

一个用于 Minecraft Fabric **1.21.11** 的客户端宏模组，支持脚本循环、导入导出、GUI 管理与热键控制。

## 功能

- 脚本命令：
  - `KeyDown "W"`
  - `KeyUp "W"`
  - `KeyPress "Num 2"`
  - `LeftDown`
  - `LeftUp`
  - `Delay 2000`
  - `For 9`
  - `Next`
- 兼容 `, 1` 风格参数（如 `KeyDown "W", 1`）
- 默认脚本可循环执行（Repeat 可开关）
- 世界/地图切换时自动停止脚本
- GUI 管理（`/mmacro gui`）：
  - 脚本列表刷新、切换、加载、保存、删除
  - 一键模板创建
  - Start/Pause / Stop
  - Repeat / AimLock 开关
  - 在 GUI 中修改 **Start/Pause** 和 **Stop** 热键

## 指令

- `/mmacro start` 开始或继续
- `/mmacro pause` 暂停/继续
- `/mmacro stop` 停止
- `/mmacro import <name>` 从脚本目录加载
- `/mmacro export <name>` 导出内存脚本到文件
- `/mmacro set <script>` 直接设置脚本内容
- `/mmacro repeat on|off`
- `/mmacro aimlock on|off`
- `/mmacro where` 查看脚本目录
- `/mmacro gui` 打开管理界面

## 脚本目录

`config/macro_mod/scripts/`

## 构建

```bash
gradle build
```

产物：`build/libs/client-utilities-0.1.0.jar`

## 说明

- 本项目是客户端输入模拟方案，行为仍受 Minecraft 焦点/输入机制影响。
- Mod Metadata：
  - `id`: `client_utilities`
  - `name`: `Client Utilities`
