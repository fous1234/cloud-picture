package com.example.cloudpicture.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 32, message = "账号长度需在 4 到 32 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号只能包含字母、数字和下划线")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在 8 到 32 之间")
    private String userPassword;

    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;

    @Size(max = 32, message = "昵称长度不能超过 32")
    private String userName;
}