# 项目开发总览

## 当前状态

### 已完成阶段

**第一阶段：项目搭建** ✅ — SpringBoot + Vue3 前后端分离架构、数据库设计、JWT认证
**第二阶段：基础数据** ✅ — 电影/影院/影厅/场次 CRUD、管理端页面
**第三阶段：核心购票流程** ✅ — 可视化选座、订单创建/支付/取消、Redis锁座
**第四阶段：智能功能** ✅ — 推荐系统、数据统计、Dashboard 可视化
**第五阶段（接近完成）** ✅(管理端) — 排片/订单/用户管理、海报上传修复、Redis 序列化修复
**第五阶段（用户端新增）** ✅ — 取票功能（取票码 + 二维码 + 摄像头扫码 + 手动输入）

### 下一阶段

- **第五阶段（剩余）**：用户端密码修改、退改签、评价、智能客服
- **第六阶段**：测试、优化、部署文档

## 项目结构

```
movie-ticket-system/
├── docs/                                    # 文档
│   ├── QUICK_START.md                       # 快速启动指南
│   └── superpowers/specs/                   # 设计文档
│       └── 2026-05-16-movie-ticket-system-design.md
│
├── movie-ticket-backend/                    # 后端服务
│   ├── src/main/java/com/movie/ticket/
│   │   ├── MovieTicketApplication.java      # 启动类
│   │   ├── config/                          # 配置类
│   │   │   ├── SecurityConfig.java          # Security配置
│   │   │   ├── RedisConfig.java             # Redis配置
│   │   │   ├── WebMvcConfig.java            # 静态资源配置
│   │   │   ├── FileUploadConfig.java        # 文件上传配置
│   │   │   ├── MyMetaObjectHandler.java     # 自动填充
│   │   │   └── DataInitializer.java         # 初始数据
│   │   ├── controller/                      # 控制器
│   │   │   ├── AuthController.java          # 认证接口
│   │   │   ├── UserController.java          # 用户管理接口
│   │   │   ├── MovieController.java         # 电影接口
│   │   │   ├── CinemaController.java        # 影院接口
│   │   │   ├── HallController.java          # 影厅接口
│   │   │   ├── ShowtimeController.java      # 场次接口
│   │   │   ├── OrderController.java         # 订单接口
│   │   │   ├── FileUploadController.java    # 文件上传接口
│   │   │   ├── ImageServeController.java    # 静态图片访问接口
│   │   │   ├── RecommendController.java     # 推荐接口
│   │   │   └── StatisticsController.java    # 统计接口
│   │   ├── service/                         # 服务层
│   │   │   ├── AuthService.java
│   │   │   ├── MovieService.java
│   │   │   ├── CinemaService.java
│   │   │   ├── HallService.java
│   │   │   ├── ShowtimeService.java
│   │   │   ├── OrderService.java
│   │   │   ├── FileUploadService.java
│   │   │   ├── RecommendService.java
│   │   │   └── StatisticsService.java
│   │   ├── mapper/                          # 数据访问层
│   │   │   ├── UserMapper.java
│   │   │   ├── CinemaMapper.java
│   │   │   ├── HallMapper.java
│   │   │   ├── MovieMapper.java
│   │   │   ├── ShowtimeMapper.java
│   │   │   └── TicketOrderMapper.java
│   │   ├── entity/                          # 实体类
│   │   │   ├── User.java
│   │   │   ├── Cinema.java
│   │   │   ├── Hall.java
│   │   │   ├── Movie.java
│   │   │   ├── Showtime.java
│   │   │   └── TicketOrder.java
│   │   ├── dto/                             # 数据传输对象
│   │   ├── vo/                              # 视图对象
│   │   ├── security/                        # 安全相关
│   │   ├── common/                          # 公共类
│   │   ├── exception/                       # 异常处理
│   │   └── utils/                           # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml                  # 配置文件
│   │   └── db/
│   │       ├── schema.sql                   # 数据库表结构
│   │       ├── data.sql                     # 初始数据
│   │       ├── migration_fix_poster_url.sql # 历史海报 URL 前缀补全
│   │       ├── migration_fix_showtime_status.sql # 排片状态字段修复
│   │       └── migration_add_ticket_pickup.sql   # 取票字段（ticket_code/is_picked/pick_time）
│   ├── pom.xml                              # Maven配置
│   └── README.md
│
├── movie-ticket-user/                       # 用户端前端
│   ├── src/
│   │   ├── api/                             # API接口
│   │   │   ├── auth.ts
│   │   │   ├── movie.ts
│   │   │   ├── cinema.ts
│   │   │   ├── showtime.ts
│   │   │   └── order.ts                     # 含取票接口 pickTicket
│   │   ├── components/                      # 公共组件
│   │   │   ├── Navbar.vue
│   │   │   ├── TicketQRCode.vue             # 取票码 + 二维码展示/下载
│   │   │   └── QRScanner.vue                # 摄像头扫码 + 手动输入取票码
│   │   ├── router/                          # 路由配置
│   │   │   └── index.ts
│   │   ├── stores/                          # 状态管理
│   │   │   └── user.ts
│   │   ├── types/                           # 类型定义
│   │   │   ├── auth.ts
│   │   │   └── index.ts
│   │   ├── utils/                           # 工具函数
│   │   │   └── request.ts
│   │   ├── views/                           # 页面组件
│   │   │   ├── Login.vue
│   │   │   ├── Register.vue
│   │   │   ├── Home.vue
│   │   │   ├── Movies.vue
│   │   │   ├── MovieDetail.vue
│   │   │   ├── Cinemas.vue
│   │   │   ├── SeatSelection.vue
│   │   │   ├── OrderConfirm.vue
│   │   │   ├── OrderPay.vue
│   │   │   ├── OrderList.vue
│   │   │   └── OrderDetail.vue
│   │   ├── App.vue
│   │   └── main.ts
│   ├── package.json
│   ├── vite.config.ts
│   └── README.md
│
├── movie-ticket-admin/                      # 管理端前端
│   ├── src/
│   │   ├── api/
│   │   │   ├── auth.ts
│   │   │   ├── movie.ts
│   │   │   ├── cinema.ts
│   │   │   ├── hall.ts
│   │   │   ├── upload.ts
│   │   │   ├── showtime.ts
│   │   │   ├── order.ts
│   │   │   └── user.ts
│   │   ├── layout/
│   │   │   └── MainLayout.vue
│   │   ├── router/
│   │   │   └── index.ts
│   │   ├── stores/
│   │   │   └── user.ts
│   │   ├── types/
│   │   │   ├── auth.ts
│   │   │   └── index.ts
│   │   ├── utils/
│   │   │   └── request.ts
│   │   ├── views/
│   │   │   ├── Login.vue
│   │   │   ├── Dashboard.vue
│   │   │   ├── Movie.vue
│   │   │   ├── Cinema.vue
│   │   │   ├── Hall.vue
│   │   │   ├── Showtime.vue
│   │   │   ├── Order.vue
│   │   │   └── User.vue
│   │   ├── App.vue
│   │   └── main.ts
│   ├── package.json
│   ├── vite.config.ts
│   └── README.md
│
├── .gitignore
└── README.md
```

