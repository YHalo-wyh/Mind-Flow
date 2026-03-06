package com.example.mindflow.model;

import com.google.gson.annotations.SerializedName;

public class MetaResponse {
    // 对应老高后端返回的 lookback_windows
    @SerializedName("lookback_windows")
    public int lookbackWindows;

    @SerializedName("device")
    public String device;

    @SerializedName("ckpt_path")
    public String ckptPath;
}