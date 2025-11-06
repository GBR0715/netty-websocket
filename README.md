# Netty WebSocket 分布式服务器

这是一个基于Spring Boot和Netty实现的WebSocket服务器，支持分布式部署。

## 功能特性

- 基于Netty的高性能WebSocket服务
- 支持分布式部署，使用Redis实现跨服务器通信
- 支持单聊、群聊和广播消息
- 提供REST API接口管理WebSocket连接和消息
- 自动管理WebSocket连接的生命周期
- 支持用户分组和群组消息

## 技术栈

- Spring Boot 2.7.15
- Netty 4.1.94.Final
- Redis
- Spring Cloud

## 项目结构

```
src/main/java/com/example/nettywebsocket/
├── NettyWebSocketApplication.java    # Spring Boot应用主入口
├── config/
│   └── RedisMessageListenerConfig.java  # Redis消息监听配置
├── controller/
│   └── WebSocketController.java      # REST API控制器
├── handler/
│   └── WebSocketHandler.java         # WebSocket消息处理器
├── listener/
│   └── RedisMessageListener.java     # Redis消息监听器
├── manager/
│   ├── WebSocketConnectionManager.java  # WebSocket连接管理器接口
│   └── RedisWebSocketConnectionManager.java  # Redis实现的连接管理器
├── model/
│   └── WebSocketMessage.java         # WebSocket消息模型
└── server/
    ├── NettyServerInitializer.java   # Netty服务器初始化器
    └── NettyWebSocketServer.java     # Netty WebSocket服务器
```

## 配置说明

主要配置位于 `application.yml` 文件中：

- `server.port`: Spring Boot应用端口
- `netty.websocket.port`: Netty WebSocket服务端口
- `spring.redis`: Redis配置
- `eureka`: 服务注册配置（可选）

## 使用方法

### 1. 启动Redis服务器

确保Redis服务器已经启动并可访问。

### 2. 启动应用

使用Maven构建并启动应用：

```bash
mvn clean package
java -jar target/netty-websocket-1.0.0.jar
```

### 3. 连接WebSocket

客户端可以通过以下地址连接WebSocket：

```
ws://localhost:8081/websocket?userId=your_user_id
```

其中 `your_user_id` 是用户的唯一标识。

### 4. 使用REST API

应用提供了以下REST API接口：

- `GET /api/websocket/online-count` - 获取在线用户数量
- `GET /api/websocket/is-online/{userId}` - 检查用户是否在线
- `POST /api/websocket/send-to-user` - 向指定用户发送消息
- `POST /api/websocket/send-to-group` - 向指定群组发送消息
- `POST /api/websocket/broadcast` - 广播消息给所有在线用户
- `POST /api/websocket/add-to-group` - 将用户添加到群组
- `POST /api/websocket/remove-from-group` - 将用户从群组移除

## 消息格式

WebSocket消息使用JSON格式，包含以下字段：

```json
{
  "type": "CHAT",  // 消息类型：CHAT, GROUP, BROADCAST, SYSTEM, ERROR, CONFIRM
  "content": "消息内容",
  "senderId": "发送者ID",
  "receiverId": "接收者ID",
  "timestamp": 1634567890000,
  "messageId": "消息唯一ID"
}
```

## 分布式部署说明

在分布式环境中：

1. 每个应用实例都需要连接到同一个Redis服务器
2. Redis用于存储用户连接信息和转发跨服务器的消息
3. 可以使用Spring Cloud的服务发现功能（如Eureka）进行服务注册和发现
4. 客户端可以通过负载均衡器连接到任意一个应用实例

## 注意事项

1. 确保Redis服务器配置正确且可访问
2. 在生产环境中，建议配置适当的连接池和超时设置
3. 可以根据实际需求调整Netty的线程池配置
4. 对于大规模应用，考虑使用Redis集群以提高可用性