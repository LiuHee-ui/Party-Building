package com.hongmai.org.controller;

import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageResult;
import com.hongmai.common.web.R;
import com.hongmai.org.dto.RosterQuery;
import com.hongmai.org.service.OrgService;
import com.hongmai.org.service.RosterService;
import com.hongmai.org.vo.EthnicityStatVO;
import com.hongmai.org.vo.OrgBriefVO;
import com.hongmai.org.vo.RosterItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端台账接口。操作员身份取自 LoginContext，数据范围由 OrgService 解析。 */
@RestController
@RequestMapping("/roster")
@RequiredArgsConstructor
public class RosterController {

    private final RosterService rosterService;
    private final OrgService orgService;

    /** 党员台账分页。支持姓名检索、民族筛选、身份类型筛选。 */
    @GetMapping("/page")
    public R<PageResult<RosterItemVO>> page(@Valid RosterQuery query) {
        return R.ok(rosterService.pageRoster(LoginContext.currentUserId(), query));
    }

    /** 少数民族党员统计。orgId 不传时取操作员可见范围。 */
    @GetMapping("/ethnicity")
    public R<List<EthnicityStatVO>> ethnicity(@RequestParam(required = false) Long orgId) {
        return R.ok(rosterService.statEthnicity(LoginContext.currentUserId(),
                orgId == null ? 0L : orgId));
    }

    /** 可见组织列表，供前端组织选择器使用。 */
    @GetMapping("/orgs")
    public R<List<OrgBriefVO>> orgs() {
        long operatorId = LoginContext.currentUserId();
        return R.ok(orgService.listVisible(operatorId).stream()
                .map(org -> new OrgBriefVO(org.getId(), org.getName(), org.getLevel(), org.getPath()))
                .toList());
    }
}
