package com.example.plantsafe.network.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Represents the top-level JSON response from OpenWeatherMap API
public class WeatherResponse {

    // Maps the "weather" JSON array to a List of Weather objects
    @SerializedName("weather")
    public List<Weather> weather;

    // Maps the "main" JSON object to a Main object
    @SerializedName("main")
    public Main main;

    // Maps the "name" JSON string to a String field (City name)
    @SerializedName("name")
    public String name;

    // Maps the "cod" JSON number to an int field (Response code, 200 means OK)
    @SerializedName("cod")
    public int cod;

}