package com.example.cloudpicture.ai.dto.response;

import java.util.List;
import lombok.Data;

/**
 * AI 图片元数据建议：仅作为建议返回，不含图片 ID 与任何持久化字段
 */
@Data
public class ImageMetadataVO {

    /** 图片内容的简短中文描述，去除首尾空白后最多 512 个字符 */
    private String introduction;

    /** 自由标签，已去空、去重、丢弃超长项，最多 10 个且总长不超过 512 个字符 */
    private List<String> tags;

    public ImageMetadataVO(String introduction, List<String> tags) {
        this.introduction = introduction;
        this.tags = tags;
    }
}