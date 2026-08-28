# 取票功能实现文档

## 功能概述

实现了完整的电影票取票功能，包括：
1. 支付后自动生成8位取票码
2. 二维码生成和显示
3. 摄像头扫码取票
4. 手动输入取票码
5. 取票状态管理和显示

## 数据库变更

### 新增字段（ticket_order 表）

```sql
-- 取票码（8位数字）
ticket_code VARCHAR(8)

-- 是否已取票（0-未取票，1-已取票）
is_picked INT NOT NULL DEFAULT 0

-- 取票时间
pick_time DATETIME

-- 取票码索引
INDEX idx_ticket_code (ticket_code)
```

### 迁移脚本

位置：`movie-ticket-backend/src/main/resources/db/migration_add_ticket_pickup.sql`

执行方式：
```bash
mysql -u root -p movie_ticket < migration_add_ticket_pickup.sql
```

## 后端实现

### 1. 实体类更新

**TicketOrder.java**
- 新增 `ticketCode` 字段
- 新增 `isPicked` 字段
- 新增 `pickTime` 字段

**OrderVO.java**
- 新增 `ticketCode` 字段
- 新增 `isPicked` 字段
- 新增 `pickTime` 字段

### 2. 服务层

**OrderService.java**

新增方法：
```java
// 生成取票码（订单号后4位 + 4位随机数）
private String generateTicketCode(String orderNo)

// 取票接口
public OrderVO pickTicket(String ticketCode)
```

修改方法：
```java
// 支付时生成取票码
public void payOrder(String orderNo)
```

### 3. 控制器

**OrderController.java**

新增接口：
```java
POST /order/pick?ticketCode={ticketCode}
```

## 前端实现

### 1. 类型定义

**types/index.ts**
```typescript
export interface Order {
  // ... 其他字段
  ticketCode?: string
  isPicked?: number
  pickTime?: string
}
```

### 2. API 接口

**api/order.ts**
```typescript
pickTicket(ticketCode: string): Promise<Order>
```

### 3. 组件实现

#### TicketQRCode.vue（取票码显示）
- 生成8位取票码
- 生成包含订单信息的二维码
- 显示票务详情
- 支持下载二维码
- 支持复制二维码数据（调试用）

#### QRScanner.vue（扫码取票）
- 调用摄像头进行实时扫描
- 支持前置/后置摄像头切换
- 扫描成功后调用后端取票接口
- 支持手动输入取票码
- 显示取票成功/失败状态
- 扫描指示器和使用提示

#### Profile.vue（个人中心）
- 根据 `isPicked` 字段显示"已取票"状态
- 已取票订单显示绿色"已取票"按钮（禁用）
- 未取票订单显示"查看取票码"按钮

### 4. 扫描优化

**扫描配置：**
- fps: 5（降低处理频率，提高识别率）
- qrbox: 动态大小（占视窗70%）
- 支持多种摄像头模式回退
- 实时扫描状态指示

**调试功能：**
- 控制台输出详细日志
- 二维码数据复制功能
- 扫描失败错误提示

## 使用流程

### 用户端流程

1. **下单并支付**
   - 用户选座下单
   - 支付成功后，系统自动生成取票码

2. **查看取票码**
   - 在订单详情页点击"查看取票码"
   - 显示二维码和8位取票码
   - 可下载二维码或复制数据

3. **取票**
   
   **方式一：扫码取票**
   - 在首页点击"扫码取票"
   - 允许摄像头权限
   - 点击"开始扫描"
   - 将取票二维码对准扫描框
   - 扫描成功后显示票务信息
   - 点击"确认取票"完成取票

   **方式二：手动输入**
   - 在扫码界面点击"手动输入"
   - 输入8位取票码
   - 点击"确认"完成取票

4. **查看已取票状态**
   - 个人中心订单显示"已取票"标记
   - 已取票订单不能再次取票

### 影院端流程

1. 用户到达影院
2. 在自助取票机扫描二维码或输入取票码
3. 系统验证取票码有效性
4. 打印实体票

## 取票码规则

