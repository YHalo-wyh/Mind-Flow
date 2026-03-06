#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MindFlow backend model training (local/offline).

What it trains:
- Cognitive state classification: S = {深度专注, 轻度专注, 休闲刷屏, 高压忙乱, 放松休息}
- Interruptibility regression: I_t in [0, 1]

Model:
- Lightweight Transformer encoder over last T minutes (sequence of windows).
- Tabular + categorical embeddings per time step.
- Multi-task heads: state logits + interruptibility score.

Data:
- Expects your CSV split, especially:
  features_windows.csv, labels_windows.csv
  (others optional)

Usage examples:
  # Train on the synthetic dataset zip you generated earlier
  python mindflow_train.py --data_zip mindflow_synth_dataset.zip --out_dir ./ckpt

  # Or if already extracted:
  python mindflow_train.py --data_dir ./mindflow_synth_dataset --out_dir ./ckpt

Notes:
- Splits are by user_id to avoid leakage.
- Handles missing values and categorical vocab automatically.
- Produces a single checkpoint with model + preprocess artifacts.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import random
import zipfile
from dataclasses import asdict, dataclass
from typing import Dict, List, Optional, Tuple
import time


import numpy as np
import pandas as pd

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset


# -------------------------
# Repro / utilities
# -------------------------
def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def ensure_dir(p: str) -> None:
    os.makedirs(p, exist_ok=True)


def unzip_if_needed(zip_path: str, out_dir: str) -> str:
    """Return extracted folder path."""
    ensure_dir(out_dir)
    with zipfile.ZipFile(zip_path, "r") as z:
        z.extractall(out_dir)
    # if zip contains mindflow_synth_dataset/<files>, return that
    # otherwise return out_dir
    candidate = os.path.join(out_dir, "mindflow_synth_dataset")
    return candidate if os.path.isdir(candidate) else out_dir


def softmax_np(x: np.ndarray, axis: int = -1) -> np.ndarray:
    x = x - np.max(x, axis=axis, keepdims=True)
    e = np.exp(x)
    return e / (np.sum(e, axis=axis, keepdims=True) + 1e-12)


def macro_f1_from_logits(logits: torch.Tensor, y_true: torch.Tensor, num_classes: int) -> float:
    """
    Compute macro F1 without sklearn.
    logits: [B, C], y_true: [B]
    """
    y_pred = torch.argmax(logits, dim=-1)
    f1s = []
    for c in range(num_classes):
        tp = ((y_pred == c) & (y_true == c)).sum().item()
        fp = ((y_pred == c) & (y_true != c)).sum().item()
        fn = ((y_pred != c) & (y_true == c)).sum().item()
        prec = tp / (tp + fp + 1e-12)
        rec = tp / (tp + fn + 1e-12)
        f1 = 2 * prec * rec / (prec + rec + 1e-12)
        f1s.append(f1)
    return float(np.mean(f1s))


# -------------------------
# Config
# -------------------------
@dataclass
class TrainConfig:
    # data
    window_minutes: int = 5
    lookback_windows: int = 12          # e.g. 12 windows * 5 min = 60 min
    stride: int = 1
    max_gap_windows: int = 2           # break sequences if gap > max_gap_windows * window_minutes

    # split
    train_ratio: float = 0.7
    val_ratio: float = 0.15

    # optimization
    epochs: int = 20
    batch_size: int = 256
    lr: float = 2e-4
    weight_decay: float = 1e-2
    grad_clip: float = 1.0
    num_workers: int = 0
    seed: int = 42
    device: str = "cuda" if torch.cuda.is_available() else "cpu"

    # model
    d_model: int = 128
    n_heads: int = 4
    n_layers: int = 3
    dropout: float = 0.1

    # multitask
    lambda_reg: float = 1.0
    label_smoothing: float = 0.05

    # early stopping
    # model
    model_type: str = "transformer"  # "transformer" or "mlp"
    d_model: int = 128
    n_heads: int = 4
    n_layers: int = 3
    dropout: float = 0.1


# -------------------------
# Preprocessing
# -------------------------
class StandardScalerLite:
    """Minimal standard scaler (store mean/std)."""
    def __init__(self):
        self.mean_: Optional[np.ndarray] = None
        self.std_: Optional[np.ndarray] = None

    def fit(self, x: np.ndarray) -> "StandardScalerLite":
        self.mean_ = np.nanmean(x, axis=0)
        self.std_ = np.nanstd(x, axis=0)
        self.std_ = np.where(self.std_ < 1e-6, 1.0, self.std_)
        return self

    def transform(self, x: np.ndarray) -> np.ndarray:
        assert self.mean_ is not None and self.std_ is not None
        x = np.where(np.isnan(x), self.mean_, x)
        return (x - self.mean_) / self.std_

    def to_dict(self) -> Dict[str, list]:
        return {"mean": self.mean_.tolist(), "std": self.std_.tolist()}

    @staticmethod
    def from_dict(d: Dict[str, list]) -> "StandardScalerLite":
        s = StandardScalerLite()
        s.mean_ = np.array(d["mean"], dtype=np.float32)
        s.std_ = np.array(d["std"], dtype=np.float32)
        return s


