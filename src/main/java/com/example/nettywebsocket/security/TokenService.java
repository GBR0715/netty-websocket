package com.example.nettywebsocket.security;

/**
 * Token验证服务接口
 * 用于校验用户token的有效性，可配置为调用外部业务系统的接口
 */
public interface TokenService {

    /**
     * 验证token的有效性
     *
     * @param token 用户token
     * @return 是否有效
     */
    boolean validateToken(String token);

    /**
     * 根据token获取用户ID
     *
     * @param token 用户token
     * @return 用户ID，如果token无效则返回null
     */
    String getUserIdByToken(String token);

    /**
     * 释放已使用的token，允许重复验证
     *
     * @param token 要释放的token
     */
    void releaseToken(String token);
}