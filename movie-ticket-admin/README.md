# 电影购票系统 - 管理端前端

基于 Vue 3 + TypeScript + Element Plus 的电影购票系统管理后台

## 技术栈

- Vue 3.4
- TypeScript 5.3
- Vite 5.1
- Vue Router 4.3
- Pinia 2.1
- Element Plus 2.6
- Axios 1.6
- ECharts 5.5（数据可视化）

## 项目结构

```
movie-ticket-admin/
├── public/                    # 静态资源
├── src/
│   ├── api/                   # API接口
│   │   ├── auth.ts            # 认证接口
│   │   ├── cinema.ts          # 影院接口
│   │   ├── hall.ts            # 影厅接口
│   │   ├── movie.ts           # 电影接口
│   │   └── showtime.ts        # 场次接口
│   ├── layout/                # 布局组件
│   │   └── MainLayout.vue     # 侧边栏+顶栏布局
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
│   │   ├── Dashboard.vue      # 仪表盘
│   │   ├── Movie.vue          # 电影管理
│   │   ├── Cinema.vue         # 影院管理
│   │   ├── Hall.vue           # 影厅管理
│   │   ├── Showtime.vue       # 排片管理
│   │   ├── Order.vue          # 订单管理
│   │   └── User.vue           # 用户管理
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

访问 `http://localhost:3001`

### 3. 构建生产版本

```bash
npm run build
```

## 功能模块

### 已实现功能

- ✅ 登录认证（JWT）
- ✅ 仪表盘（数据统计卡片 + 票房排行榜）
- ✅ 影院管理（列表/新增/编辑/删除）
- ✅ 影厅管理（列表/新增/编辑/删除，含座位布局）
- ✅ 电影管理（列表/新增/编辑/删除）
- ✅ 排片管理（列表/新增/编辑/删除）
- ✅ 订单管理（列表/详情/状态跟踪）
- ✅ 用户管理（列表/查询）

### 待开发功能

- [ ] 系统设置
- [ ] 日志查看
- [ ] 数据可视化图表（ECharts 深度集成）

## 权限说明

### 角色权限

- **超级管理员（ADMIN）**：拥有所有权限
- **影院管理员（CINEMA_ADMIN）**：管理所属影院的数据

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 超级管理员 |
| cinema_admin | admin123 | 影院管理员 |

## 开发计划

1. 搭建管理后台布局（侧边栏+顶栏）
2. 实现登录和权限控制
3. 开发各功能模块的CRUD
4. 集成ECharts数据可视化
5. 完善用户体验和交互
