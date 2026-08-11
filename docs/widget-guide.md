# 小组件指南

## 概述

TimeTrace 支持 Android App Widget，用户可以在桌面上直接查看他们关注的事件，无需打开应用。

## 文件结构

```
widget/
├── TraceWidgetProvider.kt            # 小组件 Provider（基础）
├── TraceWidgetProviders.kt           # 多尺寸 Provider 注册
├── TraceWidgetUpdater.kt             # 更新触发逻辑
├── TraceWidgetSize.kt                # 尺寸与布局参数
└── TraceWidgetBackgroundRenderer.kt  # 背景渲染
```

## 小组件尺寸

| 尺寸 | 最小宽高 | 用途 |
|------|----------|------|
| 小 (2×1) | 200×100dp | 紧凑的单事件展示 |
| 中 (4×2) | 360×150dp | 标准卡片布局 |
| 大 (4×4) | 360×360dp | 多事件列表或大卡片 |

## 更新机制

### 触发时机

小组件在以下情况会被更新：

1. **数据变更时**：用户添加/删除/编辑/排序事件后，`EventViewModel` 调用 `TraceWidgetUpdater.requestAllUpdate()`
2. **倒计时自动推进后**：`checkAndAdvanceCountdowns()` 完成重复事件的日期推进后触发
3. **导入备份后**：从 ZIP 恢复数据完成后触发
4. **系统定时更新**：由 `updatePeriodMillis` 控制（在 `app_widget_info.xml` 中配置）

### 更新流程

```
数据变更 → EventViewModel → TraceWidgetUpdater.requestAllUpdate()
                                    ↓
                          AppWidgetManager.updateAppWidget()
                                    ↓
                          TraceWidgetProvider.onUpdate()
                                    ↓
                          RemoteViews 渲染
```

### TraceWidgetUpdater

`TraceWidgetUpdater` 是触发小组件刷新的统一入口：

```kotlin
// 请求更新所有已放置的小组件实例
TraceWidgetUpdater.requestAllUpdate(context)
```

这是一个静态工具方法，可以在 ViewModel、Activity 等任何上下文中调用。

## 小组件配置

用户通过 `WidgetConfigActivity` 配置小组件参数：

1. 用户在桌面长按 → 选择 TimeTrace 小组件
2. 系统启动 `WidgetConfigActivity`
3. 用户选择要显示的事件和小组件样式
4. 确认后小组件放置在桌面

## 背景渲染

`TraceWidgetBackgroundRenderer` 负责小组件背景的绘制：

- 支持自定义背景图片（从应用内部存储读取）
- 支持遮罩层（不透明度可配）
- 适配小组件容器的圆角和尺寸

由于 App Widget 使用 `RemoteViews`（运行在 Launcher 进程），背景图片需要渲染为 Bitmap 后通过 `ImageView` 设置，不能直接使用 Compose 组件。

## 数据共享

小组件数据通过以下方式获取：

1. **Room 数据库直接读取**：小组件 Provider 可以直接查询 `AppDatabase`
2. **SharedPreferences**：小组件配置参数存储（如选择的事件 ID、背景设置）

## 开发注意事项

### RemoteViews 限制

App Widget 使用 `RemoteViews`，仅支持有限的 View 类型：
- `TextView`、`ImageView`、`LinearLayout`、`RelativeLayout` 等基础组件
- **不支持** Compose、WebView、自定义 View 等

这意味着小组件的 UI 需要用 XML 布局文件定义，逻辑在 `TraceWidgetProvider` 中用 `RemoteViews` API 构建。

### 性能考虑

- 小组件更新操作应尽量轻量，避免在 `onUpdate()` 中执行耗时 IO
- 数据库查询应在后台线程执行
- Bitmap 渲染注意内存管理，及时回收

### 测试

测试小组件时注意：
- 模拟器可能不完全支持小组件的所有行为
- 真机测试建议在不同 Launcher（系统桌面、Nova、Lawnchair 等）上验证视觉效果
- 小组件尺寸在不同设备/DPI 上可能有差异
