# 智能电影购票系统 - 设计文档

**项目名称**: 智能电影购票系统  
**技术栈**: SpringBoot 3.x + Vue 3 + TypeScript + MySQL + Redis  
**设计日期**: 2026-05-16  
**项目类型**: 本科毕业设计

## 一、项目概述

### 1.1 项目背景
开发一个面向C端用户和B端管理的智能电影购票系统，集成智能推荐、智能选座、智能定价、智能客服等AI功能，提升用户购票体验和影院运营效率。

### 1.2 核心特性
- **双端系统**: 用户端（购票）+ 管理端（运营）
- **智能推荐**: 基于内容+协同过滤+热度的混合推荐算法
- **智能选座**: 根据人数和偏好自动推荐最佳座位
- **智能定价**: 时段+座位+热度的动态定价模型
- **智能客服**: FAQ规则匹配 + AI API兜底

### 1.3 系统规模
- 支持3家影院
- 每家影院4-5个影厅
- 不同影厅支持差异化座位布局
- 三级权限：普通用户、影院管理员、超级管理员

### 1.4 技术栈

**前端**
- Vue 3.x + TypeScript
- Element Plus（UI组件库）
- Pinia（状态管理）
- Vue Router（路由）
- Axios（HTTP客户端）

**后端**
- SpringBoot 3.x
- MyBatis-Plus（ORM框架）
- Spring Security + JWT（认证授权）
- Redis（缓存+会话）
- MySQL 8.0（数据库）

**开发工具**
- Maven 3.8+
- Node.js 18+
- Git

## 二、系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                   用户端 (Vue3 - User)                    │
│  ┌──────────┬──────────┬──────────┬──────────┐          │
│  │ 首页推荐 │ 电影列表 │ 选座购票 │ 个人中心 │          │
│  │ 智能客服 │ 订单管理 │ 退票改签 │ 会员中心 │          │
│  └──────────┴──────────┴──────────┴──────────┘          │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                  管理端 (Vue3 - Admin)                    │
│  ┌──────────┬──────────┬──────────┬──────────┐          │
│  │ 影院管理 │ 影厅管理 │ 电影管理 │ 排片管理 │          │
│  │ 订单管理 │ 用户管理 │ 数据统计 │ 系统设置 │          │
│  └──────────┴──────────┴──────────┴──────────┘          │
└─────────────────────────────────────────────────────────┘
                            ↓ HTTP/HTTPS
