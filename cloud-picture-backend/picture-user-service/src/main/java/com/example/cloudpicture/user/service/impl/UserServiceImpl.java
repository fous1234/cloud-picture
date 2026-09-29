package com.example.cloudpicture.user.service.impl;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.user.dto.request.UserUpdateRequest;
import com.example.cloudpicture.user.dto.response.UserBriefVO;
import com.example.cloudpicture.user.dto.response.UserVO;
import com.example.cloudpicture.user.entity.User;
import com.example.cloudpicture.user.mapper.UserMapper;
import java.util.List;
import com.example.cloudpicture.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserVO getCurrentUser() {
        User user = userMapper.selectById(CurrentUser.get().getId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    public boolean updateCurrentUser(UserUpdateRequest request) {
        User user = new User();
        user.setId(CurrentUser.get().getId());
        user.setUserName(request.getUserName());
        user.setUserProfile(request.getUserProfile());
        return userMapper.updateById(user) > 0;
    }

    /** 内部接口：只返回上传者基本信息，供共享图库展示 */
    public List<UserBriefVO> listBriefByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream().map(UserBriefVO::from).toList();
    }
}
