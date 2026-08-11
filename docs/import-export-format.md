# KIPPU Trace 导入/导出数据格式说明

## 概述

KIPPU Trace 使用 **ZIP 压缩包** 作为导入/导出的文件格式（`.zip`）。ZIP 包内包含一个 JSON 数据文件以及可选的背景图片文件夹。

## ZIP 包结构

```
backup.zip
├── events.json          # 事件数据（必需）
└── backgrounds/         # 背景图片（可选）
    ├── abc123.jpg
    └── def456.png
```

## events.json 格式

`events.json` 是一个 **JSON 数组**，每个元素代表一个事件对象。

### 示例

```json
[
  {
    "id": 1,
    "title": "生日倒计时",
    "targetDate": 1735689600000,
    "isFuture": true,
    "isLunar": false,
    "mode": "COUNT_DOWN",
    "isPinned": true,
    "maskOpacity": 0.3,
    "backgroundFile": "abc123.jpg",
    "repeatMode": "YEARLY",
    "repeatCustomDays": 0,
    "repeatAnchorDate": 1735689600000,
    "anniversaryYearEnabled": false,
    "anniversaryMonthEnabled": false,
    "anniversaryWeekEnabled": false,
    "anniversaryCombinedText": ""
  },
  {
    "id": 2,
    "title": "恋爱纪念日",
    "targetDate": 1609459200000,
    "isFuture": false,
    "isLunar": false,
    "mode": "ACCUMULATE",
    "isPinned": false,
    "maskOpacity": 0.5,
    "repeatMode": "NONE",
    "customAnniversaryDays": 100,
    "anniversaryYearEnabled": true,
    "anniversaryMonthEnabled": true,
    "anniversaryWeekEnabled": false,
    "anniversaryCombinedText": "今天是我们在一起的纪念日"
  }
]
```

### 字段说明

| 字段                        | 类型        | 必需    | 默认值      | 说明                                                              |
|---------------------------|-----------|-------|----------|-----------------------------------------------------------------|
| `id`                      | `integer` | ✅     | -        | 事件唯一标识，导入时会覆盖同 ID 的已有事件                                         |
| `title`                   | `string`  | ✅     | -        | 事件标题                                                            |
| `targetDate`              | `integer` | ✅     | -        | 目标日期，**毫秒级 Unix 时间戳**（`Date.now()` 格式）                          |
| `isFuture`                | `boolean` | ✅     | -        | 目标日期是否在未来。`true`=未来日期，`false`=过去日期。影响显示文案（"还有 X 天" vs "已经 X 天"） |
| `isLunar`                 | `boolean` | ❌     | `false`  | 是否为农历日期                                                         |
| `mode`                    | `string`  | ✅     | -        | 显示模式，取值见下方枚举                                                    |
| `isPinned`                | `boolean` | ❌     | `false`  | 是否置顶                                                            |
| `maskOpacity`             | `number`  | ❌     | `0.3`    | 背景遮罩不透明度，取值范围 `0.0` ~ `1.0`                                     |
| `backgroundFile`          | `string`  | ❌     | -        | 背景图片文件名（不含路径）。对应的图片文件需放在 `backgrounds/` 目录下                     |
| `repeatMode`              | `string`  | ❌     | `"NONE"` | 倒数模式自动重置规则，取值见下方枚举                                              |
| `repeatCustomDays`        | `integer` | ❌     | `0`      | 自定义重置天数（仅 `repeatMode` 为 `CUSTOM_DAYS` 时有效）                     |
| `repeatAnchorDate`        | `integer` | ❌     | `targetDate` | 重复计算的原始锚点日期，毫秒级 Unix 时间戳；旧备份缺失时使用 `targetDate`              |
| `customAnniversaryDays`   | `integer` | ❌     | `0`      | 自定义纪念日间隔天数（`0` 表示不启用）                                           |
| `anniversaryYearEnabled`  | `boolean` | ❌     | `false`  | 是否启用年纪念日                                                        |
| `anniversaryMonthEnabled` | `boolean` | ❌     | `false`  | 是否启用月纪念日                                                        |
| `anniversaryWeekEnabled`  | `boolean` | ❌     | `false`  | 是否启用周纪念日                                                        |
| `anniversaryCombinedText` | `string`  | ❌     | `""`     | 多纪念日同时触发时的自定义合并文案                                               |

### `mode` 枚举值

| 值            | 含义                     |
|--------------|------------------------|
| `COUNT_DOWN` | 倒数模式 — 显示距离目标日期还有多少天   |
| `ACCUMULATE` | 累计模式 — 显示从目标日期起已经过了多少天 |

