package com.example.nettywebsocket.handler;

import com.example.nettywebsocket.manager.WebSocketConnectionManager;
import com.example.nettywebsocket.model.WebSocketMessage;
import com.example.nettywebsocket.security.TokenService;
import com.example.nettywebsocket.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * WebSocket消息处理器
 */
@Component
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    
    private WebSocketServerHandshaker handshaker;
    private static final String WEBSOCKET_PATH = "/websocket";
    
    @Autowired
    private WebSocketConnectionManager connectionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private CustomerService customerService;
    
    // 空闲超时时间（秒）
    @Value("${netty.websocket.idleTimeout:180}")
    private int idleTimeout;
    
    // 存储当前连接的用户ID
    private String userId;
    
    // 存储当前连接的token
    private String token;
    
    // 用户角色：AGENT(客服) 或 USER(普通用户)
    private String userRole;
    
    // 分配的客服ID（普通用户使用）
    private String assignedAgentId;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端连接成功: {}", ctx.channel().remoteAddress());
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端断开连接: {}", ctx.channel().remoteAddress());
        
        // 如果已经成功建立连接并有用户ID
        if (userId != null) {
            // 根据角色处理客服相关逻辑
            if ("AGENT".equals(userRole)) {
                // 客服下线，注销客服
                customerService.unregisterAgent(userId);
                logger.info("客服 {} 已下线", userId);
            } else if ("USER".equals(userRole)) {
                // 普通用户下线，从客服服务列表移除
                customerService.removeUserFromAgent(userId);
                logger.info("普通用户 {} 已下线，从客服服务列表移除", userId);
            }
        }
        
        // 从连接管理器中移除连接
        connectionManager.removeConnection(ctx.channel());
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 处理HTTP请求，WebSocket握手
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        }
        // 处理WebSocket消息
        else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }
    
    /**
     * 处理HTTP请求，完成WebSocket握手
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // 要求GET方法
        if (!req.method().name().equals("GET")) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN));
            return;
        }
        
        // 从请求参数中获取token
        String uri = req.uri();
        String token = null;
        
        if (uri.contains("?")) {
            String query = uri.substring(uri.indexOf("?") + 1);
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                    token = keyValue[1];
                    break;
                }
            }
        }
        
        // 如果没有提供token，尝试从请求头获取
        if (token == null) {
            token = req.headers().get("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7); // 移除 "Bearer " 前缀
            }
        }
        
        // 验证token
        boolean tokenValid = false;
        if (token != null && !token.isEmpty()) {
            tokenValid = tokenService.validateToken(token);
            if (tokenValid) {
                userId = tokenService.getUserIdByToken(token);
                this.token = token;
                logger.info("WebSocket连接token验证成功: userId={}", userId);
            }
        }
        
        // 如果token无效，拒绝连接
        if (!tokenValid || userId == null) {
            logger.warn("WebSocket连接token验证失败");
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            response.content().writeBytes("Unauthorized: Invalid token".getBytes(CharsetUtil.UTF_8));
            sendHttpResponse(ctx, req, response);
            return;
        }
        
        // 构建WebSocket握手响应
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(req), null, true);
        handshaker = factory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
            // 从请求参数中获取用户角色
            String role = req.uri().contains("role=agent") ? "AGENT" : "USER";
            this.userRole = role;
            
            // 根据角色处理
            if ("AGENT".equals(role)) {
                // 客服角色，注册到客服系统
                customerService.registerAgent(userId);
                logger.info("客服 {} 已上线", userId);
                
                // 发送欢迎消息给客服
                WebSocketMessage welcomeMsg = new WebSocketMessage("SYSTEM", "欢迎回来！您可以开始为客户服务了。", "server", userId);
                sendMessage(ctx, objectMapper.writeValueAsString(welcomeMsg));
            } else {
                // 普通用户角色，分配客服
                String agentId = customerService.assignCustomerService(userId);
                this.assignedAgentId = agentId;
                
                if (agentId != null) {
                    logger.info("普通用户 {} 已分配给客服 {}", userId, agentId);
                } else {
                    logger.warn("普通用户 {} 暂时没有可用客服", userId);
                    // 发送系统消息告知用户正在等待客服
                    WebSocketMessage waitingMsg = new WebSocketMessage("SYSTEM", "正在为您分配客服，请稍候...", "server", userId);
                    sendMessage(ctx, objectMapper.writeValueAsString(waitingMsg));
                }
            }
            
            // 握手成功后，将连接添加到管理器
            connectionManager.addConnection(userId, ctx.channel());
            
            // 发送连接成功消息
            WebSocketMessage welcomeMsg = new WebSocketMessage("SYSTEM", 
                    role.equals("AGENT") ? "客服连接成功" : "用户连接成功", 
                    "server", userId);
            
            // 添加额外信息
            if ("USER".equals(role) && assignedAgentId != null) {
                welcomeMsg.setContent(welcomeMsg.getContent() + ", 您的客服是: " + assignedAgentId);
            }
            
            sendMessage(ctx, objectMapper.writeValueAsString(welcomeMsg));
            
            logger.info("用户 {} WebSocket连接成功，角色 = {}", userId, role);
        }
    }
    
    /**
     * 处理WebSocket帧消息
     */
    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 关闭帧
        if (frame instanceof CloseWebSocketFrame) {
            logger.info("用户 {} 请求关闭连接", userId);
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        
        // Ping帧
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        
        // 文本帧
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            logger.info("收到用户 {} 的消息: {}", userId, text);
            
            try {
                // 解析消息
                WebSocketMessage message = objectMapper.readValue(text, WebSocketMessage.class);
                message.setSenderId(userId);
                message.setMessageId(UUID.randomUUID().toString());
                
                // 客服消息处理
                if ("AGENT".equals(userRole)) {
                    // 客服发送消息给用户
                    if (message.getType() == null || "CHAT".equals(message.getType())) {
                        // 确保消息类型正确
                        message.setType("CHAT");
                        // 发送给指定用户
                        handleChatMessage(ctx, message);
                        logger.info("客服 {} 发送消息给用户 {}", userId, message.getReceiverId());
                    } else {
                        // 处理其他类型消息
                        switch (message.getType()) {
                            case "GROUP":
                                handleGroupMessage(ctx, message);
                                break;
                            case "BROADCAST":
                                handleBroadcastMessage(ctx, message);
                                break;
                            default:
                                sendMessage(ctx, text);
                                break;
                        }
                    }
                } else if ("USER".equals(userRole)) {
                    // 普通用户发送消息，自动转发给分配的客服
                    if (assignedAgentId != null) {
                        message.setType("CHAT");
                        message.setReceiverId(assignedAgentId); // 强制发送给客服
                        handleChatMessage(ctx, message);
                        logger.info("用户 {} 发送消息给客服 {}", userId, assignedAgentId);
                    } else {
                        // 没有分配客服，发送系统消息
                        WebSocketMessage errorMsg = new WebSocketMessage("SYSTEM", "正在为您分配客服，请稍候...", "server", userId);
                        sendMessage(ctx, objectMapper.writeValueAsString(errorMsg));
                    }
                } else {
                    // 原来的消息处理逻辑
                    switch (message.getType()) {
                        case "CHAT":
                            handleChatMessage(ctx, message);
                            break;
                        case "GROUP":
                            handleGroupMessage(ctx, message);
                            break;
                        case "BROADCAST":
                            handleBroadcastMessage(ctx, message);
                            break;
                        default:
                            sendMessage(ctx, text);
                            break;
                    }
                }
            } catch (Exception e) {
                logger.error("处理消息失败", e);
                // 发送错误消息
                WebSocketMessage errorMsg = new WebSocketMessage("ERROR", "消息格式错误", "server", userId);
                try {
                    sendMessage(ctx, objectMapper.writeValueAsString(errorMsg));
                } catch (Exception ex) {
                    logger.error("发送错误消息失败", ex);
                }
            }
            return;
        }
        
        // 二进制帧处理（如果需要）
        if (frame instanceof BinaryWebSocketFrame) {
            // 这里可以添加二进制消息的处理逻辑
            logger.info("收到二进制消息，长度: {}", frame.content().readableBytes());
        }
    }
    
    /**
     * 处理聊天消息
     */
    private void handleChatMessage(ChannelHandlerContext ctx, WebSocketMessage message) throws Exception {
        String receiverId = message.getReceiverId();
        
        // 记录消息到会话历史
        if ("AGENT".equals(userRole)) {
            // 客服发送消息，调用handleAgentMessage记录
            customerService.handleAgentMessage(message);
        } else if ("USER".equals(userRole)) {
            // 用户发送消息，调用handleUserMessage记录
            customerService.handleUserMessage(message);
        }
        
        // 发送给接收者
        boolean sent = connectionManager.sendMessage(receiverId, objectMapper.writeValueAsString(message));
        
        // 发送确认消息给发送者
        WebSocketMessage confirmMsg = new WebSocketMessage(
                "CONFIRM", 
                sent ? "消息已发送" : "接收者不在线", 
                "server", 
                userId
        );
        confirmMsg.setMessageId(message.getMessageId());
        sendMessage(ctx, objectMapper.writeValueAsString(confirmMsg));
    }
    
    /**
     * 处理群组消息
     */
    private void handleGroupMessage(ChannelHandlerContext ctx, WebSocketMessage message) throws Exception {
        String groupId = message.getReceiverId();
        
        // 发送到群组
        connectionManager.sendToGroup(groupId, objectMapper.writeValueAsString(message));
        
        // 发送确认消息
        WebSocketMessage confirmMsg = new WebSocketMessage(
                "CONFIRM", 
                "群组消息已发送", 
                "server", 
                userId
        );
        confirmMsg.setMessageId(message.getMessageId());
        sendMessage(ctx, objectMapper.writeValueAsString(confirmMsg));
    }
    
    /**
     * 处理广播消息
     */
    private void handleBroadcastMessage(ChannelHandlerContext ctx, WebSocketMessage message) throws Exception {
        // 广播消息
        connectionManager.broadcast(objectMapper.writeValueAsString(message));
        
        // 发送确认消息
        WebSocketMessage confirmMsg = new WebSocketMessage(
                "CONFIRM", 
                "广播消息已发送", 
                "server", 
                userId
        );
        confirmMsg.setMessageId(message.getMessageId());
        sendMessage(ctx, objectMapper.writeValueAsString(confirmMsg));
    }
    
    /**
     * 发送HTTP响应
     */
    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        // 返回响应
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        
        // 发送响应并关闭连接
        ctx.channel().writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * 获取WebSocket地址
     */
    private static String getWebSocketLocation(FullHttpRequest req) {
        String location = req.headers().get(HttpHeaderNames.HOST) + WEBSOCKET_PATH;
        return "ws://" + location;
    }
    
    /**
     * 发送消息
     */
    private void sendMessage(ChannelHandlerContext ctx, String message) {
        ctx.channel().writeAndFlush(new TextWebSocketFrame(message));
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("WebSocket异常", cause);
        ctx.close();
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 读空闲超时，关闭连接
                logger.info("用户 {} WebSocket连接空闲超时（{}秒），自动断开连接", userId, idleTimeout);
                // 发送超时消息给客户端
                try {
                    WebSocketMessage timeoutMsg = new WebSocketMessage("SYSTEM", "连接空闲超时，即将断开", "server", userId);
                    sendMessage(ctx, objectMapper.writeValueAsString(timeoutMsg));
                    // 延迟100毫秒关闭，确保消息发送完成
                    ctx.channel().close().syncUninterruptibly();
                } catch (Exception e) {
                    logger.error("发送超时消息失败", e);
                    ctx.close();
                }
            }
        }
        super.userEventTriggered(ctx, evt);
    }
    
    // 移除硬编码的idleTimeout，使用@Value注入的值
    
    // Setter方法，用于手动注入依赖
    public void setConnectionManager(WebSocketConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public void setTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    public void setCustomerService(CustomerService customerService) {
        this.customerService = customerService;
    }
    
    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }
}