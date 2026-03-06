#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MindFlow FastAPI Local Inference Server
======================================

你将此文件保存为：mindflow_api.py
并将你的模型文件放在同一目录的：./ckpt/mindflow_best.pt
（也就是你训练脚本保存 best checkpoint 的路径）

------------------------------------------------------------
1) 安装依赖
------------------------------------------------------------
在你当前 conda 环境里执行：

  pip install fastapi uvicorn pydantic pandas

注意：
- 你已经能训练 PyTorch 了，所以 torch 不需要重复装。
- 若你想用 GPU 推理，请确保 torch.cuda 可用。

------------------------------------------------------------
2) 启动服务（两种方式）
------------------------------------------------------------
方式 A（推荐，热重载，开发方便）：
  uvicorn mindflow_api:app --host 127.0.0.1 --port 8000 --reload

方式 B（直接 python 运行）：
  python mindflow_api.py

------------------------------------------------------------
3) 查看接口文档
------------------------------------------------------------
启动后，浏览器打开：
  http://127.0.0.1:8000/docs

------------------------------------------------------------
4) 先看模型需要哪些字段（很重要）
------------------------------------------------------------
访问：
  GET http://127.0.0.1:8000/meta

它会返回：
- lookback_windows：模型需要的窗口数（例如 12）
- required_numeric_cols / required_cat_cols：推理必须提供的字段名
- example_request：一个可参考的请求样例骨架

------------------------------------------------------------
5) 调用推理接口（必须都在本目录，且有torch）
------------------------------------------------------------
POST http://127.0.0.1:8000/predict
第二个终端测试：Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8000/predict" -ContentType "application/json" -InFile ".\req.json" | ConvertTo-Json -Depth 10

请求 JSON 结构（核心）：
{
  "recent_windows": [
     { ...第1个窗口特征... },
     { ...第2个窗口特征... },
     ...
     { ...第N个窗口特征... }
  ],
  "return_suggestion": true
}

关键要求：
- recent_windows 的长度必须 == lookback_windows（例如 12）
- 建议按时间从旧到新排列（如果有 window_start 字段，服务端会自动排序）
- 每个窗口至少包含 meta 返回的 required_numeric_cols + required_cat_cols 字段
  （你可以多给字段，服务端会忽略多余字段）

响应 JSON（核心）：
{
  "pred_state": "深度专注",
  "state_probs": {...},
  "interruptibility_score": 0.12,
  "suggested_action": {...}   # 示例建议（可由你的 JITAI 模块替换）
}


------------------------------------------------------------
6) 你要做“另一个代码模块做 JITAI”
------------------------------------------------------------
这个 API 内置的 suggested_action 只是示例策略（规则/阈值）。
你未来可以：
- 保留 API 只输出 pred_state + interruptibility_score + state_probs
- 由另一个模块（JITAI）来消费这些输出并做干预决策
或者：
- 直接把你的 JITAI 函数 import 进来替换 propose_action() 即可

