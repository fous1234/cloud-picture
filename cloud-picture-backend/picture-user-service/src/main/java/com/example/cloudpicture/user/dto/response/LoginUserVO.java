package com.example.cloudpicture.user.dto.response;

import lombok.Data;

@Data
public class LoginUserVO {

    private Long id;
    private String account;
    private String name;
    private String avatar;
    private String role;
    private String token;
}