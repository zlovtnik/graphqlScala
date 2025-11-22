package com.rcs.ssf.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * AOP aspect for automatic span instrumentation of MFA operations.
 * 
 * Intercepts WebAuthn and MFA service method calls to create spans with:
 * - Span name: mfa.<operation> (registration, authentication, verify)
 * - Attributes: MFA method, user ID, success/failure, operation time
 * 
 * Operations traced:
 * - startRegistration (WebAuthn registration challenge generation)
 * - completeRegistration (WebAuthn credential verification and storage)
 * - startAuthentication (WebAuthn authentication challenge generation)
 * - verifyAssertion (WebAuthn assertion verification)
 * - Any method in WebAuthnService or MFA-related classes
 * 
 * Security Notes:
 * - Does not log sensitive credential data
 * - Tracks authentication attempts for security monitoring
 * - Records MFA bypass attempts or failures
 * 
 * Usage (automatic):
 * webAuthnService.completeRegistration(userId, response, nickname);
 * // Traced as mfa.registration with attributes: user_id, method, status
 */
@Aspect
@Component
@Slf4j
public class MFAOperationInstrumentation {

    private final Tracer tracer;

    public MFAOperationInstrumentation(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Trace all WebAuthn service operations.
     */
    @Around("execution(* com.rcs.ssf.security.mfa.WebAuthnService.*(..))")
    public Object traceWebAuthnOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceMFAOperation(joinPoint, "webauthn");
    }

    /**
     * Trace all MFA-related service operations.
     */
    @Around("execution(* com.rcs.ssf.security.mfa..*.*(..)) && " +
            "!execution(* com.rcs.ssf.security.mfa..*.hashCode(..)) && " +
            "!execution(* com.rcs.ssf.security.mfa..*.equals(..))")
    public Object traceMFAServiceOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass()
                : joinPoint.getSignature().getDeclaringType();
        if (targetClass == null) {
            return joinPoint.proceed();
        }

        String className = targetClass.getSimpleName();
        if (!shouldTraceClass(targetClass, className)) {
            return joinPoint.proceed();
        }
        return traceMFAOperation(joinPoint, "mfa");
    }

    private boolean shouldTraceClass(Class<?> targetClass, String className) {
        // Future: look for marker interfaces/annotations to opt-in tracing.
        return !(className.contains("Response") ||
                className.contains("Options") ||
                className.contains("Credential"));
    }

    /**
     * Core tracing logic for MFA operations.
     * 
     * Creates span with MFA operation type, method name, and user context.
     */
    private Object traceMFAOperation(ProceedingJoinPoint joinPoint, String mfaType) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String operationType = inferMFAOperationType(methodName);
        String spanName = String.format("mfa.%s", operationType);

        Span span = tracer.spanBuilder(spanName).startSpan();
        long startTime = System.currentTimeMillis();

        try (Scope scope = span.makeCurrent()) {
            // Add MFA operation attributes
            span.setAttribute("mfa.type", mfaType);
            span.setAttribute("mfa.method", methodName);
            span.setAttribute("mfa.operation", operationType);

            captureUserIdAttribute(joinPoint, span);

            // Execute MFA operation
            Object result = joinPoint.proceed();

            // Record successful result
            span.setAttribute("mfa.status", "success");
            if (result != null) {
                span.setAttribute("mfa.result_type", result.getClass().getSimpleName());
                span.setAttribute("mfa.result_available", true);
            }

            return result;

        } catch (Throwable throwable) {
            // Record exception in span with security classification (sanitized for PII)
            span.recordException(throwable);
            span.setAttribute("error", true);
            span.setAttribute("error.type", throwable.getClass().getSimpleName());
            span.setAttribute("mfa.status", "failure");

            // Classify security events
            if (throwable.getClass().getSimpleName().contains("Verification") || 
                throwable.getMessage() != null && throwable.getMessage().contains("invalid")) {
                span.setAttribute("security.event", "mfa_verification_failed");
            } else if (throwable.getMessage() != null && throwable.getMessage().contains("challenge")) {
                span.setAttribute("security.event", "mfa_challenge_expired");
            }

            // Log without including raw exception or message to avoid leaking sensitive MFA data
            log.warn("MFA operation failure: {} in method {} ({})", spanName, methodName, throwable.getClass().getSimpleName());
            throw throwable;

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            span.setAttribute("mfa.duration_ms", duration);
            
            // Flag slow MFA operations
            if (duration > 2000) {
                span.setAttribute("mfa.slow_operation", true);
            }
            
            span.end();
        }
    }

    private void captureUserIdAttribute(ProceedingJoinPoint joinPoint, Span span) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];

            if (!(arg instanceof Long)) {
                continue;
            }

            if (isUserIdParameter(parameter)) {
                span.setAttribute("mfa.user_id", (Long) arg);
                return;
            }
        }
    }

    private boolean isUserIdParameter(Parameter parameter) {
        if (parameter.getType().equals(Long.class) || parameter.getType().equals(long.class)) {
            String paramName = parameter.getName();
            if (paramName != null) {
                String normalized = paramName.toLowerCase();
                if (normalized.equals("userid") || normalized.equals("user_id") || normalized.equals("uid")) {
                    return true;
                }
            }

            for (Annotation annotation : parameter.getAnnotations()) {
                String annotationName = annotation.annotationType().getSimpleName();
                if (annotationName.equals("UserId") || annotationName.equals("MfaUserId")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Infer MFA operation type from method name.
     * 
     * Examples:
     * - startRegistration → registration
     * - completeRegistration → registration
     * - startAuthentication → authentication
     * - verifyAssertion → authentication
     * - verify → verify
     */
    private String inferMFAOperationType(String methodName) {
        if (methodName.contains("registration") || methodName.contains("Register")) {
            return "registration";
        } else if (methodName.contains("authentication") || 
                   methodName.contains("authenticate") || 
                   methodName.contains("assertion") ||
                   methodName.contains("Authenticate")) {
            return "authentication";
        } else if (methodName.contains("verify") || methodName.contains("Verify")) {
            return "verify";
        } else if (methodName.contains("enroll")) {
            return "enrollment";
        } else if (methodName.contains("challenge")) {
            return "challenge";
        } else {
            return "operation";
        }
    }
}
