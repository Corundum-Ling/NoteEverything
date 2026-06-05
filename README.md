# NoteEverything

> 以软件/游戏为锚点的个人记录工具 — 绑定到每个 App 的感想笔记 + 时长追踪

[![Alpha](https://img.shields.io/badge/status-alpha-orange)](https://github.com/Corundum-Ling/NoteEverything)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.24-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/compose-BOM%202024.06-4285F4)](https://developer.android.com/jetpack/compose)

## 这是什么

玩游戏后想记感想？想知道这周在每个游戏上花了多少时间？

NoteEverything 不是又一本通用笔记软件。它以**你在用什么软件**来组织信息——每条笔记和时长记录都绑定到具体的软件/游戏条目，形成以 App 为维度的记录体系。

同时，这个项目也是通过 **Vibecoding（AI 辅助编程）** 间接学习 Android 开发的实践：描述需求 → AI 生成代码 → 阅读理解 → 逐步上手，产品本身即是学习素材。

## 功能

- **软件管理** — 添加软件条目（名称/平台/分类），按分类分组展示
- **计时器** — 内置计时器 + 手动录入，支持 NumberPicker 滚轮选时和日期选择
- **感想笔记** — 关联到软件的笔记，也支持不关联任何软件的自由随笔
- **时长关联** — 笔记可关联当天的时长记录，软件笔记默认全选
- **时长统计** — 今日/本周/本月排行，一目了然
- **时间轴** — 全部笔记按日期分组浏览，支持搜索、类型筛选、批量删除
- **类别筛选** — 记录页按分类 FilterChip 筛选

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material3 |
| 数据库 | Room (SQLite) |
| 架构 | MVVM + Repository + StateFlow |
| 导航 | Navigation Compose |
| 构建 | Gradle 8.7 + AGP 8.4.2 |

## 构建

用 Android Studio 打开项目根目录，Sync 后直接 Run。

```bash
./gradlew assembleDebug
```

> 路径含中文需在 `gradle.properties` 中设置 `android.overridePathCheck=true`

## 项目结构

```
app/src/main/java/com/corunling/noteeverything/
├── data/               # 数据层
│   ├── entity/         # Room 实体（Software/Note/TimeRecord + 多对多关联表）
│   ├── dao/            # 数据访问对象
│   ├── AppDatabase.kt  # 数据库定义
│   └── NoteEverythingRepository.kt
├── ui/                 # UI 层
│   ├── navigation/     # 路由 + NavGraph
│   ├── theme/          # Material3 主题
│   ├── software/       # 记录页（软件列表 + 详情）
│   ├── note/           # 笔记编辑 + 时间轴
│   └── time/           # 时长总览
├── util/               # 工具类
├── MainActivity.kt
└── App.kt
```

## 开发状态

**Alpha** — 核心功能已可用，仍在积极开发和打磨中。

- [x] MVP 核心闭环
- [x] 笔记↔时长关联
- [x] 类型筛选 + 批量操作
- [ ] 富文本编辑 + 图片插入
- [ ] 云端同步

## License

MIT
