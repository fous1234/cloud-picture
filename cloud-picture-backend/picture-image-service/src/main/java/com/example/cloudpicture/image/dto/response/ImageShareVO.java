package com.example.cloudpicture.image.dto.response;

import lombok.Data;

/**
 * 分享管理响应：仅返回给已鉴权的图片所有者，链接由前端用当前 origin 加 /share/{token} 生成
 */
@Data
public class ImageShareVO {

    /** 是否已启用分享链接 */
    private boolean enabled;

    /** 当前分享 token，未启用时为 null */
    private String token;

    public ImageShareVO(boolean enabled, String token) {
        this.enabled = enabled;
        this.token = token;
    }
}