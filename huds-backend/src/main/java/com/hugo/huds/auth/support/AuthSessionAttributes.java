package com.hugo.huds.auth.support;

/**
 * @author Meiyan Wang
 * @date 2026-10-16 9:33 a.m.
 */
public class AuthSessionAttributes {
    public static final String USER_ID = "HUDS_USER_ID";
    public static final String DEVICE_ID = "HUDS_DEVICE_ID";
    public static final String DEVICE_LABEL = "HUDS_DEVICE_LABEL";
    public static final String CLIENT_TYPE = "HUDS_CLIENT_TYPE";
    public static final String IP_ADDRESS = "HUDS_IP";
    public static final String USER_AGENT = "HUDS_USER_AGENT";
    //Epoch mills(Long) after which the session is invalid regardless of activity
    public static final String ABSOLUTE_EXPIRES_AT = "HUDS_ABSOLUTE_EXPIRES_AT";

    public AuthSessionAttributes() {
    }
}
