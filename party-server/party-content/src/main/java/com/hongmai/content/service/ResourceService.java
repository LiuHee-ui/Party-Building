package com.hongmai.content.service;

import com.hongmai.common.enums.ResourceStatus;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import com.hongmai.content.dto.ResourceFilter;
import com.hongmai.content.dto.ResourceSaveCmd;
import com.hongmai.content.vo.ResourceCardVO;
import com.hongmai.content.vo.ResourceDetailVO;

public interface ResourceService {

    /** 用户端分页检索：只返回已上架资源。 */
    PageResult<ResourceCardVO> pageForUser(long userId, PageQuery query, ResourceFilter filter);

    /**
     * 用户端资源详情。
     * VR 类返回 experienceHint 且 contentUrl 恒为空（spec F5）。
     *
     * @throws com.hongmai.common.exception.BizException 4006 资源不存在或未上架
     */
    ResourceDetailVO detailForUser(long userId, long resourceId);

    /**
     * 新增或编辑资源。校验通过后落库为「待审核」并经 AuditService 落 SUBMIT 流水。
     *
     * @throws com.hongmai.common.exception.BizException 1000 字段校验不通过
     */
    long saveResource(long operatorId, ResourceSaveCmd cmd);

    /**
     * 状态迁移。仅允许 待审核→已上架／草稿、已上架→已下架，每次迁移落审核流水。
     *
     * @throws com.hongmai.common.exception.BizException 4004 状态不允许该迁移
     */
    void changeStatus(long operatorId, long resourceId, ResourceStatus target, String remark);
}
