package com.example.netspeed.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 从请求属性中读取拦截器创建的包装响应。
 * <p>用于控制器显式选择限速输出流；没有包装器时回退原始响应。
 * 属性键必须与 BandwidthLimitInterceptor 一致，工具本身不创建令牌桶。
 */
public class BandwidthLimitHelper {
    /**
     * 请求中保存包装响应的属性键，Helper 与拦截器必须保持一致。
     */
    private static final String WRAPPED_RESPONSE_ATTR = "BandwidthLimitWrappedResponse";

    /**
     * 读取请求中的限速包装响应，未设置时返回调用者传入的默认响应。
     * <p>不创建包装器或验证限速参数，属性类型不符时可能发生类型转换异常。
     *
     * @param request 当前 HTTP 请求及共享的请求属性
     * @param defaultResponse 没有包装响应时返回的响应对象
     * @return 请求中的包装响应或 defaultResponse
     */
    public static HttpServletResponse getLimitedResponse(HttpServletRequest request, HttpServletResponse defaultResponse) {
        BandwidthLimitResponseWrapper wrappedResponse =
                (BandwidthLimitResponseWrapper) request.getAttribute(WRAPPED_RESPONSE_ATTR);

        if (wrappedResponse != null)
            return wrappedResponse;

        return defaultResponse;
    }

    /**
     * 检查包装响应属性是否非空，不验证正文是否实际通过包装器写入。
     *
     * @param request 当前 HTTP 请求及共享的请求属性
     * @return 存在该请求属性时为 true
     */
    public static boolean isLimited(HttpServletRequest request) {
        return request.getAttribute(WRAPPED_RESPONSE_ATTR) != null;
    }
}
