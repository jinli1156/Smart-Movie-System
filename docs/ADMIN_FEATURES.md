# 管理后台功能开发说明

> 完成日期：2026-05-31
> 涉及模块：电影管理（海报上传修复）、排片管理、订单管理、用户管理

## 一、改动概览

| 模块 | 前端 | 后端 |
| --- | --- | --- |
| 电影管理 - 海报上传 | 优化 [Movie.vue](../movie-ticket-admin/src/views/Movie.vue) 上传交互 | 修复 [FileUploadService.java](../movie-ticket-backend/src/main/java/com/movie/ticket/service/FileUploadService.java) 返回完整可访问 URL |
| 排片管理 | 新增 [Showtime.vue](../movie-ticket-admin/src/views/Showtime.vue) | 复用 ShowtimeController |
| 订单管理 | 新增 [Order.vue](../movie-ticket-admin/src/views/Order.vue)、[api/order.ts](../movie-ticket-admin/src/api/order.ts) | 复用 OrderController |
| 用户管理 | 新增 [User.vue](../movie-ticket-admin/src/views/User.vue)、[api/user.ts](../movie-ticket-admin/src/api/user.ts) | 新增 [UserController.java](../movie-ticket-backend/src/main/java/com/movie/ticket/controller/UserController.java) |

## 二、电影管理 - 海报上传

### 修复点

- **海报上传成功后图片显示失败**：根因是 `FileUploadService` 返回 `url` 字段时只返回裸文件名（如 `abc123.jpg`），浏览器以页面路径解析为相对地址 → 404。
- **修复**：拼接 `accessPrefix`（默认 `/api/uploads/`）后返回完整路径 `/api/uploads/abc123.jpg`，前端 vite 代理 `/api` → `localhost:8080`，最终命中 `ImageServeController`。

### UI 增强

- 上传中显示加载动画与"上传中..."提示，禁用输入框防止重复操作。
- 图片加载失败时展示占位图标与"加载失败"文案。
- 输入框追加"清除"按钮，方便重置 URL。

### 旧数据迁移

数据库内已存在裸文件名格式的旧海报，可一次性补齐前缀：

```sql
UPDATE movies
SET poster = CONCAT('/api/uploads/', poster)
WHERE poster IS NOT NULL
  AND poster != ''
  AND poster NOT LIKE '/%'
  AND poster NOT LIKE 'http%';
```

## 三、排片管理（/showtime）

### 功能列表

| 功能 | 说明 |
| --- | --- |
| 排片列表 | 按电影 / 影院 / 日期筛选，分页展示电影海报、影院、影厅、起止时间、票价、座位余量、状态 |
| 添加排片 | 选择电影 → 选择影院 → 联动加载影厅 → 选择开始时间 → 自动计算结束时间（开始时间 + 电影时长） → 设置基础票价 |
| 编辑排片 | 同新增表单，修改后调用 `PUT /showtime/{id}` |
| 取消排片 | 调用 `PUT /showtime/{id}/status?status=2` 将状态置为已取消 |
| 删除排片 | 仅 `ADMIN` 角色可操作，调用 `DELETE /showtime/{id}` |

### 关键交互

- **影院 → 影厅级联**：选定影院后自动通过 `GET /hall/cinema/{cinemaId}` 拉取该影院下的影厅列表，未选影院前影厅下拉禁用。
- **结束时间自动计算**：当电影和开始时间都已选定时，按 `开始时间 + 电影时长（分钟）` 自动填充结束时间，仍允许手动调整。
- **座位余量低提示**：可售座位 < 10 时数字标黄。
- **状态可视化**：可售（绿）/ 已结束（灰）/ 已取消（红）。

## 四、订单管理（/order）

### 功能列表

| 功能 | 说明 |
| --- | --- |
| 订单列表 | 支持按订单号、状态筛选，分页展示电影、影院、影厅、放映时间、座位、用户、金额、状态、下单时间 |
| 订单详情 | 弹窗展示完整订单信息（订单号、用户、座位、金额、支付时间、备注等） |
| 取消订单 | 仅"待支付"状态订单可取消，调用 `POST /order/{orderNo}/cancel` |

