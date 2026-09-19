package com.hongmai.exam.vo;

import lombok.Data;

/** 榜单条目。用户端只拿得到本组织榜，管理端拿跨组织榜。 */
@Data
public class RankingItemVO {

    private Long userId;
    private Long orgId;
    private String orgName;

    /** 展示名。列表里只给姓名，不给手机号 */
    private String displayName;

    private Double score;

    private Integer rankNo;

    /** 是否当前登录用户本人，客户端高亮用 */
    private Boolean self;
}
