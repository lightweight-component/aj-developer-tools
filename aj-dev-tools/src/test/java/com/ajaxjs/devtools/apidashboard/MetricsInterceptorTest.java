package com.ajaxjs.devtools.apidashboard;

import com.ajaxjs.devtools.apidashboard.model.HierarchicalMethodMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 验证 MVC 请求生命周期、失败状态、异步派发与指标查询的隔离。
 */
class MetricsInterceptorTest {
    private final MetricsRegistry registry = new MetricsRegistry();
    private final MetricsInterceptor interceptor = new MetricsInterceptor(registry);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoController(), new MetricsController(registry))
            .addInterceptors(interceptor).build();

    @Test
    void recordsSuccessfulRequestAndTimeBucket() throws Exception {
        mvc.perform(get("/ok")).andReturn();
        HierarchicalMethodMetrics metric = registry.getMetricsSnapshot().get("DemoController.ok()");
        assertNotNull(metric);
        assertEquals(1, metric.getTotalCount().get());
        assertEquals(1, metric.getSuccessCount().get());
        assertTrue(metric.getMinTime() >= 0);
        long now = System.currentTimeMillis();
        assertEquals(1, metric.queryTimeRange(now - 60000, now + 1000).getTotalCount());
    }

    @Test
    void treatsHttpErrorAsFailure() throws Exception {
        mvc.perform(get("/bad")).andReturn();
        assertEquals(1, registry.getMetricsSnapshot().get("DemoController.bad()").getFailCount().get());
    }

    @Test
    void recordsUnhandledException() {
        assertThrows(Exception.class, () -> mvc.perform(get("/broken")));
        assertEquals(1, registry.getMetricsSnapshot().get("DemoController.broken()").getFailCount().get());
    }

    @Test
    void recordsAsyncRequestOnlyAfterFinalDispatch() throws Exception {
        MvcResult initial = mvc.perform(get("/async")).andReturn();
        assertTrue(initial.getRequest().isAsyncStarted());
        assertTrue(registry.getMetricsSnapshot().isEmpty());
        mvc.perform(asyncDispatch(initial)).andReturn();
        assertEquals(1, registry.getMetricsSnapshot().get("DemoController.async()").getTotalCount().get());
    }

    @Test
    void excludesMetricsEndpointsIncludingClear() throws Exception {
        mvc.perform(get("/ok"));
        mvc.perform(get("/api/metrics"));
        assertEquals(1, registry.getMetricsSnapshot().size());
        mvc.perform(delete("/api/metrics"));
        assertTrue(registry.getMetricsSnapshot().isEmpty());
    }

    @Test
    void ignoresResourceHandlersAndDuplicateCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object resource = new Object();
        assertTrue(interceptor.preHandle(request, response, resource));
        interceptor.afterCompletion(request, response, resource, null);
        assertTrue(registry.getMetricsSnapshot().isEmpty());
        HandlerMethod handler = new HandlerMethod(new DemoController(), DemoController.class.getMethod("ok"));
        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);
        interceptor.afterCompletion(request, response, handler, null);
        assertEquals(1, registry.getMetricsSnapshot().get("DemoController.ok()").getTotalCount().get());
    }

    @Test
    void removesExpiredEntriesAndReturnsIndependentMap() {
        registry.record("example", 12, true);
        registry.getMetricsSnapshot().clear();
        assertEquals(1, registry.getMetricsSnapshot().size());
        registry.getMetricsSnapshot().get("example").setLastAccessTime(System.currentTimeMillis() - 10000);
        registry.removeStaleMetrics(1000);
        assertTrue(registry.getMetricsSnapshot().isEmpty());
    }

    @Test
    void configurationRegistersComponentsWhenEnabled() {
        new ApplicationContextRunner().withUserConfiguration(ApiDashboardConfiguration.class)
                .withPropertyValues("devtools.apidashboard.enable=true")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(MetricsInterceptor.class));
                    assertNotNull(context.getBean(MetricsRegistry.class));
                    assertNotNull(context.getBean(MetricsCleanupService.class));
                });
    }

    @Test
    void configurationIsDisabledByDefault() {
        new ApplicationContextRunner().withUserConfiguration(ApiDashboardConfiguration.class)
                .run(context -> assertEquals(0, context.getBeansOfType(MetricsInterceptor.class).size()));
    }

    @RestController
    public static class DemoController {
        @GetMapping("/ok")
        public String ok() {
            return "ok";
        }

        @GetMapping("/bad")
        public ResponseEntity<String> bad() {
            return ResponseEntity.badRequest().body("bad");
        }

        @GetMapping("/broken")
        public String broken() {
            throw new IllegalStateException("test");
        }

        @GetMapping("/async")
        public Callable<String> async() {
            return () -> "async";
        }
    }
}
