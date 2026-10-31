# 变更记录

> 按时间倒序记录项目的功能演进与重要修复。  
> 模块细节见 [ARCHITECTURE.md](ARCHITECTURE.md)；启动步骤见 [GETTING_STARTED.md](GETTING_STARTED.md)。

格式：`新增` / `修复` / `优化` + 一句话描述。

---

## 2026-10-19

- **修复** 仅调整“批量删除”按钮为与其他批量按钮一致的样式；删除前弹出确认提示，说明数量及不可恢复性，点击“确认删除”后才提交请求。
- **修复** 批量操作组件的删除开关显式默认启用，恢复电影、影厅、排片和兑换码页面缺失的批量删除按钮；影院管理员的禁止删除配置继续生效。
- **优化** 后台管理表格按内容区宽度自动分配列宽，完整显示长文本和操作按钮，避免底部横向滚动；窄屏按记录展示全部字段，兑换记录、经营分析及推荐评估表格同步适配，筛选与分页支持换行。
- **修复** 管理后台切换菜单后丢失查询条件：排片、影院、影厅、电影、订单、用户和兑换码列表统一保留筛选、页码及每页条数；返回页面按保留条件重新请求最新数据。
- **优化** 智能排片保留影院、日期、策略和自定义参数，经营分析抽屉保留预测条件，推荐评估页保留参数及已完成结果；当前标签页刷新可恢复，按账号、角色与影院归属隔离，退出登录清理。
- **新增** 后台页面状态浏览器回归，覆盖菜单切换、刷新、重置、“全部”状态、账号隔离、日期失效以及存储异常降级。

## 2026-09-03

- **新增** 推荐系统严格时间 leave-one-out 评估：截断未来行为/订单/评价/趋势，加入热门与纯内容基线、三组消融，以及 Precision@K、Recall@K、NDCG@K、覆盖率和多样性
- **新增** 用户端推荐理由与五维偏好雷达，管理端新增推荐算法实验评估页
- **新增** Google OR-Tools CP-SAT 多目标排片：约束片长、清场、影厅冲突、场次数、单片上限、黄金时段与影片多样性，比较人工、启发式和优化方案
- **优化** 排片预测改为按时间 80/20 外推测试，报告 MAE、RMSE、MAPE 和全局均值基线，不再用 `100-MAE` 冒充准确率

## 2026-09-01

- **新增** 登录、下单、支付、取票和退票完整接口回归，以及管理员/普通用户越权测试
- **新增** Redis 锁座、数据库占座兜底和并发购票防超卖测试，库存扣减与归还改为原子 SQL
- **新增** MySQL 8、Redis 7 Testcontainers 真实依赖测试和用户端/管理端 Playwright Chromium E2E
- **优化** 一键验收脚本支持 `-Integration` 与 `-E2E` 分层执行，新增自动化测试指南

## 2026-08-31

- **安全** 移除后端配置中的真实 AI 密钥，数据库、JWT 与 DashScope 凭据改由环境变量注入
- **优化** 重整文档、设计稿和辅助脚本目录，新增完成度报告、数据库指南和一键验收脚本
- **优化** 新增 Maven Wrapper，干净环境无需预装 Maven
- **优化** 数据重置脚本同步清理优惠券与推荐行为；v5/v6 迁移补充数据库选择并增强幂等性
- **验证** 后端 12 项单元测试全部通过，用户端与管理端生产构建通过

## 2026-08-29

- **修复** 用户端订单确认路由恢复使用 `OrderConfirm.vue`，选座后先进入确认页，确认提交并创建订单后再跳转支付页
- **修复** Redis 不可用时热门推荐自动降级查询数据库，避免 `/recommend/hot` 返回 500；本地默认启用 `dev` 内嵌 Redis；影院和场次 GET 接口加入匿名访问白名单
- **修复** 访客首页的可选数据请求改为静默降级，并校验本地 JWT 有效期，避免残留/过期令牌触发“权限不足”或推荐服务异常弹窗
- **新增** 2026-08-30 至 2026-09-30 功能测试排片脚本，4 部指定电影每天各 2 场，共 256 场，支持重复执行并自动跳过冲突

## 2026-08-28

- **修复** 票房排行按订单所属场次映射电影后分别聚合，避免每部电影重复统计全部已支付订单；影院统计同步限定为本影院场次，并补充回归测试
- **修复** 数据库结构与当前代码对齐：订单数量统一使用 `seat_count`，补齐取票/支付/取消字段，取票码改为唯一索引，影厅补齐并自动维护 `total_seats`
- **优化** 新库初始化统一使用 `db/schema.sql` + `db/data.sql`；已有业务库使用 `migration_v3_schema_alignment.sql` 原地升级，保留用户、订单及历史扩展表数据

---

## 2026-06-02

- **新增** 电影评价：新增 `comment` 表（`movie_id / user_id / order_id / rating / content`，`uk_user_movie` 唯一约束防重复评价），`CommentController`（`POST /comment` 发布、`GET /comment/movie/{movieId}` 列表公开、`GET /comment/eligibility` 资格查询）+ `CommentService`。**资格规则：必须购票（订单已支付）且已观影（场次开始时间已过）才能评价**，每位用户每部电影限评一次；发布后按全部评分均值（1-5 星 ×2）回写 `movie.rating`（见 `migration_add_comment.sql`）
- **新增** 用户端评价 UI：`MovieDetail.vue` 新增"观众评价"卡片（平均分 + 星级 + 评价列表）与写评价对话框（`el-rate` 打分 + 文本），`api/comment.ts`、`types` 新增 `Comment / CommentEligibility`；无资格时按"购票观影后才能评价 / 已评价"分别提示
- **优化** 用户端"收藏"统一更名为"想看"：左侧菜单导航此前已是"想看"，本次同步 `Home.vue / Movies.vue / MovieDetail.vue` hero 区残留的"收藏"按钮文案
- **优化** 个人中心快捷功能区从右下移至左侧边栏底部（钱包卡下方），窄列下两列网格 + 字号收敛

