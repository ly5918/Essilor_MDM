package org.dromara.cmd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.CmdChangeRequest;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdHierarchyRelation;
import org.dromara.cmd.domain.CmdImportJob;
import org.dromara.cmd.domain.IntRun;
import org.dromara.cmd.mapper.AuditEventMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdChangeRequestMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdFlowEngineMapper;
import org.dromara.cmd.mapper.CmdHierarchyRelationMapper;
import org.dromara.cmd.mapper.CmdImportJobMapper;
import org.dromara.cmd.mapper.CmdNavMapper;
import org.dromara.cmd.mapper.IntRunMapper;
import org.dromara.cmd.service.ICmdNavService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 侧边导航统计 服务层实现
 * <p>
 * 设计原则：角标数字 = 点进去以后页面里的数字，口径完全对齐，避免「菜单说有 7 条、
 * 页面只有 3 条」这类自相矛盾。所有数字均为业务表实时聚合（COUNT），不落统计表。
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdNavServiceImpl implements ICmdNavService {

    /** 批量导入「待处置」状态：待复核 / 进行中 / 部分成功（部分成功仍需人工分流） */
    private static final List<String> IMPORT_PENDING_STATUS = List.of("WAIT_REVIEW", "RUNNING", "PARTIAL_SUCCESS");

    /** 集成运行「需关注」状态：失败 / 重试中 */
    private static final List<String> INT_RUN_ABNORMAL_STATUS = List.of("FAILED", "RETRYING");

    /** 审计事件结果：失败（需 Auditor 关注） */
    private static final String AUDIT_RESULT_FAILED = "FAILED";

    private final CmdApprovalTaskMapper approvalTaskMapper;
    private final CmdCustomerMapper customerMapper;
    private final CmdHierarchyRelationMapper hierarchyRelationMapper;
    private final CmdImportJobMapper importJobMapper;
    private final CmdChangeRequestMapper changeRequestMapper;
    private final CmdNavMapper navMapper;
    private final CmdFlowEngineMapper flowEngineMapper;
    private final AuditEventMapper auditEventMapper;
    private final IntRunMapper intRunMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Long> selectMenuBadges(String role) {
        String roleKey = StringUtils.isBlank(role) ? "bu" : role.trim().toLowerCase();
        // 审批队列维度：BU 角色看 BU 队列，GC 决策者看 GC 队列（与「治理与审批」页面 KPI / 列表同口径）
        String apprScope = "gc".equals(roleKey) ? CmdConstants.SCOPE_GC : CmdConstants.SCOPE_BU;

        // 1) 治理与审批：待处理 + 退回待补充
        long approval = countApproval(apprScope, CmdConstants.APPR_STATUS_PENDING)
            + countApproval(apprScope, CmdConstants.APPR_STATUS_RETURNED);

        // 2) 客户主档：待审批 + 退回补充的客户
        long customers = nz(customerMapper.selectCount(new LambdaQueryWrapper<CmdCustomer>()
            .in(CmdCustomer::getStatus, CmdConstants.CUST_STATUS_PENDING, CmdConstants.CUST_STATUS_RETURNED)));

        // 3) 客户层级：待归位主数据 + 待审批的层级关系申请
        long hier = nz(navMapper.countUnassignedCustomer()) + nz(hierarchyRelationMapper.selectCount(
            new LambdaQueryWrapper<CmdHierarchyRelation>()
                .eq(CmdHierarchyRelation::getStatus, CmdConstants.HIER_REL_STATUS_PENDING)));

        // 4) 批量治理：待复核 / 进行中 / 部分成功的导入任务
        long batch = nz(importJobMapper.selectCount(new LambdaQueryWrapper<CmdImportJob>()
            .in(CmdImportJob::getJobStatus, IMPORT_PENDING_STATUS)));

        // 5) 变更与停用：待审批 + 退回补充的变更申请
        long change = nz(changeRequestMapper.selectCount(new LambdaQueryWrapper<CmdChangeRequest>()
            .in(CmdChangeRequest::getStatus, CmdConstants.CHG_STATUS_PENDING, CmdConstants.CHG_STATUS_RETURNED)));

        // 6) 流程中心：运行中实例 / 今日已结束实例
        long flowWorkitem = nz(flowEngineMapper.countUnfinishedInstance());
        long flowDone = nz(flowEngineMapper.countFinishedTodayInstance());

        // 7) 关注类：审计失败或高风险事件、失败或重试中的集成运行
        long audit = nz(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
            .and(w -> w.eq(AuditEvent::getResult, AUDIT_RESULT_FAILED)
                .or().eq(AuditEvent::getRiskLevel, CmdConstants.RISK_HIGH))));
        long integration = nz(intRunMapper.selectCount(new LambdaQueryWrapper<IntRun>()
            .in(IntRun::getRunStatus, INT_RUN_ABNORMAL_STATUS)));

        // 治理类角标只对「Data Steward（BU / GC Scope）」有意义：
        // 治理与审批 / 批量治理是 Steward 的待办队列，Business User、Platform Admin、Auditor 都不处理审批，
        // 若沿用同一套数字会出现「Auditor 只读角色却显示 10 条待审批」的角色越界展示（测试报告 BUG-11）。
        boolean steward = "bu".equals(roleKey) || "gc".equals(roleKey);
        if (!steward) {
            approval = 0L;
            batch = 0L;
        }
        // Auditor 为独立只读角色（总设计「Auditor 独立只读」）：不承载任何待办，
        // 只保留「关注类」统计（审计失败 / 高风险事件、集成异常）。
        boolean readOnly = "audit".equals(roleKey);
        if (readOnly) {
            customers = 0L;
            hier = 0L;
            change = 0L;
            flowWorkitem = 0L;
        }

        Map<String, Long> badges = new LinkedHashMap<>();
        // 工作台 = 待办合计（总入口，让 Steward 一眼看到手上还剩多少事；不含审计/集成等「关注类」）
        badges.put("dash", approval + customers + hier + batch + change + flowWorkitem);
        badges.put("approval", approval);
        badges.put("customers", customers);
        badges.put("hier", hier);
        badges.put("batch", batch);
        badges.put("change", change);
        badges.put("flowWorkitem", flowWorkitem);
        badges.put("flowDone", flowDone);
        badges.put("audit", audit);
        badges.put("integration", integration);
        // 平台管理是配置态能力页（元数据 / 工作流定义 / 权限矩阵），没有「待办」语义，恒为 0
        badges.put("admin", 0L);
        return badges;
    }

    /**
     * 统计审批任务数（Scope + 状态）
     *
     * @param scope  审批 Scope（BU / GC）
     * @param status 任务状态
     * @return 任务数
     */
    private long countApproval(String scope, String status) {
        LambdaQueryWrapper<CmdApprovalTask> lqw = new LambdaQueryWrapper<CmdApprovalTask>()
            .eq(CmdApprovalTask::getStatus, status);
        if (StringUtils.isNotBlank(scope)) {
            lqw.eq(CmdApprovalTask::getScope, scope);
        }
        return nz(approvalTaskMapper.selectCount(lqw));
    }

    /**
     * COUNT 结果空值兜底
     *
     * @param value 计数
     * @return 非空计数
     */
    private long nz(Long value) {
        return value == null ? 0L : value;
    }
}
