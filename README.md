# 响指电影——智能电影购票系统

基于 Spring Boot 3、Vue 3、TypeScript、MySQL 和 Redis 的电影购票系统，包含用户端、管理端和后端服务，同时维护独立的 uniCloud 阿里云迁移工程。主要能力覆盖购票、模拟支付、取票、退票、评价、混合推荐、AI 观影规划、确认下单和智能排片。

**总说明更新日期：2026-10-09（北京时间）。** 功能与接口按当前工作区源码整理，包含尚未提交的改动；测试和部署进展按下文标注日期的已有记录汇总。本次文档整理完成源码、命令和链接核对，未重新运行业务测试或访问线上环境。

## 当前进度与运行边界

Spring Boot 本地版已实现核心购票链路及智能功能，已有后端、真实本机依赖、浏览器与构建验证记录。uniCloud 版已形成独立代码和部署副本，2026-10-09 的记录显示新空间部署及基础接口、登录验证完成；云端并发、事务、AI 工具多轮调用和算法一致性仍待验收。

| 目录 | 职责与技术 | 默认访问位置 | 进度依据 |
| --- | --- | --- | --- |
| `movie-ticket-backend/` | Spring Boot 3.2.5、Java 21、MyBatis-Plus、Security、JWT、Flyway、MySQL、Redis、OR-Tools | `http://localhost:8080/api` | 源码已覆盖核心业务；2026-09-27 权限回归记录 |
| `movie-ticket-user/` | Vue 3、TypeScript、Vite、Element Plus、Pinia；浏览、购票、个人中心和 AI 助手 | `http://localhost:3000` | 购票与助手等浏览器测试；已有生产构建通过记录 |
| `movie-ticket-admin/` | Vue 3、TypeScript、Element Plus、ECharts；业务管理、统计、推荐评估和排片工作台 | `http://localhost:3001` | 权限、排片等浏览器测试；已有生产构建通过记录 |
| `movie-ticket-unicloud/` | 两端独立前端副本、uniCloud Node.js 云函数、云数据库集合和网页托管 | 用户端根路径、管理端 `/admin/`、API `/api` | 2026-10-09 新空间部署记录；完整业务验收仍待完成 |

两套后端采用不同的数据存储和部分算法实现，测试结果及推荐实验结果分别记录：

| 能力 | Spring Boot 本地版 | uniCloud 迁移版 |
| --- | --- | --- |
| 数据与座位占用 | MySQL 事务、库存条件更新、Redis 座位锁及数据库占用校验 | `mts_` 集合、云端座位占用与业务处理；真实云事务和并发待验收 |
| 电影推荐 | 混合推荐、严格时间切分、基线和消融实验 | 规则推荐及评估；现有汇总数据可能含后续信息 |
| 排片优化 | OR-Tools CP-SAT 多目标约束求解 | 约束启发式方案；未实现 CP-SAT 求解及全局最优证明 |
| AI 助手 | DeepSeek 工具调用、多轮 SSE、会话与观影计划持久化 | 整段回答、查询工具、会话及确认卡迁移代码；多轮工具流程待完整验收 |
| 超时订单 | 后端定时任务取消超过 10 分钟的待支付订单 | 订单查询或支付时检查并清理；列表每次最多清理 20 笔 |
| 验证方式 | JUnit、隔离 MySQL/Redis 集成、Playwright | 本地 Node.js 测试与云端基础接口验收；内存替身测试不等同于真实云数据库验证 |

详情见 [影院权限说明](docs/CINEMA_PERMISSIONS.md) 和 [uniCloud 部署说明](movie-ticket-unicloud/README.md)。

## 已实现功能总览

以下为 Spring Boot 本地版的功能总览；云端迁移范围与差异见上表及云端部署说明。

### 用户与观影服务

管理端已增加 **AI 辅助上架**（`/listing`）：上传电影、影院或影厅资料，提取为独立待审核草稿，支持原文对照、暂存、驳回和确认发布。提供 Excel、CSV、JSON 模板，默认下载中文 Excel；支持 PDF、DOCX、文字和图片资料。详见 [AI 辅助上架](docs/AI_LISTING.md)。

