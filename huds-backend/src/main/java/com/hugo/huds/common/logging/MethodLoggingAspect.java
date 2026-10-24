package com.hugo.huds.common.logging;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Meiyan Wang
 * @date 2026-10-17 10:07 a.m
 * @description Logs entry, exit, duration and failure of every controller and service method.
 *
 * <p>Entry and exit are logged at DEBUG. Arguments are serialized to JSON with sensitive
 * fields masked. Methods in the auth package never have their arguments logged.
 * Failures are logged here in one line only; the stack trace is logged once by
 * GlobalExceptionHandler.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MethodLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodLoggingAspect.class);

    private static final long SLOW_THRESHOLD_MS = 1000;
    private static final int MAX_ARGS_LENGTH = 2000;
    private static final String AUTH_PACKAGE_PREFIX = "com.hugo.huds.auth.";

    // Masks the string value of any JSON field whose name looks sensitive.
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(\"(?:password|passwordHash|currentPassword|newPassword|token|secret"
                    + "|phone|username|userName|email|address\\w*|street\\w*|postalCode)\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"",
            Pattern.CASE_INSENSITIVE);

    // Parameter names whose values must never be logged, matched against the method's parameter name.
    private static final Pattern SENSITIVE_PARAM_NAME = Pattern.compile(
            "password|passwordHash|currentPassword|newPassword|token|secret"
                    + "|phone|username|userName|email|address\\w*|street\\w*|postalCode",
            Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public MethodLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerLayer() {
    }

    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceLayer() {
    }

    @Around("controllerLayer() || serviceLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        if (log.isDebugEnabled()) {
            log.debug("-> {} args={}", method, describeArgs(signature, joinPoint.getArgs()));
        }

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (elapsedMs >= SLOW_THRESHOLD_MS) {
                log.warn("<- {} slow call, took {} ms", method, elapsedMs);
            } else {
                log.debug("<- {} ok in {} ms", method, elapsedMs);
            }
            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("<- {} failed in {} ms: {}: {}",
                    method, elapsedMs, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private String describeArgs(MethodSignature signature, Object[] args) {
        // Never log arguments of authentication code (passwords, session data).
        if (signature.getDeclaringTypeName().startsWith(AUTH_PACKAGE_PREFIX)) {
            return "[hidden]";
        }
        String[] names = signature.getParameterNames();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String name = names != null ? names[i] : "arg" + i;
            String value = SENSITIVE_PARAM_NAME.matcher(name).find() ? "\"***\"" : render(args[i]);
            parts.add(name + "=" + value);
        }
        String text = "{" + String.join(", ", parts) + "}";
        return text.length() > MAX_ARGS_LENGTH ? text.substring(0, MAX_ARGS_LENGTH) + "...(truncated)" : text;
    }

    private String render(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof ServletRequest || arg instanceof ServletResponse
                || arg instanceof HttpSession || arg instanceof MultipartFile
                || arg instanceof BindingResult || arg instanceof Principal) {
            return "<" + arg.getClass().getSimpleName() + ">";
        }
        try {
            return SENSITIVE_FIELD.matcher(objectMapper.writeValueAsString(arg)).replaceAll("$1\"***\"");
        } catch (RuntimeException ex) {
            return "<unserializable " + arg.getClass().getSimpleName() + ">";
        }
    }
}
