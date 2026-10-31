# 电影票系统 uniCloud 阿里云迁移工程

此目录是独立迁移副本。根目录原有的 `movie-ticket-user`、`movie-ticket-admin`、`movie-ticket-backend` 不参与这里的构建或修改。

2026-09-27 已同步影院管理员数据权限与页面一致性改动。2026-09-30 已部署订单请求时清理功能并删除云端定时触发器；本地云函数测试通过。详细权限矩阵见 [影院权限说明](../docs/CINEMA_PERMISSIONS.md)。

已有影院管理员必须由平台管理员在“用户管理 → 编辑”中设置所属影院；未分配时服务端返回 403。普通用户访问本人订单、影院管理员访问所属影院订单，平台管理员访问全局。订单分页、详情、核销、影厅/场次、经营统计及排片采用相同边界，重新分配影院或调整角色对旧登录令牌立即生效。

管理端新增取票核销和推荐评估入口；云端评估展示规则推荐的真实协议和局限，不冒充 Spring Boot 混合推荐实验。用户端已去除未实现的通知入口、改签承诺和按 ID 编造的影院服务/价格等内容。

## 2026-10-31 AI 升级部署

新增 AI 上架、个人海报、营销工作台及观影纪念票接口，更新两端入口及排片时间轴。新版 API 为 `ai-cloud-20261031`，新增 5 个集合及 `movie-ai-worker`。自定义域名首页缓存尚待控制台刷新，暂用 `https://www.jinli115.cn/?v=demand-home-20261031`。验证结果、真实生图证据和纪念票验收限制见 [实际部署记录](../docs/DEPLOYMENT_AI_2026-10-31.md)。

同日已发布按需生图：仅有活跃任务时调用工作器，Qwen 异步生成，返回页面时查询并保存；请在 24 小时内回来保存结果，排队任务返回后继续提交。原每分钟触发器已改为每年 1 月 1 日一次且入口不执行扫描，云端重新下载确认；源码无定时器，**云端遗留年度触发器尚需控制台删除**。状态每 8 秒查询、隐藏页面暂停，营销展示位每 5 分钟刷新。首页票房块和每 5 秒查询已删除，改为“即将上映”，复用一次加载数据。无法保证任意访问量下整月 GBs 一定不超限。

## 历史部署状态

**2026-10-09 已整体迁移到新的阿里云 uniCloud 服务空间 `dy`（SpaceId `mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6`，基础版，2026-11-08 到期）；原空间 `bs` 不再作为部署目标。**

- 用户端：<https://static-mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6.next.bspapp.com/>
- 管理端：<https://static-mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6.next.bspapp.com/admin/>
- API 健康检查：<https://fc-mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6.next.bspapp.com/api/health>（返回 `release: cinema-scope-20260927`）
- 已上传：19 个 `mts_` 集合的 Schema/索引、`movie-api`、`movie-maintenance`，以及两端静态资源（224 个文件、22.75 MB）。
- 数据：18 个业务集合由 2026-09-25 的 MySQL 快照导入，记录数与快照清单逐一核对一致（`mts_showtime` 2742、`mts_movie` 40、`mts_cinema` 10、`mts_hall` 21、`mts_order` 47、`mts_user` 5 等）；`mts_captcha` 仅存运行时验证码，未导入。
- 已验证：`/api/health`、`/api/movie/page`、`/api/auth/captcha`、`/api/assistant/capabilities`（`configured: true`），以及 admin、user1、cinema_admin 三个账号的验证码登录与 `user/me` 带令牌访问；`cinema_admin` 因未分配影院，订单接口按预期返回 403。跨域已由 URL 化网关自动允许托管域名。
- 待用户在控制台完成：把网页托管的 404 页面指向根目录 `index.html`（用户端 history 路由刷新需要），并在控制台删除已停用的 `movie-data-import` 云函数；给 `cinema_admin` 分配所属影院后再验收影院管理员数据边界。
- 旧空间 `bs`（`mp-03f8e08d-bcc8-446a-9d39-d8ba78a46819`）此前的部署记录与 `movie-maintenance` 定时触发器删除等历史同上，仅作为归档参考。
- 2026-09-26 已验证用户端影片展示、管理端登录页、管理员登录、订单与优惠券查询、排片工作台与洞察查询，以及访客 AI 真正返回模型回答。云网关管理浏览器跨域，云函数只返回业务响应头。
- 2026-09-30 `movie-api` 请求时清理版本已上传，`movie-maintenance` 的云端定时触发器已删除并刷新确认。当前套餐的云函数 GBs 用量已超额，`/api/health` 暂返回 `PrePayResourceExhausted`；需等额度恢复或升级套餐后再做线上功能验收。删除触发器只会阻止后续定时执行，不会清除已经产生的用量。

