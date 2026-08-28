# 智能电影购票系统 - 快速启动

## 📋 启动前检查清单

- [ ] MySQL 8.0+ 已安装并运行
- [ ] Redis 7.0+ 已安装并运行  
- [ ] JDK 17+ 已安装
- [ ] Node.js 18+ 已安装
- [ ] Maven 已安装（或使用IDE）

## 🚀 快速启动步骤

### 1. 初始化数据库

```bash
# 连接MySQL
mysql -u root -p123456

# 执行以下命令
source C:/Users/jinli/movie-ticket-system/movie-ticket-backend/schema.sql
source C:/Users/jinli/movie-ticket-system/movie-ticket-backend/data.sql

# 或者在MySQL客户端中直接执行SQL文件内容
```

### 2. 启动后端（推荐使用IDEA）

**方式一：使用IDEA**
1. 用IDEA打开 `movie-ticket-backend` 目录
2. 等待Maven依赖下载完成
3. 找到 `src/main/java/com/movie/ticket/MovieTicketBackendApplication.java`
4. 右键 -> Run 'MovieTicketBackendApplication'
5. 看到 "Started MovieTicketBackendApplication" 表示启动成功

**方式二：使用命令行**
```bash
cd movie-ticket-backend
mvn clean install
mvn spring-boot:run
```

### 3. 启动用户端前端

```bash
cd movie-ticket-user
npm install
npm run dev
```

访问：http://localhost:3000

### 4. 启动管理端前端

```bash
cd movie-ticket-admin
npm install  
npm run dev
```

访问：http://localhost:3001

## 🔑 测试账号

### 用户端
- 用户名：`user`
- 密码：`admin123`

### 管理端
- 用户名：`admin`
- 密码：`admin123`

## 🌐 访问地址

- **用户端**：http://localhost:3000
- **管理端**：http://localhost:3001
- **后端API**：http://localhost:8080/api

## ✅ 验证启动成功

### 后端验证
```bash
# 测试登录接口
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"user\",\"password\":\"user123\"}"
```

### 前端验证
1. 打开 http://localhost:3000
2. 应该看到电影列表
3. 点击登录，输入测试账号
4. 登录成功后可以浏览电影

## 📊 初始数据说明

系统已预置以下测试数据：

- **用户**：3个（普通用户、管理员、影院管理员）
- **影院**：5个（万达、CGV、博纳、耀莱、UME）
- **影厅**：10个
- **电影**：10部（流浪地球3、长安三万里等）
- **场次**：未来7天的排片

## 🎬 功能演示流程

### 用户端完整购票流程

1. **浏览电影**
   - 访问首页
   - 查看"热门推荐"
   - 查看"为你推荐"（登录后）
   - 切换"正在热映"/"即将上映"

2. **选择电影**
   - 点击电影卡片
   - 查看电影详情
   - 查看"相似推荐"

3. **选择场次**
   - 选择影院
   - 选择日期
   - 选择场次

4. **选择座位**
   - 查看座位图
   - 点击选择座位（绿色可选）
   - 最多选5个座位
   - 查看总价

5. **确认订单**
   - 查看观影信息
   - 填写备注（可选）
   - 提交订单

6. **支付订单**
   - 查看订单信息
   - 点击"立即支付"
   - 确认支付

7. **查看订单**
   - 点击"我的订单"
   - 查看订单列表
   - 点击查看详情

### 管理端功能演示

1. **Dashboard**
   - 查看统计数据
   - 查看票房排行榜
   - 使用快捷操作

2. **影院管理**
   - 查看影院列表
   - 添加/编辑/删除影院
   - 管理影院状态

3. **电影管理**
   - 查看电影列表
   - 添加/编辑/删除电影
   - 管理电影状态

4. **场次管理**
   - 查看场次列表
   - 添加/编辑/删除场次
   - 管理场次状态

## ❗ 常见问题

### 1. 后端启动失败

**数据库连接失败**
```
检查：
1. MySQL是否启动
2. 数据库名称是否为 movie_ticket
3. 用户名密码是否正确（root/123456）
4. 端口是否为3306
```

**Redis连接失败**
```
检查：
1. Redis是否启动
2. 端口是否为6379
3. 是否设置了密码
```

### 2. 前端启动失败

**端口被占用**
```
解决方案：
1. 修改vite.config.ts中的端口
2. 或结束占用端口的进程
```

**依赖安装失败**
```
解决方案：
1. 清除缓存：npm cache clean --force
2. 删除node_modules
3. 重新安装：npm install
```

### 3. 数据库表不存在

```
解决方案：
执行schema.sql创建表结构
执行data.sql插入测试数据
```

## 📝 开发建议

### 后端开发
- 使用IDEA的热部署功能
- 开启Debug模式
- 查看控制台日志

### 前端开发
- Vite支持热更新
- 使用浏览器开发者工具
- 查看Network面板

## 📚 相关文档

- [项目总览](./OVERVIEW.md)
- [设计文档](./DESIGN.md)
- [详细启动指南](./STARTUP_GUIDE.md)
- [第三阶段测试指南](./STAGE3_TEST_GUIDE.md)

## 🎯 下一步

启动成功后，建议：

1. 完整体验购票流程
2. 测试推荐功能
3. 查看管理端统计
4. 尝试各种操作

## 💡 提示

- 首次启动可能需要下载依赖，请耐心等待
- 建议使用Chrome浏览器获得最佳体验
- 遇到问题请查看控制台日志
- 可以使用浏览器开发者工具调试

---

**祝你使用愉快！** 🎉

如有问题，请查看详细文档或联系开发团队。
