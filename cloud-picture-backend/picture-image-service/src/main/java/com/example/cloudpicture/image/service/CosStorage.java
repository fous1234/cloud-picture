package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.image.config.CosConfig;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 私有桶 COS 访问：上传对象、删除对象、生成短期签名 URL（可带图片处理参数）
 */
@Slf4j
@Service
public class CosStorage {

    private static final int THUMBNAIL_SIZE = 300;
    /** COS 图片处理参数，形如 ?imageMogr2/thumbnail/300x300> */
    private static final String IMAGE_MOGR = "imageMogr2";
    private static final String THUMBNAIL_PARAM = "thumbnail/" + THUMBNAIL_SIZE + "x" + THUMBNAIL_SIZE + ">";

    private final CosConfig cosConfig;
    private final long signExpireSeconds;

    public CosStorage(CosConfig cosConfig,
                      @Value("${picture.cos.sign-expire-seconds:3600}") long signExpireSeconds) {
        this.cosConfig = cosConfig;
        this.signExpireSeconds = signExpireSeconds;
    }

    public void upload(MultipartFile file, String key, String contentType) {
        requireConfigured();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(contentType);
        try (InputStream inputStream = file.getInputStream()) {
            cosConfig.cosClient().putObject(new PutObjectRequest(cosConfig.getBucket(), key, inputStream, metadata));
        } catch (CosClientException e) {
            log.error("图片上传 COS 失败, key={}", key, e);
            throw new BusinessException(ErrorCode.COS_ERROR, "图片上传失败");
        } catch (IOException e) {
            log.error("读取上传文件失败, key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取上传文件失败");
        }
    }

    /** 字节上传：内容已在内存中（如图源导入下载完成后）的场景 */
    public void upload(byte[] content, String key, String contentType) {
        requireConfigured();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        try {
            cosConfig.cosClient().putObject(new PutObjectRequest(cosConfig.getBucket(), key,
                    new ByteArrayInputStream(content), metadata));
        } catch (CosClientException e) {
            log.error("图片上传 COS 失败, key={}", key, e);
            throw new BusinessException(ErrorCode.COS_ERROR, "图片上传失败");
        }
    }

    public void delete(String key) {
        requireConfigured();
        try {
            cosConfig.cosClient().deleteObject(cosConfig.getBucket(), key);
        } catch (CosClientException e) {
            log.error("删除 COS 对象失败, key={}", key, e);
            throw new BusinessException(ErrorCode.COS_ERROR, "删除图片失败");
        }
    }

    public String signedUrl(String key) {
        return presign(key, Map.of());
    }

    public String signedThumbnailUrl(String key) {
        return presign(key, Map.of(IMAGE_MOGR, THUMBNAIL_PARAM));
    }

    private String presign(String key, Map<String, String> parameters) {
        requireConfigured();
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(cosConfig.getBucket(), key, HttpMethodName.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + signExpireSeconds * 1000L));
        parameters.forEach(request::addRequestParameter);
        try {
            return cosConfig.cosClient().generatePresignedUrl(request).toString();
        } catch (CosClientException e) {
            log.error("生成 COS 签名地址失败, key={}", key, e);
            throw new BusinessException(ErrorCode.COS_ERROR, "生成图片预览地址失败");
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(cosConfig.getBucket())
                || !StringUtils.hasText(cosConfig.getSecretId())
                || !StringUtils.hasText(cosConfig.getSecretKey())) {
            throw new BusinessException(ErrorCode.COS_ERROR,
                    "COS 未配置，请设置 COS_SECRET_ID / COS_SECRET_KEY / COS_BUCKET");
        }
    }
}
