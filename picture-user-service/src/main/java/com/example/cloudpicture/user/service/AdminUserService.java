package com.example.cloudpicture.user.service;

import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.user.dto.request.UserQueryRequest;
import com.example.cloudpicture.user.dto.response.UserVO;

public interface AdminUserService {

    PageData<UserVO> listUsers(UserQueryRequest request);

    boolean updateStatus(Long id, Integer status);
}
