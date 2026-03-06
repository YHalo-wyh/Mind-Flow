
import pandas as pd
import numpy as np
import os
import uuid

def gen_dummy():
    os.makedirs("mindflow_synth_dataset", exist_ok=True)
    
    users = [f"user_{i}" for i in range(5)]
    records = []
    
    # Generate 100 windows per user
    for u in users:
        start_t = 1600000000000
        for i in range(100):
            row = {
                "window_id": str(uuid.uuid4()),
                "user_id": u,
                "window_start": start_t + i * 300000,
                # Numeric features
                "hour_of_day": np.random.randint(0, 24),
                "day_of_week": np.random.randint(0, 7),
                "in_focus_session": np.random.randint(0, 2),
                "touch_count": np.random.randint(0, 1000),
                "scroll_count": np.random.randint(0, 500),
                "scroll_speed": np.random.random() * 100,
                "key_stroke_count": np.random.randint(0, 200),
                "touch_freq_var": np.random.random() * 10,
                "app_switch_count": np.random.randint(0, 20),
                "screen_on_ms": np.random.randint(0, 300000),
                "notif_received_count": np.random.randint(0, 5),
                "notif_clicked_count": np.random.randint(0, 5),
                "notif_dismissed_count": np.random.randint(0, 5),
                "notif_blocked_in_focus_count": np.random.randint(0, 2),
                "notif_work_count": np.random.randint(0, 3),
                "notif_social_count": np.random.randint(0, 3),
                "notif_learning_count": np.random.randint(0, 3),
                "hr_mean": np.random.randint(60, 100),
                "hrv_rmssd": np.random.random() * 50 + 20,
                "steps": np.random.randint(0, 100),
                # Categorical
                "session_type": np.random.choice(["work", "study", "social", "unknown"]),
                "major_app_category": np.random.choice(["social", "prod", "game", "other"]),
                "activity_level": np.random.choice(["still", "walking", "running"]),
                "loc_category": np.random.choice(["home", "work", "transit"]),
                "network_type": np.random.choice(["wifi", "4g", "5g"]),
                "noise_level": np.random.choice(["quiet", "noisy"]),
                "timezone": "UTC",
                # Targets
                "cognitive_state": np.random.choice(["深度专注", "轻度专注", "休闲刷屏", "高压忙乱", "放松休息"]),
                "interruptibility_score": np.random.random()
            }
            records.append(row)
            
    df = pd.DataFrame(records)
    
    # Split
    df_feats = df.drop(columns=["cognitive_state", "interruptibility_score"])
    df_labs = df[["window_id", "user_id", "window_start", "cognitive_state", "interruptibility_score"]]
    
    df_feats.to_csv("mindflow_synth_dataset/features_windows.csv", index=False)
    df_labs.to_csv("mindflow_synth_dataset/labels_windows.csv", index=False)
    print("Dummy data generated.")

if __name__ == "__main__":
    gen_dummy()
