package com.example.ss_final_java;

import android.content.Context;
import android.widget.Toast;

public class ToastWrapper {

    private final Context context;
    private final boolean isTest;

    // Constructor for production: assumes not test
    public ToastWrapper(Context context) {
        this.context = context;
        this.isTest = false;
    }

    // Additional constructor for tests
    public ToastWrapper(Context context, boolean isTest) {
        this.context = context;
        this.isTest = isTest;
    }

    public void show(String message) {
        if (isTest) {
            System.out.println("[Toast] " + message);
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}