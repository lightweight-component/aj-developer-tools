package com.example.netspeed.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明方法或控制器类型的响应下载带宽策略。
 * <p>方法注解优先于类型注解。拦截器计算字节速率、桶容量及维度键，并将包装响应存入请求属性。
 * 控制器需要通过 BandwidthLimitHelper 获取包装响应并写入，单独声明注解不会替换 MVC 使用的原始响应。
 * <p>容量和速率应为正数，当前实现不自动校验数值或溢出。free、vip 与 value 共用 unit。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BandwidthLimit {
    /**
     * 默认带宽数值，与 unit 共同确定每秒速率；当前未执行正数校验。
     *
     * @return 默认二百，或配置的单位数值
     */
    long value() default 200;

    /**
     * value、free、vip 共同使用的带宽单位。
     *
     * @return 带宽换算单位，默认为 KB
     */
    BandwidthUnit unit() default BandwidthUnit.KB;

    /**
     * 请求之间共享令牌桶的维度，同一实例内生效。
     *
     * @return 限速维度，默认为 GLOBAL
     */
    LimitType type() default LimitType.GLOBAL;

    /**
     * X-User-Type 为 free 时使用的档位，正数才生效，否则回退 value。
     *
     * @return 普通档带宽数值，默认负一
     */
    long free() default -1;

    /**
     * X-User-Type 为 vip 时使用的档位，正数才生效，否则回退 value。
     *
     * @return VIP 档带宽数值，默认负一
     */
    long vip() default -1;

    /**
     * 桶容量相对于一秒带宽的倍数，计算后转 long 截断小数。
     * <p>只用于首次创建桶，复用桶不会调整容量；正数和溢出由调用者保证。
     *
     * @return 容量倍数，默认为一
     */
    double capacityMultiplier() default 1.0;

    /**
     * 数组写入的请求分块大小，非正数表示自动选择。
     * <p>限速流将正数限制在 512 至 65536 字节，不改变桶容量。
     *
     * @return 分块字节数，默认负一
     */
    int chunkSize() default -1;

    /**
     * USER 维度读取用户键的请求头名，缺失时回退远端地址，不执行用户认证。
     *
     * @return 默认 X-User-Id 或配置的头名
     */
    String userHeader() default "X-User-Id";
}
