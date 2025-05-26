package com.example.ss_final_java;

import android.util.Log;

public class Logger {
    private static final boolean isTestEnvironment = isRunningTest();

    public static void d(String tag, String message) {
        if (isTestEnvironment) {
            System.out.println("DEBUG [" + tag + "]: " + message);
        } else {
            Log.d(tag, message);
        }
    }

    public static void e(String tag, String message) {
        System.err.println("ERROR [" + tag + "]: " + message);
    }

    private static boolean isRunningTest() {
        return "true".equals(System.getProperty("IS_TEST"));
    }
}