package com.example.cloudpicture.image.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ImageReviewRequest {

    @NotNull(message = "图片 id 不能为空")
    private Long id;

    /** 1 通过 / 2 拒绝 */
    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "审核状态只能为 1 或 2")
    @Max(value = 2, message = "审核状态只能为 1 或 2")
    private Integer reviewStatus;

    @Size(max = 512, message = "审核信息长度不能超过 512")
    private String reviewMessage;
}