- **账号与权限**：注册、图片验证码登录、JWT 会话、路由守卫、个人资料编辑、密码修改；后端每次请求检查数据库中的账号状态、角色及影院归属。
- **电影与影院**：电影搜索、类型筛选、热映与待映、详情、海报与预告、票房与影片分析；影院列表、详情、位置与距离展示、地图入口及场次查询。
- **购票闭环**：选择场次、可视化选座、连座推荐、订单确认、优惠试算、模拟支付、订单列表与详情、取消待支付订单、超时取消和退票。
- **取票与评价**：取票码、二维码展示与下载、摄像头扫码或手动输入核销；满足购票观影条件后评价，按评价回写影片评分。
- **AI 观影纪念票**：已支付订单可生成专属星空科幻纪念卡；AI 创作背景，程序绘制真实订单信息，支持保存 PNG 与分享，明确标记非入场凭证。见 [功能说明](docs/AI_SOUVENIR_TICKET.md)。
- **AI 个性化电影海报**：用户端 `/posters` 支持电影主题、六种风格、一句话创意、可选人物照片和三种画幅；异步生成、历史恢复、PNG 下载与分享。见 [功能说明](docs/AI_PERSONAL_POSTER.md)。
- **个人资产与偏好**：想看列表、冷启动类型偏好、优惠券、兑换码领取、积分抵扣与推荐偏好雷达。
- **影厅布局**：支持停用座位及多条纵向过道；前后端共享座位布局，连座判断不会跨越过道。见 [影厅过道配置](docs/HALL_AISLES.md)。

普通购票流程：

```text
电影/影院 → 场次 → 登录 → 选座 → 确认订单 → 模拟支付
                                         ↓
                        订单详情 → 取票 → 观影后评价
                                         ↘ 符合规则时退票
```

### AI 助手、观影规划与确认下单

独立入口为用户端 `/assistant`，复用站内真实业务数据与本人身份。

- **业务查询与知识问答**：电影、影院、场次、座位、本人订单、优惠试算和偏好查询；购票规则采用本地关键词知识检索，来源见 [知识库](movie-ticket-backend/src/main/resources/assistant/knowledge.json)。
- **连续观影规划**：记住日期、时间、人数、预算、评分、时长、影院、座位偏好、连座、排除类型/影片及观影场景，支持“改成明天，其他不变”“选第二个”“不用积分”等增量修改。
- **完整方案**：结合真实可售场次、优惠后总预算和连座情况输出候选方案，可带入选座流程；无结果时说明条件交集、预算或连座限制，保留用户条件。
- **会话与长期偏好**：登录用户可恢复、继续和删除历史会话，观影条件独立于最近问答保存；支持保存和管理长期排除偏好，访客状态保留于页面内存。
- **确认下单**：生成包含场次、人数、座位、原价、优惠及预计实付的确认卡。卡片有效期 5 分钟；用户点击或明确确认后，服务端复查库存、价格和优惠，创建待支付订单并进入原有支付页。
- **幂等与权限**：同一卡片的重复确认返回同一订单；过期、座位占用或报价变化会被拒绝。模型工具只能准备确认卡，支付由用户在支付页完成。
- **聊天交互**：多轮流式回答、停止生成、真实结果卡片，以及支持浏览器上的语音输入与回答朗读。模型未配置或不可用时显示对应状态。

当前 [工具定义](movie-ticket-backend/src/main/resources/assistant/tools.json) 包含 **15 个工具**；[系统提示词](movie-ticket-backend/src/main/resources/assistant/system-prompt.md) 与工具执行共同决定助手能力。

详细说明：[电影小助手](docs/MOVIE_ASSISTANT.md)、[连续观影规划](docs/ASSISTANT_PLANNING.md)、[AI 确认下单](docs/ASSISTANT_CHECKOUT.md)。

### 智能比价、优惠券与积分

- 电影详情、支付相关流程和助手支持站内同日跨影院场次比价，比较总实付、影院、影厅、时间及优惠组合。
- 比价使用本系统排片与本人资产；支持日期、人数、用券和积分偏好。页面比价由后端计算，助手自然语言查询需要模型配置。
- 优惠试算与支付共用 [价格计算器](movie-ticket-backend/src/main/java/com/movie/ticket/service/TicketPriceCalculator.java)：每单最多一张券，先扣券，再按 100 积分抵 1 元，积分每单最多抵 1 元，并受余额与剩余应付限制。
- 比价查询不锁座、不占券、不扣积分；余票为查询时的快照，付款时重新校验实际订单和优惠资格。
- 用户可输入兑换码领取优惠券；服务端检查生效时间、有效期、启用状态、发放上限和账号重复领取，同一兑换码每个账号限领一次。
- 平台管理员可维护兑换码、优惠金额、使用门槛、领取后有效天数、最大发放量、生效/截止时间与状态，并查询兑换记录。

