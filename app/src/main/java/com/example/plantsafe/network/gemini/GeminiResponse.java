package com.example.plantsafe.network.gemini;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Represents the response received from the Gemini API
public class GeminiResponse {

    @SerializedName("candidates")
    private List<Candidate> candidates;

    // --- Getter ---
    public List<Candidate> getCandidates() {
        return candidates;
    }

    // --- Inner Classes for nested structure ---
    public static class Candidate {
        @SerializedName("content")
        private Content content;

        @SerializedName("finishReason")
        private String finishReason;
        // Can add safetyRatings etc. if needed

        // --- Getter ---
        public Content getContent() {
            return content;
        }
    }

    public static class Content {
        @SerializedName("parts")
        private List<Part> parts;

        @SerializedName("role")
        private String role;

        // --- Getter ---
        public List<Part> getParts() {
            return parts;
        }
    }

    public static class Part {
        @SerializedName("text")
        private String text; // This holds the generated text

        // --- Getter ---
        public String getText() {
            return text;
        }
    }
    // ------------------------------------------
}