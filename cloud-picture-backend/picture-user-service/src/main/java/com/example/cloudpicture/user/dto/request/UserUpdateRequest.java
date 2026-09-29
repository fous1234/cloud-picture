package com.example.cloudpicture.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @Size(max = 32, message = "昵称长度不能超过 32")
    private String userName;

    @Size(max = 512, message = "简介长度不能超过 512")
    private String userProfile;
}