### 生成规则
- 格式：8位数字
- 组成：订单号后4位 + 4位随机数
- 示例：订单号 `ORDER1234567890` → 取票码 `78905678`

### 验证规则
- 取票码必须存在
- 订单状态必须为"已支付"（status = 1）
- 订单不能已取票（isPicked = 0）
- 取票成功后标记 isPicked = 1，记录 pickTime

## 二维码数据格式

```json
{
  "type": "MOVIE_TICKET",
  "orderNo": "ORDER1234567890",
  "ticketCode": "78905678",
  "movieTitle": "电影名称",
  "cinemaName": "影院名称",
  "hallName": "影厅名称",
  "seats": "5排6座,5排7座",
  "showtime": "2026-05-31T19:30:00",
  "timestamp": 1717171200000
}
```

## 安全性考虑

1. **取票码唯一性**
   - 每个订单生成唯一取票码
   - 数据库添加索引确保查询效率

2. **防重复取票**
   - 后端验证 isPicked 状态
   - 已取票订单无法再次取票

3. **权限验证**
   - 取票接口需要登录
   - 验证订单状态和支付状态

4. **数据完整性**
   - 取票时记录取票时间
   - 保留完整的取票记录

## 测试建议

### 功能测试

1. **取票码生成**
   - 支付订单后检查取票码是否生成
   - 验证取票码格式（8位数字）

2. **二维码扫描**
   - 测试不同光线条件下的扫描
   - 测试不同距离的扫描
   - 测试前置/后置摄像头

3. **手动输入**
   - 输入正确取票码
   - 输入错误取票码
   - 输入已使用的取票码

4. **状态显示**
   - 未取票订单显示"查看取票码"
   - 已取票订单显示"已取票"
   - 个人中心正确显示状态

### 异常测试

1. 未支付订单尝试取票
2. 已取消订单尝试取票
3. 重复取票
4. 无效取票码
5. 摄像头权限被拒绝

## 已知问题和改进方向

### 当前限制

1. **扫描灵敏度**
   - 屏幕二维码扫描可能较困难
   - 建议打印二维码或调整屏幕亮度

2. **浏览器兼容性**
   - 需要 HTTPS 才能访问摄像头
   - 部分旧版浏览器可能不支持

### 改进方向

1. **取票码短信通知**
   - 支付成功后发送取票码短信

2. **取票提醒**
   - 开场前提醒用户取票

3. **取票统计**
   - 统计取票率
   - 分析取票时间分布

4. **多种取票方式**
   - 支持身份证取票
   - 支持手机号取票

## 文件清单

### 后端文件
- `entity/TicketOrder.java` - 订单实体
- `vo/OrderVO.java` - 订单VO
- `service/OrderService.java` - 订单服务
- `controller/OrderController.java` - 订单控制器
- `resources/db/migration_add_ticket_pickup.sql` - 数据库迁移脚本

### 前端文件
- `types/index.ts` - 类型定义
- `api/order.ts` - 订单API
- `components/TicketQRCode.vue` - 取票码组件
- `components/QRScanner.vue` - 扫码组件
- `views/Profile.vue` - 个人中心
- `views/OrderDetail.vue` - 订单详情
- `views/Home.vue` - 首页

## 部署步骤

1. **执行数据库迁移**
   ```bash
   mysql -u root -p movie_ticket < migration_add_ticket_pickup.sql
   ```

2. **重启后端服务**
   ```bash
   cd movie-ticket-backend
   mvn clean package
   java -jar target/movie-ticket-backend.jar
   ```

3. **重新构建前端**
   ```bash
   cd movie-ticket-user
   npm run build
   ```

4. **测试功能**
   - 创建订单并支付
   - 查看取票码
   - 测试扫码取票
   - 测试手动输入
   - 验证状态显示

## 技术栈

### 后端
- Spring Boot
- MyBatis Plus
- MySQL

### 前端
- Vue 3
- TypeScript
- Element Plus
- qrcode（二维码生成）
- html5-qrcode（二维码扫描）

## 联系方式

如有问题，请查看：
- 控制台日志（F12）
- 后端日志
- 数据库记录
