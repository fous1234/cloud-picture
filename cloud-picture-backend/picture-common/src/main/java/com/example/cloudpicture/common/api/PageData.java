package com.example.cloudpicture.common.api;

import java.util.List;
import lombok.Data;

/**
 * 分页数据，统一放在响应体 data 中，字段固定为 records/total/current/size
 */
@Data
public class PageData<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageData<T> of(List<T> records, long total, long current, long size) {
        PageData<T> pageData = new PageData<>();
        pageData.records = records;
        pageData.total = total;
        pageData.current = current;
        pageData.size = size;
        return pageData;
    }
}