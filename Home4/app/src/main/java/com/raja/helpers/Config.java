package com.raja.helpers;

public class Config {
    public static final String serverUri = "tcp://mybroker.com:1883";  // avoid a trailing slash
    public static final String subscriptionTopic = "a/b/c";
    public static final String publishTopic = "a/c/d/e";
    public static final boolean enableStatusNotifications = true;
    public static final String userName = "user";
    public static final String password = "password";
    public static final int QOS = 0;  // for subscription
}
