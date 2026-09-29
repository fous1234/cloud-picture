package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.image.client.UserServiceClient;
import com.example.cloudpicture.image.dto.response.ImageVO;
import com.example.cloudpicture.image.dto.response.UserBriefVO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 批量补齐图片上传者的基本信息，共享图库与管理端列表共用
 */
@Slf4j
@Service
public class UploaderFiller {

    private final UserServiceClient userServiceClient;

    public UploaderFiller(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    /** 上传者信息属于列表的增强信息，查询失败不影响列表返回 */
    public void fillOwners(List<ImageVO> records) {
        List<Long> ownerIds = records.stream()
                .map(ImageVO::getOwnerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return;
        }
        ApiResponse<List<UserBriefVO>> response;
        try {
            response = userServiceClient.batchGetUsers(ownerIds);
        } catch (Exception e) {
            log.error("查询上传者信息失败, ownerIds={}", ownerIds, e);
            return;
        }
        if (response == null || response.getData() == null) {
            return;
        }
        Map<Long, UserBriefVO> ownerMap = response.getData().stream()
                .collect(Collectors.toMap(UserBriefVO::getId, Function.identity(), (first, second) -> first));
        records.forEach(record -> record.setOwner(ownerMap.get(record.getOwnerId())));
    }
}