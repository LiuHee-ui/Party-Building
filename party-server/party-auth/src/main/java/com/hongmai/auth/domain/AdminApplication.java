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
import java.util.List;

/** 管理员注册申请。通过后向 t_user_role 插入管理类角色。 */
@Data
@TableName(value = "t_admin_application", autoResultMap = true)
public class AdminApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long orgId;

    private String realName;

    @TableField(value = "mobile_cipher", typeHandler = MobileCipherHandler.class)
    private String mobile;

    private String mobileTail;

    /** 身份证明图片对象键列表，JSON 数组 */
    private String proofUrlsJson;

    /** 0 待审核 / 1 通过 / 2 驳回 */
    private Integer status;

    private Long reviewerId;

    private LocalDateTime reviewedAt;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 申请可被审核的前提：仍处于待审核状态。 */
    public boolean isPending() {
        return status != null && status == 0;
    }

    public List<String> proofUrls() {
        return ProofUrlsCodec.decode(proofUrlsJson);
    }
}
