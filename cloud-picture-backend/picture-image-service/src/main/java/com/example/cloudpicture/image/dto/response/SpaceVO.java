package com.example.cloudpicture.image.dto.response;

import com.example.cloudpicture.image.entity.PrivateSpace;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SpaceVO {

    private Long id;
    private String name;
    /** 空间内未删除图片数 */
    private Long imageCount;
    /** 空间内图片总大小（字节） */
    private Long totalSize;
    private LocalDateTime createTime;

    public static SpaceVO from(PrivateSpace space, long imageCount, long totalSize) {
        if (space == null) {
            return null;
        }
        SpaceVO vo = new SpaceVO();
        vo.id = space.getId();
        vo.name = space.getName();
        vo.imageCount = imageCount;
        vo.totalSize = totalSize;
        vo.createTime = space.getCreateTime();
        return vo;
    }
}