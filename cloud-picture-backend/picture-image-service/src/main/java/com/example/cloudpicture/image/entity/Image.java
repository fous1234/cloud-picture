package com.example.cloudpicture.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@TableName("t_image")
public class Image {

    public static final int REVIEW_PENDING = 0;
    public static final int REVIEW_PASSED = 1;
    public static final int REVIEW_REJECTED = 2;

    public static final String SOURCE_LOCAL = "LOCAL";
    public static final String SOURCE_PEXELS = "PEXELS";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** COS 对象 Key，不存访问链接，预览地址由后端生成短期签名 URL */
    private String cosKey;

    private String name;

    private String introduction;

    private String category;

    /** 标签，逗号分隔，与 t_image_tag 字典保持一致 */
    private String tags;

    private Long picSize;

    private Integer picWidth;

    private Integer picHeight;

    private String picFormat;

    private Long ownerId;

    private Integer reviewStatus;

    private String reviewMessage;

    private Long reviewerId;

    private LocalDateTime reviewTime;

    /** 图片来源：LOCAL 本地上传 / PEXELS 图源导入 */
    private String source;

    /** 来源平台图片 ID（Pexels 图片 ID），来源去重键 */
    private String sourceId;

    /** 来源图片详情页地址 */
    private String sourcePageUrl;

    /** 摄影师名称 */
    private String photographer;

    /** 摄影师主页地址 */
    private String photographerUrl;

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

    /** 去空白、去重后的标签串，作为字典键与存储值 */
    public static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                normalized.add(tag.trim());
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }
}