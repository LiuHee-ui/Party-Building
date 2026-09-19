package com.hongmai.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hongmai.common.crypto.MobileCipherHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户与党员。
 *
 * 手机号三个字段的分工（这是本表最关键的设计）：
 *   mobile        —— 密文列，由 MobileCipherHandler 自动加解密，仅少数需要完整号码的场景使用
 *   mobileHash    —— 确定性哈希，唯一索引，用于「手机号是否已被占用」的唯一性校验
 *   mobileTail    —— 明文末四位，列表与详情直接展示，不解密、不进敏感访问日志
 *
 * 不能用密文做唯一性校验：GCM 随机 IV 导致同一手机号密文每次都不同，等值查询永远查不到。
 * autoResultMap = true 是 typeHandler 在 select 时生效的必要条件。
 */
@Data
@TableName(value = "t_user", autoResultMap = true)
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openId;

    private String realName;

    @TableField(value = "mobile_cipher", typeHandler = MobileCipherHandler.class)
    private String mobile;

    private String mobileHash;

    private String mobileTail;

    private Long orgId;

    /** 1 正式党员 / 2 预备党员 / 3 积极分子 / 4 群众 / 5 学生 */
    private Integer identityType;

    private String ethnicity;

    /** 1 = 本组织党组织书记或班子成员，决定默认目标学时 */
    private Integer leaderFlag;

    /** 0 待审核 / 1 通过 / 2 驳回 */
    private Integer auditStatus;

    private String auditRemark;

    private String avatarUrl;

    /** 1 正常 / 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 是否已通过实名审核——通过后才计入台账、参与排行与考核。 */
    public boolean isRealNameApproved() {
        return auditStatus != null && auditStatus == 1;
    }
}
