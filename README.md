# framework-java

基础框架与嘟嘟共享业务的公共 Maven 项目。前台和后台通过 Maven 构件引用共享业务，公共模块不依赖任何一端的父 POM。

| 目录 | Maven 坐标 | 职责 |
| --- | --- | --- |
| business-core | com.dodosupermarket:dodo-business-core:1.0.0 | 晒单、补货提醒、榜单规则、DAO 与 Mapper |
| im-core | com.dodosupermarket:dodo-im-core:1.0.0 | IM 会话、消息、附件、业务卡片协议 |
| survey-core | com.dodosupermarket:dodo-survey-core:1.0.0 | 问卷定义、发布、凭证、答卷及 SurveyJS 静态资源 |
| db | — | 共享业务、IM、埋点和榜单的手工数据库脚本 |
| docs | — | 共享业务设计、接口契约与接入说明 |

原有 framework-starter、db-config-starter 等基础模块继续在本项目维护。共享业务沿用原坐标和 Java 包名，应用继续负责身份、租户、业务目录与接口适配。

## 本地构建与两端引用

在本目录执行（JDK 11+、Maven；私有依赖需已安装或配置内部仓库）：

```powershell
mvn -pl business-core,im-core,survey-core -am -Dmaven.test.skip=true clean install
```

随后分别在 `D:/code/dodo-supermarket-frontstage`、`D:/code/dodo-supermarket-backstage` 执行：

```powershell
mvn -Dmaven.test.skip=true clean package
```

两端父 POM 的 `dodo.business.version` 统一控制这三个依赖的版本。`-am` 只能构建当前 Maven reactor 中的模块，应用仓库内不再构建共享源码；修改公共业务后先重新 install，再构建两端。其他机器与 CI 需先安装公共构件及 `pers.binaryhunter:framework-starter-parent:1.0.0`，或将它们发布至既有内部 Maven 仓库。

## 验证与恢复

```powershell
mvn -pl business-core,im-core,survey-core -Dmaven.test.skip=false -DskipTests=false clean test
```

`business-core` 默认编译并运行 H2 单元测试。MySQL 专项测试需要显式本机隔离环境，未配置时跳过。MySQL 脚本测试在 `business-core/src/test/scripts/`；`rank-migration-mysql.cjs` 默认核对相邻 `dodo-supermarket-frontstage` 仓库的门店 Mapper，非相邻布局可指定 `DODO_FRONTSTAGE_ROOT`。

若应用仍读取旧代码，先重建公共构件，再对应用执行 clean package；依赖缺失时先确认私有依赖和本地 Maven 仓库配置。构建不会连接生产环境，也不会自动执行数据库迁移。

共享建表和菜单 SQL 统一维护在 `db/test/`，随 `business-core` JAR 以相同路径发布。旧版重复的分散脚本已移除，应用启动不自动执行 DDL，SQL 由用户在数据库工具中执行。

目标库、执行顺序、顶部选项、历史数据影响和失败处理见 [SQL 执行说明](db/test/SQL执行说明.md)。

| 文件 | 目标库与内容 |
| --- | --- |
| `01-business.sql` | 已有业务主库：晒单、补货、问卷完整建表及旧表更新 |
| `02-im.sql` | 实际 IM 库（默认日志库）：完整建表、历史字段及索引更新 |
| `03-menu.sql` | 业务主库：IM、晒单、问卷、补货菜单和原有角色关联 |
| `04-analytics-business.sql` | 业务主库：付款状态台账、扫描游标、旧表升级、订单索引和行为明细菜单 |
| `04-analytics-log.sql` | 日志主库：完整事件表、事件 ID 精确幂等升级和门店接收时间索引 |
| `05-rank.sql` | 业务主库：五张榜单完整表、历史升级、榜单菜单及角色关联；默认 `migrate` 可重复执行，不含模拟数据 |

Analytics 已拆为业务主库和日志主库两个独立文件，分别连接对应数据库后整份执行，无需选择库类型。两份 analytics SQL 均无需修改选项或存储过程权限。台账、事件和扫描进度保持原值。

Rank 可将顶部 `rank_action` 改为 `check`（阻断问题与详细诊断）、`preview`（排序预览）、`status`（结构状态）。这些模式不修改业务表；也可只选中对应 BEGIN/END 标记内的 SELECT 执行。整份 05-rank.sql 需 CREATE ROUTINE 权限，临时过程在成功执行后移除；两份 analytics SQL 不需要存储过程权限。旧榜单迁移先备份并停写，DDL 隐式提交，遇错停止后用 status 检查，不要忽略错误继续。

2026-11-09：上述三个模块及对应 SQL、专题文档从前台仓库迁入。历史验证记录中的应用日志仍位于原前台仓库的 logs/，未迁移日志或旧构建产物作为源码。前台收藏脚本继续在原仓库 db/；analytics 与 rank SQL 统一到上述文件。