## 数据库表设计

### 1. user - 用户表
- 用户认证信息
- 角色权限（USER/CINEMA_ADMIN/ADMIN）
- 个人信息

### 2. cinema - 影院表
- 影院基本信息
- 地址、联系方式
- 营业状态

### 3. hall - 影厅表
- 影厅信息
- 座位布局（JSON）
- 所属影院

### 4. movie - 电影表
- 电影详细信息
- 导演、演员、类型
- 上映状态

### 5. showtime - 场次表
- 放映场次
- 时间、影厅、票价
- 座位状态

### 6. ticket_order - 订单表
- 订单信息
- 座位、价格
- 订单状态
- **取票字段**：`ticket_code`（8 位取票码，索引）、`is_picked`（0/1 是否已取票）、`pick_time`（取票时间）

## 技术亮点

1. **前后端分离**：Vue3 + SpringBoot
2. **JWT认证**：无状态认证机制
3. **Redis缓存**：分布式锁（锁座）、缓存热门推荐
4. **MyBatis-Plus**：简化数据访问
5. **TypeScript**：类型安全
6. **Element Plus**：现代化UI
7. **响应式设计**：适配多端
8. **Canvas选座**：可视化座位选择
9. **推荐系统**：热门/个性化/相似推荐算法
10. **图片上传**：本地文件上传，支持多图管理与预览
11. **取票闭环**：支付自动出码 → 二维码/取票码下载 → 摄像头扫码或手动输入 → 状态防重复

## 文件上传功能

### 后端
- **FileUploadController.java** — 图片上传接口 `POST /upload/image`
- **FileUploadService.java** — 文件存储逻辑（UUID 重命名、类型校验、大小限制）
- **FileUploadConfig.java** — 上传配置（路径 `uploads/`，最大 5MB，允许 jpg/png/gif/webp/bmp）
- **WebMvcConfig.java** — 静态资源映射 `/uploads/**` → 本地文件目录

### 前端
- **movie-ticket-admin/src/api/upload.ts** — 上传 API 封装
- **movie-ticket-admin/src/views/Cinema.vue** — 影院表单中集成图片上传组件
  - 拖拽/点击上传
  - 缩略图预览列表（最多5张）
  - 单图放大预览
  - 单图删除

### 配置项（application.yml）
```yaml
file:
  upload:
    path: uploads/
    access-prefix: /uploads/
    max-size: 5242880   # 5MB
    allowed-types: jpg,jpeg,png,gif,webp,bmp
```

## 取票功能（2026-05-31 新增）

