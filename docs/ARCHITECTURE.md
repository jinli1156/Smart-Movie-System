# 系统架构与功能说明

> 单一来源的开发文档：项目结构、数据库、接口、各功能模块的实现细节都在这里。  
> 历史改动按日期记录在 [CHANGELOG.md](CHANGELOG.md)；启动步骤见 [QUICK_START.md](QUICK_START.md)。

---

## 一、项目结构

```
movie-ticket-system/
├── docs/
│   ├── ARCHITECTURE.md                    # 本文档
│   ├── CHANGELOG.md                       # 变更记录
│   ├── QUICK_START.md                     # 启动指南
│   └── superpowers/specs/                 # 原始设计文档
│       └── 2026-05-16-movie-ticket-system-design.md
│
├── movie-ticket-backend/                  # SpringBoot 3.2.5 后端
│   ├── src/main/java/com/movie/ticket/
│   │   ├── MovieTicketApplication.java
│   │   ├── config/                        # SecurityConfig / RedisConfig / WebMvcConfig
│   │   │                                   # FileUploadConfig / MyMetaObjectHandler / DataInitializer
│   │   ├── controller/                    # Auth / User / Movie / Cinema / Hall / Showtime
│   │   │                                   # Order / Comment / FileUpload / ImageServe / Recommend / Statistics
│   │   ├── service/                       # 与 controller 对应
│   │   ├── mapper/                        # MyBatis-Plus Mapper
│   │   ├── entity/                        # User / Cinema / Hall / Movie / Showtime / TicketOrder / Comment
│   │   ├── dto / vo / security / common / exception / utils
│   │   └── ...
│   └── src/main/resources/
│       ├── application.yml
│       └── db/
│           ├── schema.sql                          # 主表结构
│           ├── data.sql                            # 初始数据
│           └── migration_v3_schema_alignment.sql   # 已有数据库结构对齐
│
├── movie-ticket-user/                     # Vue3 用户端（3000）
│   └── src/{api,components,router,stores,types,utils,views}/
│       └── components/{Navbar,TicketQRCode,QRScanner}.vue
│
├── movie-ticket-admin/                    # Vue3 管理端（3001）
│   └── src/{api,layout,router,stores,types,utils,views}/
│
├── README.md
└── .gitignore
```

---

## 二、技术栈

| 层 | 技术 | 版本 | 说明 |
| --- | --- | --- | --- |
| 后端框架 | SpringBoot | 3.2.5 | 核心 |
| 安全 | Spring Security + JWT | 6.x / 0.12.5 | 无状态认证 |
| ORM | MyBatis-Plus | 3.5.6 | 分页 / 自动填充 |
| 数据库 | MySQL | 8.0 | |
| 缓存 / 锁 | Redis | 7.0 | dev profile 默认走 embedded-redis |
| 工具 | Hutool | 5.8.26 | |
| 前端框架 | Vue + TypeScript | 3.4 / 5.3 | |
| 构建 | Vite | 5.1 | |
| UI | Element Plus | 2.6 | |
| 状态 / 路由 | Pinia / Vue Router | 2.1 / 4.3 | |
| 可视化 | ECharts | 5.5 | 管理端 Dashboard |
| 取票 | qrcode + html5-qrcode | — | 二维码生成 + 摄像头扫码 |

---

## 三、数据库

### 表清单

| 表 | 用途 |
| --- | --- |
| `user` | 账户（USER / CINEMA_ADMIN / ADMIN）+ 个人资料 |
| `cinema` | 影院基本信息 + 状态 |
| `hall` | 影厅 + 座位布局（JSON） |
| `movie` | 电影详情、上映状态、评分（由评价均值回写） |
| `showtime` | 排片：电影 × 影厅 × 时间 × 票价 × 余位 |
| `ticket_order` | 订单：用户 × 场次 × 座位 × 状态 × 取票码 |
| `comment` | 电影评价：用户 × 电影 × 评分 × 内容（`uk_user_movie` 一人一片一评） |

### `ticket_order` 关键字段

| 字段 | 说明 |
| --- | --- |
| `status` | 0 待支付 / 1 已支付 / 2 已取消 / 3 已退款 / 4 已完成 |
| `ticket_code` | 8 位取票码（订单号后 4 位 + 4 位随机数），唯一索引 `uk_ticket_code` |
| `is_picked` | 0 未取票 / 1 已取票 |
| `pick_time` | 取票时间戳 |

### `comment` 关键字段

| 字段 | 说明 |
| --- | --- |
| `movie_id` / `user_id` | 评价归属，组合唯一键 `uk_user_movie` 保证一人一片仅一条 |
| `order_id` | 关联触发评价资格的已支付订单（观影凭据） |
| `rating` | 1-5 星整数 |
| `content` | 评价内容，≤ 500 字，可空 |

