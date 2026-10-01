package com.example.cloudpicture.image.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ImageImportRequest {

    @NotBlank(message = "关键词不能为空")
    @Size(max = 64, message = "关键词长度不能超过 64")
    private String keyword;

    /** 导入张数，缺省 5，单次最多 10 */
    @Min(value = 1, message = "导入张数最少为 1")
    @Max(value = 10, message = "导入张数最多为 10")
    private Integer count = 5;

    @Size(max = 64, message = "分类长度不能超过 64")
    private String category;

    /** 不传时使用 [keyword] */
    private List<String> tags;
}
