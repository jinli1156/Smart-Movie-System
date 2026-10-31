# 电影购票系统 - 后端

基于 SpringBoot 3.x 的电影购票系统后端服务

## 技术栈

- SpringBoot 3.2.5
- MyBatis-Plus 3.5.6
- Spring Security + JWT
- Redis
- MySQL 8.0
- Maven 3.8+
- JDK 21

## 项目结构

```
movie-ticket-backend/
├── src/main/java/com/movie/ticket/
│   ├── MovieTicketApplication.java    # 启动类
│   ├── config/                        # 配置类
│   │   ├── SecurityConfig.java        # Security配置
│   │   ├── RedisConfig.java           # Redis配置
│   │   ├── MyMetaObjectHandler.java   # MyBatis Plus自动填充
│   │   └── DataInitializer.java       # 初始数据初始化
│   ├── controller/                    # 控制器
│   │   ├── AuthController.java        # 认证接口
│   │   ├── MovieController.java       # 电影接口
│   │   ├── CinemaController.java      # 影院接口
│   │   ├── HallController.java        # 影厅接口
│   │   ├── ShowtimeController.java    # 场次接口
│   │   ├── OrderController.java       # 订单接口
│   │   ├── RecommendController.java   # 推荐接口
│   │   └── StatisticsController.java  # 统计接口
│   ├── service/                       # 服务层
│   │   ├── AuthService.java           # 认证服务
│   │   ├── MovieService.java          # 电影服务
│   │   ├── CinemaService.java         # 影院服务
│   │   ├── HallService.java           # 影厅服务
│   │   ├── ShowtimeService.java       # 场次服务
│   │   ├── OrderService.java          # 订单服务
│   │   ├── RecommendService.java      # 推荐服务
│   │   └── StatisticsService.java     # 统计服务
│   ├── mapper/                        # 数据访问层
│   │   ├── UserMapper.java
│   │   ├── CinemaMapper.java
│   │   ├── HallMapper.java
│   │   ├── MovieMapper.java
│   │   ├── ShowtimeMapper.java
│   │   └── TicketOrderMapper.java
│   ├── entity/                        # 实体类
│   │   ├── User.java
│   │   ├── Cinema.java
│   │   ├── Hall.java
│   │   ├── Movie.java
│   │   ├── Showtime.java
│   │   └── TicketOrder.java
│   ├── dto/                           # 数据传输对象
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── CinemaDTO.java
│   │   ├── HallDTO.java
│   │   ├── MovieDTO.java
│   │   ├── ShowtimeDTO.java
│   │   └── OrderCreateDTO.java
│   ├── vo/                            # 视图对象
│   │   ├── LoginResponse.java
│   │   ├── CinemaVO.java
│   │   ├── HallVO.java
│   │   ├── MovieVO.java
│   │   ├── ShowtimeVO.java
│   │   └── OrderVO.java
│   ├── security/                      # 安全相关
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── UserDetailsImpl.java
│   │   └── UserContext.java
│   ├── common/                        # 公共类
│   │   └── Result.java                # 统一响应
│   ├── exception/                     # 异常处理
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   └── utils/                         # 工具类
│       └── JwtUtil.java               # JWT工具
├── src/main/resources/
│   ├── application.yml                # 配置文件
│   ├── db/
│   │   ├── schema.sql                 # 创建空数据库的引导脚本
│   │   ├── data.sql                   # 初始数据
│   │   └── flyway/                    # Flyway 版本化结构迁移
│   └── mapper/                        # MyBatis XML
└── pom.xml                            # Maven配置
```

## 快速开始

### 1. 环境准备

确保已安装：
- JDK 21
- Maven 3.8+
- MySQL 8.0
- Redis 7.0

### 2. 数据库初始化

```bash
# 登录MySQL
mysql -u root -p

# 只创建空数据库
source src/main/resources/db/schema.sql
```

随后启动后端，Flyway 会自动创建或升级业务表、索引、外键和检查约束。需要演示数据时，应在 Flyway 完成 V24 后停止后端，再执行：

```bash
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE src/main/resources/db/data.sql;"
```

导入后重新启动后端。演示排片覆盖北京时间执行当天至未来第 10 天（含首尾共 11 天），每天最多四场，自动校验影片、影厅归属并计算片长；影院管理员已绑定影院 1，普通用户已有新人券，兑换码为 `JINLI699`。

已有业务数据的数据库不要执行 `data.sql`。请先完整备份，再直接启动后端；首次接管时 Flyway 将现有库标记为版本 6，并执行 V7 及后续迁移至 V24。V8 会归档可再生成的孤立推荐行为，但发现核心业务孤儿数据或非法状态时会中止升级，需修复数据后重试。V24 会预检并统一旧库非空字段、排序规则和取票码唯一约束。

### 3. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库和Redis连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/movie_ticket?...
    username: root
    password: your_password
  
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
```

### 4. 启动项目

先在操作系统或 IDE 的运行配置中注入 DeepSeek API Key，或使用项目根目录已忽略的 `.local/assistant.properties`。不要把真实密钥写入 `application.yml` 或提交到 Git：

```powershell
# Windows PowerShell：写入当前用户环境变量，重启终端/IDE 后生效
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "<你的密钥>", "User")
```

电影小助手默认通过 `https://api.deepseek.com` 调用 `deepseek-v4-flash`。模型可通过 `AI_ASSISTANT_MODEL` 覆盖。独立页面、系统提示词、只读工具和流式协议见 [电影小助手说明](../docs/MOVIE_ASSISTANT.md)。

