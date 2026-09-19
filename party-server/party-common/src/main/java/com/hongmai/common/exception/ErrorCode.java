package com.hongmai.common.exception;

import lombok.Getter;

/**
 * 错误码。分段规则（plan §5.1）：
 * 0 成功 / 1000-1999 参数与校验 / 2000-2999 认证与登录态 /
 * 3000-3999 权限与数据范围 / 4000-4999 业务规则 / 5000-5999 系统与外部依赖。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success", "成功"),

    PARAM_INVALID(1000, "param.invalid", "参数校验不通过"),

    TOKEN_INVALID(2001, "auth.token.invalid", "登录态已失效，请重新登录"),
    WECHAT_CODE_INVALID(2002, "auth.wechat.code.invalid", "微信登录凭证无效或已过期"),
    WECHAT_API_ERROR(2003, "auth.wechat.api.error", "微信接口调用失败"),

    FORBIDDEN(3001, "auth.forbidden", "无权访问该数据"),
    ENTRY_NOT_ALLOWED(3002, "auth.entry.not.allowed", "当前账号无权进入该入口"),

    MOBILE_OCCUPIED(4001, "user.mobile.occupied", "该手机号已被其他账号实名占用"),
    EXAM_DUPLICATE_SUBMIT(4002, "exam.duplicate.submit", "该试卷已提交，不能重复提交"),
    HEARTBEAT_RATE_LIMITED(4003, "learn.heartbeat.rate.limited", "上报过于频繁"),
    AUDIT_ILLEGAL_TRANSITION(4004, "audit.illegal.transition", "当前状态不允许该操作"),
    ADMIN_APPLY_EXISTS(4005, "admin.apply.exists", "已存在待审核或已通过的管理员申请"),
    RESOURCE_NOT_FOUND(4006, "resource.not.found", "学习资源不存在或已下架"),
    ORG_NOT_FOUND(4007, "org.not.found", "组织不存在或已停用"),
    USER_NOT_FOUND(4008, "user.not.found", "用户不存在"),
    PAPER_NOT_FOUND(4009, "exam.paper.not.found", "试卷不存在或已停用"),
    CREDIT_TARGET_INVALID(4010, "credit.target.invalid", "目标学时必须大于 0"),

    SYSTEM_ERROR(5000, "system.error", "系统繁忙，请稍后重试");

    private final int code;
    private final String messageKey;
    private final String message;

    ErrorCode(int code, String messageKey, String message) {
        this.code = code;
        this.messageKey = messageKey;
        this.message = message;
    }
}