### 初始化与迁移

```bash
# 新建数据库：schema.sql 会重建业务表，仅用于空库/测试库
mysql -u root -p < src/main/resources/db/schema.sql
mysql -u root -p movie_ticket < src/main/resources/db/data.sql

# 已有数据库：先备份，只执行非破坏性结构对齐脚本
mysql -u root -p movie_ticket < src/main/resources/db/migration_v3_schema_alignment.sql
```

---

## 四、接口清单

> 所有接口前缀 `/api`，受保护接口需要在 Header 携带 `Authorization: Bearer {token}`。

### 认证 / 用户

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 公开 | 注册（默认 USER） |
| POST | `/auth/login` | 公开 | 登录，返回 JWT |
| GET | `/user/me` | 登录 | 当前用户信息 |
| PUT | `/user/me` | 登录 | 修改本人资料 |
| POST | `/user/change-password` | 登录 | 修改本人密码（校验原密码 + BCrypt，禁止新旧相同） |
| GET | `/user/page` | ADMIN | 用户分页（keyword / role / status） |
| GET | `/user/{id}` | ADMIN | 详情（密码脱敏） |
| PUT | `/user/{id}` | ADMIN | 修改昵称 / 手机 / 邮箱 / 角色 / 状态 / 头像 |
| PUT | `/user/{id}/status?status=` | ADMIN | 启用 / 禁用 |
| PUT | `/user/{id}/reset-password?password=` | ADMIN | BCrypt 重置（≥6 位） |
| DELETE | `/user/{id}` | ADMIN | 删除 |

### 电影 / 影院 / 影厅 / 排片

| 模块 | 关键接口 |
| --- | --- |
| 电影 | `GET /movie/page`、`/movie/now-showing`、`/movie/coming-soon`、`/movie/{id}`、`POST/PUT/DELETE /movie`、`PUT /movie/{id}/status` |
| 影院 | `GET /cinema/page`、`/cinema/all`、`/cinema/{id}`、CRUD + `/cinema/{id}/status` |
| 影厅 | `GET /hall/page`、`/hall/cinema/{cinemaId}`、`/hall/{id}`、CRUD + `/hall/{id}/status` |
| 排片 | `GET /showtime/page`、`/showtime/list`、`/showtime/{id}`、`/showtime/{id}/seats`、CRUD + `/showtime/{id}/status` |

排片创建时会校验：电影/影院/影厅存在、影厅归属影院、开始 < 结束、与同影厅其他场次不冲突，并自动计算座位数。

### 订单

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/order` | 登录 | 创建订单（含 Redis 锁座） |
| GET | `/order/my` | 登录 | 我的订单（分页 + 状态筛选） |
| GET | `/order/page` | ADMIN / CINEMA_ADMIN | 管理端订单分页 |
| GET | `/order/{orderNo}` | 登录 | 订单详情 |
| POST | `/order/{orderNo}/pay` | 登录 | 模拟支付，生成取票码 |
| POST | `/order/{orderNo}/cancel` | 登录 | 取消（仅"待支付"），释放座位锁 |
| POST | `/order/{orderNo}/refund` | 登录 | 退票（仅"已支付"）：开场前 30 分钟禁退、24 小时内扣 10% 手续费、24 小时外全额退；恢复座位 + 释放锁 |
| POST | `/order/pick?ticketCode={code}` | 登录 | 取票（仅"已支付且未取票"） |

### 评价

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/comment` | 登录 | 发布评价（`movieId / rating 1-5 / content`），需购票观影、每片限一条 |
| GET | `/comment/movie/{movieId}` | 公开 | 某电影的评价列表（倒序） |
| GET | `/comment/eligibility?movieId=` | 登录 | 返回 `watched / commented / canComment` |

### 推荐 / 统计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/recommend/hot?limit=` | 热门推荐（Redis 缓存 1h） |
| GET | `/recommend/personal?limit=` | 个性化推荐（基于观影历史） |
| GET | `/recommend/similar/{movieId}?limit=` | 相似推荐（按类型重叠度） |
| GET | `/statistics/dashboard` | 电影/影院/今日订单/今日收入/用户总数 |
| GET | `/statistics/box-office?limit=` | 票房排行 |
| GET | `/statistics/showtime/{id}` | 场次上座率 |
| GET | `/statistics/revenue?startDate=&endDate=` | 收入趋势 |

