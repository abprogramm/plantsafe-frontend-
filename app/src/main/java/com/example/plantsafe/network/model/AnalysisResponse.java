package com.example.plantsafe.network.model;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AnalysisResponse {

    @SerializedName("annotated_image")
    private String annotatedImageBase64; // Base64 encoded image string

    @SerializedName("leaf_results")
    private List<LeafResult> leafResults;

    public String getAnnotatedImageBase64() {
        return annotatedImageBase64;
    }

    public List<LeafResult> getLeafResults() {
        return leafResults;
    }

    // Inner class for individual leaf results
    public static class LeafResult {
        @SerializedName("leaf_index")
        private int leafIndex;

        @SerializedName("percentage")
        private float percentage;

        public int getLeafIndex() {
            return leafIndex;
        }

        public float getPercentage() {
            return percentage;
        }
    }
}