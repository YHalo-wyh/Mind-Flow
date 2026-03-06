#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MindFlow Model Exporter for Android (TorchScript)
=================================================
Usage:
  python export_mobile.py

Output:
  - mindflow_mobile.ptl (Lite Interpreter optimized model)
  - assets.json (Preprocessing metadata: scales, vocabs)
"""
import os
import json
import torch
import torch.nn as nn
import numpy as np
from typing import Dict, List, Tuple

# Import your training modules
# Ensure mindflow_train.py is in the same directory
from mindflow_train import MindFlowInferencer, TabSeqTransformer, TrainConfig

# ------------------------------------------------------------------
# Wrapper Module for Export
# ------------------------------------------------------------------
class MobileModelWrapper(nn.Module):
    """
    Wraps the TabSeqTransformer to accept flattened Tensor inputs
    instead of (Tensor, Dict[str, Tensor]), simplifying Android/JNI calls.
    
    Inputs:
      x_num: [B, L, num_cols] (float32)
      x_cat_flat: [B, L, num_cat_cols] (int64) 
               (We stack categorical features along the last dim)
    """
    def __init__(self, model: TabSeqTransformer, cat_cols: List[str]):
        super().__init__()
        self.model = model
        self.cat_cols = cat_cols # Fixed order list

    def forward(self, x_num: torch.Tensor, x_cat_flat: torch.Tensor) -> Tuple[torch.Tensor, torch.Tensor]:
        # Unpack x_cat_flat [B, L, C] into Dictionary expected by model
        x_cat_dict: Dict[str, torch.Tensor] = {}
        for i, c in enumerate(self.cat_cols):
            # Extract i-th slice along last dim
            x_cat_dict[c] = x_cat_flat[:, :, i]
        
        # Delegate to original model
        logits, it_score = self.model(x_num, x_cat_dict)
        
        # Apply softmax/sigmoid inside the model for easier Java consumption
        probs = torch.softmax(logits, dim=-1)
        
        return probs, it_score

# ------------------------------------------------------------------
# Main Export Logic
# ------------------------------------------------------------------
def export():
    # 1. Load the trained inferencer (CPU is fine for export)
    ckpt_path = os.path.join(".", "ckpt", "mindflow_best.pt")
    if not os.path.exists(ckpt_path):
        print(f"[Error] Checkpoint not found at {ckpt_path}. Please train first.")
        return

    print("Loading checkpoint...")
    device = "cpu"
    inf = MindFlowInferencer(ckpt_path=ckpt_path, device=device)
    base_model = inf.model
    base_model.eval()

    # 2. Extract Preprocessing Metadata
    print("Extracting preprocessing assets...")
    preproc = inf.preproc
    
    # We need a fixed order for categorical columns to match x_cat_flat indices
    cat_cols_order = preproc.cat_cols
    
    assets = {
        "num_cols": preproc.num_cols,
        "cat_cols": cat_cols_order,
        "scaler_mean": preproc.scaler.mean_.tolist(),
        "scaler_std": preproc.scaler.std_.tolist(),
        "vocabs": {c: preproc.vocabs[c].to_dict() for c in cat_cols_order},
        "label_map": preproc.label_map,
        "inv_label_map": preproc.inv_label_map,
        "lookback_windows": inf.cfg.lookback_windows
    }
    
    with open("mindflow_assets.json", "w", encoding="utf-8") as f:
        json.dump(assets, f, ensure_ascii=False, indent=2)
    print(f"Saved mindflow_assets.json (for Android assets)")

    # 3. Prepare Dummy Input for Tracing
    # Batch=1, Lookback=12 (or whatever config is)
    B = 1
    L = inf.cfg.lookback_windows
    D_num = len(preproc.num_cols)
    D_cat = len(preproc.cat_cols)

    dummy_num = torch.randn(B, L, D_num, dtype=torch.float32)
    dummy_cat = torch.zeros(B, L, D_cat, dtype=torch.long) # all zeros (UNK)

    # 4. Wrap & Trace
    print("Tracing model...")
    wrapper = MobileModelWrapper(base_model, cat_cols_order)
    
    # Check trace
    scripted_module = torch.jit.trace(wrapper, (dummy_num, dummy_cat))
    
    # 5. Optimize for Mobile (Lite Interpreter)
    # Note: _save_for_lite_interpreter is the robust way for Android deployment
    print("optimizing for mobile...")
    scripted_module.eval()
    
    out_path = "mindflow_mobile.ptl"
    scripted_module._save_for_lite_interpreter(out_path)
    
    print(f"✅ Success! Saved {out_path}")
    print("Next steps:")
    print(f"1. Copy 'mindflow_mobile.ptl' to Android 'app/src/main/assets/'")
    print(f"2. Copy 'mindflow_assets.json' to Android 'app/src/main/assets/'")

if __name__ == "__main__":
    export()
