package com.example.cloudpicture.image.dto.response;

import java.util.List;
import lombok.Data;

/**
 * AI 审核结论（与 ai-service 的 ImageModerationVO 字段对应）
 */
@Data
public class AiModerationVO {

    private String verdict;

    private Integer confidence;

    private List<String> labels;
}
