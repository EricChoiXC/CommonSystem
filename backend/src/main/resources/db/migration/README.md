# db/migration 存档说明

## 停用声明

本目录自 2026-11-17（任务 F19-BE-010）起**停用**：Flyway 依赖（`flyway-core` / `flyway-mysql`）与 `spring.flyway.*` 配置段已从工程移除，本目录下 21 个脚本（`V1_01_001` ~ `V1_01_046`，缺号为 ERP 侧序号）保留为**只读历史存档**，不再被任何工具执行，**禁止新增脚本**。

## 表结构变更口径

- 表结构变更一律通过实体注解（`@TableSource` / `@ColumnField` / `@TableIndex`）声明，由 `common.source` 注解引擎在启动时按 `CS_SOURCE_SYNC_MODE` 三模式（VALIDATE / INSTALL / APPLY）同步到数据库；
- 数据回填通过 `SourceDataMigration` SPI 承接（实现类登记于 `SysStartupTask` 启动任务，引擎先建表、再执行迁移，先结构后数据）。

## `flyway_schema_history` 留档口径

存量库中该表原样保留、留档不读写（注解引擎与业务代码均不触碰）；全新库不再生成该表。

## 数据类语句去向备查

21 个脚本中仅 2 条数据类语句（UPDATE），去向如下（登记清单见任务清单 §6「存量数据类语句迁移登记清单」）：

| 脚本 | 语句 | 去向 |
| --- | --- | --- |
| `V1_01_038__sys_process_template_default_backfill.sql` | 1 条 UPDATE（流程模板默认回填） | 已由 DataMigration 首例 `DM-20261117-001`（`SysProcessTemplateDefaultBackfillMigration`）等价承接 |
| `V1_01_019__sys_process_template_default.sql` | 1 条 UPDATE（流程模板默认置位） | 一次性引导回填：现网库已随 Flyway 执行完毕、新库冷启动建表后无模板数据为空操作，登记备查（其守护版语义已由 `DM-20261117-001` 承接） |
