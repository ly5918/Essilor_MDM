package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.ApprovalActionBo;
import org.dromara.cmd.domain.bo.CmdApprovalTaskBo;
import org.dromara.cmd.domain.vo.CmdApprovalActionVo;
import org.dromara.cmd.domain.vo.CmdApprovalDetailVo;
import org.dromara.cmd.domain.vo.CmdApprovalKpiVo;
import org.dromara.cmd.domain.vo.CmdApprovalTaskVo;
import org.dromara.cmd.domain.vo.CmdWorkflowStepLogVo;
import org.dromara.cmd.service.ICmdApprovalService;
import org.dromara.cmd.service.ICmdWorkflowStepLogService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统一待办 / 审批 控制层
 * <p>
 * 对应页面：治理与审批 approval（我的队列 / 我已处理 / 升级与退回）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/approval")
public class CmdApprovalController extends BaseController {

    private final ICmdApprovalService approvalService;
    private final ICmdWorkflowStepLogService stepLogService;

    /**
     * 分页查询待办任务列表（页面队列表 + 筛选行）
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 待办分页结果
     */
    @GetMapping("/list")
    public R<PageResult<CmdApprovalTaskVo>> list(CmdApprovalTaskBo bo, PageQuery pageQuery) {
        return R.ok(approvalService.selectPageTaskList(bo, pageQuery));
    }

    /**
     * 查询待办详情
     *
     * @param id 主键
     * @return 待办详情
     */
    @GetMapping("/{id}")
    public R<CmdApprovalTaskVo> getInfo(@PathVariable Long id) {
        return R.ok(approvalService.selectTaskById(id));
    }

    /**
     * 查询审批轨迹
     *
     * @param taskId 待办 ID
     * @return 轨迹列表
     */
    @GetMapping("/action/{taskId}")
    public R<List<CmdApprovalActionVo>> actionList(@PathVariable Long taskId) {
        return R.ok(approvalService.selectActionList(taskId));
    }

    /**
     * 执行审批动作（批准 / 拒绝 / 退回 / 升级 / 转办 / 认领）
     *
     * @param bo 动作入参
     * @return 操作结果
     */
    @Log(title = "治理与审批", businessType = BusinessType.UPDATE)
    @PostMapping("/action")
    public R<Void> doAction(@Validated @RequestBody ApprovalActionBo bo) {
        return toAjax(approvalService.doAction(bo));
    }

    /**
     * 查询当前用户的待办统计
     *
     * @return 统计结果
     */
    @GetMapping("/stats")
    public R<Map<String, Long>> stats() {
        return R.ok(approvalService.selectTaskStats(LoginHelper.getUserId()));
    }

    /**
     * 查询治理与审批页顶部 KPI 指标
     *
     * @param scope 审批范围（BU / GC）
     * @return 指标列表
     */
    @GetMapping("/kpi")
    public R<List<CmdApprovalKpiVo>> kpi(@RequestParam(required = false) String scope) {
        return R.ok(approvalService.selectKpi(scope, LoginHelper.getUserId()));
    }

    /**
     * 按任务编号查询处理详情（含决策标签与操作按钮）
     *
     * @param taskNo 任务编号
     * @return 处理详情
     */
    @GetMapping("/task/{taskNo}/detail")
    public R<CmdApprovalDetailVo> detail(@PathVariable String taskNo) {
        return R.ok(approvalService.selectDetailByTaskNo(taskNo));
    }

    /**
     * 查询工作流步骤执行日志（按客户 One ID 或任务编号）
     * <p>用于「流程跟踪 / 审批详情」展示每一步（提交 / 系统自动 / 人工决策）并贯穿客户标识。</p>
     *
     * @param oneId  客户主数据标识（优先）
     * @param taskNo 审批任务编号
     * @return 步骤列表
     */
    @GetMapping("/workflow-steps")
    public R<List<CmdWorkflowStepLogVo>> workflowSteps(@RequestParam(required = false) String oneId,
                                                      @RequestParam(required = false) String taskNo) {
        if (StringUtils.isNotBlank(taskNo)) {
            return R.ok(stepLogService.listByTaskNo(taskNo));
        }
        if (StringUtils.isNotBlank(oneId)) {
            return R.ok(stepLogService.listByOneId(oneId));
        }
        return R.ok(List.of());
    }
}
