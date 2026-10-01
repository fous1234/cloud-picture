package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.image.dto.request.ImageQueryRequest;
import com.example.cloudpicture.image.dto.request.ImageUpdateRequest;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.dto.response.ImageVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {

    ImageVO upload(MultipartFile file, ImageUploadRequest request);

    PageData<ImageVO> pageImages(ImageQueryRequest request);

    PageData<ImageVO> pageMyImages(ImageQueryRequest request);

    ImageVO getImageById(Long id);

    boolean updateImage(ImageUpdateRequest request);

    boolean deleteImage(Long id);

    List<String> listTagNames(int limit);
}