```bash
# 使用Maven启动
mvn spring-boot:run

# 或者先打包再运行
mvn clean package
java -jar target/movie-ticket-backend-1.0.0.jar
```

服务将在 `http://localhost:8080/api` 启动

如果 IDE 启动出现 `NoClassDefFoundError: com/movie/ticket/entity/User`，先确认编译已经完成。若 `User.java` 存在，这类错误通常来自未完成或不一致的 `target/classes`，不需要修改实体或 Mapper 注入。停止本项目后端后，在后端目录顺序执行：

```powershell
.\mvnw.cmd clean package
if ($LASTEXITCODE -eq 0) {
    java -jar target/movie-ticket-backend-1.0.0.jar
}
```

上述构建只清理后端 `target` 产物，并运行测试；只有构建成功才启动。构建期间不要同时使用 IDE 编译相同的输出目录。使用打包 JAR 运行时，后续 IDE 增量编译不会替换运行中的业务类。

## API文档

### 认证接口

#### 用户登录
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

#### 用户注册
```
POST /api/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "password": "123456",
  "nickname": "测试用户",
  "phone": "13800138888"
}
```

### 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 超级管理员 |
| cinema_admin | admin123 | 影院管理员 |
| user1 | admin123 | 普通用户 |

## 开发说明

### 添加新功能

1. 在 `entity` 包创建实体类
2. 在 `mapper` 包创建Mapper接口
3. 在 `service` 包创建Service类
4. 在 `controller` 包创建Controller类
5. 在 `dto` 和 `vo` 包创建数据传输对象

### 权限控制

使用 `@PreAuthorize` 注解控制接口权限：

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public Result<?> getUsers() {
    // ...
}
```

### 统一响应格式

所有接口返回统一的 `Result<T>` 格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

## 已实现功能

### 认证模块
- ✅ 用户登录 / 注册
- ✅ JWT 令牌生成与验证
- ✅ 角色权限控制（USER / CINEMA_ADMIN / ADMIN）

### 电影模块
- ✅ 电影 CRUD（列表/详情/新增/编辑/删除）
- ✅ 上映状态管理
- ✅ 按类型/关键词搜索

### 影院模块
- ✅ 影院 CRUD
- ✅ 影厅 CRUD（含座位布局 JSON）

### 场次模块
- ✅ 场次 CRUD
- ✅ 按电影/影院/日期查询

### 订单模块
- ✅ 座位状态查询
- ✅ Redis 分布式锁座
- ✅ 订单创建 / 支付（模拟）/ 取消
- ✅ 订单列表 / 详情查询

### 推荐模块
- ✅ 热门推荐（热度算法 + Redis 缓存）
- ✅ 个性化推荐（基于观影历史）
- ✅ 相似电影推荐（基于类型匹配）

### 统计模块
- ✅ Dashboard 统计（电影数/影院数/今日订单/收入/用户数）
- ✅ 票房排行榜
- ✅ 场次上座率统计
- ✅ 收入趋势分析

## API 接口清单

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/register | 用户注册 |

### 电影
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/movies | 电影列表（分页+搜索） |
| GET | /api/movies/{id} | 电影详情 |
| GET | /api/movies/playing | 正在热映 |
| GET | /api/movies/coming | 即将上映 |

### 影院
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/cinemas | 影院列表 |
| GET | /api/cinemas/{id} | 影院详情（含影厅） |

### 场次
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/showtimes/movie/{movieId} | 按电影查场次 |
| GET | /api/showtimes/cinema/{cinemaId} | 按影院查场次 |
| GET | /api/showtimes/{id} | 场次详情 |
| POST | /api/showtime/{id}/seat-recommendation | 自然语言智能选座、模型解释与实时推荐 |

### 选座
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/orders/seats/{showtimeId} | 查询座位状态 |
| POST | /api/orders/lock | 锁定座位 |
| POST | /api/orders/unlock | 释放座位 |

### 订单
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/orders | 创建订单 |
| GET | /api/orders/my | 我的订单列表 |
| GET | /api/orders/{orderNo} | 订单详情 |
| PUT | /api/orders/{orderNo}/pay | 支付订单 |
| PUT | /api/orders/{orderNo}/cancel | 取消订单 |

### 推荐
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/recommend/hot | 热门推荐 |
| GET | /api/recommend/personal | 个性化推荐 |
| GET | /api/recommend/similar/{movieId} | 相似推荐 |

### 统计
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/statistics/dashboard | Dashboard 数据 |
| GET | /api/statistics/box-office | 票房排行 |
| GET | /api/statistics/showtime/{id} | 场次统计 |
| GET | /api/statistics/revenue | 收入趋势 |

## 当前验证与后续工作

- [x] 退票、评价、个人中心与 AI 电影助手接口
- [x] 后端快速测试，覆盖核心服务、完整购票接口、权限与并发防超卖
- [x] MySQL/Redis Testcontainers 真实依赖集成测试
- [x] 用户端与管理端 Playwright 浏览器 E2E
- [ ] 改签接口
- [ ] 操作审计日志、健康检查与生产部署配置

项目级验收方法见 [GETTING_STARTED.md](../docs/GETTING_STARTED.md) 和 [TESTING.md](../docs/TESTING.md)。