- `frontend/movie-ticket-user`：用户端副本，可通过 `VITE_API_BASE_URL` 指向云函数 URL 化地址，生产构建通过。
- `frontend/movie-ticket-admin`：管理端副本，生产路径 `/admin/`，采用 hash 路由，生产构建通过。
- `movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api`：URL 化云函数入口，包含登录、用户资料、电影/影院/影厅/场次 CRUD、订单与座位、优惠券、评价、规则推荐及评估、智能比价、连座推荐、AI 整段回答与数据库查询工具、观影条件、会话及确认卡下单、票房统计、排片工作台/预测/模拟/约束启发式方案、图片上传的迁移代码。
- `movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/order-expiry.js`：查询本人订单、管理端订单或单笔订单时，取消已超过 10 分钟的待支付订单，并释放仍属于该订单的座位占用；支付过期订单时也会清理并拒绝支付。每次列表请求最多清理 20 笔，剩余订单在后续请求中继续清理。
- `movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-maintenance`：保留的旧维护函数代码；云端定时触发器已删除，本地 `package.json` 也已移除定时配置，不再每分钟执行。
- `movie-ticket-unicloud-cloud/uniCloud-aliyun/database`：19 个独立 `mts_` 集合的 Schema 与索引配置；禁止浏览器直接读写集合。
- `api-inventory.json`：由原 Spring 控制器生成的接口清单，用于逐项核对功能。
- `scripts/export-mysql.js`：只读导出原 MySQL，生成每个集合独立 JSONL 和 SHA256 清单；`.local/` 里的数据含真实业务信息，已被 Git 忽略。

目标空间的默认托管域名适合验收；需要面向公众访问时，应在控制台绑定已备案的自定义域名。新服务空间 `dy` 为基础版，到期时间 2026-11-08，届时还需续费或调整套餐。

线上登录链路可用 `node scripts/verify-cloud-login.js https://<URL化域名>/api [username] [password]` 自检：脚本本地解码验证码图像后登录，再带令牌调用受保护接口。验证码点阵带随机噪声，识别偶有失败，重跑即可。

**上线前仍需验收**：排片算法目前是约束启发式，未实现原服务的 CP-SAT 求解和全局最优证明。AI 普通回答已在云端实测；带数据库工具调用的多轮对话尚未完整验收。规则推荐、评估、预测和连座算法尚未与原服务做等价校验。云空间事务、锁座并发、订单与优惠券、确认卡幂等及故障恢复均须实测。当前目录不能直接替换原系统的生产版本。

## 本地验证

```powershell
cd movie-ticket-unicloud
node scripts/generate-schemas.js
python scripts/inventory-routes.py
node --test test/*.test.js

cd scripts
npm ci
# 将 MySQL 凭据作为当前终端环境变量传入，不写入仓库
node export-mysql.js --write
node validate-export.js ../.local/<本次导出目录名>

cd frontend/movie-ticket-user
npm ci
npm run build

cd ../movie-ticket-admin
npm ci
npm run build
```

## 配置与重新部署

