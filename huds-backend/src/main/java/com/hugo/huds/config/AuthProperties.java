package com.hugo.huds.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;


/**
 * @author Meiyan Wang
 * @date 2026-10-16 9:39 a.m.
 */
@ConfigurationProperties(prefix = "huds.auth")
public record AuthProperties(@DefaultValue("5") int maxFailedAttempts,
                             @DefaultValue("15m") Duration lockDuration,
                             @DefaultValue("30m")
                             Duration desktopIdleTimeout,
                             @DefaultValue("12h") Duration desktopAbsoluteTimeout,
                             @DefaultValue("24h") Duration mobileIdleTimeout,
                             @DefaultValue("14d") Duration mobileAbsoluteTimeout,
                             @DefaultValue("30") int ipFailureLimit,
                             @DefaultValue("10m") Duration ipFailureWindow,
                             @DefaultValue("true") boolean cookieSecure) {

}
