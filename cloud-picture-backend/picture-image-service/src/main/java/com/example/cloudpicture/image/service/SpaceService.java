package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.dto.request.SpaceCreateRequest;
import com.example.cloudpicture.image.dto.request.SpaceImageQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.SpaceImageVO;
import com.example.cloudpicture.image.dto.response.SpaceVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 私有空间：一人一个，图片免审核且仅本人可见
 */
public interface SpaceService {

    /** 当前用户的私有空间；不存在返回 null，由前端引导创建 */
    SpaceVO getMine();

    SpaceVO create(SpaceCreateRequest request);

    boolean rename(SpaceRenameRequest request);

    /** 删除自己的空间并级联删除其中全部图片与 COS 对象 */
    boolean deleteMine();

    SpaceImageVO upload(MultipartFile file, ImageUploadRequest request);

    PageData<SpaceImageVO> pageImages(SpaceImageQueryRequest request);

    String download(Long id);

    boolean deleteImage(Long id);
}