package com.ajaxjs.devtools.netspeed;

import com.ajaxjs.devtools.netspeed.annotation.BandwidthLimit;
import com.ajaxjs.devtools.netspeed.annotation.BandwidthUnit;
import com.ajaxjs.devtools.netspeed.annotation.LimitType;
import com.ajaxjs.devtools.netspeed.core.BandwidthLimitManager;
import com.ajaxjs.devtools.netspeed.core.BandwidthLimitStats;
import com.ajaxjs.devtools.netspeed.core.TokenBucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 解析限速注解，准备共享桶和包装响应，并在请求完成后关闭输出流。
 * <p>只处理 HandlerMethod，方法上的注解优先。preHandle 将包装器存入请求属性，
 * 并不会直接替换传入 MVC 调用链的响应，业务代码需通过 Helper 读取包装器。
 * <p>用户及代理 IP 来自请求头，当前代码不验证其可信性或执行身份认证。
 */
@Slf4j
public class BandwidthLimitInterceptor implements HandlerInterceptor {
    /**
     * 拦截器自行创建的管理器，与配置单独暴露的 Bean 独立。
     */
    private final BandwidthLimitManager limitManager = new BandwidthLimitManager();

    /**
     * 请求中保存包装响应的属性键，Helper 与拦截器必须保持一致。
     */
    private static final String WRAPPED_RESPONSE_ATTR = "BandwidthLimitWrappedResponse";

    /**
     * 保存原始响应的请求属性键，当前后续流程未读取该属性。
     */
    private static final String ORIGINAL_RESPONSE_ATTR = "BandwidthLimitOriginalResponse";

    /**
     * 实现 MVC 前置回调，查找方法或类注解并准备共享桶、响应头和包装响应。
     * <p>非 HandlerMethod 或没有注解时直接放行。包装器只保存到请求属性，
     * 不替换 MVC 传递的原始响应，控制器需显式通过 Helper 使用它。
     *
     * @param request  当前 HTTP 请求及共享的请求属性
     * @param response 原始或调用者传入的 HTTP 响应
     * @param handler  MVC 选定的处理器
     * @return 始终为 true，表示继续请求处理
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod))
            return true;

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        BandwidthLimit annotation = handlerMethod.getMethodAnnotation(BandwidthLimit.class);

        if (annotation == null)  // 如果方法没有注解，检查类级别的注解
            annotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), BandwidthLimit.class);

        if (annotation != null) {
            String path = request.getRequestURI();
            log.info("========== Interceptor: Found @BandwidthLimit for path: {}, type: {}, value: {} {}/s ==========",
                    path, annotation.type(), annotation.value(), annotation.unit());

            LimitType type = annotation.type(); // 获取带宽参数
            long bandwidth = calculateBandwidth(request, annotation);
            long bandwidthBytesPerSecond = annotation.unit().toBytesPerSecond(bandwidth);
            long capacity = (long) (bandwidthBytesPerSecond * annotation.capacityMultiplier());
            String key = getLimitKey(request, type, path, annotation);

            // 获取或创建令牌桶
            TokenBucket bucket = limitManager.getBucket(type, key, capacity, bandwidthBytesPerSecond);

            log.info("Interceptor: Token bucket created - type={}, key={}, capacity={}/s, rate={}/s",
                    type, key, BandwidthUnit.formatBytes(capacity), BandwidthUnit.formatBytes(bandwidthBytesPerSecond));

            // 设置响应头到原始响应（这样浏览器才能看到）
            response.setHeader("X-Bandwidth-Limit", BandwidthUnit.formatBytes(bandwidthBytesPerSecond) + "/s");
            response.setHeader("X-Bandwidth-Type", type.name());
            response.setHeader("X-Bandwidth-Key", key);
            response.setHeader("X-Bandwidth-Capacity", BandwidthUnit.formatBytes(capacity));

            log.info("Interceptor: Response headers set - X-Bandwidth-Limit={}", BandwidthUnit.formatBytes(bandwidthBytesPerSecond) + "/s");

            // 创建限速响应包装器（传入共享的 TokenBucket）
            BandwidthLimitResponseWrapper wrappedResponse = new BandwidthLimitResponseWrapper(response, bucket, bandwidthBytesPerSecond, annotation.chunkSize());

            request.setAttribute(WRAPPED_RESPONSE_ATTR, wrappedResponse); // 将包装器保存到请求中
            request.setAttribute(ORIGINAL_RESPONSE_ATTR, response);
            request.setAttribute("BandwidthLimit", annotation);
        }

        return true;
    }

    /**
     * 实现 MVC 完成回调，关闭请求属性中的包装响应。
     * <p>关闭异常记录日志并吞掉；没有包装响应时不执行操作，不按 ex 是否为空决定关闭。
     *
     * @param request  当前 HTTP 请求及共享的请求属性
     * @param response 原始或调用者传入的 HTTP 响应
     * @param handler  MVC 选定的处理器
     * @param ex       MVC 处理时的异常，没有异常时为 null
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理资源
        BandwidthLimitResponseWrapper wrappedResponse = (BandwidthLimitResponseWrapper) request.getAttribute(WRAPPED_RESPONSE_ATTR);

        if (wrappedResponse != null) {
            try {
                wrappedResponse.close();
            } catch (Exception e) {
                log.error("Error closing wrapped response", e);
            }
        }
    }

    /**
     * 当 free 或 vip 为正时根据 X-User-Type 选择档位，未配置档位或不识别类型回退 value。
     * <p>返回数值尚未转换成字节；请求头不代表已认证的用户权限。
     *
     * @param request    当前 HTTP 请求及共享的请求属性
     * @param annotation 匹配的限速注解
     * @return 当前请求使用的注解单位数值
     */
    private long calculateBandwidth(HttpServletRequest request, BandwidthLimit annotation) {
        if (annotation.free() > 0 || annotation.vip() > 0) {
            String userType = request.getHeader("X-User-Type");

            if ("vip".equalsIgnoreCase(userType))
                return annotation.vip() > 0 ? annotation.vip() : annotation.value();
            else if ("free".equalsIgnoreCase(userType))
                return annotation.free() > 0 ? annotation.free() : annotation.value();
        }

        return annotation.value();
    }

