package com.example.plantsafe.network;

import com.example.plantsafe.network.model.AnalysisResponse;
import com.example.plantsafe.network.model.WeatherResponse;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface WeatherApiService {

    // --- Weather Endpoint ---
    @GET("data/2.5/weather")
    Call<WeatherResponse> getCurrentWeather(
            @Query("lat") double latitude,
            @Query("lon") double longitude,
            @Query("appid") String apiKey,
            @Query("units") String units
    );

    // --- Leaf Analysis Endpoint ---
    @Multipart
    @POST("/analyze")
    Call<AnalysisResponse> analyzeLeafImage(
            @Part MultipartBody.Part image
    );
}