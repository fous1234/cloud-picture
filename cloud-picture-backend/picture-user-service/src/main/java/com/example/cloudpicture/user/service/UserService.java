package com.example.cloudpicture.user.service;

import com.example.cloudpicture.user.dto.request.UserUpdateRequest;
import com.example.cloudpicture.user.dto.response.UserBriefVO;
import com.example.cloudpicture.user.dto.response.UserVO;
import java.util.List;

public interface UserService {

    UserVO getCurrentUser();

    boolean updateCurrentUser(UserUpdateRequest request);

    List<UserBriefVO> listBriefByIds(List<Long> ids);
}
