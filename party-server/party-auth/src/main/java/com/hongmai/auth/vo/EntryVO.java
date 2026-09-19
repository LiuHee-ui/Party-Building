package com.hongmai.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 可进入的入口。 */
@Data
@AllArgsConstructor
public class EntryVO {

    /** USER / ADMIN */
    private String code;

    private String name;
}
