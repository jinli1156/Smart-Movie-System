# AIGC 营销工作台

更新：2026-10-30（北京时间）。本轮实现图文生产和人工审核上架，视频生成暂缓。实现位于 Spring Boot 工程；uniCloud 独立副本尚未迁移本模块。

## 使用入口与流程

启动后进入管理端左侧的“营销工作台”，或访问 `http://localhost:3001/marketing/studio`。页面内可切换“营销创作”“任务与素材”“审核与发布”三个功能，左侧保持一个入口。

1. 新建营销需求，选择所属影院、影片、活动主题、目标人群和比例。保存需求本身不调用模型。
2. 在“生成候选文案”点击生成文案，选用喜欢的标题与文案，带入“编辑发布内容”。也可直接在编辑区填写宣传文案。
3. 在“编辑发布内容”核对关联影片、营销标题和短文案，再选择海报张数并点击“生成海报”。多影片需求不自动选择第一部影片，选用候选文案时会带入对应影片；手动切换影片会清空上一部的文案和海报选择。确认框显示本次影片名称。默认 1 张，每批最多 18 张，仅为当前关联影片生成，按需求中已选比例分配。生成时保存当前文案快照，后续继续编辑不会改变已提交任务。原来的“全部生成”入口已移除。
4. 编辑区和“任务与素材”自动展示进度。选用生成好的海报后保存内容版本；支持失败重试和下载素材。新海报直接使用模型生成的完整图片，保存版本时保留选中的海报；如果需要让新标题出现在图片上，重新生成海报。历史背景型素材仍兼容原有排版方式。
5. 提交审核。平台管理员在“审核与发布”预览和对照版本，审核通过或说明退回原因。
6. 发布到首页精选或所属影院的活动位，设置开始和截止时间；可随时撤下。

营销内容与电影基础资料独立保存。审核只授权具体版本；修改后保存新版本并重新审核。公开查询和图片访问同时检查发布状态、有效期及电影和影院状态。

## 模型与本机配置

本机生图使用阿里云 `qwen-image-3.0`，配置保存在 Git 忽略的 `.local/qwen.properties`，启动时覆盖 `.local/marketing.properties` 的生图设置。`qwen-images` 协议通过 DashScope 原生 `/api/v1/services/aigc/multimodal-generation/generation` 调用，同时支持文字与人物参考图；个人海报详情见 [AI 个性化电影海报](AI_PERSONAL_POSTER.md)。原有 `openai-images` 与智谱协议继续可用。模型直接生成完整宣传海报，系统保存图片，不叠加固定文字与蒙版。输出比例为 16:9、3:4、1:1。

通用 `MarketingImageClient` 支持图片 URL 和 `data[].b64_json` 返回。Base64 图片先写入本地暂存文件，再把短引用写入任务，避免把大段编码写进数据库；保存失败后可复用结果，任务成功后清理暂存。保留智谱协议兼容，历史任务和旧图片地址仍可读取。第三方下载域名可通过 `marketing.image-download-hosts` 配置，不向图片下载地址转发 API Key。

海报生成按“选用并编辑宣传文案 → 语言模型根据影片资料和营销标题编写提示词 → 生图模型生成宣传海报”的顺序执行，需要配置语言模型。专用系统提示词位于 `MarketingPrompts.IMAGE_INSTRUCTION`，仅要求“根据电影资料和营销标题，为该影片创作相应的宣传海报”。已去除抽象氛围、角色表现、留白、禁止文字和固定视觉风格等创作限制。JSON 格式仅用于程序读取结果。

生图请求需提交 `posterContent: { movieId, title, copy }`，后端校验影片属于该需求，标题与文案非空且分别不超过 60 和 300 字。文案快照保存在图片任务的 `result.posterContent`；语言模型仅接收 `movie` 和 `marketingTitle`，图片创作依据为影片与营销标题。`result.prompt` 保存语言模型提示词，`result.submittedPrompt` 保存实际提交生图的完整提示词，`result.promptVersion` 标记提示词版本。成功后这些信息与素材引用一并保留，无需新增数据库字段。相同请求标识仅可重放相同影片、文案和张数的请求；修改文案需使用新的标识。生图失败重试复用当前版本指令，旧版本指令会重新规划；已有图片结果的转存重试不会再次调用模型。图片调用尝试数只统计生图提交。

修改 Java 代码后需重启正在运行的后端进程；仅刷新前端不会加载后端新逻辑。新版能力接口返回 `posterContentRequired: true`，前端在缺少该标记时禁用海报生成并提示更新服务，避免旧服务忽略已编辑文案并始终取需求首部影片。

