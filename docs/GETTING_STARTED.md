# 启动与验收指南

## 1. 环境准备

| 依赖 | 建议版本 | 必需 |
| --- | --- | --- |
| JDK | 21 | 是 |
| MySQL | 8.0+ | 是 |
| Node.js / npm | 18+ / 9+ | 是 |
| Redis | 7.x | 否；`dev` 配置默认使用内嵌 Redis |
| Maven | 3.9+ | 否；仓库已提供 Maven Wrapper |

先复制根目录 `.env.example` 中需要的变量到操作系统或 IDE 的运行配置。Spring Boot 不会自动加载该示例文件。

常用变量：

```text
DB_URL=jdbc:mysql://localhost:3306/movie_ticket?...
DB_USERNAME=root
DB_PASSWORD=你的数据库密码
JWT_SECRET=至少32位随机字符串
DEEPSEEK_API_KEY=你的DeepSeek密钥（使用电影助手时配置）
```

本机也可以在项目根目录 `.local/assistant.properties` 中设置 `DEEPSEEK_API_KEY`，后端会自动加载。未配置密钥时小助手明确提示服务未配置。详见 [电影小助手说明](MOVIE_ASSISTANT.md)。

## 2. 初始化数据库

新建空数据库：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE movie-ticket-backend/src/main/resources/db/schema.sql;"
```

启动后端时，Flyway 会自动创建或升级表、索引、外键和检查约束。按第 3 节启动后端，确认迁移达到 V24，然后停止后端，在仓库根目录执行：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE movie-ticket-backend/src/main/resources/db/data.sql;"
```

随后重新启动后端。演示排片按北京时间从执行当天生成至未来第 10 天（含首尾共 11 天），当天只生成距开场至少 15 分钟的场次，每厅每天最多 4 场。影片片长、影院归属和座位容量自动关联，兑换码 `JINLI699` 和新人券也可直接使用。

`data.sql` 会在事务中清空并重建全部演示业务数据，不可用于生产库或需要保留数据的库。已有数据库请先备份，然后直接启动后端，由 Flyway 从版本 6 基线接管。完整规则见 [DATABASE.md](DATABASE.md)。

## 3. 启动后端

```powershell
cd movie-ticket-backend
.\mvnw.cmd spring-boot:run
```

启动类为 `com.movie.ticket.MovieTicketApplication`，服务地址为 `http://localhost:8080/api`。

## 4. 启动前端

分别打开两个终端：

```powershell
cd movie-ticket-user
npm ci
npm run dev
```

```powershell
cd movie-ticket-admin
npm ci
npm run dev
```

- 用户端：`http://localhost:3000`
- 管理端：`http://localhost:3001`

## 5. 演示账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `user1` | `admin123` | 普通用户 |
| `cinema_admin` | `admin123` | 影院管理员 |
| `admin` | `admin123` | 系统管理员 |

执行新版演示数据脚本后，`cinema_admin` 已绑定影院 1，可直接登录。已有业务库升级不会自动分配影院；未分配的旧账号仍需由 `admin` 在“用户管理”中指定所属影院。重新登录可刷新菜单中的账号信息，后端权限在下一次请求即按最新角色、影院和禁用状态生效。详见 [影院权限与页面一致性](CINEMA_PERMISSIONS.md)。

## 6. 一键验收

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

脚本会执行：

1. `movie-ticket-backend/mvnw.cmd test`
2. `movie-ticket-admin/npm run build`
3. `movie-ticket-user/npm run build`

## 7. 建议演示流程

1. 使用 `user1` 登录用户端并设置偏好。
2. 浏览热门/个性化推荐，进入电影详情选择场次。
3. 使用智能选座，确认订单并完成模拟支付。
4. 展示取票二维码、订单列表和退票规则。
5. 使用 `admin` 登录管理端，展示 Dashboard、智能排片和各业务管理页面。
6. 在管理端“推荐算法评估”选择 K，运行实验并导出 JSON，结合 [推荐算法评估说明](RECOMMENDATION_EVALUATION.md) 展示论文指标。
7. 使用已绑定影院 1 的演示影院管理员，展示所属影院订单和取票码核销，再验证访问其他影院订单返回 403；旧账号需先分配影院。

## 8. 常见问题

### 数据库连接失败

确认 MySQL 已启动、`movie_ticket` 数据库已初始化，并检查 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。

### 端口被占用

后端端口在 `application.yml` 中配置；前端端口在各自 `vite.config.ts` 中配置。

### Redis 启动失败

本地默认 `dev` 配置会尝试启动内嵌 Redis。若 6379 已被其他服务占用，请复用现有 Redis 或调整端口配置。

### AI 助手提示服务不可用

检查运行后端的进程是否配置了 `DEEPSEEK_API_KEY`，以及 DeepSeek 账号额度与网络连接。修改配置后重启后端。不要把密钥直接写进 `application.yml` 或提交到 Git。

### 前端构建出现大分块警告

这是 Vite/Rollup 的体积提示，不影响构建成功。答辩演示前可继续按 ECharts、Element Plus 等依赖拆分 vendor chunk。
