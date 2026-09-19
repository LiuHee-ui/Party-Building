package com.hongmai.org.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织。
 * 不变式：path 必须以 /{id}/ 结尾；组织移动时须整棵子树重写 path。
 */
@Data
@TableName("t_org")
public class Org {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 1 党委 / 2 党总支 / 3 党支部 */
    private Integer level;

    /** 上级组织，根为 0 */
    private Long parentId;

    /** 祖先路径 /1/12/35/，数据范围查询走这个字段的前缀匹配 */
    private String path;

    private String regionCode;

    /** 1 正常 / 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isEnabled() {
        return status != null && status == 1;
    }

    public boolean isRoot() {
        return parentId == null || parentId == 0L;
    }
}