详情见 [AI 智能比价](docs/AI_PRICE_COMPARISON.md)；兑换入口见 [CouponController](movie-ticket-backend/src/main/java/com/movie/ticket/controller/CouponController.java)，后台入口见 [AdminCouponController](movie-ticket-backend/src/main/java/com/movie/ticket/controller/AdminCouponController.java)。

### 推荐算法与实验评估

本地版提供热门、个性化与相似推荐，融合显式偏好、浏览、想看、已支付购票、评价、内容特征、场景与近期趋势，并支持时间衰减、多样性重排、推荐理由和五维偏好雷达。

平台管理员可运行严格时间 leave-one-out 评估，比较完整混合模型、热门/纯内容基线和三组消融，记录 Precision@K、Recall@K、NDCG@K、Coverage、Diversity，并导出 JSON。评估会截断未来行为、订单、评价与趋势；缺少历史快照的显式偏好在离线评估时禁用。

接口实现见 [RecommendController](movie-ticket-backend/src/main/java/com/movie/ticket/controller/RecommendController.java)；实验口径见 [推荐算法评估](docs/RECOMMENDATION_EVALUATION.md) 和 [创新点与实验复现](docs/INNOVATION_EXPERIMENTS.md)。云端规则评估采用独立口径。

### 管理后台与智能排片

- **基础管理**：电影、影院、影厅、场次、订单和用户管理；海报与影院图片上传，订单详情、取消、退票和取票核销。
- **运营与实验**：Dashboard、票房榜、收入趋势、场次统计，以及平台管理员专用的推荐实验和优惠券兑换码管理。
- **排片工作台**：选择影院与日期、生成整日草案、时间轴查看与微调、换片、时间与基础票价编辑、添加/移除未锁定场次、检查比较后确认应用。
- **约束与预测**：CP-SAT 按收入、上座率和影片多样性优化，同时约束影厅冲突、片长、清场间隔、上映日期、影片上限和黄金时段；历史预测采用时间外测试，提供 MAE、RMSE、MAPE 及基线。
- **经营分析与 AI 解释**：总览、热力图、单场预测、换片模拟和优化分析使用真实统计与算法结果；模型补充经营建议，响应区分模型来源、算法来源及不可用状态。
- **保存与恢复草稿**：支持服务器暂存、列出、恢复和删除本人草案，同时校验当前影院权限。暂存不发布；恢复后的草案应用时仍须重新校验快照与售票状态。
- **发布保护**：已售、待付款、已开场场次保持锁定；应用整日变化使用事务并检查版本与库存变化。发生冲突时返回 409，要求刷新与重新检查。

工作台细节见 [智能排片说明](docs/AI_SCHEDULING.md)；草稿接口和权限见 [SchedulingSavedDraftController](movie-ticket-backend/src/main/java/com/movie/ticket/controller/SchedulingSavedDraftController.java)。

## AIGC 营销工作台

管理端新增“营销工作台”统一入口，页内切换营销创作、任务与素材、审核与发布。运营可按影院和影片批量生成文案与海报，人工编辑后保存内容版本，由平台管理员审核并发布到首页或影院活动位。图片支持 OpenAI Images 兼容接口（本机配置为夯炸了 AI 的 `gpt-image-2`）及智谱协议，文案独立复用 DeepSeek 或智谱文本服务；包含任务恢复、影院权限、版本审核、发布幂等、有效期和撤下。视频生成暂缓；uniCloud 副本已迁移营销、上架、个人海报及纪念票，部署结果与验收限制见 [实际部署记录](docs/DEPLOYMENT_AI_2026-10-31.md)。

配置、使用流程与验证方法见 [AIGC 营销工作台](docs/AIGC_MARKETING.md)。真实 Key 仅保存在被 Git 忽略的本机配置中。

## 角色与数据权限

