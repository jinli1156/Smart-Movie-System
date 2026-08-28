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

访问 `http://localhost:3000`

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

### 待开发功能

- [ ] 个人中心

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

## 相关文档

项目级别的完整文档位于 `docs/` 目录：

| 文档 | 说明 |
|------|------|
| [项目总览](../docs/PROJECT_OVERVIEW.md) | 项目整体架构与各阶段状态 |
| [快速启动](../docs/QUICK_START.md) | 前后端完整启动指南（含前置条件检查） |
| [设计规格](../docs/superpowers/specs/2026-05-16-movie-ticket-system-design.md) | 系统架构设计与技术选型 |
| [进度跟踪](../docs/PROGRESS.md) | 各阶段开发进度与里程碑 |

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user1 | admin123 | 普通用户 |

## 下一步开发

1. 开发个人中心
2. 接入智能推荐功能
3. 接入智能客服
4. 移动端适配优化
