package com.example.plantsafe.network.model;

import com.google.gson.annotations.SerializedName;

// Represents the "main" object in the API response, containing core weather data
public class Main {

    // Maps the "temp" JSON number (Temperature. Unit depends on "units" query param)
    @SerializedName("temp")
    public double temp;

    // Maps the "feels_like" JSON number (Temperature accounting for human perception)
    @SerializedName("feels_like")
    public double feelsLike;

    // Maps the "temp_min" JSON number (Minimum temperature at the moment)
    @SerializedName("temp_min")
    public double tempMin;

    // Maps the "temp_max" JSON number (Maximum temperature at the moment)
    @SerializedName("temp_max")
    public double tempMax;

    // Maps the "pressure" JSON number (Atmospheric pressure, hPa)
    @SerializedName("pressure")
    public int pressure;

    // Maps the "humidity" JSON number (Humidity, %)
    @SerializedName("humidity")
    public int humidity;

}