| 角色 | 数据范围与主要权限 |
| --- | --- |
| 普通用户 `USER` | 查询公开目录，管理本人资料、订单、优惠券、想看、评价和助手会话 |
| 影院管理员 `CINEMA_ADMIN` | 管理所属影院的资料、影厅、场次、订单、核销、经营统计和排片；草稿还须属于本人 |
| 平台管理员 `ADMIN` | 全局业务管理、影院分配、用户管理、电影库、兑换码维护与推荐算法评估 |

影院管理员未分配影院时，管理请求返回 403。用户角色、影院或禁用状态变化后，旧 JWT 在下一次请求即按数据库中的新权限处理。支付仍限订单本人；管理员进行取消、退票、取票也须满足业务规则。

现有 `cinema_admin` 账号须由 `admin` 在“用户管理 → 编辑 → 所属影院”完成分配。升级由 Flyway V21 增加影院关联；完整矩阵与回归方法见 [影院权限与页面一致性](docs/CINEMA_PERMISSIONS.md)。

## 项目目录与数据库

```text
movie-ticket-system/
├── movie-ticket-backend/                 # Spring Boot 后端
│   ├── src/main/java/com/movie/ticket/   # 控制器、服务、权限、数据访问
│   ├── src/main/resources/assistant/     # 系统提示词、工具定义、规则知识
│   ├── src/main/resources/db/flyway/     # 版本化数据库迁移
│   ├── src/test/                        # 单元、接口与集成测试
│   └── uploads/                         # 本地演示图片
├── movie-ticket-user/                    # Vue 用户端及 Playwright 测试
├── movie-ticket-admin/                   # Vue 管理端及 Playwright 测试
├── movie-ticket-unicloud/                # 独立云端迁移工程
│   ├── frontend/                        # 用户端、管理端副本
│   ├── movie-ticket-unicloud-cloud/     # 云函数与集合 Schema
│   ├── scripts/                         # 清单生成、数据导出、构建与验证
│   └── test/                            # 本地云函数测试
├── docs/                                # 功能说明、工程参考与历史报告
├── scripts/verify.ps1                    # 本地版统一验收入口
├── output/                              # 既有测试证据、图集与论文产物
├── .env.example                         # 环境变量示例
└── README.md                            # 本总说明
```

MySQL 结构以 [Flyway 迁移目录](movie-ticket-backend/src/main/resources/db/flyway/) 为依据，当前最高迁移版本为 **V21**：

| 数据域 | 表 |
| --- | --- |
| 账号、影院、影厅、电影、场次 | `user`、`cinema`、`hall`、`movie`、`showtime` |
| 购票与优惠 | `ticket_order`、`user_coupon`、`coupon_code`、`coupon_code_redemption` |
| 评价与推荐行为 | `comment`、`user_movie_behavior` |
| 助手状态与确认卡 | `assistant_conversation`、`assistant_turn`、`assistant_preference`、`assistant_checkout` |
| 排片草稿 | `scheduling_saved_draft` |

另有 V8 创建的迁移孤立行为归档表，以及 Flyway 自身的版本历史表。近期结构迁移包括 V16 兑换码与钱包字段调整、V17 会话、V18 观影计划与长期偏好、V19 确认卡、V20 排片草稿、V21 影院管理员归属。

`db/schema.sql` 仅创建空数据库，启动后端时 Flyway 创建或升级业务表；完成 V24 并停止后端后，`db/data.sql` 可事务重置演示业务数据，生成北京时间执行当天至未来第 10 天的排片（含首尾共 11 天）。影院管理员已绑定影院 1，普通用户有新人券及可用兑换码。已有库须先备份，并确认历史 v6 基础结构后，由 Flyway 从版本 6 基线接管后续迁移。见 [数据库脚本指南](docs/DATABASE.md)，具体版本清单以迁移目录为准。

## 本地快速开始

### 环境与配置

需要 JDK 21、MySQL 8.0+、Node.js 18+ 和 npm。仓库提供 Maven Wrapper；默认开发配置可启动内嵌 Redis，也可配置外部 Redis。

| 配置 | 用途 |
| --- | --- |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | MySQL 连接 |
| `JWT_SECRET` | JWT 签名配置 |
| `DEEPSEEK_API_KEY` | 助手、选座与经营分析的模型调用 |
| `AI_ASSISTANT_MODEL`、`AI_ASSISTANT_BASE_URL` | 本地版模型与服务地址覆盖 |
| `VITE_AMAP_KEY`、`VITE_AMAP_SECURITY_CODE` | 前端地图与定位，需匹配实际访问域名 |
| `VITE_API_BASE_URL` | 云端前端构建时指定完整的云函数 API 地址 |

