# 嘟嘟内嵌问卷核心

Java 11 / Spring Boot 2.3 共享模块，供现有客户端服务和管理服务使用。前端使用 MIT 授权的 SurveyJS Form Library 3.2.1，题目编辑器属于嘟嘟自身代码。不依赖 Survey Creator、Formbricks、云端问卷服务或运行时 CDN。

## 集成与构建

1. 在两个服务共用的**主业务库**执行公共工程的 `db/test/01-business.sql`（从本模块目录访问为 `../db/test/01-business.sql`）。该文件包含问卷 4 张表的完整定义、旧表游客字段和去重索引升级及中文注释更新，保留历史用户和未过期凭证归属，可以重复执行；同时包含晒单、补货业务的建表和更新；榜单统一执行同目录的 `05-rank.sql`。程序不会自动建表。后台菜单执行同目录的 `03-menu.sql`，不再单独维护问卷 SQL。
2. 生成静态资源并同步到管理前端：

```powershell
cd D:/code/framework-java/survey-core/web
npm ci --ignore-scripts
npm run build -- --admin-root D:/code/dodo-supermarket-backend
cd ../..
mvn -pl survey-core -am install
```

3. 两个 `web-starter` 已依赖 `com.dodosupermarket:dodo-survey-core:1.0.0`。分别按原有方式编译和启动；CI 应先构建此模块并发布到内部 Maven 仓库，或在相同构建环境安装该模块。IM 与 business-core 同样在公共工程安装，完整流程见 ../README.md。
4. 管理前端按原有 `npm run build` 构建；预览页开发路径为 `/static/survey/index.html#preview`，生产产物路径为 `/survey/index.html#preview`。
5. 客户端答题页由已有客户端 Java 服务提供 `/survey/index.html`。反向代理应将该路径及其子资源转发到客户端服务。无需新增独立服务或端口。

数据库连接默认复用 `spring.datasource.dynamic.default.writeDataSource.url/username/password`，在主库上使用一个最多 5 连接的专用池，确保发布、答卷校验和行锁不会被路由到只读副本。特殊环境可通过 `dudu.survey.jdbc-url/jdbc-username/jdbc-password` 覆盖。凭据只放入已有的环境配置，不写入代码。

## 业务规则

- 现有 ADMIN 角色负责全局问卷管理；选择所有门店或一个指定门店。答卷和统计仅管理接口可读取。
- 草稿保存采用 revision 乐观锁；发布时复制为不可变版本。编辑草稿不改变线上题目、投放范围或时间。列表会提示未发布修改。
- 问卷提供单选、多选、下拉、短文本、长文本、1–5 评分和是非题。每份 1–60 题，每道选择题 2–30 项。条件显示仅允许引用此前的单选或下拉答案。
- 定义契约是 `title/description/storeId/startsAt/endsAt/questions`。时间戳单位为毫秒，0 表示不限；`storeId=0` 表示全部门店。题目 `name` 不可重复；需要条件显示时传 `condition:{question,equals}`。
- H5 分享链接可免登录填写。有效 Session 中的用户 ID 和门店取自服务端，忽略传入的 userId/storeId；缺少或失效的会话按游客处理。游客使用浏览器本地保存的 32 位标识，不将游客标识当作登录凭据。每个账号或游客浏览器对同一问卷跨版本仅可提交一次；清除浏览器存储或换设备会形成新的游客，无法据此识别同一个自然人。
- 登录用户继续校验当前门店范围。游客没有可信的当前门店，指定门店的分享问卷归属已发布的目标门店；全部门店问卷的游客门店记为 0（未知），不采信客户端传入的门店。
- 答卷明细及 CSV 区分登录用户与游客，记录用户 ID 或游客标识。身份在签发答题凭证时绑定；填写页监听登录、登出及会话失效后重新签发凭证，公开填写页不会因后台初始化请求的会话失效被强制跳到登录页。
- App 获取两小时有效的随机答题凭证，数据库仅保存 SHA-256。凭证放在 URL fragment，通过 Authorization 头提交；长期登录令牌不会进入 WebView URL。答题页不使用本地存储持久化凭证或答案。
- 答题和提交均重新检查发布状态、版本、门店和收集时间。暂停或重新发布会阻止旧页面继续提交；网络重试会返回首次成功的答卷 ID。
- 服务端独立校验必填、题型、选项和显示条件，删除隐藏题答案，拒绝未知字段。SurveyJS 布尔题的 `false` 是有效答案。
- `schema.duduConditions` 是本项目运行时的变量绑定表；页面在创建 SurveyJS Model 后通过 `setVariable` 注入并移除该元数据，再渲染。条件表达式不拼入选项文本，因此引号和反斜杠不会改变表达式含义。后台预览使用同一方式。
- 问卷有草稿、已发布、暂停、归档四种状态；归档不可恢复，历史答卷仍保留。
- 按发布版本查询答卷和统计。统计读取本版本全部答卷，文字题展示最近 20 条；CSV 单次最多 10000 份、约 1000 万字符，含 UTF-8 BOM，并防护电子表格公式注入。

## 接口

管理服务（原有管理登录 + ADMIN）：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/survey/admin?page=1&size=20&search=` | 列表 |
| POST | `/api/survey/admin` | 保存 `{id?,revision?,definition}` |
| GET | `/api/survey/admin/{id}` | 草稿及发布版本 |
| POST | `/api/survey/admin/{id}/publish` | 发布 `{revision}` |
| POST | `/api/survey/admin/{id}/state` | 暂停/归档 `{revision,status}` |
| GET | `/api/survey/admin/{id}/responses?version=1&page=1&size=20` | 答卷 |
| GET | `/api/survey/admin/{id}/stats?version=1` | 统计 |
| GET | `/api/survey/admin/{id}/csv?version=1` | CSV 文件名及文本 |

客户端服务：

| 方法 | 路径 | 鉴权 |
|---|---|---|
| GET | `/api/survey/client?page=1&size=20` | 原有客户 Session |
| POST | `/api/survey/client/{id}/ticket?guestId=<32位小写十六进制>` | 可选客户 Session；游客必须提供浏览器标识，登录用户忽略此参数 |
| GET | `/api/survey/fill` | `Authorization: Bearer <ticket>` |
| POST | `/api/survey/fill`，`{answers:{...}}` | 同上 |

问卷接口统一不缓存。MySQL 表不含手机号、姓名或账号密码，仅存已有用户/门店 ID 和用户填写的答案。原有全局压缩过滤器在问卷 API 路径下跳过，避免破坏非 200 的错误响应。

## 本地验收

详见 App 仓库 `docs/全局问卷系统实施与验证.md`。不包含生产数据库操作。Android/iOS 真机和微信小程序业务域名需在发布前分别验收；本轮覆盖 Java、管理前端构建、App H5 编译、实际 UTS 控制器及浏览器填答闭环。

第三方授权原文及构建指纹保留在 `static/survey/vendor/LICENSE.txt` 和 `manifest.json`。升级 SurveyJS 后必须重新生成两个服务及管理前端静态产物，并重跑条件题、提交和布局验收。
