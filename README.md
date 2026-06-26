# Prefab Schematic Addon / 创世神建筑附属模组

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.20-orange)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE.txt)

**Prefab Schematic Addon** 是 [Prefab](https://www.curseforge.com/minecraft/mc-mods/prefab) 模组的附属模组，将 [WorldEdit](https://www.curseforge.com/minecraft/mc-mods/worldedit) 创世神模组的 `.schem` 原理图文件作为预制建筑使用。

> **Prefab Schematic Addon** bridges WorldEdit `.schem` schematic files into the [Prefab](https://www.curseforge.com/minecraft/mc-mods/prefab) mod, allowing you to use your WorldEdit creations as prefabricated buildings.

---

## 🏗️ 功能介绍 / Features

- **导入创世神原理图** — 将 `config/worldedit/schematics/` 中的 `.schem` 文件自动识别为预制建筑
- **Prefab 风格交互** — 右键方块顶部打开菜单，支持**预览**（半透明幽灵方块投影）和**建造**，与 Prefab 原版交互方式一致
- **数据包支持** — 通过数据包 JSON 文件自定义建筑的显示名称、描述、类别、合成配方和建筑材料
- **自动发现** — 无需 JSON 配置即可自动注册原理图文件
- **旋转系统** — 在菜单中可旋转建筑朝向（北/南/东/西）
- **创造模式物品栏** — 所有建筑自动出现在"创世神建筑"标签页中
- **中文本地化** — 完整的简体中文翻译
- **多人游戏支持** — 通过客户端↔服务器网络数据包实现建筑放置

## 📦 依赖 / Dependencies

| 模组 | 版本要求 |
|------|---------|
| [Prefab](https://www.curseforge.com/minecraft/mc-mods/prefab) | 1.10.0+ |
| [WorldEdit](https://www.curseforge.com/minecraft/mc-mods/worldedit) | 7.2.15+ (仅用于生成 .schem 文件，非运行时依赖) |
| Minecraft Forge | 47.4.20+ |
| Minecraft | 1.20.1 |

## 🚀 快速开始 / Quick Start

### 安装 / Installation

1. 安装 Minecraft Forge 1.20.1
2. 将 `prefab-1.10.0.1.jar` 放入 `mods/` 文件夹
3. 将 `prefabschem-1.0.0.jar` 放入 `mods/` 文件夹
4. 将你的 `.schem` 文件放入 `config/worldedit/schematics/`
5. 启动游戏

### 使用 / Usage

1. 进入存档，打开创造模式物品栏
2. 找到"**创世神建筑**"标签页
3. 取出一个建筑蓝图物品
4. **右键点击方块顶部** → 弹出菜单
5. 菜单选项：
   - **预览** — 显示半透明蓝色投影，查看建筑位置
   - **建造** — 立即放置建筑
   - **旋转** — 调整建筑朝向
   - **取消** — 关闭菜单
6. 在预览模式下，**右键确认**建造，**Shift+右键**取消

### 自定义建筑定义 / Custom Definitions

在数据包中创建 JSON 文件：`data/<namespace>/prefab_schematics/<name>.json`

```json
{
  "schematic_file": "my_house.schem",
  "display_name": {"text": "我的房子", "color": "gold"},
  "description": [
    {"text": "一座温馨的小木屋", "color": "gray"},
    {"text": "适合生存初期使用", "color": "dark_gray"}
  ],
  "category": "houses",
  "building_cost": {
    "minecraft:oak_log": 32,
    "minecraft:cobblestone": 64
  },
  "config": {
    "allow_in_dimensions": ["minecraft:overworld"],
    "can_rotate": true
  }
}
```

## ⚙️ 配置 / Configuration

配置文件位于 `config/prefabschem-common.toml`：

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| `enableSchematicBuildings` | `true` | 全局开关 |
| `maxSchematicSize` | `50000` | 单个建筑最大方块数（0=无限制） |
| `disabledSchematics` | `[]` | 禁用的原理图列表 |
| `dimensionBlacklist` | `[]` | 禁止建造的维度列表 |

## 🔧 开发 / Development

```bash
# 克隆仓库
git clone git@github.com:xunfin-akatsuki/prefab-schematic-addon.git
cd prefab-schematic-addon

# 构建
./gradlew build

# 运行开发客户端
./gradlew runClient
```

## 📁 项目结构 / Project Structure

```
src/main/java/.../prefabschematic/
├── PrefabSchematicAddon.java      # 主模组类
├── config/
│   └── SchematicConfig.java       # 配置文件
├── gui/
│   ├── SchematicBuildScreen.java  # 建造菜单 GUI
│   └── ClientPreviewHandler.java  # 幽灵方块预览渲染
├── network/
│   └── BuildSchematicPacket.java  # 客户端→服务器建造数据包
├── registry/
│   ├── ModItems.java              # 物品注册
│   ├── SchematicBlueprintItem.java # 蓝图物品
│   └── SchematicRegistry.java     # 原理图注册与加载
└── schematic/
    ├── SchematicParser.java       # .schem 文件解析
    └── SchematicToPrefabConverter.java # 转换为 Prefab Structure
```

## 📄 许可 / License

MIT License - 详见 [LICENSE.txt](LICENSE.txt)
