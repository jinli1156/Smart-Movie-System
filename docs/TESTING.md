# 自动化测试指南

项目采用“快速测试、真实依赖集成、浏览器 E2E”三层测试结构。快速套件不依赖本机 MySQL、Redis 或 Docker，适合每次提交运行；真实依赖套件由 Testcontainers 隔离启动 MySQL 8 与 Redis 7；浏览器套件使用 Playwright Chromium。

## 覆盖范围

| 层级 | 数量 | 主要覆盖 |
| --- | ---: | --- |
| 后端快速测试 | 25 | 推荐/统计与联表查询；登录、下单、支付、取票、退票接口契约及 HTTP 状态码；JWT 鉴权与角色边界；Redis 同座竞争、锁持有者释放和重复取消幂等 |
| 真实依赖集成测试 | 5 | Flyway 下的真实 MySQL/Redis；完整订单生命周期；并发同座；支付与超时、取票与退款竞争；用户资料持久化回读 |
| 浏览器 E2E | 3 | 用户端登录到选座、下单、支付、取票码、退票；管理端登录、订单列表和详情；影院管理员无法进入平台用户管理 |

浏览器 E2E 对后端 HTTP 响应做确定性拦截，专注验证 Vue 页面、路由、Pinia、Axios 与真实 Chromium 的协作；MySQL/Redis 和后端业务真实性由 Testcontainers 层验证。这样浏览器用例不依赖演示数据日期，也不会修改开发数据库。

## 执行命令

快速后端测试：

```powershell
cd movie-ticket-backend
.\mvnw.cmd test
```

真实 MySQL/Redis 集成测试（需要 Docker Desktop 或兼容的 Docker daemon）：

```powershell
cd movie-ticket-backend
.\mvnw.cmd verify -Pintegration
```

用户端浏览器 E2E：

```powershell
cd movie-ticket-user
npm ci
npx playwright install chromium
npm run test:e2e
```

管理端浏览器 E2E：

```powershell
cd movie-ticket-admin
npm ci
npx playwright install chromium
npm run test:e2e
```

仅验证后台查询与工作参数恢复（使用接口拦截，不修改开发数据库）：

```powershell
cd movie-ticket-admin
npm run test:e2e -- e2e/page-state.spec.ts --workers=2
```

覆盖七个管理列表的筛选、分页、菜单切换和刷新；同时检查排片重置与“全部”状态、智能排片设置、经营分析预测条件、推荐评估结果、账号/影院权限隔离、退出清理及存储异常。页面状态保存在当前标签页的 `sessionStorage`，关闭标签页后不保留；业务列表在恢复条件后重新读取接口。

后台布局回归：

```powershell
cd movie-ticket-admin
npm run test:e2e -- e2e/management-layout.spec.ts --workers=2
```

覆盖 320、390、768、1024、1280、1440、1920 像素宽度下七个管理列表的完整字段及横向溢出，使用长影院名、电影名、邮箱、订单号与多种操作按钮验证。另检查窄屏全选/清空/编辑、空列表以及兑换记录和经营分析抽屉；布局截图写入 `output/admin-layout/`。

从仓库根目录统一执行：

```powershell
# 快速套件与构建
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1

# 包含真实依赖和浏览器测试
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1 -Integration -E2E
```

## CI 建议

- 普通提交运行 `mvn test`、两端 `npm run build` 和两端 `npm run test:e2e`。
- 合并到主分支前，在具备 Docker 的 runner 上额外运行 `mvn verify -Pintegration`。
- 保留 Playwright 的失败截图和 trace；相关目录已加入 `.gitignore`，CI 可将其作为 artifact 上传。
- 不要把真实数据库密码写进测试配置；Testcontainers 使用一次性容器账号和数据库。
