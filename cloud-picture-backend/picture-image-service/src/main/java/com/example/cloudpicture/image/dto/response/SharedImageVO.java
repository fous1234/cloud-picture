package com.example.cloudpicture.image.dto.response;

import com.example.cloudpicture.image.entity.Image;
import java.util.List;
import lombok.Data;

/**
 * 分享页公开数据：只含分享页需要的字段，不含 ownerId、COS Key、token、审核信息等内部字段
 */
@Data
public class SharedImageVO {

    private Long id;
    /** 短期签名预览地址，每次请求重新生成 */
    private String url;
    private String name;
    private String introduction;
    private String category;
    private List<String> tags;
    private Long picSize;
    private Integer picWidth;
    private Integer picHeight;
    private String picFormat;
    /** 来源：LOCAL 本地上传 / PEXELS 导入 */
    private String source;
    /** 来源图片详情页地址 */
    private String sourcePageUrl;
    /** 摄影师名称，前端展示 "Photo by {photographer} on Pexels" */
    private String photographer;
    /** 摄影师主页地址 */
    private String photographerUrl;

    public static SharedImageVO from(Image image, String url) {
        SharedImageVO vo = new SharedImageVO();
        vo.id = image.getId();
        vo.url = url;
        vo.name = image.getName();
        vo.introduction = image.getIntroduction();
        vo.category = image.getCategory();
        vo.tags = image.tagList();
        vo.picSize = image.getPicSize();
        vo.picWidth = image.getPicWidth();
        vo.picHeight = image.getPicHeight();
        vo.picFormat = image.getPicFormat();
        vo.source = image.getSource();
        vo.sourcePageUrl = image.getSourcePageUrl();
        vo.photographer = image.getPhotographer();
        vo.photographerUrl = image.getPhotographerUrl();
        return vo;
    }
}