营销创作和任务页按实际状态显示生成动画及“构思画面 → 生成图片 → 保存素材”步骤，排队时显示等待处理。页面每 2.5 秒刷新任务；完成、失败或结果待核实时停止对应动画。离开后返回仍可继续查看任务，动画尊重系统减少动态效果设置。

营销文案通过独立的 `MarketingTextClient` 调用。默认 `auto`：若现有 DeepSeek 已配置则复用，否则使用同一智谱凭据调用文本模型 `glm-4.7-flash`。可把 `marketing.text-provider` 设置为 `deepseek` 或 `zhipu` 固定来源。此选择不更改原有观影助手的模型配置。

生图 Key 保存在 Git 忽略的 `.local/marketing.properties`，与智谱文本凭据、`.local/assistant.properties` 中的 DeepSeek 配置分别使用。文案优先使用现有 DeepSeek；智谱文本回退固定使用独立的 `marketing.text-base-url`，不会随生图 Base URL 切换。上述文件内容不得提交。Spring Boot 从项目根目录或后端目录启动时均会导入这些配置。

供其他环境参考的配置模板（占位符，非实际 Key）：

```properties
marketing.image-provider=openai-images
marketing.image-api-key=YOUR_IMAGE_API_KEY
marketing.image-base-url=https://api.hangzhale.com/v1
marketing.image-model=gpt-image-2
marketing.text-provider=auto
marketing.text-model=glm-4.7-flash
marketing.text-base-url=https://open.bigmodel.cn/api/paas/v4
marketing.zhipu-api-key=YOUR_OPTIONAL_ZHIPU_TEXT_API_KEY
marketing.proxy-url=http://127.0.0.1:7890
marketing.connect-timeout-seconds=60
marketing.request-timeout-seconds=600
marketing.download-timeout-seconds=120
marketing.storage-path=../.local/marketing-assets
marketing.max-images-per-batch=18
marketing.daily-image-limit=60
marketing.concurrency=2
marketing.worker-enabled=true
```

相对存储路径以上从后端目录启动为例；部署时建议配置持久目录的绝对路径。资产目录不在公开的 uploads 映射内。管理预览携带当前 JWT，公开素材仅限仍在展示的已发布内容。

本机联网使用 HTTP 代理 127.0.0.1:7890，localhost、127.0.0.1、::1 直连。部署时按环境配置代理；不需要代理时将 `marketing.proxy-url` 留空。

