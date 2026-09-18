package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.CmdGovernanceTaskBo;
import org.dromara.cmd.domain.vo.CmdGovernanceTaskVo;
import org.dromara.cmd.service.ICmdGovernanceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 治理任务 控制层
 * <p>
 * 对应页面：治理任务 gov（4 张指标卡与下钻明细）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/governance")
public class CmdGovernanceController extends BaseController {

    private final ICmdGovernanceService governanceService;

    /**
     * 分页查询治理任务列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 治理任务分页结果
     */
    @GetMapping("/list")
    public R<PageResult<CmdGovernanceTaskVo>> list(CmdGovernanceTaskBo bo, PageQuery pageQuery) {
        return R.ok(governanceService.selectPageTaskList(bo, pageQuery));
    }

    /**
     * 查询治理任务详情
     *
     * @param id 主键
     * @return 治理任务详情
     */
    @GetMapping("/{id}")
    public R<CmdGovernanceTaskVo> getInfo(@PathVariable Long id) {
        return R.ok(governanceService.selectTaskById(id));
    }

    /**
     * 认领治理任务
     *
     * @param id 主键
     * @return 操作结果
     */
    @Log(title = "治理任务", businessType = BusinessType.UPDATE)
    @PostMapping("/claim/{id}")
    public R<Void> claim(@PathVariable Long id) {
        return toAjax(governanceService.claimTask(id, LoginHelper.getUserId()));
    }

    /**
     * 处理治理任务（关联已有 / 确认新建 / 排除 / 合并 / 退回）
     *
     * @param id         主键
     * @param resolution 处理结论
     * @param opinion    处理意见
     * @return 操作结果
     */
    @Log(title = "治理任务", businessType = BusinessType.UPDATE)
    @PostMapping("/resolve/{id}")
    public R<Void> resolve(@PathVariable Long id,
                           @RequestParam String resolution,
                           @RequestParam(required = false) String opinion) {
        return toAjax(governanceService.resolveTask(id, resolution, opinion));
    }

    /**
     * 查询治理指标卡统计（Suspect / Review / New / Cross-BU / Total）
     *
     * @param bo 统计范围
     * @return 指标统计
     */
    @GetMapping("/stats")
    public R<Map<String, Long>> stats(CmdGovernanceTaskBo bo) {
        return R.ok(governanceService.selectTaskStats(bo));
    }
}