### 状态映射

| 值 | 含义 | Tag 颜色 |
| --- | --- | --- |
| 0 | 待支付 | warning（黄） |
| 1 | 已支付 | success（绿） |
| 2 | 已取消 | info（灰） |
| 3 | 已退款 | danger（红） |
| 4 | 已完成 | 默认（蓝） |

### 权限

- `GET /order/page`、订单详情、取消订单：`ADMIN` 与 `CINEMA_ADMIN` 均可访问（原后端配置）。

## 五、用户管理（/user）

### 后端新增 UserController

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/user/page` | ADMIN | 分页查询，支持 `keyword`（用户名/昵称/手机/邮箱模糊匹配）、`role`、`status` 筛选 |
| GET | `/user/{id}` | ADMIN | 用户详情（密码字段已脱敏置空） |
| PUT | `/user/{id}` | ADMIN | 更新昵称、手机、邮箱、角色、状态、头像（用户名与密码不可改） |
| PUT | `/user/{id}/status?status=` | ADMIN | 启用 / 禁用（1/0） |
| PUT | `/user/{id}/reset-password?password=` | ADMIN | 重置密码（BCrypt 加密存储，密码 ≥ 6 位） |
| DELETE | `/user/{id}` | ADMIN | 删除用户 |

### 前端功能

| 功能 | 说明 |
| --- | --- |
| 用户列表 | 头像、用户名、昵称、手机、邮箱、角色（ADMIN/CINEMA_ADMIN/USER）、状态、注册时间 |
| 搜索筛选 | 关键字（多字段模糊）、角色、状态 |
| 编辑用户 | 修改昵称 / 手机 / 邮箱 / 角色 / 状态；用户名灰显不可改 |
| 启用/禁用 | 一键切换账户状态 |
| 重置密码 | 弹窗输入新密码 + 确认密码，前端校验长度 ≥ 6 与一致性，后端 BCrypt 加密 |
| 删除用户 | 二次确认后删除 |

### 角色 Tag 颜色

- ADMIN → danger（红）
- CINEMA_ADMIN → warning（黄）
- USER → info（灰）

## 六、菜单与路由

侧边菜单已在 [MainLayout.vue](../movie-ticket-admin/src/layout/MainLayout.vue) 中配置，"用户管理"仅对 `ADMIN` 角色可见。路由保护见 [router/index.ts](../movie-ticket-admin/src/router/index.ts)：

- 全局守卫校验登录态。
- `/user` 路由 meta `{ role: 'ADMIN' }`，非管理员访问会被重定向回 `/dashboard`。

## 七、类型定义

[types/index.ts](../movie-ticket-admin/src/types/index.ts) 新增：

- `Order`：订单 VO，匹配后端 `OrderVO`。
- `User`：用户实体，密码字段可选，前端默认不读取。

## 八、上线注意事项

1. **后端需重启**：`UserController` 与 `FileUploadService` 改动后必须重启 Spring Boot 服务。
2. **数据库无需迁移**：用户、订单、排片相关表已存在，本次未改 schema。
3. **海报历史数据**：参考第二节 SQL 一次性补齐前缀，或重新上传覆盖。
4. **首位管理员账号**：通过 `/auth/register` 创建后，需手动在 `user` 表里把 `role` 改为 `ADMIN`，登录后台后才能看到"用户管理"菜单。

## 九、联调清单

| 步骤 | 操作 |
| --- | --- |
| 1 | `cd movie-ticket-backend && mvn spring-boot:run` 启动后端（8080） |
| 2 | `cd movie-ticket-admin && npm run dev` 启动管理后台（3001） |
| 3 | 访问 http://localhost:3001/login，使用 ADMIN 账号登录 |
| 4 | 依次验证：电影管理（海报上传 + 显示）→ 排片管理（新增/编辑/取消）→ 订单管理（搜索/详情/取消）→ 用户管理（CRUD/重置密码） |
