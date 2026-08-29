# 智能电影购票系统

基于 SpringBoot 3.x + Vue 3 + TypeScript 的智能电影购票系统（本科毕业设计）。

## 模块

| 子项目                  | 端口 | 说明                                                        |
| ----------------------- | ---- | ----------------------------------------------------------- |
| `movie-ticket-backend/` | 8080 | SpringBoot + MyBatis-Plus + Spring Security + Redis + MySQL |
| `movie-ticket-user/`    | 3000 | Vue 3 + TS + Element Plus，购票 / 取票 / 个人中心           |
| `movie-ticket-admin/`   | 3001 | Vue 3 + TS + Element Plus + ECharts，管理后台               |

## 核心能力

- 智能推荐（热门 / 个性化 / 相似），数据统计 Dashboard
- 完整购票流程：选座（Canvas）→ Redis 锁座 → 下单 → 支付 → 取消
- 取票：支付自动出 8 位取票码 + 二维码、摄像头扫码或手输
- 管理端：影院 / 电影 / 排片 / 订单 / 用户 五大模块 + 图片上传
- 详细设计、接口、模块说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## 启动

环境：JDK 17+、MySQL 8.0、Redis 7.0（dev profile 自带 embedded-redis）、Node 18+、Maven 3.8+。

```bash
# 1. 初始化数据库
mysql -u root -p < movie-ticket-backend/src/main/resources/db/schema.sql
mysql -u root -p movie_ticket < movie-ticket-backend/src/main/resources/db/data.sql

# 2. 启动后端（8080）
cd movie-ticket-backend && mvn spring-boot:run

# 3. 启动用户端（3000）
cd movie-ticket-user && npm install && npm run dev

# 4. 启动管理端（3001）
cd movie-ticket-admin && npm install && npm run dev
```

若数据库中已经有用户和订单数据，不要执行上述重建脚本。请先备份，再单独执行：

```bash
mysql -u root -p movie_ticket < movie-ticket-backend/src/main/resources/db/migration_v3_schema_alignment.sql
```

详细步骤、常见问题、迁移脚本见 [docs/QUICK_START.md](docs/QUICK_START.md)。

## 测试账号

| 用户名       | 密码     | 角色         |
| ------------ | -------- | ------------ |
| admin        | admin123 | ADMIN        |
| cinema_admin | admin123 | CINEMA_ADMIN |
| user1        | admin123 | USER         |

## 文档

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 系统架构、数据库、接口清单、功能模块
- [docs/CHANGELOG.md](docs/CHANGELOG.md) — 变更记录与进度
- [docs/QUICK_START.md](docs/QUICK_START.md) — 启动指南
- [docs/superpowers/specs/2026-05-16-movie-ticket-system-design.md](docs/superpowers/specs/2026-05-16-movie-ticket-system-design.md) — 原始设计文档

## 许可证

MIT
