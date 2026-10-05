package com.example.cloudpicture.ai.dto.response;

import java.util.List;
import lombok.Data;

/**
 * AI 图片审核结论：verdict 三态（PASS 正常 / REVIEW 拿不准 / BLOCK 违规），
 * confidence 为模型自报确信度 0-100，labels 为命中的违规类别
 */
@Data
public class ImageModerationVO {

    private String verdict;

    private Integer confidence;

    private List<String> labels;

    public ImageModerationVO(String verdict, Integer confidence, List<String> labels) {
        this.verdict = verdict;
        this.confidence = confidence;
        this.labels = labels;
    }
}