    /**
     * 生成维度键：全局为 global，接口为 URI，用户为配置请求头，IP 为提取的来源地址。
     * <p>用户头为 null 时回退远端地址，空字符串不会回退。
     *
     * @param request    当前 HTTP 请求及共享的请求属性
     * @param type       限速维度或调试传入的维度文本
     * @param path       当前请求 URI
     * @param annotation 匹配的限速注解
     * @return 管理器查找共享桶所用键
     */
    private String getLimitKey(HttpServletRequest request, LimitType type, String path, BandwidthLimit annotation) {
        return switch (type) {
            case GLOBAL -> "global";
            case API -> path;
            case USER -> {
                String userId = request.getHeader(annotation.userHeader());
                yield userId != null ? userId : request.getRemoteAddr();
            }
            case IP -> getClientIp(request);
        };
    }

    /**
     * 依次检查 X-Forwarded-For、X-Real-IP、远端地址，前两者为空或 unknown 时继续回退。
     * <p>多地址值取第一个并去空白，不验证代理可信性或 IP 格式。
     *
     * @param request 当前 HTTP 请求及共享的请求属性
     * @return 推断的客户端 IP 文本
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getRemoteAddr();
        if (ip != null && ip.contains(","))
            ip = ip.split(",")[0].trim();

        return ip;
    }

    /**
     * 委托内部管理器读取统计，不使用配置中单独的管理器 Bean。
     *
     * @return 内部管理器的统计快照
     */
    public BandwidthLimitStats getStats() {
        return limitManager.getStats();
    }

    /**
     * 委托内部管理器重置全局桶，不清零累计统计。
     */
    public void resetGlobalBucket() {
        limitManager.resetGlobalBucket();
    }

    /**
     * 委托内部管理器清除非全局桶映射及访问记录。
     */
    public void clearAllBuckets() {
        limitManager.clearAllBuckets();
    }

    /**
     * 委托内部管理器关闭守护清理执行器。
     */
    public void shutdown() {
        limitManager.shutdown();
    }
}