class CatVocab:
    """Per-column vocab: string -> int (0 reserved for UNK)."""
    def __init__(self):
        self.stoi: Dict[str, int] = {"__UNK__": 0}
        self.itos: List[str] = ["__UNK__"]

    def add_many(self, values: List[str]) -> None:
        for v in values:
            if v not in self.stoi:
                self.stoi[v] = len(self.itos)
                self.itos.append(v)

    def encode(self, v: str) -> int:
        return self.stoi.get(v, 0)

    def __len__(self) -> int:
        return len(self.itos)

    def to_dict(self) -> Dict[str, object]:
        return {"itos": self.itos}

    @staticmethod
    def from_dict(d: Dict[str, object]) -> "CatVocab":
        cv = CatVocab()
        cv.itos = list(d["itos"])
        cv.stoi = {s: i for i, s in enumerate(cv.itos)}
        if "__UNK__" not in cv.stoi:
            cv.stoi["__UNK__"] = 0
            cv.itos[0] = "__UNK__"
        return cv


@dataclass
class PreprocessArtifacts:
    num_cols: List[str]
    cat_cols: List[str]
    scaler: Dict[str, object]
    vocabs: Dict[str, object]
    label_map: Dict[str, int]
    inv_label_map: Dict[int, str]


class MindFlowPreprocessor:
    """
    - Selects features from features_windows.csv
    - Joins labels_windows.csv
    - Builds categorical vocabs
    - Standardizes numerical features
    """
    def __init__(self, num_cols: List[str], cat_cols: List[str]):
        self.num_cols = num_cols
        self.cat_cols = cat_cols
        self.scaler = StandardScalerLite()
        self.vocabs: Dict[str, CatVocab] = {c: CatVocab() for c in cat_cols}

        # fixed label order (matches your doc)
        states = ["深度专注", "轻度专注", "休闲刷屏", "高压忙乱", "放松休息"]
        self.label_map = {s: i for i, s in enumerate(states)}
        self.inv_label_map = {i: s for s, i in self.label_map.items()}

    def fit(self, df: pd.DataFrame) -> "MindFlowPreprocessor":
        # categorical
        for c in self.cat_cols:
            vals = df[c].fillna("__NA__").astype(str).tolist()
            self.vocabs[c].add_many(vals)

        # numeric
        x_num = df[self.num_cols].to_numpy(dtype=np.float32)
        self.scaler.fit(x_num)
        return self

    def transform(self, df: pd.DataFrame) -> Tuple[np.ndarray, Dict[str, np.ndarray]]:
        x_num = df[self.num_cols].to_numpy(dtype=np.float32)
        x_num = self.scaler.transform(x_num).astype(np.float32)

        x_cat: Dict[str, np.ndarray] = {}
        for c in self.cat_cols:
            vals = df[c].fillna("__NA__").astype(str).tolist()
            enc = np.array([self.vocabs[c].encode(v) for v in vals], dtype=np.int64)
            x_cat[c] = enc
        return x_num, x_cat

    def export(self) -> PreprocessArtifacts:
        return PreprocessArtifacts(
            num_cols=self.num_cols,
            cat_cols=self.cat_cols,
            scaler=self.scaler.to_dict(),
            vocabs={c: self.vocabs[c].to_dict() for c in self.cat_cols},
            label_map=self.label_map,
            inv_label_map=self.inv_label_map,
        )

    @staticmethod
    def load(artifacts: PreprocessArtifacts) -> "MindFlowPreprocessor":
        p = MindFlowPreprocessor(artifacts.num_cols, artifacts.cat_cols)
        p.scaler = StandardScalerLite.from_dict(artifacts.scaler)
        p.vocabs = {c: CatVocab.from_dict(artifacts.vocabs[c]) for c in artifacts.cat_cols}
        p.label_map = artifacts.label_map
        p.inv_label_map = {int(k): v for k, v in artifacts.inv_label_map.items()} if isinstance(next(iter(artifacts.inv_label_map.keys())), str) else artifacts.inv_label_map
        return p


# -------------------------
# Sequence dataset
# -------------------------
@dataclass
class SeqIndex:
    user_id: str
    end_idx: int
    start_idx: int


