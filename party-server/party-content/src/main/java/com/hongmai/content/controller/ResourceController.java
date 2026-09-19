package com.hongmai.content.controller;

import com.hongmai.common.enums.ResourceStatus;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import com.hongmai.common.web.R;
import com.hongmai.content.dto.ResourceFilter;
import com.hongmai.content.dto.ResourceSaveCmd;
import com.hongmai.content.service.ResourceService;
import com.hongmai.content.vo.ResourceCardVO;
import com.hongmai.content.vo.ResourceDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学习资源接口。用户端只读已上架资源；管理端可编辑与变更状态。 */
@RestController
@RequestMapping("/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    /** 用户端资源列表。仅返回已上架资源。 */
    @GetMapping("/page")
    public R<PageResult<ResourceCardVO>> page(@Valid PageQuery query, ResourceFilter filter) {
        return R.ok(resourceService.pageForUser(LoginContext.currentUserId(), query, filter));
    }

    /** 用户端资源详情。VR 类返回 experienceHint，contentUrl 为空。 */
    @GetMapping("/{resourceId}")
    public R<ResourceDetailVO> detail(@PathVariable long resourceId) {
        return R.ok(resourceService.detailForUser(LoginContext.currentUserId(), resourceId));
    }

    /** 新增或编辑资源。保存后进入待审核。 */
    @PostMapping("/save")
    public R<Long> save(@RequestBody @Valid ResourceSaveCmd cmd) {
        return R.ok(resourceService.saveResource(LoginContext.currentUserId(), cmd));
    }

    /** 下架或退回草稿。上架须走审核流程。 */
    @PostMapping("/status")
    public R<Void> changeStatus(@RequestParam long resourceId,
                                @RequestParam int target,
                                @RequestParam(required = false) String remark) {
        resourceService.changeStatus(LoginContext.currentUserId(), resourceId,
                ResourceStatus.of(target), remark);
        return R.ok();
    }
}
