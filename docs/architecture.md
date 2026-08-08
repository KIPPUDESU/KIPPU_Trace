# 架构说明

## 整体架构

TimeTrace 采用 MVVM（Model-View-ViewModel）架构，结合 Android Jetpack 组件构建。整个应用遵循单向数据流原则，从数据层向上流动到 UI 层，用户操作通过 ViewModel 向下传递到数据层。

```
┌─────────────────────────────────────┐
│              UI Layer               │
│  (Compose Screens / Components)     │
├─────────────────────────────────────┤
│          ViewModel Layer            │
│        (EventViewModel)             │
├─────────────────────────────────────┤
│          Data Layer                 │
│  (Room Database / Repository)       │
├─────────────────────────────────────┤
│          Model Layer                │
│        (DateEvent)                  │
└─────────────────────────────────────┘
```

## 包结构

```
com.kippu.trace/
├── data/           # 数据持久化层
│   ├── AppDatabase.kt      # Room 数据库定义
│   ├── Converters.kt       # 类型转换器
│   └── EventRepository.kt  # 数据仓库
├── model/          # 数据模型
│   └── DateEvent.kt        # 事件实体 & 枚举
├── ui/             # 用户界面
│   ├── components/         # 可复用组件
│   │   ├── AnniversaryConfigSection.kt  # 纪念日配置组件
│   │   ├── NormalEventCard.kt           # 普通事件卡片
│   │   └── PinnedEventCard.kt           # 置顶事件卡片
│   ├── screens/            # 页面
│   │   ├── HomeScreen.kt       # 主页
│   │   ├── DetailScreen.kt     # 详情页
│   │   ├── EditorScreen.kt     # 编辑页
│   │   └── SettingsScreen.kt   # 设置页
│   └── theme/              # 主题
│       ├── Color.kt            # 色板定义
│       ├── Theme.kt            # Material 3 主题
│       └── Type.kt             # 字体排版
├── utils/          # 工具类
│   ├── AnniversaryUtils.kt    # 纪念日计算
│   ├── BackupManager.kt       # 备份导入导出
│   ├── FileUtils.kt           # 文件操作
│   ├── ImageUtils.kt          # 图片处理
│   ├── LanguagePreferences.kt # 语言偏好
│   ├── TextUtils.kt           # 文本处理
│   ├── ThemePreferences.kt    # 主题偏好
│   ├── TimeUtils.kt           # 时间计算与显示
│   └── UIUtils.kt             # UI 辅助
├── viewmodel/      # ViewModel
│   └── EventViewModel.kt     # 事件业务逻辑
├── widget/         # 桌面小组件
│   ├── TraceWidgetBackgroundRenderer.kt  # 小组件背景渲染
│   ├── TraceWidgetProvider.kt            # 小组件 Provider
│   ├── TraceWidgetProviders.kt           # 多尺寸 Provider
│   ├── TraceWidgetSize.kt                # 小组件尺寸定义
│   └── TraceWidgetUpdater.kt             # 小组件更新触发
├── MainActivity.kt            # 主 Activity 入口
└── WidgetConfigActivity.kt    # 小组件配置 Activity
```

## 数据模型

### DateEvent

核心数据实体，使用 Room 持久化存储。表名 `date_events`。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 主键，自增 |
| `title` | `String` | 事件标题 |
| `targetDate` | `Long` | 目标日期（毫秒时间戳） |
| `isFuture` | `Boolean` | 语义判断：未来/过去 |
| `isLunar` | `Boolean` | 是否农历日期 |
| `mode` | `DisplayMode` | 显示模式（倒数/累计） |
| `backgroundUri` | `String?` | 背景图片 URI |
| `isPinned` | `Boolean` | 是否置顶 |
| `maskOpacity` | `Float` | 遮罩不透明度 (0.0~1.0) |
| `position` | `Int` | 排序位置 |
| `repeatMode` | `RepeatMode` | 倒数重复模式 |
| `repeatCustomDays` | `Int` | 自定义重复天数 |
| `repeatAnchorDate` | `Long?` | 重复计算的原始锚点日期 |
| `customAnniversaryDays` | `Int` | 自定义纪念日天数 |
| `anniversaryYearEnabled` | `Boolean` | 周年纪念日开关 |
| `anniversaryMonthEnabled` | `Boolean` | 月纪念日开关 |
| `anniversaryWeekEnabled` | `Boolean` | 周纪念日开关 |
| `anniversaryCombinedText` | `String` | 多纪念日合并文案 |

### 枚举类型

**DisplayMode（显示模式）**
- `COUNT_DOWN` — 倒数模式，显示距离目标日期还有多少天
- `ACCUMULATE` — 累计模式，显示从目标日期起已经过了多少天

**RepeatMode（重复模式）**
- `NONE` — 不重复
- `YEARLY` — 每年
- `MONTHLY` — 每月
- `WEEKLY` — 每周
- `CUSTOM_DAYS` — 自定义天数

## 数据流

### 读取流程

```
Room DB → EventDao (Flow) → EventRepository → EventViewModel (StateFlow) → Compose UI
```

1. Room 数据库通过 `EventDao.getAllEvents()` 返回 `Flow<List<DateEvent>>`
2. `EventRepository` 封装 DAO，对外暴露 Flow
3. `EventViewModel` 使用 `stateIn()` 将 Flow 转换为 `StateFlow`
4. Compose UI 通过 `collectAsState()` 订阅 StateFlow，自动重组

### 写入流程

```
Compose UI → EventViewModel (viewModelScope) → EventRepository → EventDao → Room DB
                                                      ↓
                                            TraceWidgetUpdater
```

1. 用户操作触发 ViewModel 方法（`addEvent` / `deleteEvent` / `updateEventsOrder`）
2. ViewModel 在 `viewModelScope` 中启动协程执行数据库操作
3. 写操作完成后自动触发小组件更新

## 核心模块

### EventViewModel

应用的唯一 ViewModel（`AndroidViewModel`），管理所有事件状态：

- **`allEvents`**：`StateFlow<List<DateEvent>>`，UI 层的数据源，按置顶优先 + 位置 + ID 排序
- **`checkAndAdvanceCountdowns()`**：初始化时自动检查所有倒数事件，对已过期且设置了重复规则的事件自动推进目标日期
- **`addEvent` / `deleteEvent`**：增删事件，操作后自动触发小组件刷新
- **`exportBackup` / `importBackup`**：通过 `BackupManager` 实现 ZIP 格式的数据导入导出

### 主题系统

- 基于 Material 3 设计语言
- 支持深色/浅色模式，通过 `ThemePreferences` 管理
- 色板和字体定义在 `ui/theme/` 中，统一管理

### 多语言

原生支持简体中文、英语、日语。通过 Android 标准的 `strings.xml` 资源文件实现，`LanguagePreferences` 管理语言切换。

### 小组件

详见 [小组件指南](./widget-guide.md)。

## 导航

应用使用 Jetpack Navigation Compose 进行页面路由：

- **主页** (`HomeScreen`) — 事件列表，默认首页
- **编辑页** (`EditorScreen`) — 新建/编辑事件
- **详情页** (`DetailScreen`) — 事件详情展示，支持全屏背景海报预览
- **设置页** (`SettingsScreen`) — 主题切换、语言切换、备份导入导出
