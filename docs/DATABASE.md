# 数据库脚本指南

数据库脚本统一位于 `movie-ticket-backend/src/main/resources/db/`。业务表结构以 `db/flyway/` 中的版本化迁移为唯一来源；后端目录下的 `schema.sql` 和 `data.sql` 只是兼容入口。

## 脚本清单

| 脚本 | 用途 | 是否会清空数据 |
| --- | --- | --- |
| `schema.sql` | 仅创建空的 `movie_ticket` 数据库 | 否 |
| `data.sql` | 事务重置全部演示业务数据，生成执行当天至未来第 10 天的排片 | **会** |
| `flyway/V1__baseline_schema.sql` | 新库的 v6 基础表结构 | 否 |
| `flyway/V7__profile_soft_delete_and_query_indexes.sql` | 用户资料、软删除字段和查询索引 | 否 |
| `flyway/V8__relational_integrity.sql` | 数据预检、外键和检查约束 | 否 |
| `flyway/V24__align_legacy_schema.sql` | 补齐旧库唯一取票码、角色索引、非空约束和字符排序规则 | 否 |
| `migration_v2.sql` | 早期历史库统一升级 | 否 |
| `migration_v3_schema_alignment.sql` | 历史库字段与当前 Java 实体对齐 | 否 |
| `migration_v4_user_coupon.sql` | 优惠券及订单优惠字段 | 否 |
| `migration_v5_latest_movies.sql` | 幂等补充近期影片 | 否 |
| `migration_v6_hybrid_recommendation.sql` | 幂等补充偏好与用户行为表 | 否 |
| `test_showtimes_2026_09.sql` | 可选的 2026-08-30 至 2026-09-30 功能测试排片 | 否 |

## 新建数据库

在仓库根目录执行：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE movie-ticket-backend/src/main/resources/db/schema.sql;"
```

启动后端后，Flyway 自动执行 V1、V7 至 V24。确认启动成功、迁移达到 V24 后，先停止后端，再在仓库根目录导入演示数据：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE movie-ticket-backend/src/main/resources/db/data.sql;"
```

导入完成后重新启动后端。PowerShell 使用上述 `--execute` 命令，不使用不受支持的输入重定向 `<`。后端根目录中的兼容入口也可在 `movie-ticket-backend` 目录用 `mysql -u root -p --execute="SOURCE data.sql;"` 执行。

新库不要手工执行 v2-v6 历史迁移；`test_showtimes_2026_09.sql` 只保留作历史测试数据，不作为当前初始化步骤。

### 演示数据与日期范围

- 三个演示账号 `user1`、`admin`、`cinema_admin` 的密码均为 `admin123`；影院管理员已关联影院 1，可直接登录管理所属影院。
- 包含 10 个影院、20 个影厅、40 部影片；普通用户有新人券，可用兑换码 `JINLI699` 另领一张券。优惠券和兑换码的有效期从脚本执行日计算。
- 按北京时间计算执行日期，覆盖当天至第 10 天，含首尾共 11 个自然日。例如 2026-10-26 执行，排片截至 2026-11-05；换一天执行会自动改变日期。
- 每个影厅每日最多四场，开场时间为 09:40、13:20、17:00、20:40；只安排已上映的热映影片，按实际片长计算结束时间，并至少留 20 分钟清场。
- 当天已过或距执行时刻不足 15 分钟的场次不会插入；深夜执行时从次日开始有可售场次。凌晨执行时最多生成 880 场。
- 这是手动执行时生成的演示排片，不会在后端每次启动时自动清库或滚动续排；保留数据的环境请用管理端新增排片。
- 全程启用外键检查；显式重置新增助手、兑换、草稿等业务数据，失败时回滚。脚本要求 V24 完成，并使用明确的种子 ID，不依赖自增计数被清零。

## 升级已有数据库

1. 先导出完整备份，并在同版本测试库演练。
2. 确认现有库已经具备历史 v6 结构；更早的库先按旧脚本补齐到 v6。
3. 直接启动后端。首次接管非空库时，Flyway 会建立版本 6 基线，然后执行 V7 及后续迁移至 V24；不要再手工执行这些迁移。
4. V8 会先把缺失用户或电影的推荐行为归档到 `migration_orphan_user_movie_behavior`，再从实时表移除。核心订单、场次、评价等出现孤儿记录或非法金额/状态时，迁移会中止且不会静默修复。
5. 修复预检报告的数据后重新启动，再回归登录、下单、支付、退票、取票、评价和推荐功能。

V24 在任何表结构变更前检查角色、状态、时间戳是否存在 NULL，以及目标排序规则下的用户名、订单号、取票码是否重复。发现问题时停止升级并给出原因，不会猜测业务值或删除订单。旧库额外的 `ticket_count` 保留，应用继续通过 `seat_count` 读写购票张数。

### 初始化回归测试

真实 MySQL 与 MySQL 命令行客户端可用时，在后端目录执行：

```powershell
$env:LOCAL_INFRA_TEST='1'
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify -Pintegration '-Dit.test=DemoDatabaseIT'
```

测试仅创建随机命名的独立数据库并自动删除，覆盖完整 Flyway 迁移、跨年份日期、非北京时间会话、重复重置、优惠券循环引用、事务回滚、旧库预检和结构升级。

## 表结构

| 表 | 说明 |
| --- | --- |
| `user` | 用户、角色、资料与类型偏好 |
| `cinema` | 影院及位置 |
| `hall` | 影厅与座位布局 |
| `movie` | 电影基础信息 |
| `showtime` | 场次、价格与余座 |
| `ticket_order` | 订单、支付、优惠、退票与取票状态 |
| `user_coupon` | 用户优惠券 |
| `comment` | 购票观影后的评价 |
| `user_movie_behavior` | 浏览与想看行为，用于混合推荐 |
| `coupon_code`、`coupon_code_redemption` | 兑换规则、发放计数与领取记录 |
| `assistant_conversation`、`assistant_turn` | 助手会话、回答及观影计划 |
| `assistant_preference` | 助手长期观影偏好 |
| `assistant_checkout` | 确认下单卡及幂等状态 |
| `scheduling_saved_draft` | 管理员暂存的排片草案 |

## 安全约束

- `schema.sql` 只负责创建空数据库，不会删除已有表；但不要在需要保留数据的环境执行 `data.sql`。
- 不要把数据库密码写入 SQL、README 或 Git；使用 `DB_PASSWORD`。
- 测试账号与固定密码只用于本地演示。
- 生产执行迁移前必须备份，并在同版本的测试库先演练。


### V17：电影助手会话持久化

- `assistant_conversation`：UUID主键、用户归属、标题、活动请求、生成租约及创建/更新时间；按用户和更新时间索引。
- `assistant_turn`：一轮用户问题与助手回答，包含请求标识、生成状态、回答来源、工具结果JSON、建议JSON及时间；`(conversation_id, request_id)`唯一。
- 会话删除时级联删除消息；用户身份从登录态捕获，所有会话接口限制当前账号。重启后端由Flyway自动升级。
