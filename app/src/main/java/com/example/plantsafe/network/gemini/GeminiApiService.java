package com.example.plantsafe.network.gemini;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path; // Import Path
import retrofit2.http.Query; // Import Query

public interface GeminiApiService {

    // Endpoint for Gemini content generation
    @POST("v1beta/models/{model}:generateContent") // Use {model} as path parameter
    Call<GeminiResponse> generateContent(
            @Path("model") String modelName, // Parameter for the model name in the URL
            @Query("key") String apiKey,     // API Key as query parameter
            @Body GeminiRequest requestBody   // JSON request body
    );
}