# 电影购票系统 - 用户端前端

基于 Vue 3 + TypeScript + Element Plus 的电影购票系统用户端

## 技术栈

- Vue 3.4
- TypeScript 5.3
- Vite 5.1
- Vue Router 4.3
- Pinia 2.1
- Element Plus 2.6
- Axios 1.6

## 项目结构

```
movie-ticket-user/
├── public/                    # 静态资源
├── src/
│   ├── api/                   # API接口
│   │   ├── auth.ts            # 认证接口
│   │   ├── cinema.ts          # 影院接口
│   │   ├── movie.ts           # 电影接口
│   │   ├── order.ts           # 订单接口
│   │   └── showtime.ts        # 场次接口
│   ├── assets/                # 资源文件
│   ├── components/            # 公共组件
│   ├── router/                # 路由配置
│   │   └── index.ts
│   ├── stores/                # Pinia状态管理
│   │   └── user.ts            # 用户状态
│   ├── types/                 # TypeScript类型定义
│   │   ├── auth.ts            # 认证类型
│   │   └── index.ts           # 通用类型
│   ├── utils/                 # 工具函数
│   │   └── request.ts         # Axios封装
│   ├── views/                 # 页面组件
│   │   ├── Login.vue          # 登录页
│   │   ├── Register.vue       # 注册页
│   │   ├── Home.vue           # 首页
│   │   ├── Movies.vue         # 电影列表
│   │   ├── MovieDetail.vue    # 电影详情
│   │   ├── Cinemas.vue        # 影院列表
│   │   ├── SeatSelection.vue  # 在线选座
│   │   ├── OrderConfirm.vue   # 订单确认
│   │   ├── OrderPay.vue       # 订单支付
│   │   ├── OrderList.vue      # 订单列表
│   │   └── OrderDetail.vue    # 订单详情
│   ├── App.vue                # 根组件
│   └── main.ts                # 入口文件
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── README.md
```

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

访问 `localhost:3000`

### 3. 构建生产版本

```bash
npm run build
```

### 4. 预览生产构建

```bash
npm run preview
```

## 功能说明

### 已实现功能

- ✅ 用户登录 / 注册
- ✅ JWT认证
- ✅ 路由守卫
- ✅ 状态管理（Pinia）
- ✅ 统一请求封装（Axios）
- ✅ 响应式布局
- ✅ 电影列表展示与搜索
- ✅ 电影详情页
- ✅ 影院列表
- ✅ 场次选择
- ✅ 在线选座
- ✅ 订单确认与支付
- ✅ 订单列表与详情
- ✅ 个人中心、修改密码与优惠券
- ✅ 想看列表、评价、退票与扫码取票
- ✅ 混合推荐、冷启动偏好与 AI 电影助手
- ✅ 自然语言智能选座

## 开发规范

### 目录规范

- `api/` - 按模块划分API接口文件
- `components/` - 可复用的公共组件
- `views/` - 页面级组件
- `stores/` - Pinia状态管理模块
- `types/` - TypeScript类型定义

### 命名规范

- 组件文件：PascalCase（如 `UserProfile.vue`）
- 工具函数：camelCase（如 `formatDate.ts`）
- 常量：UPPER_SNAKE_CASE（如 `API_BASE_URL`）

### 代码规范

- 使用 TypeScript 进行类型检查
- 使用 Composition API（`<script setup>`）
- 使用 Element Plus 组件库
- 统一使用 Axios 进行HTTP请求

## API代理配置

开发环境下，所有 `/api` 开头的请求会被代理到后端服务：

```typescript
// vite.config.ts
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

## 环境变量

可以创建 `.env.development` 和 `.env.production` 文件配置不同环境的变量。

### 高德地图与定位

影院页的地图、影院坐标与距离都依赖高德 JS API，需要同时满足：

1. `VITE_AMAP_KEY` 是高德控制台创建的「Web 端（JS API）」类型 Key。
2. `VITE_AMAP_SECURITY_CODE` 是该 Key 对应的安全密钥。
3. Key 的**域名白名单**包含当前访问地址：本地开发通常是 `localhost` / `127.0.0.1`，部署后是站点域名。

定位流程为「浏览器坐标 → 逆地理编码补城市 → IP 城市兜底」。城市解析失败时页面显示「当前位置」而不是一直停在「定位中」，点击顶部城市胶囊或地图左上角的位置标签可强制重新定位。

PC 端高德定位默认优先「精确 IP 定位」；开着代理/VPN 时会定位到出口 IP 所在城市（例如日本）。`useLocation` 已改为优先浏览器定位（`GeoLocationFirst` + `enableHighAccuracy`），控制台会输出 `[定位] 来源=html5/ip`：来源为 `ip` 时城市来自出口 IP，关掉代理/VPN 后点击位置标签重试即可。

如果代理/VPN 必须常开，或浏览器拒绝定位，点击顶部城市胶囊打开「选择城市」弹窗：全国地级市通过高德行政区查询动态加载，支持搜索，选择后会以该城市中心点计算距离；未开通影院的城市影院列表会为空。选择结果保存在 `localStorage`，下次打开仍然生效，弹窗里的「重新定位」可恢复自动定位。

如果地图空白、影院没有 marker / 距离，可在浏览器控制台查看 `restapi.amap.com` 请求和高德返回的 `info`：

- `INVALID_USER_DOMAIN`：域名白名单不含当前地址。
- `INVALID_USER_SCODE`：安全密钥与 Key 不匹配。
- `USERKEY_PLAT_NOMATCH`：Key 类型不是「Web 端（JS API）」。

## 相关文档

项目级别的完整文档位于 `docs/` 目录：

| 文档 | 说明 |
|------|------|
| [自动化测试指南](../docs/TESTING.md) | 测试覆盖范围与执行命令 |
| [启动指南](../docs/GETTING_STARTED.md) | 前后端完整启动与验收步骤 |
| [系统架构](../docs/ARCHITECTURE.md) | 模块、数据库与接口说明 |
| [设计规格](../docs/design/SYSTEM_DESIGN.md) | 系统架构设计与技术选型 |

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user1 | admin123 | 普通用户 |

## 后续优化

1. 补充浏览器端到端测试。
2. 优化移动端适配与首屏包体积。
3. 完善错误边界、弱网重试和可访问性。
