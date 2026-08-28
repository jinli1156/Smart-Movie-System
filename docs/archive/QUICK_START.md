# 快速启动指南

本文档提供快速启动项目的详细步骤。

## 前置条件检查

在开始之前，请确保已安装以下软件：

```bash
# 检查Java版本（需要17+）
java -version

# 检查Maven版本（需要3.8+）
mvn -version

# 检查Node.js版本（需要18+）
node -version

# 检查MySQL版本（需要8.0+）
mysql --version

# 检查Redis是否安装
redis-server --version
```

## 第一次启动

### 1. 数据库初始化

```bash
# 启动MySQL服务
# Windows: 在服务中启动MySQL
# Linux/Mac: sudo service mysql start

# 登录MySQL
mysql -u root -p

# 在MySQL命令行中执行
source movie-ticket-backend/src/main/resources/db/schema.sql
source movie-ticket-backend/src/main/resources/db/data.sql

# 如已部署过旧版本，请按需执行以下迁移脚本（顺序无关）：
source movie-ticket-backend/src/main/resources/db/migration_fix_poster_url.sql
source movie-ticket-backend/src/main/resources/db/migration_fix_showtime_status.sql
source movie-ticket-backend/src/main/resources/db/migration_add_ticket_pickup.sql

# 退出MySQL
exit
```

### 2. 修改配置

编辑 `movie-ticket-backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    username: root
    password: 你的MySQL密码
  
  data:
    redis:
      password: 你的Redis密码（如果有）
```

### 3. 启动Redis

```bash
# Windows: 
redis-server

# Linux/Mac:
redis-server
```

### 4. 启动后端

打开新的终端窗口：

```bash
cd movie-ticket-backend
mvn clean install
mvn spring-boot:run
```

等待看到 "Started MovieTicketApplication" 表示启动成功。

### 5. 启动用户端前端

打开新的终端窗口：

```bash
cd movie-ticket-user
npm install
npm run dev
```

访问 http://localhost:3000

### 6. 启动管理端前端

打开新的终端窗口：

```bash
cd movie-ticket-admin
npm install
npm run dev
```

访问 http://localhost:3001

## 日常启动

第一次启动后，后续启动只需：

### 方式一：分别启动

```bash
# 终端1：启动Redis
redis-server

# 终端2：启动后端
cd movie-ticket-backend
mvn spring-boot:run

# 终端3：启动用户端
cd movie-ticket-user
npm run dev

# 终端4：启动管理端
cd movie-ticket-admin
npm run dev
```

### 方式二：使用脚本（推荐）

#### Windows (PowerShell)

创建 `start-all.ps1`：

```powershell
# 启动Redis
Start-Process powershell -ArgumentList "redis-server"

# 启动后端
Start-Process powershell -ArgumentList "cd movie-ticket-backend; mvn spring-boot:run"

# 等待后端启动
Start-Sleep -Seconds 30

# 启动用户端
Start-Process powershell -ArgumentList "cd movie-ticket-user; npm run dev"

# 启动管理端
Start-Process powershell -ArgumentList "cd movie-ticket-admin; npm run dev"
```

运行：
```bash
.\start-all.ps1
```

#### Linux/Mac (Bash)

创建 `start-all.sh`：

```bash
#!/bin/bash

# 启动Redis
redis-server &

# 启动后端
cd movie-ticket-backend
mvn spring-boot:run &

# 等待后端启动
sleep 30

# 启动用户端
cd ../movie-ticket-user
npm run dev &

# 启动管理端
cd ../movie-ticket-admin
npm run dev &
```

运行：
```bash
chmod +x start-all.sh
./start-all.sh
```

## 测试登录

### 用户端测试
1. 访问 http://localhost:3000
2. 点击"登录"
3. 使用测试账号：
   - 用户名：user1
   - 密码：admin123

### 管理端测试
1. 访问 http://localhost:3001
2. 使用管理员账号：
   - 用户名：admin
   - 密码：admin123

## 常见问题

### 1. 端口被占用

如果端口被占用，可以修改配置：

- 后端端口：修改 `application.yml` 中的 `server.port`
- 用户端端口：修改 `movie-ticket-user/vite.config.ts` 中的 `server.port`
- 管理端端口：修改 `movie-ticket-admin/vite.config.ts` 中的 `server.port`

### 2. 数据库连接失败

检查：
- MySQL服务是否启动
- 用户名密码是否正确
- 数据库 `movie_ticket` 是否已创建

### 3. Redis连接失败

检查：
- Redis服务是否启动
- Redis端口（默认6379）是否被占用

### 4. 前端依赖安装失败

尝试：
```bash
# 清除缓存
npm cache clean --force

# 使用国内镜像
npm config set registry https://registry.npmmirror.com

# 重新安装
npm install
```

### 5. Maven依赖下载慢

配置Maven使用阿里云镜像，编辑 `~/.m2/settings.xml`：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

## 停止服务

按 `Ctrl + C` 停止各个终端中的服务。

## 下一步

- 查看 [设计文档](docs/superpowers/specs/2026-05-16-movie-ticket-system-design.md) 了解系统架构
- 查看各子项目的 README 了解详细功能
- 开始开发新功能
