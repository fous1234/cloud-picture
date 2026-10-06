package com.example.cloudpicture.image.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.image.client.UserServiceClient;
import com.example.cloudpicture.image.dto.request.AdminSpaceQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.AdminSpaceVO;
import com.example.cloudpicture.image.dto.response.UserBriefVO;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.entity.SpaceImage;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import com.example.cloudpicture.image.mapper.SpaceImageMapper;
import com.example.cloudpicture.image.service.AdminSpaceService;
import com.example.cloudpicture.image.service.CosStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理端空间管理：分页返回空间元信息 + 图片数与总大小聚合 + 所属用户信息，不读取任何图片行
 */
@Slf4j
@Service
public class AdminSpaceServiceImpl implements AdminSpaceService {

    private final PrivateSpaceMapper privateSpaceMapper;
    private final SpaceImageMapper spaceImageMapper;
    private final CosStorage cosStorage;
    private final UserServiceClient userServiceClient;

    public AdminSpaceServiceImpl(PrivateSpaceMapper privateSpaceMapper, SpaceImageMapper spaceImageMapper,
                                 CosStorage cosStorage, UserServiceClient userServiceClient) {
        this.privateSpaceMapper = privateSpaceMapper;
        this.spaceImageMapper = spaceImageMapper;
        this.cosStorage = cosStorage;
        this.userServiceClient = userServiceClient;
    }

    public PageData<AdminSpaceVO> pageSpaces(AdminSpaceQueryRequest request) {
        Page<PrivateSpace> page = privateSpaceMapper.selectPage(Page.of(request.getCurrent(), request.getSize()),
                request.toQueryWrapper());
        List<PrivateSpace> spaces = page.getRecords();
        Map<Long, long[]> stats = summarize(spaces.stream().map(PrivateSpace::getId).toList());
        Map<Long, UserBriefVO> owners = loadOwners(spaces.stream().map(PrivateSpace::getOwnerId).toList());
        List<AdminSpaceVO> records = spaces.stream().map(space -> {
            long[] counters = stats.getOrDefault(space.getId(), new long[]{0L, 0L});
            AdminSpaceVO vo = AdminSpaceVO.from(space, counters[0], counters[1]);
            vo.setOwner(owners.get(space.getOwnerId()));
            return vo;
        }).toList();
        return PageData.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public boolean rename(Long id, SpaceRenameRequest request) {
        PrivateSpace space = requireSpace(id);
        PrivateSpace update = new PrivateSpace();
        update.setId(space.getId());
        update.setName(request.getName());
        return privateSpaceMapper.updateById(update) > 0;
    }

    public boolean delete(Long id) {
        cascadeDelete(requireSpace(id));
        return true;
    }

    /** 一次分组查询取回本页所有空间的图片数与总大小；is_delete 条件由 @TableLogic 自动补上 */
    private Map<Long, long[]> summarize(List<Long> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = spaceImageMapper.selectMaps(new QueryWrapper<SpaceImage>()
                .select("space_id AS spaceId", "COUNT(*) AS imageCount", "COALESCE(SUM(pic_size), 0) AS totalSize")
                .in("space_id", spaceIds)
                .groupBy("space_id"));
        Map<Long, long[]> stats = new HashMap<>();
        for (Map<String, Object> row : rows) {
            stats.put(asLong(row.get("spaceId")),
                    new long[]{asLong(row.get("imageCount")), asLong(row.get("totalSize"))});
        }
        return stats;
    }

    /** 所属用户信息属于列表增强信息，查询失败不影响列表返回 */
    private Map<Long, UserBriefVO> loadOwners(List<Long> ownerIds) {
        List<Long> distinct = ownerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        ApiResponse<List<UserBriefVO>> response;
        try {
            response = userServiceClient.batchGetUsers(distinct);
        } catch (Exception e) {
            log.error("查询空间所属用户信息失败, ownerIds={}", distinct, e);
            return Map.of();
        }
        if (response == null || response.getData() == null) {
            return Map.of();
        }
        return response.getData().stream()
                .collect(Collectors.toMap(UserBriefVO::getId, Function.identity(), (first, second) -> first));
    }

    /** 级联删除：先逐张删 COS 对象（单张失败只记日志），再软删图片行，最后物理删除空间行 */
    private void cascadeDelete(PrivateSpace space) {
        List<SpaceImage> images = spaceImageMapper.selectList(new LambdaQueryWrapper<SpaceImage>()
                .eq(SpaceImage::getSpaceId, space.getId()));
        for (SpaceImage image : images) {
            try {
                cosStorage.delete(image.getCosKey());
            } catch (RuntimeException e) {
                log.warn("删除私有图片 COS 对象失败, key={}", image.getCosKey(), e);
            }
        }
        if (!images.isEmpty()) {
            spaceImageMapper.delete(new LambdaQueryWrapper<SpaceImage>()
                    .eq(SpaceImage::getSpaceId, space.getId()));
        }
        privateSpaceMapper.deleteById(space.getId());
    }

    private PrivateSpace requireSpace(Long id) {
        PrivateSpace space = privateSpaceMapper.selectById(id);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "私有空间不存在");
        }
        return space;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}