将需要的变量设置到操作系统、当前终端或 IDE 的运行配置。根目录 `.env.example` 为模板，Spring Boot 不会自动加载它；本机模型配置也可放入已忽略的根目录 `.local/assistant.properties`。JWT、数据库密码和模型密钥只放服务端私有配置，不进入前端构建。

本地默认数据库账号为 `root / 123456`，用于演示；实际环境按自己的数据库配置设置变量。未配置模型时，基础浏览与购票仍可使用，模型相关能力显示未配置状态。

### 初始化与启动

以下 PowerShell 命令分别从仓库根目录执行。后端和两个前端使用独立终端。

1. 创建空数据库：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE movie-ticket-backend/src/main/resources/db/schema.sql;"
```

2. 启动后端，等待 Flyway 迁移完成：

```powershell
cd movie-ticket-backend
.\mvnw.cmd spring-boot:run
```

3. 初始化演示环境：确认后端首次启动成功且完成 V24 后，先停止后端，再在仓库根目录导入演示数据。此命令会重置全部业务数据，已有需保留数据的库跳过：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE movie-ticket-backend/src/main/resources/db/data.sql;"
```

导入完成后重新启动后端。排片自动覆盖北京时间执行当天至第 10 天，每厅每日最多四场；当天过近的场次自动跳过。

4. 启动用户端：

```powershell
cd movie-ticket-user
npm ci
npm run dev
```

5. 启动管理端：

```powershell
cd movie-ticket-admin
npm ci
npm run dev
```

