package com.example.mindflow.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PredictionRequest {
    @SerializedName("recent_windows")
    public List<WindowData> recentWindows;

    @SerializedName("return_suggestion")
    public boolean returnSuggestion = true;

    public PredictionRequest(List<WindowData> recentWindows) {
        this.recentWindows = recentWindows;
    }
}