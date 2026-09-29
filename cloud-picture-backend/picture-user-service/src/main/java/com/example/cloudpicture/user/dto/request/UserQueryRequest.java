package com.example.cloudpicture.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserQueryRequest {

    @Min(value = 1, message = "页码最小为 1")
    private long current = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private long size = 10;

    private String account;

    private String name;
}