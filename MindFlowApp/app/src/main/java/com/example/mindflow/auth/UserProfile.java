package com.example.mindflow.auth;

/**
 * 用户信息模型
 */
public class UserProfile {
    public String id;
    public String email;
    public String username;
    public String avatarUrl;
    public long createdAt;
    public long updatedAt;
    
    public UserProfile() {}
    
    public UserProfile(String id, String email, String username) {
        this.id = id;
        this.email = email;
        this.username = username;
    }
    
    public UserProfile(String id, String email, String username, String avatarUrl) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.avatarUrl = avatarUrl;
    }
}
