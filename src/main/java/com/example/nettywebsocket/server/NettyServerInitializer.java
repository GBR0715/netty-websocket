package com.example.nettywebsocket.server;

import com.example.nettywebsocket.handler.WebSocketHandler;
import com.example.nettywebsocket.manager.WebSocketConnectionManager;
import com.example.nettywebsocket.security.TokenService;
import com.example.nettywebsocket.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty服务器初始化器，用于配置ChannelPipeline
 */
@Component
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
    
    @Autowired
    private WebSocketConnectionManager connectionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private CustomerService customerService;
    
    @Value("${netty.websocket.maxFramePayloadLength:65536}")
    private int maxFramePayloadLength;
    
    // 连接空闲超时时间（秒）
    @Value("${netty.websocket.idleTimeout:180}")
    private int idleTimeout;
    
    private static final String WEBSOCKET_PATH = "/websocket";
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 添加空闲状态处理器，设置3分钟（180秒）读空闲超时
        pipeline.addLast(new IdleStateHandler(idleTimeout, 0, 0));
        // HTTP编解码器
        pipeline.addLast(new HttpServerCodec());

        // 块写入处理器
        pipeline.addLast(new ChunkedWriteHandler());

        // HTTP对象聚合器，将HTTP消息的多个部分合并成一个完整的HTTP消息
        pipeline.addLast(new HttpObjectAggregator(65536));
        
        // WebSocket协议处理器，处理握手、ping/pong、关闭等
        // pipeline.addLast(new WebSocketServerProtocolHandler(
        //         WEBSOCKET_PATH,
        //         null,
        //         true,
        //         maxFramePayloadLength
        // ));
        
        // 为每个连接创建新的WebSocketHandler实例
        // 因为WebSocketHandler包含每个连接的状态，不能共享同一个实例
        WebSocketHandler webSocketHandler = new WebSocketHandler();
        webSocketHandler.setConnectionManager(connectionManager);
        webSocketHandler.setObjectMapper(objectMapper);
        webSocketHandler.setTokenService(tokenService);
        webSocketHandler.setCustomerService(customerService);
        webSocketHandler.setIdleTimeout(idleTimeout);
        
        pipeline.addLast(webSocketHandler);
    }
}