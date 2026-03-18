package com.example.mindflow.auth;

/**
 * 认证操作回调接口
 */
public interface AuthCallback {
    /**
     * 操作成功
     * @param userId 用户ID
     * @param email 用户邮箱
     * @param username 用户名
     */
    void onSuccess(String userId, String email, String username);
    
    /**
     * 操作失败
     * @param error 错误信息
     */
    void onFailure(String error);
}
