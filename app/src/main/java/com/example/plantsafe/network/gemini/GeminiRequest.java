package com.example.plantsafe.network.gemini;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Represents the request body sent to the Gemini API
public class GeminiRequest {

    @SerializedName("contents")
    private List<Content> contents;

    // Constructor
    public GeminiRequest(List<Content> contents) {
        this.contents = contents;
    }

    // --- Inner Classes for nested structure ---
    public static class Content {
        @SerializedName("parts")
        private List<Part> parts;

        public Content(List<Part> parts) {
            this.parts = parts;
        }
    }

    public static class Part {
        @SerializedName("text")
        private String text;

        public Part(String text) {
            this.text = text;
        }
    }
}