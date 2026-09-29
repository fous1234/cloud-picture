package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.image.dto.request.ImageQueryRequest;
import com.example.cloudpicture.image.dto.request.ImageReviewRequest;
import com.example.cloudpicture.image.dto.response.ImageVO;

public interface AdminImageService {

    PageData<ImageVO> pageImages(ImageQueryRequest request);

    boolean review(ImageReviewRequest request);

    boolean deleteImage(Long id);
}
