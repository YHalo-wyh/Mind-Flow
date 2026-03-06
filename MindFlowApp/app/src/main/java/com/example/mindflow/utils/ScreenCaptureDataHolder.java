package com.example.mindflow.utils;

import android.content.Intent;

public class ScreenCaptureDataHolder {
    private static int resultCode = -1;
    private static Intent resultData = null;

    public static void setPermissionData(int code, Intent data) {
        resultCode = code;
        resultData = data;
    }

    public static int getResultCode() {
        return resultCode;
    }

    public static Intent getResultData() {
        return resultData;
    }

    public static void clear() {
        resultCode = -1;
        resultData = null;
    }
}