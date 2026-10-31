# AI 个性化电影海报

用户端入口：`/posters`（需登录），首页侧栏和顶部导航中的“AI 海报”，或电影详情的“制作专属海报”。从详情进入时自动带入电影主题。此模块位于 Spring Boot / Vue 本地工程，独立 uniCloud 副本尚未迁移。

用户填写喜欢的电影主题，选择赛博朋克、东方神话、星际科幻、日系动画、复古胶片或悬疑电影风格，可上传人物照片，补充一句话创意，并选择竖版 3:4、方形 1:1 或横版 16:9。例如：“帮我生成一张哪吒电影的赛博朋克风格观影海报，我是主角。”主题和描述至少填写一个；不依赖购票订单。

生成任务异步保存到数据库。页面显示排队、创作中、完成或失败，每 3 秒更新进行中的作品；离开后可在“我的电影宇宙”恢复最近 50 次创作。成功作品支持预览、下载 PNG 和系统文件分享；浏览器不支持分享时下载图片供用户自行发送。界面初始画面是明确标记的风格示意，不是模型出图。

## 生图和人物照片

复用现有 `MarketingImageClient`、`marketing.*` 服务配置。`qwen-images` 使用 DashScope 原生 `/services/aigc/multimodal-generation/generation`，在 `input.messages[].content` 中发送人物参考图 `image`（`data:image/jpeg;base64,...`）和创意 `text`，在 `parameters` 中发送画幅与张数；没有照片时只传文字。`openai-images` 使用 `/images/generations` 文生图及 `/images/edits` multipart 图生图。千问不能沿用 multipart 上传方式；本机通用域名使用原生协议，OpenAI 兼容图像接口需要供应商开放相应路由。协议依据见[阿里云千问图像生成与编辑 API](https://help.aliyun.cn/zh/model-studio/qwen-image-generation-and-editing-api-reference)。人物特征的保留效果仍取决于模型，不能保证与照片完全一致。

`openai-images` 和受支持的 `qwen-images` 模型会开放照片入口；千问支持型号为 `qwen-image-3.0`、`qwen-image-3.0-pro`、`qwen-image-2.1-pro`、`qwen-image-2.1-turbo`。智谱文生图和旧版千问纯文生图模型仍关闭照片入口。电影主题、风格和用户创意直接形成生图提示词，无需额外调用文本模型。不上传照片时提示模型使用虚构主角，不声称还原用户长相。

照片限制为 JPG / PNG、最大 5MB，服务端按实际图片内容读取格式、尺寸和像素数。重新编码为最长边不超过 2048 像素的 JPEG，去除原始元数据，写入私有目录；提交时只发送创作描述和参考图片，不发送账号资料或订单。照片会发送至配置的第三方生图服务，界面在用户提交前提示此用途。成功后删除服务器上的参考照片；未完成任务保留参考照片以供手动恢复。生成结果重新编码为 PNG，图片读取须登录并核验所有权，禁止缓存，不提供公开链接。

## 任务与重复请求

- Flyway V30 新增 `personal_movie_poster`，按账号隔离任务、参考照片和结果文件。管理员也不能读取其他人的个人海报。
- 用户行锁、`user_id + request_id` 唯一键和内容摘要保护每日额度与重复提交。相同请求标识及内容返回同一任务；改变内容后复用旧标识会被拒绝。前端网络失败后提交相同创意会复用会话内保存的请求标识。
- 默认每用户每天 6 个新任务，按北京时间计算，失败也占用创建额度；单任务最多提交 3 次。只有明确失败或已有图片结果的任务允许手动重试。每日限制计任务数量，并非供应商计费次数上限。
- 生图结果先持久化再下载。下载或保存失败的重试复用原结果，不再次调用模型；网络中断、提交超时及供应商结果未知的任务不会自动重发，也不开放直接重试。
- 后台最多同时处理两个个人海报任务。失联任务的超时至少 20 分钟，按生图及下载等待时间扩大，不自动重放可能已计费的请求。
- 生图服务未配置或工作线程关闭时禁用新生成，历史作品仍可读取和下载。

## 配置

基础配置沿用被 Git 忽略的 `.local/marketing.properties` 及现有环境变量。千问配置单独放在 Git 忽略的 `.local/qwen.properties`，启动时在基础文件之后加载，覆盖生图设置，保留原有文本服务设置。联网沿用 `marketing.proxy-url=http://127.0.0.1:7890`，本机地址直连。千问配置示例（密钥使用占位符）：

```properties
marketing.image-provider=qwen-images
marketing.image-model=qwen-image-3.0
marketing.image-base-url=https://dashscope.aliyuncs.com/api/v1
marketing.image-api-key=YOUR_DASHSCOPE_API_KEY
marketing.proxy-url=http://127.0.0.1:7890
```

业务空间专属域名也可作为 Base URL，例如 `https://YOUR_WORKSPACE_ID.cn-beijing.maas.aliyuncs.com/api/v1`。账号和模型需在对应地域可用。阿里云生成图的 OSS 下载地址按 `dashscope-*.oss-*.aliyuncs.com` 校验，转存请求不携带生图密钥。能力接口增加 `imageProvider`、`imageModel`，可用于确认正在运行的后端已加载新配置。修改私有配置后必须重启后端。其他可设置项：

```properties
poster.worker-enabled=true
poster.storage-path=../.local/personal-poster-assets
poster.daily-limit=6
poster.poll-interval-ms=2500
```

存储路径相对于后端工作目录；生产环境应使用持久化目录。修改代码后重启后端，启动时自动执行 V30 迁移。

## 接口与验证

所有接口均需本人登录，前缀 `/api/posters`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/capabilities` | 服务、照片支持状态、风格与本人剩余额度 |
| GET | `/` | 本人最近 50 个任务 |
| POST | `/` | multipart：`data` 为 JSON，`photo` 为可选照片 |
| GET | `/{id}` | 本人任务状态 |
| POST | `/{id}/retry` | 安全可恢复任务的手动重试 |
| GET | `/{id}/file` | 本人已生成 PNG |

`data` 示例：

```json
{"requestId":"b481752d-0c8e-4c84-9750-3e58e32b4548","movieTitle":"哪吒","style":"CYBERPUNK","prompt":"我是主角，霓虹城市，风火轮变成悬浮装置","ratio":"3:4"}
```

后端验证：`mvnw.cmd -Dtest=PosterServiceTest,MarketingImageClientTest,SouvenirServiceTest test`。前端验证：`npm run build`、`npx playwright test e2e/poster.spec.ts`。后端测试使用真实 Spring 事务、H2、MVC 与文件，模型传输使用替身；浏览器测试覆盖主题带入、风格与画幅、照片上传、进度、恢复、下载、分享、网络重放、移动端和登录跳转。上述检查不代替真实付费模型生成及人物相似度验收。

2026-10-31 初版验证：26 项后端测试、9 项 Chromium 浏览器测试和用户端生产构建均通过；已查看桌面与 390px 手机截图。Vite 生产构建复用已有自动声明，避免与运行中的预览服务争用声明文件。初版未提交真实付费生图请求。

千问追加验证：`mvnw.cmd -Dtest=MarketingImageClientTest,PosterServiceTest,SouvenirServiceTest test` 覆盖 JSON 参考图传输、无参考图生成、型号能力及阿里云 OSS 域名校验。显式设置 `QWEN_IMAGE_LIVE_TEST=1` 后运行 `mvnw.cmd -Dtest=QwenImageReferenceLiveTest test`，使用合成人物剪影验证一次真实图生图和图片保存；请求前保存提交标记，返回结果先缓存，后续仅复用结果，结果不明时禁止重复提交。该验证不等同于真人面部相似度验收。

2026-10-31 千问联调结果：密钥鉴权及模型列表查询成功，确认账号包含 `qwen-image-3.0`。通用域名的 OpenAI 兼容生图路由明确返回 HTTP 404，未取得生成结果；改为 DashScope 原生协议后，一次合成参考图调用成功，约 41 秒生成并保存 1152×1536 PNG。28 项后端回归检查通过。没有使用用户真人照片测试，真人相似度尚待用户实际体验。
