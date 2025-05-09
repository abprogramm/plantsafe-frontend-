package com.example.plantsafe.network.model;

import com.google.gson.annotations.SerializedName;

// Represents an object within the "weather" array in the API response
public class Weather {

    // Maps the "id" JSON number (Weather condition id)
    @SerializedName("id")
    public int id;

    // Maps the "main" JSON string (Group of weather parameters: Rain, Snow, Clouds etc.)
    @SerializedName("main")
    public String main;

    // Maps the "description" JSON string (Weather condition within the group)
    @SerializedName("description")
    public String description;

    // Maps the "icon" JSON string (Weather icon id)
    @SerializedName("icon")
    public String icon;
}