package com.example.mindflow.network;

import com.example.mindflow.model.PredictionRequest;
import com.example.mindflow.model.PredictionResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import com.example.mindflow.model.MetaResponse;
import retrofit2.http.GET;

public interface FastApiService {
    // 添加这个 GET 请求，对接老高后端的 /meta 接口
    @GET("meta")
    Call<MetaResponse> meta();

    @POST("predict")
    Call<PredictionResponse> predict(@Body PredictionRequest request);
}
