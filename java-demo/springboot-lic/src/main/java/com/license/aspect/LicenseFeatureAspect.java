package com.license.aspect;

import com.license.model.License;
import com.license.model.LicenseContext;
import com.license.model.LicenseException;
import com.license.model.RequireFeature;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 许可证功能权限AOP切面
 * 用于拦截带有@RequireFeature注解的方法，检查功能权限
 */
@Component
@Aspect
@Order(1)
@Slf4j
public class LicenseFeatureAspect {
    /**
     * 环绕通知：在方法执行前检查功能权限
     */
    @Around("@annotation(requireFeature)")
    public Object checkFeaturePermission(ProceedingJoinPoint joinPoint, RequireFeature requireFeature) throws Throwable {
        License currentLicense = LicenseContext.getCurrentLicense();  // 获取当前许可证信息

        if (currentLicense == null) {
            log.warn("访问需要授权的功能，但未找到有效许可证: {}", requireFeature.value());
            throw new LicenseException("系统未找到有效许可证，请联系管理员");
        }

        // 检查功能权限
        if (currentLicense.getFeatures() == null || !currentLicense.getFeatures().contains(requireFeature.value())) {
            log.warn("功能权限不足 - 用户: {}, 需要权限: {}, 拥有权限: {}", currentLicense.getIssuedTo(),
                    requireFeature.value(), currentLicense.getFeatures());

            throw new LicenseException(requireFeature.message() + ": " + requireFeature.value());
        }

        log.debug("功能权限验证通过: {}", requireFeature.value());

        return joinPoint.proceed();
    }
}