============================================================
"""

from __future__ import annotations

import os
import sys
import json
from typing import Any, Dict, List, Optional
from fastapi.responses import JSONResponse
import traceback

import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# 复用你训练脚本中的推理类（mindflow_train.py 需要与本文件在同一目录或 PYTHONPATH 可见）
# 它会加载 checkpoint 内的 preprocess + model_state，并提供 predict_from_recent_windows()
from mindflow_train import MindFlowInferencer  # noqa: E402


# -----------------------------
# 配置：默认模型路径（同目录 ./ckpt/mindflow_best.pt）
# -----------------------------
DEFAULT_CKPT_PATH = os.getenv("MINDFLOW_CKPT", os.path.join(".", "ckpt", "mindflow_best.pt"))
DEFAULT_DEVICE = os.getenv("MINDFLOW_DEVICE", None)  # None -> 自动选择 cuda/cpu（由 MindFlowInferencer 内部决定）
API_BUILD = "explain-v2"

# -----------------------------
# Debug / Strictness Flags (env controlled)
# -----------------------------
DEBUG_MODE = os.getenv("MINDFLOW_DEBUG", "0") == "1"
DEBUG_MOCK_HIGH_STRESS = os.getenv("MINDFLOW_MOCK_HIGH_STRESS", "0") == "1"
STRICT_LOOKBACK = os.getenv("MINDFLOW_STRICT_LOOKBACK", "1") == "1"


# -----------------------------
# FastAPI app
# -----------------------------
app = FastAPI(
    title="MindFlow Local Inference API",
    version="1.0.0",
    description="Local inference for MindFlow: cognitive_state + interruptibility + (optional) suggested action.",
)

inferencer: Optional[MindFlowInferencer] = None


# -----------------------------
# Request / Response Schemas
# -----------------------------
class PredictRequest(BaseModel):
    recent_windows: List[Dict[str, Any]] = Field(
        ...,
        description="最近 lookback_windows 个窗口特征。每个元素是一行 features_windows 的 JSON 字典。",
        min_items=1,
    )
    return_suggestion: bool = Field(
        True,
        description="是否返回示例级 suggested_action（规则/阈值）。如果你用独立 JITAI 模块，可设为 false。",
    )

# -----------------------------
# State label mapping (ZH -> EN)
# -----------------------------
STATE_ZH2EN = {
    "深度专注": "deep_focus",
    "轻度专注": "light_focus",
    "休闲刷屏": "casual_scrolling",
    "高压忙乱": "high_stress",
    "放松休息": "rest_relax",
}

def zh_state_to_en(state_zh: str) -> str:
    """Map Chinese state label to stable English id."""
    return STATE_ZH2EN.get(state_zh, "unknown")

class Explanation(BaseModel):
    summary: str
    evidence: List[Dict[str, Any]]  # 每条 evidence: {feature, value, direction, note}
    latest_features: Dict[str, Any]

class SuggestedAction(BaseModel):
    allow_notification_categories: List[str] = Field(
        ..., description="建议允许的通知类别（示例：work/learning/system/social/entertainment）"
    )
    intervention: Optional[str] = Field(
        None, description="建议干预类型（示例：hint/card/lock/breath/todo），None 表示不干预。"
    )
    reason: str = Field(..., description="给出该建议的解释（用于调试/可解释性）。")


class PredictResponse(BaseModel):
    # 原字段（中文）：保持兼容
    pred_state: str
    state_probs: Dict[str, float]

    # 新增字段（英文 id）：给 JITAI 用，永不乱码
    pred_state_id: str
    state_probs_id: Dict[str, float]

    interruptibility_score: float
    suggested_action: Optional[SuggestedAction] = None

    explanation: Optional[Explanation] = None



# -----------------------------
# Startup: load model once
# -----------------------------
@app.on_event("startup")
def _startup_load_model():
    global inferencer
    ckpt_path = DEFAULT_CKPT_PATH

    if not os.path.exists(ckpt_path):
        # 给更清晰的报错：告诉用户应该把 ckpt 放哪
        raise RuntimeError(
            f"Checkpoint not found: {ckpt_path}\n"
            f"请确保你把 mindflow_best.pt 放到 ./ckpt/mindflow_best.pt，或设置环境变量 MINDFLOW_CKPT 指向正确路径。"
        )

    inferencer = MindFlowInferencer(ckpt_path=ckpt_path, device=DEFAULT_DEVICE)
    # 打印一下关键信息（方便你确认 lookback 和字段）
    print("[MindFlowAPI] Loaded checkpoint:", ckpt_path)
    print("[MindFlowAPI] device:", inferencer.device)
    print("[MindFlowAPI] lookback_windows:", inferencer.cfg.lookback_windows)
    print("[MindFlowAPI] required numeric cols:", inferencer.preproc.num_cols)
    print("[MindFlowAPI] required cat cols:", inferencer.preproc.cat_cols)
    print("[MindFlowAPI] API_BUILD:", API_BUILD, "file:", __file__)


# -----------------------------
# Helpers
# -----------------------------
def _ensure_inferencer() -> MindFlowInferencer:
    if inferencer is None:
        raise HTTPException(status_code=503, detail="Model is not loaded yet.")
    return inferencer


def _normalize_recent_windows(recent_windows: List[Dict[str, Any]], lookback: int) -> List[Dict[str, Any]]:
    if len(recent_windows) == lookback:
        return recent_windows
    if len(recent_windows) < lookback:
        if len(recent_windows) == 0:
            return recent_windows
        pad = [recent_windows[-1] for _ in range(lookback - len(recent_windows))]
        return recent_windows + pad
    # len > lookback: keep most recent
    return recent_windows[-lookback:]


def _build_recent_df(inf: MindFlowInferencer, recent_windows: List[Dict[str, Any]]) -> pd.DataFrame:
    """
    将 recent_windows(JSON list) 转成 DataFrame，并保证：
    - 列包含模型所需的全部 num_cols/cat_cols（缺失补 NaN）
    - 若存在 window_start 字段，则按 window_start 升序排序（旧->新）
    - 只保留模型需要的列（多余列丢弃）
    """
    if not isinstance(recent_windows, list) or len(recent_windows) == 0:
        raise HTTPException(status_code=422, detail="recent_windows must be a non-empty list.")

    # 允许输入里带多余字段；这里先全量读入
    df = pd.DataFrame(recent_windows)

    # 自动排序（如果有 window_start）
    if "window_start" in df.columns:
        # 若用户传的是字符串，也尽量转成 int
        try:
            df["window_start"] = pd.to_numeric(df["window_start"], errors="coerce")
            df = df.sort_values("window_start", ascending=True)
        except Exception:
            # 排序失败也不致命，保持输入顺序
            pass

    required_cols = list(inf.preproc.num_cols) + list(inf.preproc.cat_cols)

    # 对缺失列补 NaN（数值列）或 NaN（类别列也用 NaN，preproc 内会 fillna("__NA__")）
    for c in required_cols:
        if c not in df.columns:
            df[c] = pd.NA

    # 只保留模型需要的列（避免乱七八糟字段影响）
    df = df[required_cols].copy()

    return df


def propose_action(pred_state: str, interruptibility: float, latest_window: Dict[str, Any]) -> SuggestedAction:
    """
    示例级“建议干预/通知策略”（规则/阈值）。
    你后续用 JITAI 模块时，可以把这整段替换为你的策略函数。
    """
    # 可选特征（没有就当 None）
    in_focus = latest_window.get("in_focus_session", None)
    major_cat = latest_window.get("major_app_category", None)
    app_switch = latest_window.get("app_switch_count", None)
    hr_mean = latest_window.get("hr_mean", None)

    def _as_float(x):
        try:
            return float(x)
        except Exception:
            return None

    in_focus_f = _as_float(in_focus)
    app_switch_f = _as_float(app_switch)
    hr_mean_f = _as_float(hr_mean)

    # 1) 深度专注：尽量别打扰
    if pred_state == "深度专注" and interruptibility < 0.25:
        return SuggestedAction(
            allow_notification_categories=["work", "learning", "system"],
            intervention=None,
            reason="深度专注且可打断性很低：建议仅允许工作/学习/系统通知，其它延后。",
        )

    # 2) 高压忙乱：更适合轻干预（呼吸/减压）
    if pred_state == "高压忙乱" and interruptibility > 0.35:
        # 心率较高时更倾向 breath
        if hr_mean_f is not None and hr_mean_f >= 100:
            return SuggestedAction(
                allow_notification_categories=["work", "system"],
                intervention="breath",
                reason="高压忙乱且可打断性中高，同时心率偏高：建议呼吸/放松类干预，并减少非关键通知。",
            )
        return SuggestedAction(
            allow_notification_categories=["work", "system"],
            intervention="card",
            reason="高压忙乱且可打断性中高：建议卡片式提醒（降低任务压力/提示分解任务），并减少非关键通知。",
        )

    # 3) 休闲刷屏：当可打断性高、且频繁切换/娱乐类时，可做自控提示
    if pred_state == "休闲刷屏" and interruptibility > 0.55:
        # 娱乐类 + 频繁切换：更强一点
        if (isinstance(major_cat, str) and major_cat in ["entertainment", "social"]) and (app_switch_f is not None and app_switch_f >= 3):
            return SuggestedAction(
                allow_notification_categories=["work", "learning", "system"],
                intervention="lock",
                reason="休闲刷屏且可打断性高；主应用类别偏娱乐/社交且频繁切换：建议短暂锁定或限时，帮助退出刷屏循环。",
            )
        return SuggestedAction(
            allow_notification_categories=["work", "learning", "system", "social"],
            intervention="hint",
            reason="休闲刷屏且可打断性高：建议轻量提示（hint），引导回到目标任务。",
        )

    # 4) 轻度专注：允许更多信息进入，但仍控制娱乐
    if pred_state == "轻度专注" and interruptibility < 0.45:
        return SuggestedAction(
            allow_notification_categories=["work", "learning", "system", "social"],
            intervention=None,
            reason="轻度专注且可打断性不高：可允许社交通知进入，但建议继续延后娱乐类通知。",
        )

    # 5) 放松休息：基本都可以
    if pred_state == "放松休息":
        return SuggestedAction(
            allow_notification_categories=["work", "learning", "system", "social", "entertainment"],
            intervention=None,
            reason="放松休息状态：允许更多通知进入，一般不需要干预。",
        )

    # 默认：不做强干预
    return SuggestedAction(
        allow_notification_categories=["work", "learning", "system", "social"],
        intervention=None,
        reason="默认策略：不做强干预，保持工作/学习/系统/社交通知可用，娱乐类可按需延后。",
    )

def _safe_float(x):
    try:
        if x is None:
            return None
        return float(x)
    except Exception:
        return None

def build_explanation(pred_state_zh: str, state_probs_zh: Dict[str, float], it_score: float, latest: Dict[str, Any]) -> Explanation:
    """
    规则化解释：根据最后一个窗口特征 + 模型输出，生成“为什么是这个状态”的可读解释。
    这是在线推理最稳的解释方式（不依赖额外模型/SHAP）。
    """
    evidence = []

    # 取一些关键特征
    in_focus = latest.get("in_focus_session")
    major_cat = latest.get("major_app_category")
    app_switch = _safe_float(latest.get("app_switch_count"))
    notif_recv = _safe_float(latest.get("notif_received_count"))
    notif_click = _safe_float(latest.get("notif_clicked_count"))
    screen_on = _safe_float(latest.get("screen_on_ms"))
    touch = _safe_float(latest.get("touch_count"))
    scroll = _safe_float(latest.get("scroll_count"))
    keys = _safe_float(latest.get("key_stroke_count"))
    hr = _safe_float(latest.get("hr_mean"))
    steps = _safe_float(latest.get("steps"))

    # 证据规则（你可以按你业务理解继续加）
    if in_focus is not None:
        evidence.append({
            "feature": "in_focus_session",
            "value": in_focus,
            "direction": "supports_focus" if float(in_focus) >= 0.5 else "supports_non_focus",
            "note": "处于专注会话内更倾向于专注类状态。"
        })

    if isinstance(major_cat, str):
        if major_cat in ["work", "learning"]:
            evidence.append({
                "feature": "major_app_category",
                "value": major_cat,
                "direction": "supports_focus",
                "note": "主导应用类别为工作/学习时更可能处于专注状态。"
            })
        elif major_cat in ["social", "entertainment"]:
            evidence.append({
                "feature": "major_app_category",
                "value": major_cat,
                "direction": "supports_scrolling",
                "note": "主导应用偏社交/娱乐时更可能是休闲刷屏。"
            })

    if app_switch is not None:
        evidence.append({
            "feature": "app_switch_count",
            "value": app_switch,
            "direction": "supports_stable_focus" if app_switch <= 2 else "supports_distraction",
            "note": "切换次数越少越像稳定工作流；切换频繁更像分心/忙乱。"
        })

    if notif_recv is not None:
        evidence.append({
            "feature": "notif_received_count",
            "value": notif_recv,
            "direction": "supports_stable_focus" if notif_recv <= 2 else "supports_interruptions",
            "note": "通知较少通常更不易被打断；通知多更易打断/忙乱。"
        })

    if screen_on is not None:
        evidence.append({
            "feature": "screen_on_ms",
            "value": screen_on,
            "direction": "supports_active" if screen_on > 0 else "supports_inactive",
            "note": "屏幕持续点亮说明窗口内有持续活动。"
        })

    # 输入行为强度：键入/触控/滚动
    if keys is not None:
        evidence.append({
            "feature": "key_stroke_count",
            "value": keys,
            "direction": "supports_work_flow" if keys >= 80 else "supports_low_engagement",
            "note": "键入较多更像写作/办公；很少则更像浏览/休息。"
        })

    if scroll is not None:
        evidence.append({
            "feature": "scroll_count",
            "value": scroll,
            "direction": "supports_scrolling" if scroll >= 15 else "supports_task_focus",
            "note": "滚动多更像刷内容流；滚动少更像任务型操作。"
        })

    if steps is not None:
        evidence.append({
            "feature": "steps",
            "value": steps,
            "direction": "supports_rest" if steps >= 30 else "supports_stationary",
            "note": "步数较多更像走动/休息状态；步数低更像坐着工作。"
        })

    # 根据模型概率给一句总结
    p = state_probs_zh.get(pred_state_zh, 0.0)
    summary = f"模型以 {p:.1%} 的概率判定为「{pred_state_zh}」。"
    # 加入可打断性解释
    summary += f" 可打断性分数为 {it_score:.2f}（0=不宜打扰，1=适合打扰）。"

    # 针对不同状态加一句“解释性话术”
    if pred_state_zh == "轻度专注":
        summary += " 输入特征显示处于专注会话/工作类应用为主、切换不频繁，因此更像轻度专注。"
    elif pred_state_zh == "深度专注":
        summary += " 输入特征显示任务行为更稳定、干扰更少，因此更像深度专注。"
    elif pred_state_zh == "休闲刷屏":
        summary += " 输入特征显示滚动/社交娱乐占比更高，因此更像休闲刷屏。"
    elif pred_state_zh == "高压忙乱":
        summary += " 输入特征显示切换/通知/生理压力指标更高，因此更像高压忙乱。"
    elif pred_state_zh == "放松休息":
        summary += " 输入特征显示活跃度较低或步行/休息迹象更多，因此更像放松休息。"

    # 最新窗口关键特征回传（便于前端可视化/调试）
    latest_features = {
        "in_focus_session": in_focus,
        "major_app_category": major_cat,
        "app_switch_count": app_switch,
        "notif_received_count": notif_recv,
        "key_stroke_count": keys,
        "scroll_count": scroll,
        "screen_on_ms": screen_on,
        "hr_mean": hr,
        "steps": steps,
    }

    return Explanation(summary=summary, evidence=evidence, latest_features=latest_features)


# -----------------------------
# Routes
# -----------------------------
@app.get("/health")
def health():
    _ = _ensure_inferencer()
    return {"status": "ok"}


@app.get("/meta")
def meta():
    inf = _ensure_inferencer()
    lookback = inf.cfg.lookback_windows
    num_cols = inf.preproc.num_cols
    cat_cols = inf.preproc.cat_cols

    example_window = {c: None for c in (num_cols + cat_cols)}
    # 给一些常见字段示例值，便于你复制测试
    if "hour_of_day" in example_window:
        example_window["hour_of_day"] = 10
    if "day_of_week" in example_window:
        example_window["day_of_week"] = 2
    if "in_focus_session" in example_window:
        example_window["in_focus_session"] = 1
    if "major_app_category" in example_window:
        example_window["major_app_category"] = "work"
    if "activity_level" in example_window:
        example_window["activity_level"] = "sedentary"
    if "loc_category" in example_window:
        example_window["loc_category"] = "company"
    if "network_type" in example_window:
        example_window["network_type"] = "wifi"
    if "noise_level" in example_window:
        example_window["noise_level"] = "low"
    if "timezone" in example_window:
        example_window["timezone"] = "Asia/Tokyo"

    return {
        "lookback_windows": lookback,
        "required_numeric_cols": num_cols,
        "required_cat_cols": cat_cols,
        "strict_lookback": STRICT_LOOKBACK,
        "example_request": {
            "recent_windows": [example_window for _ in range(lookback)],
            "return_suggestion": True,
        },
        "ckpt_path": DEFAULT_CKPT_PATH,
        "device": inf.device,
    }


@app.post("/predict", response_model=PredictResponse)
# def predict(req: PredictRequest):
#     inf = _ensure_inferencer()
#     lookback = inf.cfg.lookback_windows
# 
#     # if len(req.recent_windows) != lookback:
#     #     raise HTTPException(
#     #         status_code=422,
#     #         detail=f"recent_windows length must be exactly {lookback}, got {len(req.recent_windows)}. "
#     #                f"请先 GET /meta 查看 lookback_windows 和所需字段。",
#     #     )
# 
#     # build df for inferencer
#     df_recent = _build_recent_df(inf, req.recent_windows)
# 
#     # 推理
#     try:
#         out = inf.predict_from_recent_windows(df_recent)
#     except Exception as e:
#         raise HTTPException(status_code=400, detail=f"Inference failed: {repr(e)}")
# 
#     pred_state = out["pred_state"]
#     it_score = float(out["interruptibility_score"])
#     state_probs = {k: float(v) for k, v in out["state_probs"].items()}
# 
#     latest = req.recent_windows[-1]
#     explanation = build_explanation(pred_state, state_probs, it_score, latest)
# 
#     suggested = None
#     if req.return_suggestion:
#         latest = req.recent_windows[-1]  # 用户输入顺序的最后一条视为“最新”
#         # 若传了 window_start 且服务端排序了，则 DataFrame 的最后一行更可靠
#         # 但动作建议用输入最新一条也行（通常一致）
#         suggested = propose_action(pred_state, it_score, latest)
# 
#     # ✅ 新增：英文 id 输出（给 JITAI 用）
#     pred_state_id = zh_state_to_en(pred_state)
#     state_probs_id = {zh_state_to_en(k): float(v) for k, v in out["state_probs"].items()}
# 
#     print("[debug] explanation type:", type(explanation))
#     print("[debug] explanation value:", explanation)
# 
#     resp = PredictResponse(
#     pred_state=pred_state,
#     state_probs=state_probs,
#     pred_state_id=pred_state_id,
#     state_probs_id=state_probs_id,
#     interruptibility_score=it_score,
#     suggested_action=suggested,
#     explanation=explanation,
# )
# 
#     # 用 JSONResponse 显式确保 UTF-8 且不转义中文
#     return JSONResponse(
#         content=resp.model_dump(),
#         media_type="application/json; charset=utf-8"
#     )




def predict(req: PredictRequest):
    """
    MindFlow 核心预测接口 - 调试增强版
    1. 自动审计缺失字段 (解决 NAType 400 错误)
    2. 提供上帝模式开关 (Mock 高压状态触发闹钟)
    3. 全量错误追踪 (traceback)
    """

    if DEBUG_MOCK_HIGH_STRESS:
        print("\n[DEBUG] 🚨 上帝模式激活：正在向 Android 发送强制高压响应...")
        mock_data = {
            "pred_state": "高压忙乱",
            "state_probs": {"深度专注": 0.0, "轻度专注": 0.0, "休闲刷屏": 0.0, "高压忙乱": 1.0, "放松休息": 0.0},
            "pred_state_id": "high_stress",  # 必须对齐 Android 端的判定 ID
            "state_probs_id": {"high_stress": 1.0},
            "interruptibility_score": 0.95,
            "suggested_action": {
                "allow_notification_categories": ["work", "learning", "system"],
                "intervention": "card",
                "reason": "调试强制触发：检测到高压，请立即休息。"
            },
            "explanation": {
                "summary": "这是调试专用的 Mock 数据，用于测试 Android 闹钟逻辑。",
                "evidence": [],
                "latest_features": {}
            }
        }
        return JSONResponse(content=mock_data, media_type="application/json; charset=utf-8")

    inf = _ensure_inferencer()

    # 1. 字段完整性审计
    lookback = inf.cfg.lookback_windows
    recent_windows = req.recent_windows
    if STRICT_LOOKBACK:
        if len(recent_windows) != lookback:
            raise HTTPException(
                status_code=422,
                detail=f"recent_windows length must be exactly {lookback}, got {len(recent_windows)}. "
                       f"请先 GET /meta 查看 lookback_windows 和所需字段。"
            )
    else:
        recent_windows = _normalize_recent_windows(recent_windows, lookback)

    # 构造 DataFrame
    df_recent = _build_recent_df(inf, recent_windows)

    if DEBUG_MODE:
        # 查找导致 NAType 400 错误的空字段
        null_cols = df_recent.columns[df_recent.isnull().any()].tolist()
        print(f"\n[DEBUG] >>> 收到 Android 请求 | 窗口数: {len(recent_windows)}")
        if null_cols:
            print(f"[DEBUG] ⚠️ 警告：Android 发来的数据缺失列: {null_cols}")
            print(f"[DEBUG] 这将导致模型推理报 NAType 错误！请检查 WindowData.java")
        else:
            print("[DEBUG] ✅ 字段完整性校验通过，准备推理...")

    # 2. 模型推理 (带深度追踪)
    try:
        out = inf.predict_from_recent_windows(df_recent)
    except Exception as e:
        # 打印完整的 Python 错误堆栈，不再盲猜
        error_trace = traceback.format_exc()
        print(f"\n[ERROR] 推理失败！详细堆栈如下：\n{error_trace}")
        raise HTTPException(
            status_code=400,
            detail=f"Inference failed: {repr(e)}. 请检查这些字段在 Android 端是否未赋值: {null_cols if 'null_cols' in locals() else 'unknown'}"
        )

    # 3. 结果封装与 ID 映射
    pred_state = out["pred_state"]
    it_score = float(out["interruptibility_score"])
    state_probs = {k: float(v) for k, v in out["state_probs"].items()}

    # 获取英文 ID 供 Android 闹钟逻辑判定
    pred_state_id = zh_state_to_en(pred_state)
    state_probs_id = {zh_state_to_en(k): float(v) for k, v in out["state_probs"].items()}

    if DEBUG_MODE:
        print(f"[DEBUG] <<< 推理成功 | 状态: {pred_state} ({pred_state_id}) | 压力分: {it_score:.4f}")

    # 4. 生成解释与动作建议
    latest = recent_windows[-1]
    explanation = build_explanation(pred_state, state_probs, it_score, latest)

    suggested = None
    if req.return_suggestion:
        suggested = propose_action(pred_state, it_score, latest)

    # 5. 构建响应体
    resp = PredictResponse(
        pred_state=pred_state,
        state_probs=state_probs,
        pred_state_id=pred_state_id,
        state_probs_id=state_probs_id,
        interruptibility_score=it_score,
        suggested_action=suggested,
        explanation=explanation,
    )

    return JSONResponse(
        content=resp.model_dump(),
        media_type="application/json; charset=utf-8"
    )



# -----------------------------
# Local run entry
# -----------------------------
if __name__ == "__main__":
    # 允许你直接 python mindflow_api.py 启动
    # 如果你更喜欢 uvicorn 命令行启动，也完全没问题（见文件头说明）
    import uvicorn

    uvicorn.run(
        "mindflow_api:app",
        host="0.0.0.0",
        port=8000,
        reload=False,   # 你开发时可以改 True；生产部署建议 False
        log_level="info",
    )