┌─────────────────────────────────────────────────────────┐
│                   SpringBoot 后端服务                     │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Controller 层                                    │   │
│  │  ├─ 用户端API (/api/user/*)                      │   │
│  │  └─ 管理端API (/api/admin/*)                     │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Service 层                                       │   │
│  │  ├─ 基础业务 (用户、订单、影院、场次、座位)       │   │
│  │  ├─ 智能引擎 (推荐、定价、选座、客服)             │   │
│  │  └─ 系统服务 (权限、日志、统计)                  │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Mapper 层 (MyBatis-Plus)                        │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │  定时任务 (订单超时、数据同步、缓存预热)          │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│              数据层                                       │
│  ┌──────────────┐    ┌──────────────┐                   │
│  │   MySQL      │    │    Redis     │                   │
│  │  (持久化)     │    │ (缓存+会话)   │                   │
│  └──────────────┘    └──────────────┘                   │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│              外部服务 (可选)                              │
│  ┌──────────────┐    ┌──────────────┐                   │
│  │  电影API     │    │   AI API     │                   │
│  │ (豆瓣/猫眼)   │    │ (智能客服)    │                   │
│  └──────────────┘    └──────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

### 2.2 分层架构

**表现层（Controller）**
- 用户端API：`/api/user/*`
- 管理端API：`/api/admin/*`
- 统一参数校验、异常处理
- Swagger API文档

**业务层（Service）**
- 基础业务服务
- 智能引擎服务
- 系统服务

**持久层（Mapper）**
- MyBatis-Plus自动CRUD
- 自定义复杂查询
- 分页插件

**数据层**
- MySQL：持久化存储
- Redis：缓存+会话+分布式锁

## 三、数据库设计

### 3.1 数据表清单（共18张表）

**用户相关（3张）**
- user - 用户表
- user_profile - 用户画像表
- user_address - 用户地址表

**影院相关（3张）**
- cinema - 影院表
- hall - 影厅表
- seat - 座位表

**电影相关（2张）**
- movie - 电影表
- movie_schedule - 排片表

**订单相关（3张）**
- order - 订单表
- order_item - 订单明细表
- refund - 退票记录表

**智能相关（3张）**
- recommendation_log - 推荐日志表
- pricing_rule - 定价规则表
- chat_history - 客服记录表

**系统相关（4张）**
- system_config - 系统配置表
- operation_log - 操作日志表
- admin_user - 管理员表
- role_permission - 角色权限表

### 3.2 核心表结构设计

#### user - 用户表
```sql
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码(加密)',
  `phone` VARCHAR(11) UNIQUE COMMENT '手机号',
  `email` VARCHAR(100) COMMENT '邮箱',
  `avatar` VARCHAR(255) COMMENT '头像URL',
  `role` VARCHAR(20) DEFAULT 'USER' COMMENT '角色:USER/CINEMA_ADMIN/SUPER_ADMIN',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0禁用 1正常',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除:0未删除 1已删除',
  INDEX idx_phone (`phone`),
  INDEX idx_role (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

#### cinema - 影院表
```sql
CREATE TABLE `cinema` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '影院名称',
  `address` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `phone` VARCHAR(20) COMMENT '联系电话',
  `province` VARCHAR(50) COMMENT '省份',
  `city` VARCHAR(50) COMMENT '城市',
  `district` VARCHAR(50) COMMENT '区县',
  `longitude` DECIMAL(10,6) COMMENT '经度',
  `latitude` DECIMAL(10,6) COMMENT '纬度',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0停业 1营业',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  INDEX idx_city (`city`),
  INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影院表';
```

#### hall - 影厅表
```sql
CREATE TABLE `hall` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `cinema_id` BIGINT NOT NULL COMMENT '影院ID',
  `name` VARCHAR(50) NOT NULL COMMENT '影厅名称',
  `seat_layout` JSON COMMENT '座位布局JSON',
  `total_seats` INT NOT NULL COMMENT '总座位数',
  `hall_type` VARCHAR(20) COMMENT '影厅类型:2D/3D/IMAX/VIP',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0停用 1启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  INDEX idx_cinema (`cinema_id`),
  FOREIGN KEY (`cinema_id`) REFERENCES `cinema`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影厅表';
```

#### movie - 电影表
```sql
CREATE TABLE `movie` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL COMMENT '电影名称',
  `poster` VARCHAR(255) COMMENT '海报URL',
  `director` VARCHAR(100) COMMENT '导演',
  `actors` VARCHAR(500) COMMENT '主演',
  `genre` VARCHAR(100) COMMENT '类型:动作/喜剧/爱情等',
  `duration` INT COMMENT '时长(分钟)',
  `rating` DECIMAL(3,1) COMMENT '评分',
  `release_date` DATE COMMENT '上映日期',
  `description` TEXT COMMENT '简介',
  `hot_score` INT DEFAULT 0 COMMENT '热度分数',
  `douban_id` VARCHAR(50) COMMENT '豆瓣ID',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0下架 1上映',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  INDEX idx_status (`status`),
  INDEX idx_hot_score (`hot_score`),
  INDEX idx_release_date (`release_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电影表';
```

#### movie_schedule - 排片表
```sql
CREATE TABLE `movie_schedule` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `movie_id` BIGINT NOT NULL COMMENT '电影ID',
  `hall_id` BIGINT NOT NULL COMMENT '影厅ID',
  `show_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `base_price` DECIMAL(10,2) NOT NULL COMMENT '基础票价',
  `sold_seats` INT DEFAULT 0 COMMENT '已售座位数',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0取消 1正常',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  INDEX idx_movie (`movie_id`),
  INDEX idx_hall (`hall_id`),
  INDEX idx_show_time (`show_time`),
  FOREIGN KEY (`movie_id`) REFERENCES `movie`(`id`),
  FOREIGN KEY (`hall_id`) REFERENCES `hall`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排片表';
```

#### `order` - 订单表
```sql
CREATE TABLE `order` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `schedule_id` BIGINT NOT NULL COMMENT '场次ID',
  `total_price` DECIMAL(10,2) NOT NULL COMMENT '总价',
  `status` VARCHAR(20) NOT NULL COMMENT '状态:PENDING/PAID/CANCELLED/REFUNDED',
  `pay_time` DATETIME COMMENT '支付时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  INDEX idx_user (`user_id`),
  INDEX idx_order_no (`order_no`),
  INDEX idx_status (`status`),
  INDEX idx_create_time (`create_time`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
  FOREIGN KEY (`schedule_id`) REFERENCES `movie_schedule`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

## 四、后端模块设计

### 4.1 Controller层

#### 用户端API
- `UserController` - 用户注册、登录、个人信息
- `MovieController` - 电影列表、详情、搜索
- `ScheduleController` - 场次查询
- `OrderController` - 创建订单、支付、查询、退票
- `RecommendController` - 智能推荐
- `ChatController` - 智能客服

#### 管理端API
- `AdminCinemaController` - 影院管理
- `AdminHallController` - 影厅管理
- `AdminMovieController` - 电影管理
- `AdminScheduleController` - 排片管理
- `AdminOrderController` - 订单管理
- `AdminUserController` - 用户管理
- `AdminStatisticsController` - 数据统计

### 4.2 Service层

#### 基础业务服务
- `UserService` - 用户管理
- `CinemaService` - 影院管理
- `HallService` - 影厅管理
- `MovieService` - 电影管理
- `ScheduleService` - 排片管理
- `OrderService` - 订单管理
- `SeatService` - 座位管理

#### 智能引擎服务
- `RecommendationService` - 推荐引擎
- `PricingService` - 定价引擎
- `SeatRecommendationService` - 选座推荐
- `ChatbotService` - 智能客服

#### 系统服务
- `CacheService` - 缓存管理
- `StatisticsService` - 数据统计
- `ScheduledTaskService` - 定时任务

### 4.3 核心业务流程

#### 选座购票流程
```
1. 用户选择电影 → 查询场次列表
2. 选择场次 → 获取座位布局和状态
3. 智能推荐座位（根据人数）
4. 用户选择座位 → 座位锁定15分钟（Redis）
5. 确认订单 → 智能定价计算
6. 模拟支付 → 订单状态更新为已支付
7. 释放座位锁定 → 座位标记为已售
8. 生成电子票（订单号+二维码）
```

#### 智能推荐流程
```
1. 获取用户画像（观影历史、偏好类型）
2. 并行计算三种推荐：
   - 内容推荐：基于用户偏好类型匹配电影
   - 协同过滤：找相似用户，推荐他们喜欢的电影
   - 热度推荐：热门+高分电影
3. 加权融合：内容40% + 协同30% + 热度30%
4. 过滤已观看电影
5. 返回Top 10推荐结果
6. 记录推荐日志
```

#### 智能定价算法
```
最终价格 = 基础价格 × 时段系数 × 座位系数 × 热度系数

时段系数：
- 工作日：0.8
- 周末：1.2
- 节假日：1.5
- 早场(12点前)：0.7
- 午场(12-18点)：1.0
- 晚场(18点后)：1.2

座位系数：
- 前排(1-3排)：0.8
- 中排(4-8排)：1.0
- 后排(9排以后)：0.9
- VIP区：1.5

热度系数：
- 上座率 < 30%：0.9
- 上座率 30-60%：1.0
- 上座率 60-80%：1.1
- 上座率 > 80%：1.3
```

#### 智能选座推荐
```
输入：人数、场次ID
输出：推荐座位列表

算法：
1. 获取可用座位
2. 根据人数确定策略：
   - 单人：推荐中间区域靠后位置
   - 双人：推荐情侣座或相邻中间位置
   - 多人：推荐连续座位，优先中间区域
3. 评分规则：
   - 中间列加分
   - 中后排加分
   - 避免孤岛座位（左右有空座）
   - 连续座位加分
4. 返回评分最高的3组座位
```

## 五、前端模块设计

### 5.1 用户端页面

#### 页面列表
- `/` - 首页（智能推荐、热映电影）
- `/movies` - 电影列表（筛选、搜索）
- `/movie/:id` - 电影详情
- `/seat/:scheduleId` - 选座页面
- `/order/confirm` - 订单确认
- `/order/pay/:orderId` - 支付页面
- `/orders` - 我的订单
- `/order/:id` - 订单详情
- `/profile` - 个人中心
- `/chat` - 智能客服

#### 核心组件
- `MovieCard` - 电影卡片
- `SeatMap` - 座位图（Canvas绘制）
- `RecommendSection` - 推荐区块
- `Chatbot` - 聊天机器人
- `OrderCard` - 订单卡片

### 5.2 管理端页面

#### 页面列表
- `/admin/dashboard` - 数据看板
- `/admin/cinema` - 影院管理
- `/admin/hall` - 影厅管理
- `/admin/seat-layout` - 座位布局编辑器
- `/admin/movie` - 电影管理
- `/admin/schedule` - 排片管理
- `/admin/order` - 订单管理
- `/admin/user` - 用户管理
- `/admin/statistics` - 数据统计
- `/admin/config` - 系统配置

#### 核心组件
- `SeatEditor` - 座位布局编辑器
- `Charts` - 图表组件（ECharts）
- `UploadExcel` - Excel导入
- `DataTable` - 数据表格

### 5.3 状态管理（Pinia）

#### User Store
```typescript
interface UserState {
  token: string
  userInfo: UserInfo
  isLogin: boolean
}
```

#### Order Store
```typescript
interface OrderState {
  currentOrder: Order
  selectedSeats: Seat[]
  scheduleInfo: Schedule
}
```

#### Movie Store
```typescript
interface MovieState {
  movieList: Movie[]
  currentMovie: Movie
  recommendList: Movie[]
}
```

## 六、技术实现要点

### 6.1 后端技术

#### MyBatis-Plus配置
- 代码生成器：自动生成Entity、Mapper、Service
- 分页插件：PaginationInterceptor
- 逻辑删除：deleted字段
- 自动填充：create_time、update_time

#### Redis应用
- 座位锁定：`seat:lock:{scheduleId}:{seatId}` TTL=15分钟
- Token存储：`token:{userId}` TTL=7天
- 热门电影缓存：`movie:hot` TTL=1小时
- 推荐结果缓存：`recommend:{userId}` TTL=30分钟
- 场次座位状态：`schedule:seats:{scheduleId}` 实时更新

#### Spring Security + JWT
- JWT Token生成和验证
- 基于角色的权限控制
- 接口权限注解：@PreAuthorize

#### 定时任务
- 订单超时检测：每分钟执行
- 电影数据同步：每天凌晨2点
- 热度分数计算：每小时执行
- 缓存预热：每天早上7点

#### 统一返回格式
```java
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
}
```

### 6.2 前端技术

#### Axios封装
- 请求拦截器：自动添加Token
- 响应拦截器：统一错误处理
- Loading状态管理
- 请求取消（防止重复请求）

#### 座位可视化
- Canvas绘制座位图
- 支持缩放、拖拽
- 实时更新座位状态
- 智能推荐座位高亮

#### 路由守卫
- 登录验证
- 权限验证
- 页面标题动态设置

## 七、项目结构

### 7.1 后端项目结构
```
movie-ticket-backend/
├── src/main/java/com/movie/
│   ├── MovieTicketApplication.java
│   ├── config/
│   ├── controller/
│   │   ├── user/
│   │   └── admin/
│   ├── service/
│   │   ├── impl/
│   │   ├── UserService.java
│   │   ├── OrderService.java
│   │   ├── RecommendationService.java
│   │   └── ...
│   ├── mapper/
│   ├── entity/
│   ├── dto/
│   ├── vo/
│   ├── common/
│   ├── exception/
│   ├── utils/
│   ├── task/
│   └── security/
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── mapper/
└── pom.xml
```

### 7.2 前端项目结构
```
movie-ticket-user/          # 用户端
├── src/
│   ├── views/
│   ├── components/
│   ├── stores/
│   ├── api/
│   ├── router/
│   ├── utils/
│   └── assets/
├── package.json
└── vite.config.ts

movie-ticket-admin/         # 管理端
├── src/
│   ├── views/
│   ├── components/
│   ├── stores/
│   ├── api/
│   ├── router/
│   ├── utils/
│   └── assets/
├── package.json
└── vite.config.ts
```

## 八、开发计划

### 第一阶段：项目搭建（1周）
- 创建SpringBoot项目，配置依赖
- 创建Vue3项目（用户端+管理端）
- 数据库设计并建表
- 配置Redis、MyBatis-Plus
- 搭建基础框架

### 第二阶段：用户认证与基础数据（1周）
- 实现用户注册、登录（JWT）
- 权限管理（Spring Security）
- 影院、影厅、座位管理
- 电影基础CRUD
- 管理端基础页面

### 第三阶段：核心购票流程（2周）
- 排片管理
- 电影列表、详情页
- 选座页面（可视化）
- 订单创建、支付
- 座位锁定机制
- 订单查询

### 第四阶段：智能功能（2-3周）
- 智能推荐引擎
- 智能定价引擎
- 智能选座推荐
- 智能客服

### 第五阶段：完善功能（1-2周）
- 退票改签
- 订单超时处理
- 电影数据同步
- 数据统计报表
- Redis缓存优化

### 第六阶段：测试与优化（1周）
- 功能测试
- 性能优化
- 接口文档完善
- 部署文档编写

**总计：8-10周**

## 九、部署方案

### 9.1 本地开发环境
- JDK 17
- MySQL 8.0
- Redis 7.0
- Node.js 18+
- Maven 3.8+

### 9.2 本地运行步骤
1. 启动MySQL，导入数据库脚本
2. 启动Redis
3. 启动SpringBoot后端（端口8080）
4. 启动用户端前端（端口3000）
5. 启动管理端前端（端口3001）

### 9.3 云服务部署预留
- Docker容器化
- docker-compose编排
- Nginx反向代理
- 预留云服务器配置说明

## 十、技术亮点

1. **智能推荐系统**：混合推荐算法（内容+协同过滤+热度）
2. **智能定价模型**：多维度动态定价（时段+座位+热度）
3. **智能选座推荐**：基于规则的座位推荐算法
4. **智能客服**：FAQ规则匹配 + AI API兜底
5. **Redis缓存优化**：多级缓存策略，提升性能
6. **座位可视化**：Canvas绘制，支持缩放拖拽
7. **分布式锁**：Redis实现座位锁定机制
8. **定时任务**：订单超时自动处理
9. **前后端分离**：Vue3 + TypeScript + SpringBoot
10. **权限管理**：Spring Security + JWT + RBAC

## 十一、预期成果

1. 完整的前后端分离系统
2. 用户端和管理端两套前端应用
3. 完善的API接口文档
4. 数据库设计文档
5. 部署文档
6. 答辩PPT
7. 演示视频

---

**设计完成日期**: 2026-05-16  
**预计开发周期**: 8-10周  
**适用场景**: 本科毕业设计
