package com.example.cloudpicture.user.dto.response;

import com.example.cloudpicture.user.entity.User;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String account;
    private String name;
    private String avatar;
    private String profile;
    private String role;
    private Integer status;
    private LocalDateTime createTime;

    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.id = user.getId();
        vo.account = user.getUserAccount();
        vo.name = user.getUserName();
        vo.avatar = user.getUserAvatar();
        vo.profile = user.getUserProfile();
        vo.role = user.getUserRole();
        vo.status = user.getStatus();
        vo.createTime = user.getCreateTime();
        return vo;
    }
}