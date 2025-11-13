package com.example.nettywebsocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Spring Boot应用主入口类
 */
@SpringBootApplication
public class NettyWebSocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(NettyWebSocketApplication.class, args);
    }

}