# REA（逆向工程分析）方法论：以逆向手段扩展教务系统适配

> 用途与边界：本文档描述的逆向方法仅用于**个人学习**与**与本人教务账号的数据互操作**
> （导入自己的课表），不用于绕过权限、批量抓取、攻击或破坏目标系统。课表 App 的适配本质上是
> "合法登录后的页面结构分析"，与 WakeUp、zfn_api 等开源项目采用的方法一致。

REA = Reverse Engineering Analysis。当一个学校的教务系统既没有开源实现、也不在任何已有
适配列表里时，用以下四条路径拿到"接口规格"，再按《教务系统适配指南》第二节新增适配器。

## 路径 0：先查开源库（成本最低，绝大多数系统不需要真逆向）

1. WakeUp 支持列表：`WakeupSchedule_BUPT/app/src/main/java/com/suda/yzune/wakeupschedule/
   schedule_import/SchoolListActivity.kt` 里有 **120+ 所高校的 URL → 教务类型映射表**，
   先确认目标学校使用的系统类型与入口 URL。
2. GitHub 搜索：`{学校名} 教务 课表 python` / `{系统名如 青果} api`——正方/强智/URP/青果
   均有大量开源爬虫可直接对照（如本项目引用的 zfn_api）。

## 路径 1：浏览器 DevTools 抓包（不需要任何工具，首选）

1. Chrome 打开教务系统 → F12 → Network → 勾选 Preserve log。
2. 手动登录一次、打开课表页，观察：
   - 登录 POST 的 URL / 表单字段名 / 密码是否密文（在 Sources 里搜字段名定位加密 JS）；
   - 课表数据的请求 URL、参数（xnm/xqm/gnmkdm 等）、响应是 JSON 还是整页 HTML；
   - Cookie 名称（JSESSIONID / route 等）。
3. HTML 型课表：右键课表表格 → 检查 → 记下 table 的 id/class、单元格内字段的组织方式
   （title 属性？`<br>` 分行？`{}` 时间块？）。

## 路径 2：对"现有课表 App"做 REA（用户指定路径）

对已有课表 App 的 APK 逆向，可以直接提取它的适配代码/接口表：

```bash
# 1. 拆包（资源+清单）
apktool d target.apk -o target_apktool

# 2. 反编译为 Java 源码（可读性最高）
jadx -d target_jadx target.apk
# 提示： WakeUp 类 App 混淆较轻，jadx 直接可读

# 3. 关键词定位适配层
grep -rn "kbtable\|xskbcx\|LoginToXk\|gnmkdm\|VIEWSTATE" target_jadx/sources | head -50
grep -rn "showSource\|JavascriptInterface" target_jadx/sources   # WebView 抓取通道
```

关注四类信息：
- **URL 常量表**：学校/系统 → 接口地址（WakeUp 在 SchoolListActivity）；
- **解析器**：Jsoup 选择器与正则（`getElementById("kbtable")`、`weekPattern` 等）；
- **WebView 注入 JS**：如何收集 iframe HTML（本项目照此实现了 WebViewGrabActivity）；
- **加密算法**：登录密码加密（如强智 `encoded`、青果 `chkpwd` 的 MD5 前 30 位大写拼接），
  通常在 assets 或混淆后的类里，可整段 JS 拷出来喂给 JS 引擎。

> 本项目实测：`xianfei/WakeupSchedule_BUPT` 的源码在 GitHub 公开，等价于"已经反编译好"的
> 参考资料，优先直接读源码，省去 apktool/jadx 步骤。研究结论见《参考项目研究笔记》。

## 路径 3：加密 JS 的处理技巧（模拟登录的最后一公里）

1. DevTools → Sources → 搜密码字段名 → 断点看加密函数输入输出；
2. 若是整段混淆 JS（强智常见）：把 `<script>` 里相关函数完整复制，保存为 `.js`，
   在 App 内用 WebView `evaluateJavascript` 或 Rhino 执行——**不要尝试人工重写**；
3. 若是 RSA：通常 modulus/exponent 由接口下发（见 zf_new），用标准库实现即可。

## 路径 4：把逆向结果变成代码

固定流水线（对应本项目目录）：

```
规格（URL/字段/响应结构）
  → XxClient.kt      登录会话（SchoolHttpClient，Cookie/GBK 自动处理）
  → XxParser.kt      纯函数解析（WeekTextParser/NodeTextParser 处理周次节次）
  → SchoolSystem 枚举 + ImportViewModel 分支接线
  → 真实页面样本 → src/test/resources → 单元测试锁定行为
  → 同时把页面标记词加进 WebViewGrabActivity.autoProbe 作为兜底
```

## 合规清单（每次逆向前过一遍）

- [ ] 只用**自己的**账号登录与抓取自己的数据；
- [ ] 不破解验证码识别服务、不做批量请求/压测（加 1-2 秒间隔，只拉一页课表）；
- [ ] 不公开传播带真实学号/姓名的页面样本（测试夹具须脱敏）；
- [ ] 尊重目标系统 robots/使用条款；逆向成果仅用于互操作，不用于绕过付费或权限。