class WindowSequenceDataset(Dataset):
    """
    Build sequences per user from joined window table.
    Each sample is a sequence of length L ending at time t, predicting labels at t.
    """

    def __init__(self, df_joined, preproc, lookback, stride, window_minutes, max_gap_windows, user_ids):
        # ✅ 过滤到当前 split 的用户后，重新生成本地索引 0..N-1
        self.df = df_joined[df_joined["user_id"].isin(user_ids)].copy()
        self.df.sort_values(["user_id", "window_start"], inplace=True)
        self.df.reset_index(drop=True, inplace=True)

        self.preproc = preproc
        self.lookback = lookback
        self.stride = stride
        self.window_ms = window_minutes * 60_000
        self.max_gap_ms = max_gap_windows * self.window_ms

        # ✅ 不要对 group reset_index，让 group 保留 self.df 的“本地索引”
        self.user_groups = {u: g for u, g in self.df.groupby("user_id", sort=False)}
        self.samples = []

        for u, g in self.user_groups.items():
            times = g["window_start"].to_numpy(dtype=np.int64)
            segment_start = 0
            for i in range(1, len(times)):
                if times[i] - times[i - 1] > self.max_gap_ms:
                    self._add_segment_samples(u, g, segment_start, i - 1)
                    segment_start = i
            self._add_segment_samples(u, g, segment_start, len(times) - 1)

        # ✅ 这里的 transform 与 self.df 对齐（长度 = len(self.df)）
        self._num_all, self._cat_all = self.preproc.transform(self.df)
        self.y_state = self.df["y_state"].to_numpy(dtype=np.int64)
        self.y_it = self.df["y_it"].to_numpy(dtype=np.float32)

    def _add_segment_samples(self, user_id: str, g: pd.DataFrame, seg_start: int, seg_end: int) -> None:
        seg_len = seg_end - seg_start + 1
        if seg_len < self.lookback:
            return
        for end in range(seg_start + self.lookback - 1, seg_end + 1, self.stride):
            self.samples.append((user_id, end))

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int):
        user_id, end_idx = self.samples[idx]
        g = self.user_groups[user_id]

        start_idx = end_idx - self.lookback + 1
        seq = g.iloc[start_idx:end_idx + 1]
        # global positional indices in self.df:
        # since g is reset_index, seq.index are 0.., but correspond to positions inside g,
        # not global. We can get global by locating the rows via user_id and window_start match,
        # but that's slow. Instead: rebuild g with global positions:
        # We'll store an additional column in self.df: global_pos; then in g it's preserved.
        # We'll do that once in the builder outside (see builder function).

        # This __getitem__ assumes df has global_pos and user_groups preserve it.
        seq = g.iloc[start_idx:end_idx + 1]

        # ✅ seq.index 是 self.df 的本地索引（0..len(self.df)-1）
        pos = seq.index.to_numpy(dtype=np.int64)

        x_num = self._num_all[pos]
        x_cat = {c: self._cat_all[c][pos] for c in self.preproc.cat_cols}

        y_state = int(self.y_state[pos[-1]])
        y_it = float(self.y_it[pos[-1]])
        return x_num, x_cat, y_state, y_it


def collate_batch(batch):
    # batch: list of (x_num[L,D], x_cat{c:[L]}, y_state, y_it)
    x_num = torch.tensor(np.stack([b[0] for b in batch], axis=0), dtype=torch.float32)  # [B,L,D]
    cat_cols = list(batch[0][1].keys())
    x_cat = {c: torch.tensor(np.stack([b[1][c] for b in batch], axis=0), dtype=torch.long) for c in cat_cols}  # [B,L]
    y_state = torch.tensor([b[2] for b in batch], dtype=torch.long)  # [B]
    y_it = torch.tensor([b[3] for b in batch], dtype=torch.float32).unsqueeze(-1)  # [B,1]
    return x_num, x_cat, y_state, y_it


