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
     * 发起跨 BU 客户合并请求（总设计 MERGE 场景：发现候选 → BU 初审 → GC 决策 → 执行合并）
     * <p>创建 sceneCode=MERGE 的审批待办并启动客户合并审批流；
     * 批准后自动执行 Golden Record 更新、Legacy 交叉引用与审计。
     *
     * @param sourceOneId 合并源 One ID（被并入方）
     * @param targetOneId 合并目标 One ID（保留的 Golden Record）
     * @param reason      发起原因
     * @return 合并审批任务编号 AP-yyyyMMdd-####
     */
    @Log(title = "客户合并", businessType = BusinessType.INSERT)
    @PostMapping("/merge")
    public R<String> launchMerge(@RequestParam String sourceOneId,
                                 @RequestParam String targetOneId,
                                 @RequestParam(required = false) String reason) {
        // 注意：R.ok(String) 会命中 ok(String msg) 重载把值塞进 msg，必须用 R.data 让 taskNo 落在 data
        return R.data(governanceService.launchMerge(sourceOneId, targetOneId, reason));
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
