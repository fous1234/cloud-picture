package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.image.dto.request.AdminSpaceQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.AdminSpaceVO;

/**
 * 管理端私有空间管理：只看元信息与统计，不提供任何图片级接口
 */
public interface AdminSpaceService {

    PageData<AdminSpaceVO> pageSpaces(AdminSpaceQueryRequest request);

    boolean rename(Long id, SpaceRenameRequest request);

    /** 删除指定空间并级联删除其中全部图片与 COS 对象 */
    boolean delete(Long id);
}