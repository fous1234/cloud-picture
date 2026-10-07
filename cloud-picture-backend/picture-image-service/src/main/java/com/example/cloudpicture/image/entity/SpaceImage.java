package com.example.cloudpicture.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 私有空间图片：与 t_image 物理分表，无审核、AI、分享、来源、摄影师字段
 */
@Data
@TableName("t_space_image")
public class SpaceImage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long spaceId;

    /** 上传用户 id：空间归属的冗余，所有权限校验单表完成 */
    private Long ownerId;

    private String cosKey;

    private String name;

    private String introduction;

    private String category;

    /** 标签，逗号分隔；纯字符串，不写入 t_image_tag 字典 */
    private String tags;

    private Long picSize;

    private Integer picWidth;

    private Integer picHeight;

    private String picFormat;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;

    public List<String> tagList() {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).filter(StringUtils::hasText).toList();
    }
}