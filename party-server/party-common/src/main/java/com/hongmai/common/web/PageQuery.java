package com.hongmai.common.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页入参。pageNo 从 1 起，pageSize 上限 100（N3 性能约束）。
 */
@Data
public class PageQuery {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    @Min(value = 1, message = "pageNo 从 1 起")
    private int pageNo = 1;

    @Min(value = 1, message = "pageSize 至少为 1")
    @Max(value = MAX_PAGE_SIZE, message = "pageSize 不得超过 100")
    private int pageSize = DEFAULT_PAGE_SIZE;

    public int getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