# -------------------------
# Model
# -------------------------
class TabSeqTransformer(nn.Module):
    def __init__(
        self,
        num_dim: int,
        cat_cardinalities: Dict[str, int],
        lookback: int,
        d_model: int = 128,
        n_heads: int = 4,
        n_layers: int = 3,
        dropout: float = 0.1,
        num_classes: int = 5,
    ):
        super().__init__()
        self.lookback = lookback
        self.d_model = d_model
        self.num_classes = num_classes

        # embeddings for categorical columns (per time step)
        self.cat_cols = list(cat_cardinalities.keys())
        emb_layers = {}
        emb_dims = {}
        for c, card in cat_cardinalities.items():
            # heuristic embedding size
            dim = int(min(32, max(4, round(math.sqrt(card) * 2))))
            emb_layers[c] = nn.Embedding(card, dim)
            emb_dims[c] = dim
        self.emb_layers = nn.ModuleDict(emb_layers)
        self.cat_total_dim = int(sum(emb_dims.values()))

        self.num_proj = nn.Linear(num_dim, d_model)
        self.cat_proj = nn.Linear(self.cat_total_dim, d_model)

        self.in_ln = nn.LayerNorm(d_model)
        self.in_drop = nn.Dropout(dropout)

        self.pos_emb = nn.Parameter(torch.zeros(lookback, d_model))
        nn.init.normal_(self.pos_emb, mean=0.0, std=0.02)

        enc_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=n_heads,
            dim_feedforward=d_model * 4,
            dropout=dropout,
            activation="gelu",
            batch_first=True,
            norm_first=True,
        )
        self.encoder = nn.TransformerEncoder(enc_layer, num_layers=n_layers)

        self.head_state = nn.Sequential(
            nn.LayerNorm(d_model),
            nn.Linear(d_model, d_model),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(d_model, num_classes),
        )
        self.head_it = nn.Sequential(
            nn.LayerNorm(d_model),
            nn.Linear(d_model, d_model // 2),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(d_model // 2, 1),
        )

    def forward(self, x_num: torch.Tensor, x_cat: Dict[str, torch.Tensor]):
        """
        x_num: [B,L,D]
        x_cat[c]: [B,L]
        """
        B, L, _ = x_num.shape
        assert L == self.lookback, f"Expected lookback={self.lookback}, got L={L}"

        num_tok = self.num_proj(x_num)  # [B,L,d]
        cat_embs = []
        for c in self.cat_cols:
            cat_embs.append(self.emb_layers[c](x_cat[c]))  # [B,L,dim]
        cat_concat = torch.cat(cat_embs, dim=-1) if len(cat_embs) else torch.zeros(B, L, 0, device=x_num.device)
        cat_tok = self.cat_proj(cat_concat)  # [B,L,d]

        h = num_tok + cat_tok
        h = h + self.pos_emb.unsqueeze(0)  # [1,L,d]
        h = self.in_ln(h)
        h = self.in_drop(h)

        z = self.encoder(h)               # [B,L,d]
        last = z[:, -1, :]                # [B,d]

        logits_state = self.head_state(last)              # [B,C]
        it_score = torch.sigmoid(self.head_it(last))      # [B,1] in [0,1]
        return logits_state, it_score


class LabelSmoothingCE(nn.Module):
    def __init__(self, smoothing: float = 0.0, weight: Optional[torch.Tensor] = None):
        super().__init__()
        self.smoothing = float(smoothing)
        self.register_buffer("weight", weight if weight is not None else None)

    def forward(self, logits: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        # logits: [B,C], target: [B]
        n_class = logits.size(-1)
        log_probs = F.log_softmax(logits, dim=-1)
        with torch.no_grad():
            true_dist = torch.zeros_like(log_probs)
            true_dist.fill_(self.smoothing / (n_class - 1))
            true_dist.scatter_(1, target.unsqueeze(1), 1.0 - self.smoothing)
        if self.weight is None:
            loss = (-true_dist * log_probs).sum(dim=-1).mean()
        else:
            # apply class weights
            w = self.weight[target]  # [B]
            loss = ((-true_dist * log_probs).sum(dim=-1) * w).sum() / (w.sum() + 1e-12)
        return loss


# -------------------------
# Data loading / building
# -------------------------
def load_joined_table(data_dir: str) -> pd.DataFrame:
    feat_path = os.path.join(data_dir, "features_windows.csv")
    lab_path = os.path.join(data_dir, "labels_windows.csv")
    if not (os.path.exists(feat_path) and os.path.exists(lab_path)):
        raise FileNotFoundError("Missing features_windows.csv or labels_windows.csv in data_dir")

    feats = pd.read_csv(feat_path)
    labs = pd.read_csv(lab_path)

    # join on window_id + user_id for safety
    df = feats.merge(
        labs[["window_id", "user_id", "cognitive_state", "interruptibility_score"]],
        on=["window_id", "user_id"],
        how="inner",
    )

    # ensure stable ordering fields
    if "window_start" not in df.columns:
        raise ValueError("features_windows.csv must include window_start")
    df["window_start"] = df["window_start"].astype(np.int64)

    # add global_pos for fast indexing inside dataset
    df = df.sort_values(["user_id", "window_start"]).reset_index(drop=True)
    df["global_pos"] = np.arange(len(df), dtype=np.int64)
    return df


def select_feature_columns(df: pd.DataFrame) -> Tuple[List[str], List[str]]:
    """
    Choose a strong default feature set based on your schema.
    You can freely edit this list for your real data.
    """
    # numeric candidates
    numeric_cols = [
        "hour_of_day", "day_of_week", "in_focus_session",
        "touch_count", "scroll_count", "scroll_speed", "key_stroke_count", "touch_freq_var",
        "app_switch_count", "screen_on_ms",
        "notif_received_count", "notif_clicked_count", "notif_dismissed_count", "notif_blocked_in_focus_count",
        "notif_work_count", "notif_social_count", "notif_learning_count",
        "hr_mean", "hrv_rmssd", "steps",
    ]
    numeric_cols = [c for c in numeric_cols if c in df.columns]

    # categorical candidates
    cat_cols = [
        "session_type", "major_app_category",
        "activity_level", "loc_category", "network_type", "noise_level",
        "timezone",
    ]
    cat_cols = [c for c in cat_cols if c in df.columns]

    if not numeric_cols:
        raise ValueError("No numeric feature columns found. Check your features_windows.csv schema.")
    return numeric_cols, cat_cols


def split_users(df: pd.DataFrame, train_ratio: float, val_ratio: float, seed: int) -> Tuple[List[str], List[str], List[str]]:
    users = df["user_id"].unique().tolist()
    rnd = random.Random(seed)
    rnd.shuffle(users)
    n = len(users)
    n_train = max(1, int(n * train_ratio))
    n_val = max(1, int(n * val_ratio))
    train_users = users[:n_train]
    val_users = users[n_train:n_train + n_val]
    test_users = users[n_train + n_val:]
    if not test_users:
        # if too few users, borrow from val
        test_users = val_users[-1:]
        val_users = val_users[:-1]
    return train_users, val_users, test_users


def build_datasets(
    df: pd.DataFrame,
    cfg: TrainConfig,
) -> Tuple[WindowSequenceDataset, WindowSequenceDataset, WindowSequenceDataset, MindFlowPreprocessor]:
    num_cols, cat_cols = select_feature_columns(df)

    # map labels
    preproc = MindFlowPreprocessor(num_cols=num_cols, cat_cols=cat_cols)
    df = df.copy()
    df["y_state"] = df["cognitive_state"].astype(str).map(preproc.label_map).astype(np.int64)
    df["y_it"] = df["interruptibility_score"].astype(np.float32)

    train_users, val_users, test_users = split_users(df, cfg.train_ratio, cfg.val_ratio, cfg.seed)

    # fit preproc on train only
    preproc.fit(df[df["user_id"].isin(train_users)])

    train_ds = WindowSequenceDataset(
        df_joined=df,
        preproc=preproc,
        lookback=cfg.lookback_windows,
        stride=cfg.stride,
        window_minutes=cfg.window_minutes,
        max_gap_windows=cfg.max_gap_windows,
        user_ids=train_users,
    )
    val_ds = WindowSequenceDataset(
        df_joined=df,
        preproc=preproc,
        lookback=cfg.lookback_windows,
        stride=cfg.stride,
        window_minutes=cfg.window_minutes,
        max_gap_windows=cfg.max_gap_windows,
        user_ids=val_users,
    )
    test_ds = WindowSequenceDataset(
        df_joined=df,
        preproc=preproc,
        lookback=cfg.lookback_windows,
        stride=cfg.stride,
        window_minutes=cfg.window_minutes,
        max_gap_windows=cfg.max_gap_windows,
        user_ids=test_users,
    )
    return train_ds, val_ds, test_ds, preproc


# -------------------------
# Training / evaluation
# -------------------------
@torch.no_grad()
def evaluate(model: nn.Module, loader: DataLoader, device: str) -> Dict[str, float]:
    model.eval()
    total_ce = 0.0
    total_mse = 0.0
    n = 0

    all_logits = []
    all_y = []
    all_it_pred = []
    all_it_true = []

    for x_num, x_cat, y_state, y_it in loader:
        x_num = x_num.to(device)
        x_cat = {k: v.to(device) for k, v in x_cat.items()}
        y_state = y_state.to(device)
        y_it = y_it.to(device)

        logits, it_pred = model(x_num, x_cat)
        ce = F.cross_entropy(logits, y_state)
        mse = F.mse_loss(it_pred, y_it)

        bs = x_num.size(0)
        total_ce += ce.item() * bs
        total_mse += mse.item() * bs
        n += bs

        all_logits.append(logits.detach().cpu())
        all_y.append(y_state.detach().cpu())
        all_it_pred.append(it_pred.detach().cpu())
        all_it_true.append(y_it.detach().cpu())

    logits = torch.cat(all_logits, dim=0)
    y = torch.cat(all_y, dim=0)
    it_pred = torch.cat(all_it_pred, dim=0).numpy().reshape(-1)
    it_true = torch.cat(all_it_true, dim=0).numpy().reshape(-1)

    acc = (torch.argmax(logits, dim=-1) == y).float().mean().item()
    f1 = macro_f1_from_logits(logits, y, num_classes=logits.size(-1))
    rmse = float(np.sqrt(np.mean((it_pred - it_true) ** 2)))
    mae = float(np.mean(np.abs(it_pred - it_true)))

    return {
        "ce": total_ce / max(1, n),
        "mse": total_mse / max(1, n),
        "acc": float(acc),
        "macro_f1": float(f1),
        "rmse": rmse,
        "mae": mae,
    }


def train(cfg: TrainConfig, data_dir: str, out_dir: str) -> str:
    ensure_dir(out_dir)

    df = load_joined_table(data_dir)
    train_ds, val_ds, test_ds, preproc = build_datasets(df, cfg)

    train_loader = DataLoader(
        train_ds,
        batch_size=cfg.batch_size,
        shuffle=True,
        num_workers=cfg.num_workers,
        collate_fn=collate_batch,
        pin_memory=(cfg.device == "cuda"),
        drop_last=False,
    )
    val_loader = DataLoader(
        val_ds,
        batch_size=cfg.batch_size,
        shuffle=False,
        num_workers=cfg.num_workers,
        collate_fn=collate_batch,
        pin_memory=(cfg.device == "cuda"),
        drop_last=False,
    )
    test_loader = DataLoader(
        test_ds,
        batch_size=cfg.batch_size,
        shuffle=False,
        num_workers=cfg.num_workers,
        collate_fn=collate_batch,
        pin_memory=(cfg.device == "cuda"),
        drop_last=False,
    )

    # class weights from train distribution
    y_train = np.array([train_ds.df.loc[i, "y_state"] for i in train_ds.df.index if train_ds.df.loc[i, "user_id"] in train_ds.df["user_id"].unique()], dtype=np.int64)
    # easier: count from original joined df subset
    y_train = train_ds.df[train_ds.df["user_id"].isin(train_ds.df["user_id"].unique())]["y_state"].to_numpy()
    counts = np.bincount(y_train, minlength=5).astype(np.float32)
    weights = (counts.sum() / (counts + 1e-6))
    weights = weights / weights.mean()
    weight_t = torch.tensor(weights, dtype=torch.float32, device=cfg.device)

    cat_cardinalities = {c: len(preproc.vocabs[c]) for c in preproc.cat_cols}

    cat_cardinalities = {c: len(preproc.vocabs[c]) for c in preproc.cat_cols}

    if cfg.model_type == "mlp":
        print(f"Using SimpleMLP (lookback={cfg.lookback_windows}, d_model={cfg.d_model})...")
        model = SimpleMLP(
            num_dim=len(preproc.num_cols),
            lookback=cfg.lookback_windows,
            d_model=cfg.d_model,  # Reusing d_model as hidden dim
            num_classes=5,
        ).to(cfg.device)
    else:
        model = TabSeqTransformer(
            num_dim=len(preproc.num_cols),
            cat_cardinalities=cat_cardinalities,
            lookback=cfg.lookback_windows,
            d_model=cfg.d_model,
            n_heads=cfg.n_heads,
            n_layers=cfg.n_layers,
            dropout=cfg.dropout,
            num_classes=5,
        ).to(cfg.device)

    ce_loss_fn = LabelSmoothingCE(smoothing=cfg.label_smoothing, weight=weight_t)
    opt = torch.optim.AdamW(model.parameters(), lr=cfg.lr, weight_decay=cfg.weight_decay)

    # cosine schedule
    total_steps = max(1, cfg.epochs * max(1, len(train_loader)))
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=total_steps)

    best_score = -1e9
    best_path = os.path.join(out_dir, "mindflow_best.pt")
    patience_left = cfg.patience
    global_step = 0

    for epoch in range(1, cfg.epochs + 1):
        model.train()
        running = {"loss": 0.0, "ce": 0.0, "mse": 0.0}
        seen = 0

        t0 = time.time()

        for x_num, x_cat, y_state, y_it in train_loader:
            x_num = x_num.to(cfg.device)
            x_cat = {k: v.to(cfg.device) for k, v in x_cat.items()}
            y_state = y_state.to(cfg.device)
            y_it = y_it.to(cfg.device)

            logits, it_pred = model(x_num, x_cat)
            ce = ce_loss_fn(logits, y_state)
            mse = F.mse_loss(it_pred, y_it)
            loss = ce + cfg.lambda_reg * mse

            opt.zero_grad(set_to_none=True)
            loss.backward()
            if cfg.grad_clip and cfg.grad_clip > 0:
                nn.utils.clip_grad_norm_(model.parameters(), cfg.grad_clip)
            opt.step()
            scheduler.step()

            bs = x_num.size(0)
            running["loss"] += loss.item() * bs
            running["ce"] += ce.item() * bs
            running["mse"] += mse.item() * bs
            seen += bs
            global_step += 1

            lr_now = opt.param_groups[0]["lr"]
            avg_loss = running["loss"] / max(1, seen)
            avg_ce = running["ce"] / max(1, seen)
            avg_mse = running["mse"] / max(1, seen)
            speed = seen / max(1e-6, (time.time() - t0))  # samples/sec

            speed = seen / max(1e-6, (time.time() - t0))  # samples/sec

        train_metrics = {
            "loss": running["loss"] / max(1, seen),
            "ce": running["ce"] / max(1, seen),
            "mse": running["mse"] / max(1, seen),
        }
        val_metrics = evaluate(model, val_loader, cfg.device)

        # composite score: prioritize state macro_f1, then interruptibility rmse
        score = val_metrics["macro_f1"] - 0.25 * val_metrics["rmse"]

        print(
            f"[Epoch {epoch:02d}] "
            f"train_loss={train_metrics['loss']:.4f} "
            f"val_f1={val_metrics['macro_f1']:.4f} val_acc={val_metrics['acc']:.4f} "
            f"val_rmse={val_metrics['rmse']:.4f} score={score:.4f}"
        )

    
        if score > best_score + 1e-4:
            best_score = score
            patience_left = cfg.patience

            artifacts = preproc.export()
            ckpt = {
                "config": asdict(cfg),
                "model_state": model.state_dict(),
                "preprocess": {
                    "num_cols": artifacts.num_cols,
                    "cat_cols": artifacts.cat_cols,
                    "scaler": artifacts.scaler,
                    "vocabs": artifacts.vocabs,
                    "label_map": artifacts.label_map,
                    "inv_label_map": artifacts.inv_label_map,
                },
                "cat_cardinalities": cat_cardinalities,
            }
            torch.save(ckpt, best_path)
            with open(os.path.join(out_dir, "mindflow_best.json"), "w", encoding="utf-8") as f:
                json.dump({"best_score": best_score, "val_metrics": val_metrics}, f, ensure_ascii=False, indent=2)
        else:
            patience_left -= 1
            if patience_left <= 0:
                print("Early stopping.")
                break

    # test with best
    ckpt = torch.load(best_path, map_location=cfg.device)
    model.load_state_dict(ckpt["model_state"])
    test_metrics = evaluate(model, test_loader, cfg.device)
    with open(os.path.join(out_dir, "test_metrics.json"), "w", encoding="utf-8") as f:
        json.dump(test_metrics, f, ensure_ascii=False, indent=2)
    print("Test metrics:", test_metrics)

    return best_path


# -------------------------
# Simple MLP Model (Pure Java Friendly)
# -------------------------
class SimpleMLP(nn.Module):
    """
    A simple MLP that flattens time series.
    Easy to implement in pure Java without external libraries.
    Structure:
    Input[B, L, D] -> Flatten[B, L*D] -> Linear -> ReLU -> Linear -> Heads
    """
    def __init__(self, num_dim: int, lookback: int, d_model: int = 64, num_classes: int = 5):
        super().__init__()
        self.lookback = lookback
        self.input_dim = num_dim * lookback
        
        # We ignore categorical embeddings for the "Lite" version to keep it simple
        # Or we can one-hot encode them if needed, but let's stick to numeric + simple cats
        
        self.fc1 = nn.Linear(self.input_dim, d_model)
        self.fc2 = nn.Linear(d_model, d_model)
        
        self.head_state = nn.Linear(d_model, num_classes)
        self.head_it = nn.Linear(d_model, 1)

    def forward(self, x_num: torch.Tensor, x_cat: Dict[str, torch.Tensor]):
        # x_num: [B, L, D]
        # Ignore x_cat for this simple version or you can concat them if embeddings are removed
        
        B, L, D = x_num.shape
        x = x_num.view(B, -1)  # Flatten [B, L*D]
        
        x = F.relu(self.fc1(x))
        x = F.relu(self.fc2(x))
        
        logits = self.head_state(x)
        it_score = torch.sigmoid(self.head_it(x))
        return logits, it_score


def export_java_weights(model: nn.Module, preproc: MindFlowPreprocessor, out_path: str):
    """
    Export model weights to a Java class file.
    """
    print(f"Exporting Java weights to {out_path}...")
    
    sd = model.state_dict()
    
    def to_java_arr(tensor):
        if tensor.ndim == 1:
            # 1D array: {1.1f, 2.2f, ...}
            vals = [f"{x:.6f}f" for x in tensor.tolist()]
            return "{" + ", ".join(vals) + "}"
        elif tensor.ndim == 2:
            # 2D array: {{...}, {...}}
            rows = []
            for row in tensor.tolist():
                vals = [f"{x:.6f}f" for x in row]
                rows.append("{" + ", ".join(vals) + "}")
            return "{\n        " + ",\n        ".join(rows) + "\n    }"
        return "{}"

    # Prepare Preprocessing Constants
    means = [f"{x:.6f}f" for x in preproc.scaler.mean_]
    stds = [f"{x:.6f}f" for x in preproc.scaler.std_]
    
    java_code = f"""package com.example.mindflow.ai;

/**
 * Auto-generated by mindflow_train.py
 * Contains raw weights for SimpleMLP to run pure Java inference.
 */
public class MindFlowModelWeights {{
    
    // --- Preprocessing ---
    public static final int LOOKBACK = {model.lookback};
    public static final int NUM_COLS_COUNT = {len(preproc.num_cols)};
    public static final String[] NUM_COLS = {{ "{'", "'.join(preproc.num_cols)}" }};
    
    public static final float[] MEAN = {{ {", ".join(means)} }};
    public static final float[] STD = {{ {", ".join(stds)} }};

    // --- Model Weights (FC1) ---
    // Input dim: {model.input_dim} -> Hidden: {model.fc1.out_features}
    public static final float[][] FC1_W = {to_java_arr(sd['fc1.weight'])};
    public static final float[] FC1_B = {to_java_arr(sd['fc1.bias'])};

    // --- Model Weights (FC2) ---
    public static final float[][] FC2_W = {to_java_arr(sd['fc2.weight'])};
    public static final float[] FC2_B = {to_java_arr(sd['fc2.bias'])};

    // --- Heads ---
    public static final float[][] HEAD_STATE_W = {to_java_arr(sd['head_state.weight'])};
    public static final float[] HEAD_STATE_B = {to_java_arr(sd['head_state.bias'])};

    public static final float[][] HEAD_IT_W = {to_java_arr(sd['head_it.weight'])};
    public static final float[] HEAD_IT_B = {to_java_arr(sd['head_it.bias'])};
    
    // --- Labels ---
    public static final String[] LABELS = {{ "{'", "'.join(list(preproc.label_map.keys()))}" }};
}}
"""
    
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(java_code)
    print("Export done.")





# -------------------------
# Inference helper
# -------------------------
class MindFlowInferencer:
    """
    Load checkpoint and run inference for a single user.
    You provide the latest lookback_windows rows from features_windows.csv for that user,
    and it returns:
      - cognitive_state probabilities (5-class)
      - interruptibility_score in [0,1]
    """
    def __init__(self, ckpt_path: str, device: Optional[str] = None):
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        ckpt = torch.load(ckpt_path, map_location=self.device)

        cfg_dict = ckpt["config"]
        self.cfg = TrainConfig(**cfg_dict)

        pp = ckpt["preprocess"]
        artifacts = PreprocessArtifacts(
            num_cols=pp["num_cols"],
            cat_cols=pp["cat_cols"],
            scaler=pp["scaler"],
            vocabs=pp["vocabs"],
            label_map=pp["label_map"],
            inv_label_map={int(k): v for k, v in pp["inv_label_map"].items()} if isinstance(next(iter(pp["inv_label_map"].keys())), str) else pp["inv_label_map"],
        )
        self.preproc = MindFlowPreprocessor.load(artifacts)

        cat_cardinalities = ckpt["cat_cardinalities"]
        self.model = TabSeqTransformer(
            num_dim=len(self.preproc.num_cols),
            cat_cardinalities=cat_cardinalities,
            lookback=self.cfg.lookback_windows,
            d_model=self.cfg.d_model,
            n_heads=self.cfg.n_heads,
            n_layers=self.cfg.n_layers,
            dropout=self.cfg.dropout,
            num_classes=5,
        ).to(self.device)
        self.model.load_state_dict(ckpt["model_state"])
        self.model.eval()

    @torch.no_grad()
    def predict_from_recent_windows(self, df_recent: pd.DataFrame) -> Dict[str, object]:
        """
        df_recent: DataFrame with at least feature columns; must be exactly lookback_windows rows,
                  sorted by time ascending (old -> new).
        """
        if len(df_recent) != self.cfg.lookback_windows:
            raise ValueError(f"df_recent must have exactly {self.cfg.lookback_windows} rows")

        # transform
        x_num, x_cat = self.preproc.transform(df_recent)
        x_num_t = torch.tensor(x_num[None, :, :], dtype=torch.float32, device=self.device)  # [1,L,D]
        x_cat_t = {c: torch.tensor(x_cat[c][None, :], dtype=torch.long, device=self.device) for c in self.preproc.cat_cols}

        logits, it = self.model(x_num_t, x_cat_t)
        probs = torch.softmax(logits, dim=-1).cpu().numpy().reshape(-1)
        it_score = float(it.cpu().numpy().reshape(-1)[0])

        pred_idx = int(np.argmax(probs))
        pred_state = self.preproc.inv_label_map[pred_idx]

        return {
            "pred_state": pred_state,
            "state_probs": {self.preproc.inv_label_map[i]: float(probs[i]) for i in range(len(probs))},
            "interruptibility_score": it_score,
        }


# -------------------------
# CLI
# -------------------------
def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--data_dir",
        type=str,
        default=".",  # ✅ 默认就是当前目录（CSV 和脚本同层时最方便）
        help="Path to extracted dataset folder containing CSVs."
    )
    ap.add_argument(
        "--data_zip",
        type=str,
        default=None,
        help="Path to zip; will be extracted to --work_dir."
    )
    ap.add_argument(
        "--work_dir",
        type=str,
        default="./_data_extract",
        help="Where to extract zip if data_zip is used."
    )
    ap.add_argument(
        "--out_dir",
        type=str,
        default="./ckpt",
        help="Checkpoint output dir."
    )

    ap.add_argument("--lookback", type=int, default=12)
    ap.add_argument("--window_minutes", type=int, default=5)
    ap.add_argument("--stride", type=int, default=1)
    ap.add_argument("--max_gap_windows", type=int, default=2)

    ap.add_argument("--epochs", type=int, default=20)
    ap.add_argument("--batch_size", type=int, default=256)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--lambda_reg", type=float, default=1.0)
    ap.add_argument("--seed", type=int, default=42)

    ap.add_argument("--epochs", type=int, default=20)
    ap.add_argument("--batch_size", type=int, default=256)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--lambda_reg", type=float, default=1.0)
    ap.add_argument("--seed", type=int, default=42)
    
    ap.add_argument("--model_type", type=str, default="transformer", choices=["transformer", "mlp"])
    ap.add_argument("--export_java", action="store_true", help="Export MindFlowModelWeights.java")

    return ap.parse_args()


