package com.example.cloudpicture.image.dto.response;

import com.example.cloudpicture.image.entity.Image;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ImageVO {

    private Long id;
    /** 短期签名预览地址，由服务端按 COS Key 生成 */
    private String url;
    /** 带 COS 图片处理参数的短期签名缩略图地址 */
    private String thumbnailUrl;
    private String name;
    private String introduction;
    private String category;
    private List<String> tags;
    private Long picSize;
    private Integer picWidth;
    private Integer picHeight;
    private String picFormat;
    private Long ownerId;
    private Integer reviewStatus;
    private String reviewMessage;
    private LocalDateTime createTime;

    /** 上传者信息，仅管理员列表填充 */
    private UserBriefVO owner;

    public static ImageVO from(Image image, String url, String thumbnailUrl) {
        if (image == null) {
            return null;
        }
        ImageVO vo = new ImageVO();
        vo.id = image.getId();
        vo.url = url;
        vo.thumbnailUrl = thumbnailUrl;
        vo.name = image.getName();
        vo.introduction = image.getIntroduction();
        vo.category = image.getCategory();
        vo.tags = image.tagList();
        vo.picSize = image.getPicSize();
        vo.picWidth = image.getPicWidth();
        vo.picHeight = image.getPicHeight();
        vo.picFormat = image.getPicFormat();
        vo.ownerId = image.getOwnerId();
        vo.reviewStatus = image.getReviewStatus();
        vo.reviewMessage = image.getReviewMessage();
        vo.createTime = image.getCreateTime();
        return vo;
    }
}