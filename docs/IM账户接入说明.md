# IM 账户接入说明

## Java 包结构

共享生产代码位于 `im-core/src/main/java/com/dodosupermarket/im`，按职责分包；文件目录和 `package` 声明一致。`web-starter` 仅保留当前应用的身份装配与业务 DAO 适配器；后台通过 Maven 依赖复用相同核心。

| 相对包名 | 类及职责 |
| --- | --- |
| `controller` | `ImController`、`ImCapabilityController`：HTTP 会话、消息与能力接口 |
| `config` | 共享 `ImConfiguration`、`ImProperties`：Bean 装配与 IM 配置；web-starter 的 `ImAppConfiguration`：应用身份／目录装配 |
| `service` | `ImAccountService`、`ImSendService`、`ImImageService`、`ImMessageContent`：账户权限、发送、附件及引用 / 卡片处理；`ImBusinessCatalog`、`ImIdentityProvider`：宿主适配接口 |
| `service.logic`（web-starter） | `ImDaoBusinessCatalog`：复用业务 DAO 的目录实现 |
| `dao` | `ImStore`：IM 会话、租约、发送台账及附件元数据持久化 |
| `client` | `WuKongProductClient`：悟空 HTTP API 客户端 |
| `exception` | `ImException`：IM 错误码及状态 |

`ImMessageContent` 包含消息权限与内容处理逻辑，属于服务，不是 DTO。附件 SQL 收拢到 `ImStore`，其 `JdbcTemplate` 为私有字段；服务不直接访问 JDBC。测试 fixture 仅位于测试源，使用新包导入生产类。接口路径、配置键、Bean 名称和数据库表保持不变。

## 账户与会话

`web-starter/src/main/java/com/dodosupermarket/im` 是账户关联模块，默认 `dudu.im.enabled=false`。普通账户默认拥有自己的会话；`agent-scopes` 服务端配置指定客服账户和客户范围，登录后自动生效，没有页面授权步骤或新的客服登录系统。不替换旧 `WxMsgLogController`，不改变现有 `session_user` 模型。

身份从已有 `X-Auth-Token` 对应的 Redis HTTP Session 取得；每账户一个持久会话，每连接一个独立租约与传输 UID。历史每次检查账户与租约，直接群发送由悟空成员控制。SDK Token 不写数据库、日志或提交配置；账户、原 Session、租约与 UID 映射存专用 MySQL 表。

当前会话列表包含自己的会话及预分配客服会话；session 可选 conversationId，仍严格检查归属或服务端范围。租约保存 sender_role，历史角色不接受消息 payload 自报。客户 / 客服各自已读；`im_participant_read` 保存非会话所有者游标。

Token TTL 依赖 Java 任务检查 Session / 截止时间、移除群成员及清 Token，不具备 Java 停机期间的硬实时失效保证。成员更新关闭频道后增量删除 / 添加；固定 tokenless `history_<conversationId>` 成员供 Java 校验权限后查询从首次初始化起的历史，避免悟空 JoinSeq 随租约变化隐藏旧消息。外部写入后事务失败的 UID 保留持久撤销意图。

## 消息、附件与卡片

- 文字发送台账为 `im_send`，接口为 `/send`、`/sends/{number}`。先独立提交意图，再使用悟空 beta.18 HTTP 发送和精确查询；稳定的服务器 publisher 身份不发放 Token，传输租约变化不改变去重身份。相同编号不同内容返回 409；超过 7 天的未知发送只核对、不重放。
- 私有图片附件和图片发送使用 `im_attachment`（已并入 `db/test/02-im.sql`）。`dudu.im.attachment-directory` 配置为空时图片禁用；启用后须为私有持久卷，不能映射静态资源。每文件 1 MiB、JPEG/PNG 实际内容校验、重编码去元数据、尺寸 / 滚动上传限制；仅本人上传的当前会话附件可发送，对方下载须有 accepted 台账且当前授权 / 租约有效。
- 引用文字回复与可信只读商品 / 订单卡：`ImDaoBusinessCatalog` 复用既有 UserDAO / GoodsDAO / OrderDAO，商品按会话客户数据库门店、订单按会话客户 userId 校验；卡片只有名称 / 订单号和状态，不执行任何业务动作。新增 `dudu.im.cards-enabled`，默认 false，完整应用业务 DAO 可用且真实权限范围核对后启用。
- `GET /api/v1/im/unread` 沿用可信应用 Session 和 `agent-scopes`，无需 IM 租约，只统计对方消息，不修改已读。返回总 `unreadCount` / `countIsExact` 和各会话摘要。MVP 每会话最多扫描 1000 条、计入 100 条，超限是不完整计数，上线前应使用服务端消息索引与缓存。

## 配置

配置前缀 `dudu.im`：`enabled`、`jdbc-url`、`jdbc-username`、`jdbc-password`、`product-url`、`server-url`、`lease-seconds`、`sweep-ms`、`attachment-directory`、`cards-enabled`。密码用环境占位，不能提交明文。独立数据源的目标由 `dudu.im.jdbc-url` 明确指定，不改原业务与日志路由。前后台的 local 配置均默认引用 `spring.datasource.dynamic.log.writeDataSource.url`，可由 `DUDU_IM_JDBC_URL` 覆盖；仓库内其他环境未配置或启用此 IM 连接，部署启用时须明确指定同一个可写数据库。五张 IM 表共用这一连接；卡片引用的商品、订单等仍通过原业务 DAO 查询。悟空服务自身消息存储和附件文件不属于这五张 MySQL 表。

`dudu.im.lease-seconds` 默认 `86400`（24 小时），允许范围为 5～86400 秒；前后台共用这一默认值，外部显式配置仍优先生效。管理工作台在到期前约 1 分钟换证。延长凭证有效期不会延长业务登录会话；登录失效或客服权限撤销后，原有请求校验和租约巡检仍会撤销访问。更新共享构件并重启应用后，新申请的凭证使用新有效期，已有凭证的到期时间保持原值。

## 建表与部署

启用前需要显式应用 `db/test/02-im.sql`（已合并 `im_send`、`im_attachment` 及巡检索引，生产首次建表只需执行这一个文件）。早期结构由该文件按需补齐 `sync_required`、`sender_role` 及巡检索引；启动不执行 DDL。专用 JDBC 为主库连接，不经业务框架读写分离；会话行锁序列化成员更新。HTTP 上游失败记录修复意图，后台重放期望成员；HTTP 与数据库不属于同一事务。

## 上线限制与风险

- Product HTTP 必须位于可信内网。台账记录包含消息正文，不包含 IM / 应用 Token；须纳入访问控制与备份。
- MySQL 和悟空消息 / 去重数据必须一起制定备份与保留策略；若单独恢复、重建或清理悟空数据，先停止未确认发送的重放并人工核对，不能仅凭一次空查询判定原消息未发送。后端本身没有数据库灾备检测能力。
- 客户端须与新增发送 API 配套部署，没有失败后切换 SDK 发送的降级路径。
- 文字 / 图片台账、私有附件和持久 Outbox 已实现；门店权限继承、可信卡片、旧历史合并、加拿大 WSS / Push 尚未实现。图片回收、全局容量监控、多实例共享存储、照片方向与格式兼容仍是上线门槛。
- 上线前需要审视历史身份、Product HTTP 内网隔离和权限范围；旧来源迁移尚未完成。
