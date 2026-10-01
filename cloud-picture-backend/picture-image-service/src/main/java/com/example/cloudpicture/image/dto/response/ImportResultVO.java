package com.example.cloudpicture.image.dto.response;

import lombok.Data;

/** 图源导入结果：imported 新写入、skipped 已存在、failed 单张失败 */
@Data
public class ImportResultVO {

    private int imported;
    private int skipped;
    private int failed;

    public ImportResultVO(int imported, int skipped, int failed) {
        this.imported = imported;
        this.skipped = skipped;
        this.failed = failed;
    }
}