def main():
    args = parse_args()
    set_seed(args.seed)

    cfg = TrainConfig(
        window_minutes=args.window_minutes,
        lookback_windows=args.lookback,
        stride=args.stride,
        max_gap_windows=args.max_gap_windows,
        epochs=args.epochs,
        batch_size=args.batch_size,
        lr=args.lr,
        lambda_reg=args.lambda_reg,
        seed=args.seed,
        model_type=args.model_type,
    )

    # ✅ 默认 data_dir="." 时不需要再强制报错
    if (args.data_dir is None or args.data_dir == "") and (args.data_zip is None or args.data_zip == ""):
        raise ValueError("Provide either --data_dir or --data_zip")

    if args.data_zip is not None:
        data_dir = unzip_if_needed(args.data_zip, args.work_dir)
    else:
        data_dir = args.data_dir

    best_path = train(cfg, data_dir=data_dir, out_dir=args.out_dir)
    print("Saved best checkpoint to:", best_path)

    # quick sanity-load
    inf = MindFlowInferencer(best_path, device=cfg.device)
    print("Inferencer ready.")
    
    if args.export_java:
        if args.model_type != "mlp":
            print("Warning: --export_java is only supported (and meaningful) for model_type='mlp'.")
        else:
            # Load best model to export
            ckpt = torch.load(best_path, map_location="cpu")
            # We must recreate the model structure to load state_dict
            # Note: num_cols comes from preproc. We can get it from inferencer
            export_model = SimpleMLP(
                num_dim=len(inf.preproc.num_cols),
                lookback=cfg.lookback_windows,
                d_model=cfg.d_model,
                num_classes=5
            )
            export_model.load_state_dict(ckpt["model_state"])
            
            java_out = os.path.join(args.out_dir, "MindFlowModelWeights.java")
            export_java_weights(export_model, inf.preproc, java_out)

if __name__ == "__main__":
    main()
