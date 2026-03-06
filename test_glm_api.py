#!/usr/bin/env python3
"""
GLM API 测试脚本
测试智谱 GLM-4V API 是否可以正常调用
"""
import json
import time
import hmac
import hashlib
import base64
import requests

API_KEY = "73d3c8d1197741148d7fe824860c6b88.REuJVDnBMWKM05sm"
API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

def generate_jwt(api_key: str) -> str:
    """生成 JWT Token"""
    parts = api_key.split(".")
    if len(parts) != 2:
        raise ValueError("API Key 格式错误")
    
    api_id, secret = parts
    timestamp = int(time.time() * 1000)
    expire = timestamp + 3600 * 1000  # 1小时有效期
    
    # Header
    header = {"alg": "HS256", "sign_type": "SIGN"}
    # Payload
    payload = {"api_key": api_id, "exp": expire, "timestamp": timestamp}
    
    # Base64 URL 编码
    def b64_encode(data):
        return base64.urlsafe_b64encode(json.dumps(data).encode()).decode().rstrip("=")
    
    encoded_header = b64_encode(header)
    encoded_payload = b64_encode(payload)
    
    # 签名
    content = f"{encoded_header}.{encoded_payload}"
    signature = hmac.new(
        secret.encode(), content.encode(), hashlib.sha256
    ).digest()
    encoded_signature = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    
    return f"{encoded_header}.{encoded_payload}.{encoded_signature}"

def test_glm47_with_thinking():
    """测试 GLM-4.7 模型（带thinking参数）"""
    print("=" * 50)
    print("测试 GLM-4.7 模型（带thinking参数）...")
    print("=" * 50)
    
    token = generate_jwt(API_KEY)
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    data = {
        "model": "glm-4.7",
        "messages": [
            {"role": "user", "content": "你好，请用一句话介绍你自己。"}
        ],
        "thinking": {"type": "enabled"},
        "max_tokens": 65536,
        "temperature": 1.0
    }
    
    print("发送请求...")
    try:
        response = requests.post(API_URL, headers=headers, json=data, timeout=30)
        print(f"响应状态码: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            content = result.get("choices", [{}])[0].get("message", {}).get("content", "")
            print(f"\n✅ GLM-4.7 可用!")
            print(f"AI 回复: {content[:200]}...")
            return True
        else:
            print(f"\n❌ GLM-4.7 不可用!")
            print(f"错误信息: {response.text}")
            return False
    except Exception as e:
        print(f"\n❌ 请求异常: {e}")
        return False

def test_list_models():
    """测试获取可用模型列表"""
    print("=" * 50)
    print("测试可用模型...")
    print("=" * 50)
    
    # 测试不同的模型名称
    models_to_test = [
        "glm-4v",           # GLM-4V 视觉模型
        "glm-4v-flash",     # GLM-4V Flash
        "glm-4v-plus",      # GLM-4V Plus
        "glm-4.7",          # GLM-4.7 文本模型（官方文档）
        "glm-4.7v",         # 用户说的 GLM-4.7v
    ]
    
    token = generate_jwt(API_KEY)
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    working_models = []
    
    for model in models_to_test:
        print(f"\n测试模型: {model}")
        data = {
            "model": model,
            "messages": [{"role": "user", "content": "你好"}]
        }
        try:
            response = requests.post(API_URL, headers=headers, json=data, timeout=15)
            if response.status_code == 200:
                print(f"  ✅ {model} 可用!")
                working_models.append(model)
            else:
                error = response.json().get("error", {}).get("message", response.text[:100])
                print(f"  ❌ {model} 不可用: {error}")
        except Exception as e:
            print(f"  ❌ {model} 请求失败: {e}")
    
    print(f"\n可用模型列表: {working_models}")
    return working_models

def test_text_chat():
    """测试纯文本对话"""
    print("=" * 50)
    print("测试 GLM API 文本对话能力...")
    print("=" * 50)
    
    token = generate_jwt(API_KEY)
    print(f"JWT Token 生成成功: {token[:50]}...")
    
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    data = {
        "model": "glm-4v-flash",
        "messages": [
            {
                "role": "user",
                "content": "你好，请用一句话介绍你自己。"
            }
        ]
    }
    
    print("\n发送请求到 GLM API...")
    try:
        response = requests.post(API_URL, headers=headers, json=data, timeout=30)
        print(f"响应状态码: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            content = result.get("choices", [{}])[0].get("message", {}).get("content", "")
            print(f"\n✅ API 调用成功!")
            print(f"AI 回复: {content}")
            return True
        else:
            print(f"\n❌ API 调用失败!")
            print(f"错误信息: {response.text}")
            return False
    except Exception as e:
        print(f"\n❌ 请求异常: {e}")
        return False

def test_image_understanding():
    """测试图像理解能力（使用示例图片URL）"""
    print("\n" + "=" * 50)
    print("测试 GLM API 图像理解能力...")
    print("=" * 50)
    
    token = generate_jwt(API_KEY)
    
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    # 使用一个公开的测试图片
    data = {
        "model": "glm-4v-flash",
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "这张图片里有什么？请简短回答。"},
                    {"type": "image_url", "image_url": {"url": "https://www.python.org/static/img/python-logo.png"}}
                ]
            }
        ]
    }
    
    print("发送图像理解请求...")
    try:
        response = requests.post(API_URL, headers=headers, json=data, timeout=60)
        print(f"响应状态码: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            content = result.get("choices", [{}])[0].get("message", {}).get("content", "")
            print(f"\n✅ 图像理解成功!")
            print(f"AI 分析结果: {content}")
            return True
        else:
            print(f"\n❌ 图像理解失败!")
            print(f"错误信息: {response.text}")
            return False
    except Exception as e:
        print(f"\n❌ 请求异常: {e}")
        return False

if __name__ == "__main__":
    print("\n🚀 开始测试 GLM API...\n")
    
    # 先测试 GLM-4.7 带thinking参数
    glm47_ok = test_glm47_with_thinking()
    
    # 测试可用模型
    working_models = test_list_models()
    
    text_ok = test_text_chat()
    image_ok = test_image_understanding()
    
    print("\n" + "=" * 50)
    print("测试结果汇总")
    print("=" * 50)
    print(f"可用模型: {working_models}")
    print(f"文本对话: {'✅ 通过' if text_ok else '❌ 失败'}")
    print(f"图像理解: {'✅ 通过' if image_ok else '❌ 失败'}")
    
    if text_ok and image_ok:
        print("\n🎉 所有测试通过! API 可正常使用。")
    else:
        print("\n⚠️ 部分测试失败，请检查 API Key 或网络连接。")