接口依据：[夯炸了 AI 接入文档](https://doc.hangzhale.com/)、[供应商图片工作台](https://aiimage.hangzhale.com/)。兼容的旧接口见[智谱图像生成 API](https://docs.bigmodel.cn/api-reference/模型-api/图像生成)。本次通过鉴权的 `/v1/models` 查询和实际端点错误确认了模型 ID 与调用路径；真实生图测试发生连接中断、未收到图片响应，未自动重发，不能视为真实出图验收成功。

## 任务恢复和调用统计

MySQL 保存批次、子任务、调用尝试、租约和生成结果。后台使用有限线程处理任务，页面离开后仍继续执行。

默认连接等待为 60 秒，生图响应等待为 600 秒（10 分钟），图片下载等待为 120 秒。图片任务租约根据“生图等待＋下载等待＋180 秒处理余量”计算，默认 15 分钟，避免长时间生成时租约先过期。连接被对端关闭仍会立即结束并标记待核实，延长等待时间不能阻止断开。

- 图片任务状态：QUEUED → RUNNING → PLANNING（语言模型构思）→ SUBMITTING（生图）→ PERSISTING → SUCCEEDED；文案任务为 QUEUED → RUNNING → SUCCEEDED。
- 语言模型构思失败或该阶段租约过期：FAILED，可以手动重试，尚未提交生图请求。
- 提交超时或服务中断且无法确认图片是否已生成：SUBMIT_UNKNOWN。需要运营先核实，再明确确认可能再次计费后重试。
- 已收到供应商图片地址后，先保存地址再下载。转存或排版失败时重试复用已有结果。
- 租约过期不会自动重发可能收费的图片请求。图片保存阶段中断可重新领取并复用结果。
- 每批最多 18 个图片任务，每影院每天默认预留 60 次图片生成额度。新生图重试也占额度；单纯转存重试不新增生图额度。额度按任务接纳时预留，失败不自动退回。
- 文案限流会显示“服务暂时繁忙”，由运营稍后手动重试；不悄悄切换成模板结果。
- 单任务最多处理 5 次。任务进度不等同于供应商按量账单。

页面显示生成数量、成功和异常数量、图片模型调用尝试、内容版本、发布数量、终态生成成功率及已决定审核版本的通过率；逐任务记录耗时。费用以供应商账单为准，本轮没有虚构成本节约或转化率结果。

## 角色与版本

| 操作 | 影院管理员 | 平台管理员 |
| --- | --- | --- |
| 需求、任务、素材和草稿 | 所属影院 | 全平台 |
| 提交审核 | 所属影院 | 全平台 |
| 审核、发布、撤下 | 无 | 有 |
| 查看待审核素材 | 所属影院 | 全平台 |

后端同时执行角色与影院范围检查。发布接口检查审核状态、影片与影院事实快照、素材文件和有效期，重复发布由幂等键与“版本＋展示位”唯一约束保护。已撤下的发布不能用重复请求重新启用，需要创建并审核新版本。

版本保存会固定标题、文案与所选海报引用，并生成内容摘要；审核日志记录具体版本、操作者、动作、原因和时间。

## 数据与代码入口

Flyway `V25__marketing_workbench.sql` 新增七张表；`V26__marketing_batch_image_count.sql` 增加批次张数字段，并按现有图片任务回填旧批次，保持重复请求的一致性：

`marketing_campaign`、`marketing_batch`、`aigc_task`、`marketing_asset`、`marketing_content_version`、`marketing_review_log`、`marketing_publication`。

主要代码：

- 后端：`src/main/java/com/movie/ticket/service/marketing/`，以及 `MarketingController`、`MarketingPublicController`。
- 管理端：`src/components/MarketingWorkspace.vue`、`MarketingImage.vue`，路由 `/marketing/studio`、`/marketing/tasks`、`/marketing/review`。
- 用户端：`src/components/MarketingPlacements.vue`，用于首页和影院详情。
- 管理接口前缀：`/api/admin/marketing`；公开展示：`GET /api/marketing/placements?slot=HOME` 或 `slot=CINEMA&cinemaId=...`。
- 私有预览：`/api/admin/marketing/assets/{id}/file`；公开素材：`/api/marketing/assets/{id}`。

用户端定期刷新发布内容；活动到期后在当前页面自动隐藏。撤下由服务端即时过滤，已打开页面在下一次刷新展示列表时移除。

## 验证方式

后端一般回归：

```powershell
cd movie-ticket-backend
.\mvnw.cmd test
```

真实接口和 MySQL 验证为显式开启，普通测试不会消耗生成服务：

```powershell
$env:MARKETING_MYSQL_TEST = '1'
$env:MARKETING_LIVE_TEST = '1'
$env:MARKETING_COPY_LIVE_TEST = '1'
.\mvnw.cmd test
```

`MarketingMysqlSmokeTest` 创建唯一命名的临时数据库，执行 V25、V26 迁移与生成、编辑、审核、发布、撤下链路，结束后仅删除自己创建的临时数据库。

`MarketingSpringMvcTest` 使用真实 Spring 容器、数据库组件代理、MVC、事务和素材文件，验证从生成请求到保存版本、审核和发布的流程；仅替换模型传输。这项测试覆盖数据库组件被 Spring 代理后的调用，防止直接读取代理对象内部字段造成空指针异常。

`MarketingImageLiveTest` 使用本机忽略配置，按供应商和模型分别缓存结果。提交前保存标记；未取得结果的请求不会自动重复提交，需先核实。下载重跑复用已有 URL 或本地图片引用。真实文案测试每次开启都会发起请求。

浏览器流程：

```powershell
cd movie-ticket-admin
npm run test:e2e -- marketing.spec.ts
cd ../movie-ticket-user
npm run test:e2e -- marketing.spec.ts
```

浏览器测试使用受控接口替身验证选稿、保存、审核、发布、撤下及权限界面，不能当作真实模型或真实数据库测试。两端还需要运行 `npm run build`。

以下为切换前的历史验证：已完成真实 GLM-Image 生图、下载与排版检查，DeepSeek 与智谱文本接口的结构化文案校验、真实 MySQL 迁移及发布链路验证、后端流程与权限回归、两端生产构建及新增浏览器流程验证。此记录不能代表当前 `gpt-image-2` 已通过真实出图验收。生成质量仅有小样本检查。

## 运营评估记录建议

固定 10 份需求，按同一规则记录：人工制作耗时、辅助制作耗时、生成数量、实际调用量、人工修改量、审核通过情况及账单费用。可覆盖：

朋友观影、家庭观影、动画片主题、冒险片主题、爱情片主题、节日活动、简洁品牌宣传、长片名、横版横幅、方形活动图。

先记录实际结果，再计算可用率、审核采纳率和单位可用素材成本。当前不预填这些结果。
