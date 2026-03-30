package com.ajaxjs.devtools.sysmonitor;

public class Utils {
    public static void sleep(float seconds) {
        try {
            Thread.sleep((long) seconds * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
