package com.hongmai.common.web;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/** 分页出参。 */
@Data
public class PageResult<T> {

    private int pageNo;
    private int pageSize;
    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(int pageNo, int pageSize, long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.pageNo = pageNo;
        result.pageSize = pageSize;
        result.total = total;
        result.records = records == null ? Collections.emptyList() : records;
        return result;
    }

    public static <T> PageResult<T> empty(int pageNo, int pageSize) {
        return of(pageNo, pageSize, 0L, Collections.emptyList());
    }
}