### 文件 / 图片

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/upload/image` | multipart 上传，返回 `{ "url": "/api/uploads/<uuid>.<ext>" }` |
| GET | `/uploads/**` | 静态资源映射 → `uploads/` 本地目录 |

`application.yml` 上传配置：
```yaml
file:
  upload:
    path: uploads/
    access-prefix: /uploads/
    max-size: 5242880        # 5MB
    allowed-types: jpg,jpeg,png,gif,webp,bmp
```

---

## 五、功能模块

### 1. 智能推荐

**热度算法**：`score = 评分×10 + 近7天订单数×2 + max(0, 100 - 上映天数)`  
**个性化**：基于用户观影历史，过滤已看，按评分排序；未登录回退热门  
**相似**：按 `type` 字段重叠度排序，最多 6 部  
**缓存**：热门列表写 Redis 1h；读写均有 try-catch 降级查库

### 2. 数据统计（管理端 Dashboard）

- 顶部 4 张统计卡（电影 / 影院 / 今日订单 / 今日收入）
- 票房排行表（电影、票房、售票数）
- 收入趋势（按日聚合）
- 场次上座率（按场次维度）

### 3. 购票流程

```
首页 → 电影详情 → 选场次 → 登录校验 → Canvas 选座 → 订单确认
  → 支付（生成取票码）→ 订单列表 / 详情 → 取票（扫码或手输）
  → 观影后评价 / 退票
```

- **座位锁**：Redis 分布式锁，15 分钟自动过期；取消 / 退票订单时主动释放
- **选座 UI**：Canvas 绘制，绿色可选 / 蓝色已选 / 灰色已占用，单次最多 5 座
- **订单状态机**：0 待支付 → 1 已支付 → (2 已取消 / 3 已退款 / 4 已完成)
- **退票**：仅"已支付"可退；开场前 30 分钟内禁退、24 小时内扣 10% 手续费、24 小时外全额退；置状态 3、恢复 `available_seats` 并释放座位锁，手续费/退款金额写入 `remark`
- **修改密码**：`POST /user/change-password`，校验原密码 + 禁止新旧相同，BCrypt 存储，成功后前端自动登出重登

### 4. 图片上传

- 后端：`FileUploadService` UUID 重命名、类型 / 大小校验、返回完整 URL `/api/uploads/<file>`
- 前端：Element Plus `el-upload` 拖拽 / 点击、缩略图列表、单图放大预览、删除
- 静态映射：`WebMvcConfig` 把 `/uploads/**` 指到本地 `uploads/` 目录

### 5. 管理端模块

| 模块 | 路径 | 关键能力 |
| --- | --- | --- |
| 影院 | `/cinema` | CRUD + 状态切换 + 多图上传 |
| 电影 | `/movie` | CRUD + 海报上传 |
| 排片 | `/showtime` | 电影 → 影院 → 影厅级联、自动算结束时间、状态可视化（绿/灰/红）、座位余量 <10 标黄 |
| 订单 | `/order` | 按订单号 / 状态筛选 + 详情弹窗 + 取消订单（仅待支付） |
| 用户 | `/user` | CRUD + 启用禁用 + 重置密码（前端校验长度/一致性，后端 BCrypt） |

**菜单与权限**：`MainLayout.vue` 侧栏 + `router/index.ts` 全局守卫。"用户管理"仅 ADMIN 可见（meta `{ role: 'ADMIN' }`，非管理员重定向到 `/dashboard`）。

**角色 Tag 颜色**：ADMIN → danger / CINEMA_ADMIN → warning / USER → info。

### 6. 取票功能

#### 后端
- `OrderService.generateTicketCode(orderNo)`：订单号后 4 位 + 4 位随机 → 8 位数字
- `OrderService.payOrder()`：支付成功时写入 `ticketCode`
- `OrderService.pickTicket(ticketCode)`：校验已支付 + 未取票 → 置 `is_picked=1` + 记录 `pick_time`
- `OrderController`：新增 `POST /order/pick?ticketCode={code}`
- `TicketOrder` / `OrderVO` 同步新增 `ticketCode / isPicked / pickTime`

#### 前端（用户端）
- `components/TicketQRCode.vue` — 展示取票码、生成 QR、下载图片、复制二维码数据
- `components/QRScanner.vue` — `html5-qrcode` 调摄像头、前/后置切换、动态扫描框（视窗 70%）、扫码失败回退手输
- `views/OrderDetail.vue` — 已支付订单显示"查看取票码"
- `views/Profile.vue` — 订单卡片按 `isPicked` 展示"已取票"或"查看取票码"
- `views/Home.vue` — 顶部入口"扫码取票"
- `api/order.ts` — `pickTicket(ticketCode)`
- `types/index.ts` — `Order` 新增 `ticketCode? / isPicked? / pickTime?`

#### 二维码 payload
```json
{
  "type": "MOVIE_TICKET",
  "orderNo": "ORDER1234567890",
  "ticketCode": "78905678",
  "movieTitle": "电影名称",
  "cinemaName": "影院名称",
  "hallName": "影厅名称",
  "seats": "5排6座,5排7座",
  "showtime": "2026-05-31T19:30:00",
  "timestamp": 1717171200000
}
```

#### 取票码生成与校验规则
- 格式：8 位数字（订单号后 4 位 + 4 位随机数）
- 验证：取票码存在 + 订单 `status=1` + `is_picked=0`
- 成功后：`is_picked=1`、写 `pick_time`，防止重复取票

#### 已知限制
- 屏幕显示的二维码在低亮度下识别率较低，建议放大或打印
- 浏览器调用摄像头需 HTTPS 或 `localhost`

### 7. 电影评价

#### 评价资格（核心规则）
**必须购票且观影后才能评价**：后端 `CommentService.findWatchedOrder` 校验当前用户存在一笔该电影的「已支付订单（`status=1`）」且其场次「开始时间已过」；并通过 `uk_user_movie` 唯一键限制每位用户每部电影只能评价一次。

#### 后端
- `comment` 表 + `Comment` / `CommentMapper` / `CommentCreateDTO` / `CommentVO`
- `CommentService`：`createComment`（校验资格 + 防重复 + 写入 `order_id` 观影凭据）、`listByMovie`、`checkEligibility`、`refreshMovieRating`
- `refreshMovieRating`：取该片全部评价星级均值 ×2（5 星制 → 10 分制）回写 `movie.rating`
- `CommentController`：`POST /comment`、`GET /comment/movie/{movieId}`（公开）、`GET /comment/eligibility`
- `SecurityConfig`：放行 `GET /comment/movie/**`，游客也能看评价

#### 前端（用户端）
- `MovieDetail.vue` 新增"观众评价"卡片：平均分 + 星级汇总 + 评价列表（头像 / 昵称 / 星级 / 日期 / 正文）
- 写评价对话框：`el-rate` 打分（1-5）+ 文本（≤500 字）；按资格展示「写评价 / 已评价 / 购票观影后才能评价」
- `api/comment.ts`、`types/index.ts` 新增 `Comment` / `CommentEligibility`
- 发布成功后刷新评价列表、资格状态与电影评分

#### 已知限制
- 评价与具体场次未一一绑定（按"看过该片任一已开场已支付订单"判定资格）

---

## 六、稳定性与已修复问题

| 问题 | 根因 | 解决方案 |
| --- | --- | --- |
| 海报上传成功后 404 | `FileUploadService` 只返回裸文件名 | 拼接 `accessPrefix` 返回完整 `/api/uploads/<file>`；旧数据用 `migration_fix_poster_url.sql` 补齐 |
| `/movie/now-showing`、`/movie/coming-soon` 返回 500 | `RedisConfig.ObjectMapper` 未注册 `JavaTimeModule`，`LocalDate/LocalDateTime` 序列化失败 | 注册 `JavaTimeModule` + 禁用 `WRITE_DATES_AS_TIMESTAMPS`；`MovieService` 缓存读写加 try-catch 降级查库 |
| 缺少用户管理后端接口 | 未实现 `UserController` | 新增 CRUD + 启用禁用 + BCrypt 重置密码 |
| 支付后没有取票凭据 | 订单缺取票码字段及生成逻辑 | `ticket_order` 加 3 个字段、`payOrder` 自动生成、`/order/pick` 校验取票 |

---

## 七、上线 / 联调清单

1. **数据库**：执行 `schema.sql` + `data.sql`；老库按需追加 migration 脚本
2. **后端**：`cd movie-ticket-backend && mvn spring-boot:run`（8080）
3. **管理端**：`cd movie-ticket-admin && npm install && npm run dev`（3001）
4. **用户端**：`cd movie-ticket-user && npm install && npm run dev`（3000）
5. **首位管理员**：通过 `/auth/register` 注册后，手动在 `user` 表把 `role` 改为 `ADMIN`
6. **HTTPS 摄像头**：取票扫码功能需要 HTTPS 或 `localhost` 访问

### 测试账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| admin | admin123 | ADMIN |
| cinema_admin | admin123 | CINEMA_ADMIN |
| user1 | admin123 | USER |

---

## 八、开发规范

- **后端命名**：阿里巴巴 Java 手册；Controller → Service → Mapper → Entity 分层
- **前端命名**：ESLint + Prettier；组件 PascalCase、文件 kebab-case
- **接口**：RESTful + 统一 `Result<T>` 响应 + 全局异常处理
- **Git 提交**：`feat / fix / docs / style / refactor / test / chore (scope): description`
