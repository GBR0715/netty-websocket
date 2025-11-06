package com.example.nettywebsocket.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Netty WebSocket服务器
 */
@Component
public class NettyWebSocketServer {
    
    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocketServer.class);
    
    @Value("${netty.websocket.port:8081}")
    private int port;
    
    @Value("${netty.websocket.bossGroupThreads:2}")
    private int bossGroupThreads;
    
    @Value("${netty.websocket.workerGroupThreads:16}")
    private int workerGroupThreads;
    
    @Autowired
    private NettyServerInitializer serverInitializer;
    
    // Boss线程组，用于接收连接
    private EventLoopGroup bossGroup;
    
    // Worker线程组，用于处理连接的IO操作
    private EventLoopGroup workerGroup;
    
    /**
     * 服务器启动方法，Spring Boot启动时自动调用
     */
    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(bossGroupThreads);
        workerGroup = new NioEventLoopGroup(workerGroupThreads);
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(serverInitializer)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            
            // 绑定端口并启动服务器
            ChannelFuture future = bootstrap.bind(port).sync();
            
            logger.info("Netty WebSocket服务器已启动，监听端口: {}", port);
            
            // 等待服务器关闭（通常不会执行到这里，除非显式关闭）
            // future.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("启动Netty WebSocket服务器失败", e);
            stop();
        }
    }
    
    /**
     * 服务器停止方法，Spring Boot关闭时自动调用
     */
    @PreDestroy
    public void stop() {
        logger.info("正在关闭Netty WebSocket服务器...");
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        logger.info("Netty WebSocket服务器已关闭");
    }
}