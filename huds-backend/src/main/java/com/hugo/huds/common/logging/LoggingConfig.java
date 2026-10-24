package com.hugo.huds.common.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author Meiyan Wang
 * @date 2026-10-17 2:28 p.m.
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class LoggingConfig {
}
