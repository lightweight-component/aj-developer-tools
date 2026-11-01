package com.ajaxjs.devtools.apidashboard;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 通过 Spring MVC 请求生命周期采集控制器指标的异步兼容拦截器。
 * <p>计时从首次 preHandle 到最终 afterCompletion，涵盖控制器、响应写出以及
 * 异步等待时间，不包括进入拦截器之前的 Filter 耗时。异步正常重派发不重置起点；
 * 没有最终完成派发的请求不由本实现额外补记。</p>
 * <p>未处理异常或 HTTP 状态码大于等于 400 计为失败；HTTP 200 中的业务失败状态
 * 不解析。忽略静态资源处理器、ERROR 派发以及指标控制器本身，不统计独立 Service 调用。
 * 标识使用简单类名和方法名，重载方法或不同包的同名控制器可能合并为同一条指标。</p>
 */
@Component
@RequiredArgsConstructor
public class MetricsInterceptor implements AsyncHandlerInterceptor {
    /**
     * 保存首次计时起点的请求属性名，属性值为 System.nanoTime 返回的纳秒值，不是纪元时间。
     */
    private static final String START_ATTRIBUTE = MetricsInterceptor.class.getName() + ".start";

    /**
     * 保存原始方法标识的请求属性名，异步重派发时复用，完成记录后移除。
     */
    private static final String METHOD_ATTRIBUTE = MetricsInterceptor.class.getName() + ".method";

    /**
     * 拦截器、指标接口和定时清理共同使用的内存注册表，通过构造器注入。
     */
    private final MetricsRegistry metricsRegistry;

    /**
     * 为受监控请求首次设置单调时钟起点和方法标识。
     * <p>请求属性跨异步派发保留，已有起点不覆盖；不受监控的处理器直接放行。</p>
     *
     * @param request 当前 Servlet 请求
     * @param response 当前 Servlet 响应，用于读取最终 HTTP 状态
     * @param handler 本次派发选择的 MVC 处理器
     * @return 始终为 true，不改变请求执行流程
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isMonitored(request, handler))
            return true;

        if (request.getAttribute(START_ATTRIBUTE) == null) {
            HandlerMethod method = (HandlerMethod) handler;
            request.setAttribute(START_ATTRIBUTE, System.nanoTime());
            request.setAttribute(METHOD_ATTRIBUTE, method.getBeanType().getSimpleName() + "." + method.getMethod().getName() + "()");
        }

        return true;
    }

    /**
     * 在最终 MVC 完成回调中记录耗时与成败，并移除计时属性。
     * <p>纳秒差转换为毫秒时截去不足 1 毫秒的部分。异步启动时此回调不会立即执行，
     * 正常重派发完成后才记录；缺少起点或方法标识时跳过，重复完成调用不会重复计数。</p>
     *
     * @param request 当前 Servlet 请求
     * @param response 当前 Servlet 响应，用于读取最终 HTTP 状态
     * @param handler 本次派发选择的 MVC 处理器
     * @param ex 未被异常解析器处理的异常，可为 null
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!isMonitored(request, handler))
            return;

        Long started = (Long) request.getAttribute(START_ATTRIBUTE);
        String methodName = (String) request.getAttribute(METHOD_ATTRIBUTE);

        if (started == null || methodName == null)
            return;

        request.removeAttribute(START_ATTRIBUTE);
        request.removeAttribute(METHOD_ATTRIBUTE);
        long duration = (System.nanoTime() - started) / 1_000_000;
        metricsRegistry.record(methodName, duration, ex == null && response.getStatus() < 400);
    }

    /**
     * 识别参与统计的 HandlerMethod，排除 ERROR 派发和 MetricsController 及其子类。
     * <p>静态资源处理器不是 HandlerMethod，自动跳过；其他 MVC 控制器不要求额外标注。</p>
     *
     * @param request 当前 Servlet 请求
     * @param handler 本次派发选择的 MVC 处理器
     * @return 当前处理器是否应当采集指标
     */
    private boolean isMonitored(HttpServletRequest request, Object handler) {
        if (request.getDispatcherType() == DispatcherType.ERROR || !(handler instanceof HandlerMethod))
            return false;

        return !MetricsController.class.isAssignableFrom(((HandlerMethod) handler).getBeanType());
    }
}
