package com.ajaxjs.devtools.netspeed.annotation;

/**
 * 决定请求之间如何共享令牌桶的限速维度。
 * <p>GLOBAL 在单个管理器实例内共享；其他维度分别以接口 URI、用户请求头或来源 IP 为键。
 * 没有跨进程或跨服务实例的协调存储。
 */
public enum LimitType {
    /**
     * 同一管理器实例内所有全局限速请求共用一个桶。
     */
    GLOBAL,

    /**
     * 同一请求 URI 的请求共享桶，不区分 HTTP 方法。
     */
    API,

    /**
     * 按 userHeader 中的用户标识共享，缺失时回退远端地址。
     */
    USER,

    /**
     * 按推断的来源 IP 共享桶。
     */
    IP
}
