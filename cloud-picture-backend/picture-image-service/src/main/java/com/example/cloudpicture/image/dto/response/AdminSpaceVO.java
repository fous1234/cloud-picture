package com.example.cloudpicture.image.dto.response;

import com.example.cloudpicture.image.entity.PrivateSpace;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 管理端私有空间视图：只暴露空间元信息，不含 cosKey 与任何图片地址（管理员看不到空间内图片）
 */
@Data
public class AdminSpaceVO {

    private Long id;
    private String name;
    /** 空间内未删除图片数 */
    private Long imageCount;
    /** 空间内图片总大小（字节） */
    private Long totalSize;
    private LocalDateTime createTime;
    /** 所属用户基本信息，查询失败时为 null */
    private UserBriefVO owner;

    public static AdminSpaceVO from(PrivateSpace space, long imageCount, long totalSize) {
        if (space == null) {
            return null;
        }
        AdminSpaceVO vo = new AdminSpaceVO();
        vo.id = space.getId();
        vo.name = space.getName();
        vo.imageCount = imageCount;
        vo.totalSize = totalSize;
        vo.createTime = space.getCreateTime();
        return vo;
    }
}