1. HBuilderX 项目为 `movie-ticket-unicloud-cloud`，已关联服务空间 `dy`（`mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6`）。切换空间时改写 `%APPDATA%\HBuilder X\projects\<项目目录>\setting.json` 中 `uniCloud.<账号>.aliyun` 的 `spaces`/`allSpaces`/`selected`，重启 HBuilderX 后用 `cli cloud functions --info` 确认绑定；CLI 的 `--assignspace` 会等待交互，不要用。上传 Schema 时 `--name` 必须带 `.schema.json` 后缀，单次耗时可达数分钟，需给足超时。
2. `movie-api` 云函数环境变量需有随机生成的 `JWT_SECRET`（至少 32 字符）和 `DEEPSEEK_API_KEY`；可用 `DEEPSEEK_MODEL` 选择模型（默认 `deepseek-flash`）。本次部署无法用 CLI 配置云端环境变量，故新增 `read-env.js`：`process.env.<KEY>` 缺失时回退读取同目录 `secrets.json`（已被 `.gitignore` 排除）。若后续在控制台配置了环境变量，环境变量优先，可删除 `secrets.json`。所有密钥仅放在服务端，不放入前端构建。
3. 云函数 `package.json` 将 URL 化路径设为 `/api`。获取云函数详情中的**URL 化域名**，将完整 `https://.../api` 地址作为两套前端构建时的 `VITE_API_BASE_URL`。服务空间的 request 域名不能直接替代这个 URL。
   获取地址后可执行 `node scripts/build-hosting.js https://<云函数URL化域名>/api`，脚本会构建两端并把可上传内容合并到一个忽略 Git 的 `.local/hosting-stage-*` 目录，管理端放在其中的 `admin/`。
   脚本还会把高德地图凭据注入构建：优先取当前终端的 `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE`，缺失时回退读取主项目 `movie-ticket-user/.env`（已被 Git 忽略）；两者都为空时脚本直接报错退出，避免像早期版本那样产出 `key:0` 的无效地图包。高德控制台还需给该 Key 配置实际访问域名白名单（如 `www.jinli115.cn`），否则浏览器端仍会被拒绝。
4. 新空间的业务数据由一次性云函数 `movie-data-import` 导入：把快照 JSONL 复制进 `cloudfunctions/movie-data-import/data/`，上传后 POST `/import-run` 并带 `token`，导入完立即清空实现、移除 URL 化入口并在控制台删除该函数。集合非空时默认跳过，需要覆盖时传 `force`。以后同步数据须重新从原 MySQL 导出最终快照并运行 `validate-export.js`，先制定增量方案；直接重导整份会遇到重复 `_id`。不要把 `.local/` 上传静态托管或提交到 Git。
5. 用户端 `dist/` 上传网页托管根目录；管理端 `dist/` 的内容上传至 `/admin/`。用户端的 history 路由需将 404 页面指向根目录 `index.html`；管理端使用 hash 路由。用户端 `public/uploads/` 已复制旧图片，构建后位于 `dist/uploads/`。
   **每次重新上传后都要在控制台刷新 CDN 缓存**：根路径 `index.html` 会被长期缓存（实测过出现 `Age` 达 75 万秒、`X-Cache: HIT` 的情况），不刷新就会一直加载旧的入口 JS，表现为地图、定位、验证码等"改了却没生效"。验证方法：直连 `static-<spaceId>.next.bspapp.com` 对比入口 JS 的 hash，或给自定义域名加随机 query 参数绕过缓存。
6. 跨域网关已登记网页托管域名。已验证管理员登录、统计和访客 AI 回答；还要按上面的清单验证并发购票、支付、退票、上传、AI 工具调用和排片应用，再对外开放访问。阿里云 URL 化请求体最大 2MB；当前管理端上传限制压缩后 1.4MB。

官方参考：[uniCloud 快速上手](https://doc.dcloud.net.cn/uniCloud/quickstart)、[URL 化](https://doc.dcloud.net.cn/uniCloud/http)、[云数据库](https://doc.dcloud.net.cn/uniCloud/hellodb.html)、[前端网页托管](https://doc.dcloud.net.cn/uniCloud/hosting.html)。
