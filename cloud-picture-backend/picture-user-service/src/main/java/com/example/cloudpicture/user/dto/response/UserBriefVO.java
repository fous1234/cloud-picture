package com.example.cloudpicture.user.dto.response;

import com.example.cloudpicture.user.entity.User;
import lombok.Data;

/** 供共享图库展示的上传者基本信息 */
@Data
public class UserBriefVO {

    private Long id;
    private String name;
    private String avatar;

    public static UserBriefVO from(User user) {
        if (user == null) {
            return null;
        }
        UserBriefVO vo = new UserBriefVO();
        vo.id = user.getId();
        vo.name = user.getUserName();
        vo.avatar = user.getUserAvatar();
        return vo;
    }
}