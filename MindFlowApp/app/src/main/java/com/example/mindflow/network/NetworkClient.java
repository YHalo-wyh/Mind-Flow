package com.example.mindflow.network;

import android.content.Context;

import com.example.mindflow.utils.AppSettings;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NetworkClient {
    private static Retrofit retrofit = null;
    private static String currentBaseUrl = null;

    public static synchronized FastApiService getService(Context context) {
        String baseUrl = AppSettings.getBackendBaseUrl(context);
        if (retrofit == null || currentBaseUrl == null || !currentBaseUrl.equals(baseUrl)) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            currentBaseUrl = baseUrl;
        }
        return retrofit.create(FastApiService.class);
    }
}
