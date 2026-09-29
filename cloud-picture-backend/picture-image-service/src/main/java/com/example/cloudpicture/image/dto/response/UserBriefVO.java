package com.example.cloudpicture.image.dto.response;

import lombok.Data;

/** 图片服务本地视图，用于接收用户服务返回的上传者信息 */
@Data
public class UserBriefVO {

    private Long id;
    private String name;
    private String avatar;
}