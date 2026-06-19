# NoteEverything

> 以软件/游戏为锚点的个人记录工具 — 绑定到每个 App 的感想笔记 + 时长追踪。
> 状态：Alpha — v0.2（功能完整，打磨中）

## 项目本质

NoteEverything 不是通用笔记软件。它以**你在用什么软件**来组织信息——每条笔记和时长记录都绑定到具体的软件/游戏条目，形成以 App 为维度的记录体系。

**核心架构：** 单模块 Android 应用，MVVM + Room + Navigation Compose，三 Tab 底部导航。

## 快速索引

| 文档 | 说明 |
|------|------|
| `docs/设计文档.md` | 产品设计、数据模型、页面结构 |
| `docs/UI重设计文档.md` | v0.2 视觉升级规范（分类色系、组件系统） |
| `docs/开发记录.md` | 版本迭代历史 + 待办 |
| `docs/体验报告.md` | Bug 清单（均已修复） |
| `docs/原始点子.txt` | 最初的灵感记录 |

## 技术栈

| 类别 | 版本/技术 |
|------|----------|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose (BOM 2024.06) + Material3 |
| 数据库 | Room 2.6.1 (SQLite) |
| 架构 | MVVM + Repository + StateFlow |
| 导航 | Navigation Compose 2.7.7 |
| 构建 | Gradle 8.7 + AGP 8.4.2 |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 34 (Android 14) |

## 文件结构

```
NoteEverything/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── app/src/main/java/com/corunling/noteeverything/
│   ├── MainActivity.kt          # 入口：NavGraph + Theme
│   ├── App.kt                   # Application（持有 database + repository）
│   ├── data/
│   │   ├── entity/              # Room 实体
│   │   │   ├── SoftwareEntity.kt
│   │   │   ├── NoteEntity.kt
│   │   │   ├── TimeRecordEntity.kt
│   │   │   └── NoteTimeRecordLink.kt     # 笔记↔时长多对多关联
│   │   ├── dao/                  # DAO 接口
│   │   ├── AppDatabase.kt        # 数据库定义
│   │   └── NoteEverythingRepository.kt
│   ├── ui/
│   │   ├── navigation/           # Routes + NavGraph
│   │   ├── theme/                # Theme + CategoryColors
│   │   ├── software/             # 软件 Tab + 详情页 + ViewModel
│   │   ├── note/                 # 笔记 Tab + 编辑器 + ViewModel
│   │   ├── time/                 # 统计 Tab + ViewModel
│   │   ├── editor/               # 富文本编辑器（WebView + Bridge）
│   │   └── MainScreen.kt        # 三 Tab 主框架 + FAB
│   ├── util/DateTimeUtils.kt
│   └── assets/
│       └── rich_editor.html      # WebView contentEditable 编辑器页面
```

## 架构约定

### 数据流
```
Compose UI → ViewModel → Repository → Room DB
         ← StateFlow ←
```

### 关键模式
- **ViewModel** 通过 `ViewModelProvider.Factory` 注入 `Repository`
- **Repository** 来自 `App.kt` 的懒加载单例
- **DAO** 返回 `Flow<>` 实现响应式查询
- **所有主线程操作**通过 `viewModelScope.launch` 切协程
- **严禁**在 Screen 或 Composable 中直接调用 DAO

### 导航路由
| 路由 | 页面 |
|------|------|
| `main` | MainScreen（三 Tab） |
| `software/{softwareId}` | 软件详情 |
| `note/editor` 带可选参数 | 笔记编辑器 |

## 色彩体系（v0.2 固定方案）

| 分类 | 主色 | 浅底 | 深色文字 |
|------|------|------|---------|
| 游戏 | `#FF9800` | `#FFF3E0` | `#E65100` |
| 工具 | `#2196F3` | `#E3F2FD` | `#1565C0` |
| 学习 | `#4CAF50` | `#E8F5E9` | `#2E7D32` |
| 其他 | `#9C27B0` | `#F3E5F5` | `#7B1FA2` |
| 随笔 | `#E91E63` | `#FCE4EC` | `#C2185B` |

Primary: `#1A73E8`（固定，不启用 dynamicColor）

## 构建

```bash
cd 01-项目/NoteEverything
./gradlew assembleDebug    # 直接命令行构建
# 或用 Android Studio 打开项目根目录，Sync → Run
```

> 中文路径需在 `gradle.properties` 设 `android.overridePathCheck=true`

## 开发状态

### ✅ 已完成
- **v0.1** MVP：软件 CRUD、笔记编辑、计时器、时长统计、时间轴
- **v0.2** UI 重设计：分类色彩体系、沉浸状态栏、组件视觉升级
- **v0.3** 富文本编辑 + 图片插入：WebView contentEditable 编辑器、格式工具栏、HTML 存储

### 🔄 待做
- [ ] Word / HTML 导出功能
- [ ] 设置页面（主题切换、数据管理）
- [ ] 动效增强（转场动画、列表动效）

### 🗺️ 远期
- 日历热力图 / 统计折线图
- 云端同步
- Steam API 集成 / Android UsageStats 自动检测
- 组件模块化

## 相关记忆

- [[NoteEverything_延迟提交]] — 文档改动不单独提交，延后至功能/Bug 修复时