详细文档见 [取票功能说明](../features/TICKET_PICKUP.md)。

### 数据库迁移
执行 `movie-ticket-backend/src/main/resources/db/migration_add_ticket_pickup.sql`：
- `ticket_order.ticket_code VARCHAR(8)` — 8 位取票码
- `ticket_order.is_picked INT DEFAULT 0` — 是否已取票
- `ticket_order.pick_time DATETIME` — 取票时间
- 索引 `idx_ticket_code (ticket_code)`

### 后端
- **OrderService.generateTicketCode(orderNo)** — 订单号后 4 位 + 4 位随机数生成取票码
- **OrderService.payOrder()** — 支付成功后自动写入 `ticketCode`
- **OrderService.pickTicket(ticketCode)** — 校验已支付 + 未取票，置 `is_picked=1` 并记录 `pick_time`
- **OrderController** — 新增接口 `POST /order/pick?ticketCode={code}`
- **TicketOrder / OrderVO** — 新增 `ticketCode / isPicked / pickTime` 字段

### 前端（用户端）
- **components/TicketQRCode.vue** — 展示取票码、生成二维码、下载图片、复制数据
- **components/QRScanner.vue** — `html5-qrcode` 调用摄像头、前/后置切换、动态扫描框、扫码失败回退手输
- **views/OrderDetail.vue** — 已支付订单显示"查看取票码"入口
- **views/Profile.vue** — 订单卡片根据 `isPicked` 显示"已取票"或"查看取票码"
- **views/Home.vue** — 顶部新增"扫码取票"入口
- **api/order.ts** — `pickTicket(ticketCode)` 调用 `POST /order/pick`
- **types/index.ts** — `Order` 新增 `ticketCode? / isPicked? / pickTime?`

### 二维码数据格式
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

### 已知限制
- 屏幕二维码扫描在低亮度下成功率较低，建议放大或打印
- 浏览器调摄像头要求 HTTPS（或 `localhost`）

## 下一步开发计划

### 第五阶段：功能完善（接近完成）

#### 用户端
- [x] 个人中心（个人信息/资料编辑）
- [x] 个人中心编辑资料（昵称/性别/生日/城市/偏好/签名）
- [x] 取票功能（取票码 + 二维码 + 摄像头扫码 + 手动输入）
- [ ] 密码修改
- [ ] 退票改签功能
- [ ] 评价功能
- [ ] 智能客服（FAQ + AI 兜底）

#### 管理端
- [x] 影院管理（完整CRUD + 状态切换 + 图片上传）
- [x] 电影管理（CRUD + 海报上传修复）
- [x] 排片管理（影院/影厅级联、自动结束时间、状态可视化）
- [x] 订单管理（按订单号/状态筛选、详情、取消）
- [x] 用户管理（CRUD + 启用禁用 + 重置密码）
- [x] 数据统计页面
- [ ] 系统设置
- [ ] 日志查看

### 后端稳定性 ✅
- [x] FileUploadService 返回完整 URL（`/api/uploads/<filename>`）
- [x] RedisConfig 注册 `JavaTimeModule`，禁用 `WRITE_DATES_AS_TIMESTAMPS`
- [x] MovieService 缓存读写降级（Redis 出错时 fallback 查库）
- [x] OrderService 支付时生成取票码、新增 `pickTicket` 取票方法

### 第六阶段：测试与优化（预计1周）

- [ ] 单元测试 / 集成测试 / 功能测试
- [ ] 性能测试 / 安全测试
- [ ] 代码 / 性能 / 数据库 / 缓存优化
- [ ] 部署文档 / 用户手册 / 答辩PPT

## 开发规范

### 代码规范
- 后端：遵循阿里巴巴Java开发手册
- 前端：使用ESLint + Prettier
- 命名：见名知意，统一风格

### Git规范
- 分支：feature/功能名、bugfix/问题描述
- 提交：type(scope): description
  - feat: 新功能
  - fix: 修复
  - docs: 文档
  - style: 格式
  - refactor: 重构
  - test: 测试
  - chore: 构建

### 接口规范
- RESTful API设计
- 统一响应格式
- 完善的错误处理
- API文档（Swagger/Apifox）

## 测试账号

| 用户名 | 密码 | 角色 | 用途 |
|--------|------|------|------|
| admin | admin123 | 超级管理员 | 管理后台全部功能 |
| cinema_admin | admin123 | 影院管理员 | 管理所属影院 |
| user1 | admin123 | 普通用户 | 用户端购票 |

## 联系方式

如有问题，请查看：
- [快速启动指南](QUICK_START.md)
- [设计文档](../design/SYSTEM_DESIGN.md)
- 各子项目的 README.md

---

**最后更新**: 2026-05-31  
**当前版本**: v1.0.0-beta  
**开发状态**: 第五阶段（管理端模块全部完成 + 用户端取票功能 + 后端稳定性修复）
