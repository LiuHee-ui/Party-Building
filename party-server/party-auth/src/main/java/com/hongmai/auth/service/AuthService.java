package com.hongmai.auth.service;

import com.hongmai.auth.dto.RealNameCmd;
import com.hongmai.auth.vo.LoginResult;
import com.hongmai.auth.vo.RealNameApplyVO;
import com.hongmai.common.enums.EntryType;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;

public interface AuthService {

    /**
     * 微信登录。入参为 wx.login 得到的临时 code。
     * 调微信 code2session 换 openid，查或建用户，计算可进入的入口，签发 Token 并写入 Redis。
     *
     * @throws com.hongmai.common.exception.BizException 2002 code 无效或已过期
     */
    LoginResult loginByWechat(String jsCode);

    /**
     * 直接用 openId 登录，跳过微信 code2session。
     *
     * **仅供开发与联调使用**：真实小程序拿不到稳定的 openId（它是微信下发的），
     * 没有这一步就无法在本地把请求链路跑通。
     * 对外暴露必须由 DevLoginController 的 @ConditionalOnProperty 严格把关，
     * 生产环境该 Bean 不会注册。业务代码不得调用本方法。
     */
    LoginResult loginByOpenId(String openId);

    /**
     * 提交实名信息。写入实名字段并置 auditStatus=待审核，
     * 同时经 AuditService 落一条 SUBMIT 流水进入组织管理员待审队列。
     *
     * @throws com.hongmai.common.exception.BizException 4001 手机号已被其他账号实名占用
     */
    void submitRealName(long userId, RealNameCmd cmd);

    /** 分页查看本组织及下辖组织的待审核实名申请。数据范围取 LoginContext。 */
    PageResult<RealNameApplyVO> pageRealNameApply(long operatorId, PageQuery query);

    /**
     * 审核实名申请。委托 AuditService 执行，因此流水写入路径唯一。
     * pass=false 时 remark 必填。
     */
    void reviewRealName(long operatorId, long targetUserId, boolean pass, String remark);

    /**
     * 进入指定入口时的鉴权。ADMIN 入口要求账号持有管理类角色之一。
     *
     * @throws com.hongmai.common.exception.BizException 3002 无该入口权限
     */
    void assertEntryAllowed(long userId, EntryType entry);
}