访问用户端 [localhost:3000](http://localhost:3000)、管理端 [localhost:3001](http://localhost:3001)；后端 API 前缀为 `http://localhost:8080/api`。前端开发代理将 `/api` 转发至后端。

| 用户名 | 本地演示密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | 平台管理员 |
| `cinema_admin` | `admin123` | 新版演示种子已绑定影院 1；旧账号仍需分配影院 |
| `user1` | `admin123` | 普通用户 |

账号表对应演示数据初始化，不表示线上账号始终使用这些密码。更完整的环境与排错步骤见 [启动与验收指南](docs/GETTING_STARTED.md)。

## 核心 API 入口

以下为 Spring Boot 版的主要接口，统一加 `/api` 前缀；受保护接口携带 `Authorization: Bearer <token>`。登录须提交验证码标识和验证码。完整路由以 [控制器源码](movie-ticket-backend/src/main/java/com/movie/ticket/controller/) 为准。

| 模块 | 主要接口（省略 `/api` 前缀） |
| --- | --- |
| 认证与资料 | `GET /auth/captcha`、`POST /auth/login`、`POST /auth/register`、`GET /user/me`、`PUT /user/profile`、`POST /user/change-password` |
| 电影、影院、场次 | `GET /movie/page`、`GET /cinema/page`、`GET /showtime/list`、`GET /showtime/{id}/seats` |
| 订单 | `POST /order`、`GET /order/my`、`GET /order/{orderNo}`、`POST /order/{orderNo}/pay`、`POST /order/{orderNo}/cancel`、`POST /order/{orderNo}/refund`、`POST /order/pick` |
| 优惠券 | `GET /coupon/my`、`POST /coupon/redeem` |
| 兑换码管理 | `GET/POST /admin/coupon-codes`、`PUT/DELETE /admin/coupon-codes/{id}`、`GET /admin/coupon-codes/redemptions` |
| 比价与报价 | `GET /pricing/compare`、`GET /pricing/orders/{orderNo}/quote` |
| 推荐 | `GET /recommend/hot`、`GET /recommend/personal`、`GET /recommend/similar/{movieId}`、`GET /recommend/profile/radar`、`GET /recommend/evaluation` |
| 助手 | `GET /assistant/capabilities`、`POST /assistant/chat`、`POST /assistant/chat/stream` |
| 历史与排除偏好 | `GET /assistant/conversations`、`GET/DELETE /assistant/conversations/{id}`、`GET/PUT /assistant/preferences` |
| 助手确认下单 | `POST /assistant/checkout/prepare`、`POST /assistant/checkout/{id}/confirm` |
| 经营统计与排片 | `GET /statistics/dashboard`、`GET /statistics/scheduling/workspace`、`POST /statistics/scheduling/check`、`POST /statistics/scheduling/apply`、`POST /statistics/scheduling/predict`、`POST /statistics/scheduling/simulate`、`POST /statistics/scheduling/optimize` |
| 保存草稿 | `GET/PUT /statistics/scheduling/saved-drafts`、`GET/DELETE /statistics/scheduling/saved-drafts/{id}` |

云端映射清单见 [api-inventory.json](movie-ticket-unicloud/api-inventory.json)；具有同名接口不代表两套算法与事务已经通过等价验证。

## 测试与验证记录

测试数字对应当次版本、范围与环境，以下记录分别呈现。本次 README 更新未重新执行这些测试。

| 日期与记录 | 已记录结果 | 范围与限制 |
| --- | --- | --- |
| [2026-09-07 系统测试](docs/SYSTEM_TEST_REPORT_2026-09-07.md) | 合计 160 项通过；包括后端快速 77、本机依赖集成 24、用户端浏览器 32、管理端浏览器 2、HTTP 检查 25；两端构建通过 | 另有模型和 Docker 条件用例跳过；真实本机依赖版本与 Docker 标准组合不同 |
| [2026-09-07 混合业务压测](docs/LOAD_TEST_200_USERS_5MIN_2026-09-07.md) | 200 个活跃虚拟用户持续 5 分钟，59,658 请求，0 失败，库存与订单一致性检查通过 | 本机、小型数据、每次操作间隔 0.5–1.5 秒；不能推导生产容量或 200 人同座抢票表现 |
| [2026-09-10 AI 确认下单](docs/ASSISTANT_CHECKOUT.md) | 后端 148 项中累计 144 通过、4 跳过；用户端相关浏览器 39 项通过；V19 与 15 工具能力检查完成 | 幂等和回滚使用隔离数据；真实模型测试使用合成库存 |
| [2026-09-27 权限回归](docs/CINEMA_PERMISSIONS.md) | 后端快速 163 项：158 通过、5 项模型条件测试跳过；真实 MySQL/Redis 权限用例通过；管理端 5、用户端 14 项相关浏览器用例通过；两端构建通过 | 浏览器数据用于交互验证；权限验证不等同于算法商业效果或云端验收 |

浏览器套件大量使用 HTTP 响应拦截；后端持久化、事务和竞争由独立集成测试验证。云函数本地测试使用内存数据库替身，云端真实锁座、支付、退票及故障恢复仍需单独验证。

### 复验命令

从仓库根目录执行：

```powershell
# 后端快速测试与两端生产构建
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1

# 首次安装或需要重装前端依赖
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1 -Install

# 再执行真实依赖集成与两端 Chromium 浏览器套件
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1 -Integration -E2E
```

`-Integration` 需要 Docker；没有 Docker 时，相关 Testcontainers 用例可能跳过，跳过不计为验证通过。`-E2E` 会安装 Playwright Chromium 并执行浏览器测试。真实模型测试采用各套件的显式开关，调用会产生模型费用。脚本任一步失败返回非零退出码。

uniCloud 本地云函数回归从独立工程执行：

```powershell
cd movie-ticket-unicloud
$cloudTestFiles = @(Get-ChildItem -LiteralPath test -Filter '*.test.js' -File | Select-Object -ExpandProperty FullName)
node --test @cloudTestFiles
```

详细分层与单独运行方法见 [自动化测试指南](docs/TESTING.md)；其中早期固定数量请结合上表的日期记录及实际运行报告使用。

## uniCloud 部署进展

以下按 [2026-10-09 部署记录](movie-ticket-unicloud/README.md) 汇总，未在本次文档整理中重新确认线上状态。

当前记录的部署目标为阿里云服务空间 **dy**（`mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6`）：

- [用户端网页托管](https://static-mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6.next.bspapp.com/)
- [管理端网页托管](https://static-mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6.next.bspapp.com/admin/)
- [API 健康检查入口](https://fc-mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6.next.bspapp.com/api/health)

记录中已上传 19 个集合的 Schema/索引、`movie-api`、`movie-maintenance` 和两端静态资源；18 个业务集合由 2026-09-25 MySQL 快照导入，运行时验证码集合未导入。

已记录验证包括健康检查、电影分页、验证码、助手配置能力，以及三个角色的验证码登录和带令牌资料访问。未分配影院的 `cinema_admin` 访问管理订单返回 403，符合权限预期。`configured: true` 表示已有模型配置，不能代替模型多轮业务验收。

云端待完成或复核项：

1. 网页托管 404 页面指向根目录 `index.html`，验证用户端 history 路由刷新；管理端使用 `/admin/` 下的 hash 路由。
2. 在控制台删除已停用的 `movie-data-import` 一次性导入函数。
3. 分配影院管理员的所属影院，再验收跨影院数据边界。
4. 验证真实云数据库事务、锁座并发、订单与优惠券一致性、确认卡幂等、故障恢复及全部关键业务流程。
5. 验证 AI 查询工具的多轮对话，并核对推荐、预测与连座算法和本地版的差异。
6. 部署记录写明基础版到期日为 2026-11-08，续费或套餐调整以控制台状态为准。

旧空间 **bs** 的 2026-09-30 额度耗尽属于迁移前历史记录；`movie-maintenance` 定时触发器已按当时记录删除，当前代码使用请求时清理超时订单。保留旧函数代码不表示仍有每分钟定时运行。

重新构建和部署须使用独立副本、正确的 URL 化 API 地址、前端地图配置及托管域名；静态资源更新后刷新 CDN 缓存。数据导出、构建、上传和空间配置步骤统一见 [uniCloud 工程 README](movie-ticket-unicloud/README.md)。

## 重要业务边界与后续工作

- 支付为模拟支付，尚未接入真实支付渠道；改签、消息通知、系统设置与操作审计日志仍属于后续完善范围。
- 待支付订单超过 10 分钟取消；Redis 锁的 15 分钟 TTL 是释放兜底，订单与库存最终状态以业务事务为准。
- 退票受开场时间、订单状态和核销状态约束；具体手续费与时间边界以 [OrderService](movie-ticket-backend/src/main/java/com/movie/ticket/service/OrderService.java) 及当前页面为准。
- 比价仅覆盖站内场次与个人优惠，不包含外部票务平台；报价和推荐座位不等于锁定库存。
- 地图、浏览器语音、摄像头扫码依赖外部服务或设备权限，仍需补充兼容性与异常环境验收。
- 推荐指标、排片预测、模拟收入与上座率须标明数据集、算法版本和样本口径；功能测试通过不能证明商业效果。
- Docker 标准依赖组合、长时间稳定性、大数据量、多实例以及网络/Redis 故障注入仍需补充。CI、容器化部署与正式运维说明也可继续完善。

## 文档导航与维护

| 主题 | 文档 |
| --- | --- |
| 本地运行与测试 | [启动与验收](docs/GETTING_STARTED.md)、[自动化测试](docs/TESTING.md) |
| AI 查询、会话、规划与下单 | [电影助手](docs/MOVIE_ASSISTANT.md)、[观影规划](docs/ASSISTANT_PLANNING.md)、[确认下单](docs/ASSISTANT_CHECKOUT.md) |
| 智能比价与座位布局 | [AI 比价](docs/AI_PRICE_COMPARISON.md)、[影厅过道](docs/HALL_AISLES.md) |
| 推荐与排片 | [推荐算法评估](docs/RECOMMENDATION_EVALUATION.md)、[创新点实验](docs/INNOVATION_EXPERIMENTS.md)、[智能排片](docs/AI_SCHEDULING.md) |
| 权限、核销与取票 | [影院权限](docs/CINEMA_PERMISSIONS.md)、[取票功能](docs/features/TICKET_PICKUP.md) |
| 云端迁移与部署 | [uniCloud 总说明](movie-ticket-unicloud/README.md) |
| 历史测试证据 | [2026-09-07 系统测试](docs/SYSTEM_TEST_REPORT_2026-09-07.md)、[200 用户压测](docs/LOAD_TEST_200_USERS_5MIN_2026-09-07.md) |
| 工程与设计参考 | [架构说明](docs/ARCHITECTURE.md)、[数据库指南](docs/DATABASE.md)、[早期设计文档](docs/design/SYSTEM_DESIGN.md)、[已有变更记录](docs/CHANGELOG.md) |

专题文档中部分章节仍保留早期实现或数量描述；核对当前接口与表结构时使用控制器源码和 Flyway 目录，核对测试与部署时使用有日期的记录。后续新增功能应同步修改本总览、对应专题说明，以及受影响的提示词、工具和规则知识。

## 许可证

MIT