### `repeatMode` 枚举值

| 值             | 含义                                |
|---------------|-----------------------------------|
| `NONE`        | 不重复（默认）                           |
| `YEARLY`      | 每年重置                              |
| `MONTHLY`     | 每月重置                              |
| `WEEKLY`      | 每周重置                              |
| `CUSTOM_DAYS` | 自定义天数重置（配合 `repeatCustomDays` 使用） |

### 注意事项

1. **`position` 字段不导出** — 事件的排序位置由列表顺序决定，导入后按 ID 顺序重建。
2. **`backgroundFile` 为 null 或不存在时** — 表示该事件没有自定义背景图。
3. **图片文件** — 如果 `backgroundFile` 指定了文件名，但 ZIP 中 `backgrounds/` 目录下找不到对应文件，该事件的背景图将为空白。
4. **ID 冲突** — 导入时采用"清空全部 + 批量插入"策略，所有已有数据会被替换。

---

## JSON Schema

```jsonschema
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://kippu.trace/schema/events.json",
  "title": "KIPPU Trace 导出数据",
  "description": "KIPPU Trace 事件数据的导入/导出格式",
  "type": "array",
  "items": {
    "$ref": "#/$defs/DateEvent"
  },
  "$defs": {
    "DateEvent": {
      "type": "object",
      "required": ["id", "title", "targetDate", "isFuture", "mode"],
      "properties": {
        "id": {
          "type": "integer",
          "description": "事件唯一标识"
        },
        "title": {
          "type": "string",
          "description": "事件标题"
        },
        "targetDate": {
          "type": "integer",
          "description": "目标日期，毫秒级 Unix 时间戳",
          "examples": [1735689600000]
        },
        "isFuture": {
          "type": "boolean",
          "description": "目标日期是否在未来。true=未来, false=过去"
        },
        "isLunar": {
          "type": "boolean",
          "description": "是否为农历日期",
          "default": false
        },
        "mode": {
          "type": "string",
          "description": "显示模式",
          "enum": ["COUNT_DOWN", "ACCUMULATE"]
        },
        "isPinned": {
          "type": "boolean",
          "description": "是否置顶",
          "default": false
        },
        "maskOpacity": {
          "type": "number",
          "description": "背景遮罩不透明度 (0.0 ~ 1.0)",
          "default": 0.3,
          "minimum": 0.0,
          "maximum": 1.0
        },
        "backgroundFile": {
          "type": "string",
          "description": "背景图片文件名，对应 backgrounds/ 目录下的文件"
        },
        "repeatMode": {
          "type": "string",
          "description": "倒数模式自动重置规则",
          "enum": ["NONE", "YEARLY", "MONTHLY", "WEEKLY", "CUSTOM_DAYS"],
          "default": "NONE"
        },
        "repeatCustomDays": {
          "type": "integer",
          "description": "自定义重置天数",
          "default": 0
        },
        "repeatAnchorDate": {
          "type": "integer",
          "description": "重复计算的原始锚点日期，毫秒级 Unix 时间戳；缺失时使用 targetDate"
        },
        "customAnniversaryDays": {
          "type": "integer",
          "description": "自定义纪念日间隔天数，0 表示不启用",
          "default": 0
        },
        "anniversaryYearEnabled": {
          "type": "boolean",
          "description": "是否启用年纪念日",
          "default": false
        },
        "anniversaryMonthEnabled": {
          "type": "boolean",
          "description": "是否启用月纪念日",
          "default": false
        },
        "anniversaryWeekEnabled": {
          "type": "boolean",
          "description": "是否启用周纪念日",
          "default": false
        },
        "anniversaryCombinedText": {
          "type": "string",
          "description": "多纪念日同时触发时的自定义合并文案",
          "default": ""
        }
      }
    }
  }
}
```

---

## 手动构建备份

如果你想手动创建一个可被 KIPPU Trace 识别的备份文件：

1. 创建 `events.json`，按上述格式填写事件数据。
2. （可选）创建 `backgrounds/` 文件夹，放入背景图片。
3. 将 `events.json` 和 `backgrounds/` 打包为 ZIP 文件（确保文件和文件夹在 ZIP 根目录下，不要嵌套多余层级）。
4. 将 `.zip` 文件传输到手机，通过 KIPPU Trace 的"导入备份"功能选择该文件即可。

```bash
# 示例：macOS/Linux 命令行打包
zip -r backup.zip events.json backgrounds/
```
