package com.geosaa.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String refreshToken;

    private String tokenType = "Bearer";

    private String username;

    private String nickname;

    private String role;

    private List<String> permissions;

    public LoginResponse(String token, String username, String nickname, String role) {
        this.token = token;
        this.tokenType = "Bearer";
        this.username = username;
        this.nickname = nickname;
        this.role = role;
    }

    public LoginResponse(String token, String refreshToken, String username, String nickname, String role, List<String> permissions) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.permissions = permissions;
    }
}