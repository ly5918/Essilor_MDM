package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.vo.CmdDashboardVo;
import org.dromara.cmd.service.ICmdDashboardService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台统计 控制层
 * <p>
 * 对应页面：各角色工作台 dash 顶部指标卡。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/dashboard")
public class CmdDashboardController extends BaseController {

    private final ICmdDashboardService dashboardService;

    /**
     * 查询工作台统计（按角色 Scope 动态返回）
     *
     * @param buScope BU 范围（为空表示 GC 全局视图）
     * @return 工作台统计
     */
    @GetMapping("/stats")
    public R<CmdDashboardVo> stats(@RequestParam(required = false) String buScope) {
        return R.ok(dashboardService.selectDashboard(buScope, LoginHelper.getUserId()));
    }
}
