# 智能电影购票系统

基于 Spring Boot 3、Vue 3 和 MySQL 的智能电影购票系统，包含用户端、管理端与后端服务。当前核心业务链路、混合推荐、AI 电影助手、智能选座和智能排片均已实现，三端能够通过构建与单元测试。

## 当前状态

综合完成度约 **88%**（评估日期：2026-08-31）。核心功能已基本完成，剩余工作集中在端到端测试、部署交付、日志/系统设置和论文实验数据沉淀。评分依据与待办见 [项目完成度报告](docs/PROJECT_STATUS.md)。

| 模块 | 技术栈 | 默认端口 | 状态 |
| --- | --- | ---: | --- |
| `movie-ticket-backend/` | Spring Boot 3.2、Java 21、MyBatis-Plus、Security、Redis、MySQL | 8080 | 12 项测试通过 |
| `movie-ticket-user/` | Vue 3、TypeScript、Element Plus、Pinia | 3000 | 生产构建通过 |
| `movie-ticket-admin/` | Vue 3、TypeScript、Element Plus、ECharts | 3001 | 生产构建通过 |

## 已实现能力

- 用户体系：注册登录、JWT 权限、个人资料、修改密码、优惠券、想看列表。
- 购票闭环：电影/影院/场次浏览、智能选座、Redis 锁座、订单确认、模拟支付、取消与退票。
- 观影服务：二维码/取票码、扫码取票、观影后评价与电影评分回写。
- 智能能力：热门/个性化/相似混合推荐、冷启动偏好、AI 电影助手、座位推荐、智能排片预测与模拟。
- 管理后台：电影、影院、影厅、场次、订单、用户、Dashboard、票房统计与智能排片。

## 快速开始

环境要求：JDK 21、MySQL 8.0+、Node.js 18+。后端已提供 Maven Wrapper，无需预装 Maven；开发环境默认可启动内嵌 Redis。

```powershell
# 1. 初始化数据库（该数据脚本会清空并重建演示数据）
mysql -u root -p < movie-ticket-backend/src/main/resources/db/schema.sql
mysql -u root -p < movie-ticket-backend/src/main/resources/db/data.sql

# 2. 后端
cd movie-ticket-backend
.\mvnw.cmd spring-boot:run

# 3. 用户端（新终端）
cd movie-ticket-user
npm ci
npm run dev

# 4. 管理端（新终端）
cd movie-ticket-admin
npm ci
npm run dev
```

默认数据库账号为 `root / 123456`，仅适合本机演示。通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET` 配置实际环境；AI 助手需额外设置 `DASHSCOPE_API_KEY`。不要把真实密钥写入仓库。

详细说明见 [启动与验收指南](docs/GETTING_STARTED.md) 和 [数据库脚本指南](docs/DATABASE.md)。

## 测试账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | ADMIN |
| `cinema_admin` | `admin123` | CINEMA_ADMIN |
| `user1` | `admin123` | USER |

以上账号仅用于本地演示。

## 项目目录

```text
movie-ticket-system/
├── movie-ticket-backend/     # 后端、测试、数据库脚本与演示海报
├── movie-ticket-user/        # 用户端
├── movie-ticket-admin/       # 管理端
├── docs/                     # 当前文档、设计稿、功能说明与历史归档
├── scripts/                  # 辅助检查脚本
├── .env.example              # 环境变量模板（不含真实凭据）
└── README.md
```

## 文档导航

- [项目完成度报告](docs/PROJECT_STATUS.md)
- [启动与验收指南](docs/GETTING_STARTED.md)
- [数据库脚本指南](docs/DATABASE.md)
- [系统架构与接口](docs/ARCHITECTURE.md)
- [推荐算法评估说明](docs/RECOMMENDATION_EVALUATION.md)
- [系统设计文档](docs/design/SYSTEM_DESIGN.md)
- [取票功能说明](docs/features/TICKET_PICKUP.md)
- [变更记录](docs/CHANGELOG.md)

## 验证

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

脚本依次运行后端测试、管理端构建和用户端构建；任一步失败都会返回非零退出码。

## 许可证

MIT
