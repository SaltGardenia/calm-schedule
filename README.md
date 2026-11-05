# 不烧心课程表（calm-schedule）

面向中国高校学生的 Android 课程表应用（Kotlin + Jetpack Compose + Room）。
本项目为**二次开发版本**：在研究 [WakeUp 课程表（BUPT 分支开源源码）](https://github.com/xianfei/WakeupSchedule_BUPT)
与 [openschoolcn/zfn_api](https://github.com/openschoolcn/zfn_api) 等已有 GitHub 项目源码的基础上，
重写为 Compose 架构，并适配全国高校常见教务系统的课表导入。

## 功能

- **课表展示**：整点时间轴（分割线与小时标签对齐，课程/日程按真实时间定位），
  单行顶栏（‹ 第N周·日期 › 导入 设置），翻到非本周时右下角浮出「回到本周」；
  非本周课程默认隐藏，可在设置中开启（淡化显示）
- **液态玻璃 UI**：顶部玻璃操作按钮（导入/设置）、玻璃顶栏与胶囊、分组玻璃卡片、
  按压缩放反馈；弹窗（课程详情/编辑、日程）同样使用玻璃材质；
  遵循 Material Design 3 语义 token 与无障碍底线（玻璃材质为有意的风格化偏离）
- **教务系统导入（快速导入）**：
  - 正方教务 **新版**（jwglxt）：原生登录（RSA 密码加密 + 验证码）→ `xskbcx_cxXsKb.html` JSON 解析
  - 正方教务 **旧版**（WebForms）：`default2.aspx` 登录 → `xskbcx.aspx` 课表页解析
  - 强智教务（jsxsd）：原生登录（实验性）+ `xskb_list.do` / `#kbtable` 解析
  - **金智 EAMS5**（合肥工业大学等）：网页登录后抓取卡片式课表，按"地点(周次)"分段解析
    （临时教室分段、单双周、波浪号周次），已用 HFUT WebVPN 真实课表验证（19 条）
  - **学校列表式导入**：选择学校 → 按步骤提示登录 → 打开课表页 → 手动「抓取本页课表」，
    自动走"强智 → 正方旧版 → 金智 EAMS → 课程列表 → 通用网格"解析链；
    每所学校单独适配（新增学校仅需在 `SchoolRegistry` 添加一条配置），
    当前支持：**合肥工业大学**（WebVPN 统一认证）
- **手动管理**：长按空白格添加课程；点击课程查看详情（右上角红色垃圾桶删除，二次确认）；配色自动分配
- **一次性日程**：课表空白区块**长按滑动**选择时间段（半小时粒度，弹窗可精确到分钟），
  填写事件内容/地点/备注；仅在所选的那一天生效，不做周次重复（虚线玻璃卡）
- **多课表**：多学期/多课表切换、独立开学日期与作息
- **设置**：开学日期（推算当前周）、每日节数、逐节作息编辑、总周数、显示周末、显示非本周课程
- **数据安全**：自动备份（应用私有目录，数据变更即落盘、启动自动读取恢复），无用户操作入口；
  覆盖升级/迁移异常后数据可自动找回

## 技术栈

| 组件 | 选择 |
|---|---|
| UI | Jetpack Compose + Material 3（Dynamic Color）+ 液态玻璃材质层 |
| 语言/构建 | Kotlin 2.0 / AGP 8.7 / Version Catalog |
| 数据 | Room 2.6（KSP）+ DataStore |
| 网络/解析 | OkHttp 4 + Jsoup（GBK/UTF-8 自动识别、Cookie 会话） |
| 玻璃材质 | 自绘液态玻璃层（半透明渐变 + 顶缘高光 + 柔和投影 + 按压弹簧） |
| 最低版本 | Android 8.0（minSdk 26） |

## 构建

要求：JDK 17、Android SDK 35（`local.properties` 指向 SDK 路径）。

```bash
./gradlew :app:assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # 解析器单元测试
```

> 国内网络：`settings.gradle.kts` 已优先配置阿里云镜像，Gradle 发行版走腾讯镜像
> （见 `gradle/wrapper/gradle-wrapper.properties`）。如需换回官方源，删除镜像仓库行即可。

## 目录结构

```
app/src/main/java/com/saltgarden/kechengbiao/
├── KcbApplication.kt          # 应用入口 + 依赖容器
├── MainActivity.kt
├── core/time/                 # 周次推算、作息工具
├── data/                      # Room 实体/DAO/数据库/仓库
├── importer/                  # ★ 导入引擎
│   ├── core/                  #   周次文本解析、节次解析、HTTP 客户端
│   ├── zf/                    #   正方新/旧版客户端与解析器
│   ├── qz/                    #   强智客户端与解析器
│   ├── generic/               #   通用 HTML 四级解析链
│   ├── text/                  #   JSON/CSV/文本行 快速导入
│   └── webview/               #   网页登录抓取 Activity
└── ui/                        # Compose 界面（schedule/import/settings/theme/nav）
app/src/test/                  # 解析器单元测试 + 真实结构 HTML/JSON 夹具
docs/                          # 适配指南、REA 逆向方法论、参考项目研究笔记
```

## 使用流程

1. 顶部「⤓ 导入」→ 选择学校（当前支持合肥工业大学）→「开始导入」
2. 在网页中完成登录（支持统一身份认证 / CAS），进入「我的课表」页面
3. 点击底部「抓取本页课表」→ 预览识别结果，勾选后「覆盖 / 追加导入」
4. 数据自动备份至应用私有目录，应用升级后自动恢复，无需手动操作

## 参考与研究

- [xianfei/WakeupSchedule_BUPT](https://github.com/xianfei/WakeupSchedule_BUPT) —— WakeUp 课程表 3.621 开源源码，
  本项目的解析策略（`kbtable`/`Table1` 定位、`title` 属性字段、`-----` 分隔多课等）与 WebView 抓取方案均来源于对它的源码研究，见 [docs/参考项目研究笔记.md](docs/参考项目研究笔记.md)
- [openschoolcn/zfn_api](https://github.com/openschoolcn/zfn_api) —— 新版正方接口规格（登录 RSA、`xqm=学期²×3` 等）
- [Chiu-xaH/HFUT-Schedule](https://github.com/Chiu-xaH/HFUT-Schedule) —— 液态玻璃底栏设计参考：
  悬浮胶囊轨道 + 独立透镜指示器 + 拖动吸附 + haze 渐进模糊；本项目复现并向更不透明、更强模糊方向调整，
  见 [docs/参考项目研究笔记.md §3](docs/参考项目研究笔记.md)
- WakeUp 官方文档 —— CSV 导入模板格式

合规说明：本项目仅与用户**本人**的教务账号交互以导入其个人课表，不存储密码、不上传任何数据到任何服务器。

## License

本项目以 [GPL-3.0](LICENSE) 协议开源。