## 2026-06-01

- **新增** 用户端修改密码：`POST /user/change-password`（`ChangePasswordRequest`，校验原密码、禁止新旧相同、BCrypt 加密），`Profile.vue` 修改密码对话框 + `api/user.ts changePassword`，修改成功后自动登出重登
- **新增** 退票：`POST /order/{orderNo}/refund` + `OrderService.refundOrder`，仅"已支付"可退，**开场前 30 分钟内禁退、24 小时内扣 10% 手续费、24 小时外全额退款**，置状态 3（已退款）并恢复座位 / 释放 Redis 座位锁；`OrderDetail.vue` 申请退票按钮 + 手续费/退款金额可视化确认弹窗，`api/order.ts refund`

## 2026-05-31

- **新增** 取票功能：`ticket_order` 增加 `ticket_code / is_picked / pick_time` 字段及索引，`OrderService.payOrder` 自动生成 8 位取票码，新增 `POST /order/pick` 接口（见 `migration_add_ticket_pickup.sql`）
- **新增** 用户端取票组件：`TicketQRCode.vue`（二维码生成 / 下载 / 复制）、`QRScanner.vue`（`html5-qrcode` 摄像头扫码 + 前/后置切换 + 手输兜底），首页加"扫码取票"入口，订单详情 / 个人中心按 `isPicked` 展示状态
- **新增** 管理端用户管理：`UserController`（分页 / 详情 / 更新 / 启用禁用 / BCrypt 重置密码 / 删除）+ 前端 `User.vue`、`api/user.ts`，"用户管理"菜单仅 ADMIN 可见
- **新增** 管理端排片管理：`Showtime.vue`，电影→影院→影厅级联，自动按片长算结束时间，状态可视化，座位余量 <10 标黄
- **新增** 管理端订单管理：`Order.vue`、`api/order.ts`，按订单号/状态筛选、详情弹窗、取消"待支付"订单
- **修复** 海报上传成功后图片 404：`FileUploadService` 返回裸文件名导致浏览器按相对路径解析，改为返回 `/api/uploads/<file>` 完整 URL；老数据用 `migration_fix_poster_url.sql` 补齐
- **修复** `/movie/now-showing`、`/movie/coming-soon` 返回 500：`RedisConfig.ObjectMapper` 未注册 `JavaTimeModule`，`MovieVO` 中的 `LocalDate / LocalDateTime` 序列化失败；注册模块 + 禁用 timestamps，`MovieService` 缓存读写加 try-catch 降级查库
- **修复** 排片状态字段不一致：`migration_fix_showtime_status.sql`

## 2026-05-30

- **新增** 用户端个人中心编辑资料：昵称 / 性别 / 生日 / 城市 / 偏好 / 签名
- **新增** 管理端影院管理完整 CRUD + 状态切换 + 多图上传组件（拖拽 / 点击 / 缩略图 / 单图预览 / 删除，最多 5 张）
- **新增** 后端文件上传：`POST /upload/image`、`FileUploadService`（UUID 重命名、类型 / 大小校验）、`WebMvcConfig` 静态映射 `/uploads/**`、`application.yml` 上传配置（5MB / jpg|png|gif|webp|bmp）

## 2026-05-16（项目集中开发期）

### 第一阶段：项目搭建
- 后端：SpringBoot 3.2.5 + Maven，分层架构（Controller/Service/Mapper/Entity）；Spring Security + JWT + Redis + MyBatis-Plus；统一 `Result<T>` 响应 + 全局异常 + 用户注册登录
- 数据库：6 张表（user/cinema/hall/movie/showtime/ticket_order）+ 初始数据脚本
- 用户端：Vue 3.4 + TS 5.3 + Vite 5.1 + Element Plus 2.6，路由 + Pinia + Axios + JWT + 路由守卫，登录/注册/首页框架
- 管理端：同上技术栈 + ECharts 5.5，目录结构与构建配置就绪

### 第二阶段：基础数据
- 电影 / 影院 / 影厅 / 场次：四套完整 CRUD + 分页 + 多条件筛选 + 状态切换
- 场次创建校验：电影/影院/影厅存在性、影厅归属、时间合法性、时间冲突检测、自动算座位数
- 管理端：登录页 + Dashboard（占位）+ 影院管理基础页 + 主布局（侧栏/面包屑/用户下拉）
- 权限：`@PreAuthorize` 区分 ADMIN / CINEMA_ADMIN

### 第三阶段：核心购票流程
- 订单服务：创建（含 Redis 锁座）+ 我的订单 / 管理端订单 / 详情 / 支付（模拟） / 取消（释放座位）
- Redis 分布式锁：15 分钟自动过期防超卖
- 用户端 8 个页面：首页 → 详情 → 场次 → 选座（Canvas）→ 确认 → 支付 → 列表 → 详情
- 选座 UI：Canvas 绘制银幕 + 座位，绿/蓝/灰三色，单次最多 5 座，实时算价

### 第四阶段：智能推荐与统计
- 推荐：热门（评分×10 + 近7天订单×2 + 时间衰减；Redis 缓存 1h）/ 个性化（基于历史，过滤已看）/ 相似（按类型重叠度）
- 统计：Dashboard（电影/影院/今日订单/今日收入/用户）/ 票房排行 / 场次上座率 / 收入趋势
- 用户端：首页"热门推荐" + "为你推荐" 模块；详情页"相似电影"
- 管理端：Dashboard 4 张统计卡 + 票房排行表 + 快捷入口
