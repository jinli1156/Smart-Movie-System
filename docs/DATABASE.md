# 数据库脚本指南

数据库脚本统一位于 `movie-ticket-backend/src/main/resources/db/`。后端目录下的 `schema.sql` 和 `data.sql` 只是兼容入口，实际内容仍由该目录维护。

## 脚本清单

| 脚本 | 用途 | 是否会清空数据 |
| --- | --- | --- |
| `schema.sql` | 新建 `movie_ticket` 并重建全部业务表 | **会** |
| `data.sql` | 重置并写入演示账号、影院、影厅、电影和场次 | **会** |
| `migration_v2.sql` | 早期历史库统一升级 | 否 |
| `migration_v3_schema_alignment.sql` | 历史库字段与当前 Java 实体对齐 | 否 |
| `migration_v4_user_coupon.sql` | 优惠券及订单优惠字段 | 否 |
| `migration_v5_latest_movies.sql` | 幂等补充近期影片 | 否 |
| `migration_v6_hybrid_recommendation.sql` | 幂等补充偏好与用户行为表 | 否 |
| `test_showtimes_2026_09.sql` | 可选的 2026-08-30 至 2026-09-30 功能测试排片 | 否 |

## 新建演示库

在仓库根目录执行：

```powershell
mysql -u root -p < movie-ticket-backend/src/main/resources/db/schema.sql
mysql -u root -p < movie-ticket-backend/src/main/resources/db/data.sql
```

如需补充近期影片与指定日期测试排片：

```powershell
mysql -u root -p < movie-ticket-backend/src/main/resources/db/migration_v5_latest_movies.sql
mysql -u root -p < movie-ticket-backend/src/main/resources/db/test_showtimes_2026_09.sql
```

新库的 `schema.sql` 已包含优惠券、推荐偏好和行为表，无需再执行 v2、v3、v4、v6。

## 升级已有数据库

1. 先导出完整备份。
2. 确认当前库最后执行过的版本。
3. 对非常早期的库从 v2 开始；已经完成 v2 的库从 v3 开始。
4. 按数字顺序执行到 v6，不要跳过中间版本。
5. 启动后端并回归登录、下单、支付、退票、取票、评价、推荐功能。

示例：

```powershell
mysql -u root -p < movie-ticket-backend/src/main/resources/db/migration_v3_schema_alignment.sql
mysql -u root -p < movie-ticket-backend/src/main/resources/db/migration_v4_user_coupon.sql
mysql -u root -p < movie-ticket-backend/src/main/resources/db/migration_v5_latest_movies.sql
mysql -u root -p < movie-ticket-backend/src/main/resources/db/migration_v6_hybrid_recommendation.sql
```

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

## 安全约束

- 不要在需要保留数据的环境执行 `schema.sql` 或 `data.sql`。
- 不要把数据库密码写入 SQL、README 或 Git；使用 `DB_PASSWORD`。
- 测试账号与固定密码只用于本地演示。
- 生产执行迁移前必须备份，并在同版本的测试库先演练。
