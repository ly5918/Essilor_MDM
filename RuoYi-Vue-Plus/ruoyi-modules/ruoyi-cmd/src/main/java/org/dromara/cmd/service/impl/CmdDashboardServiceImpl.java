package org.dromara.cmd.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdGovernanceTask;
import org.dromara.cmd.domain.CmdHierarchyNode;
import org.dromara.cmd.domain.vo.CmdDashboardVo;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdGovernanceTaskMapper;
import org.dromara.cmd.mapper.CmdHierarchyNodeMapper;
import org.dromara.cmd.service.ICmdDashboardService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 工作台统计 服务层实现
 * <p>
 * 统计口径：全部由业务表实时聚合，不维护冗余统计表（避免双写不一致）。
 * 数据权限：按 buScope 过滤；为空表示 GC 全局视图。
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdDashboardServiceImpl implements ICmdDashboardService {

    private final CmdCustomerMapper customerMapper;
    private final CmdApprovalTaskMapper taskMapper;
    private final CmdGovernanceTaskMapper governanceTaskMapper;
    private final CmdHierarchyNodeMapper hierarchyNodeMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdDashboardVo selectDashboard(String buScope, Long userId) {
        CmdDashboardVo vo = new CmdDashboardVo();

        // 客户统计
        vo.setCustomerTotal(countCustomer(buScope, null));
        vo.setCustomerActive(countCustomer(buScope, CmdConstants.CUST_STATUS_ACTIVE));
        vo.setCustomerPending(countCustomer(buScope, CmdConstants.CUST_STATUS_PENDING));
        vo.setCustomerInactive(countCustomer(buScope, CmdConstants.CUST_STATUS_INACTIVE));

        // 待办统计
        if (userId != null) {
            vo.setMyTodoCount(taskMapper.lambda()
                .eq(CmdApprovalTask::getAssigneeId, userId)
                .eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING)
                .count());
            vo.setMyDoneCount(taskMapper.lambda()
                .eq(CmdApprovalTask::getAssigneeId, userId)
                .eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_COMPLETED)
                .count());
        }
        vo.setReturnedCount(taskMapper.lambda()
            .eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_RETURNED)
            .count());
        vo.setSlaOverdueCount(taskMapper.lambda()
            .eq(CmdApprovalTask::getSlaState, CmdConstants.SLA_OVERDUE)
            .count());

        // 待办节点分布：回答「申请卡在哪一步」，而不仅是「还有几条待办」（测试报告 BUG-10）
        vo.setPendingByNode(countPendingByNode());

        // 治理指标
        vo.setGovSuspectCount(countGovernance(CmdConstants.GOV_TYPE_SUSPECT));
        vo.setGovReviewCount(countGovernance(CmdConstants.GOV_TYPE_REVIEW));
        vo.setGovNewCount(countGovernance(CmdConstants.GOV_TYPE_NEW));
        vo.setGovCrossBuCount(countGovernance(CmdConstants.GOV_TYPE_CROSS_BU));

        // 层级节点
        vo.setHierarchyNodeCount(hierarchyNodeMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CmdHierarchyNode>()
                .eq(StringUtils.isNotBlank(buScope), CmdHierarchyNode::getBuScope, buScope)));

        return vo;
    }

    /**
     * 统计客户数量
     *
     * @param buScope BU 范围
     * @param status  客户状态（为空表示全部）
     * @return 数量
     */
    private Long countCustomer(String buScope, String status) {
        return customerMapper.lambda()
            .eq(StringUtils.isNotBlank(buScope), CmdCustomer::getBuScope, buScope)
            .eq(StringUtils.isNotBlank(status), CmdCustomer::getStatus, status)
            .count();
    }

    /**
     * 统计未处理待办按「当前节点」的分布（条数倒序）
     * <p>
     * 节点名取 cmd_approval_task.current_node_name，为空时归入「待分配」，
     * 保证用户在任何数据状态下都能看到一个可解释的归属（测试报告 BUG-10）。
     *
     * @return 节点名到条数的映射
     */
    private java.util.Map<String, Long> countPendingByNode() {
        // 用「PENDING OR RETURNED」而非 in(...)：与本项目 MP 封装的 in 行为保持一致的口径，
        // 避免拼出空条件集导致节点分布恒为空（与「全部待办」页签同一口径）。
        java.util.List<CmdApprovalTask> pending = taskMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CmdApprovalTask>()
                .and(w -> w.eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING)
                    .or().eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_RETURNED)));
        java.util.Map<String, Long> grouped = new java.util.HashMap<>();
        for (CmdApprovalTask task : pending) {
            String node = StringUtils.isNotBlank(task.getCurrentNodeName()) ? task.getCurrentNodeName() : "待分配";
            grouped.merge(node, 1L, Long::sum);
        }
        // 条数倒序，保证页面展示的前几项就是主要积压节点
        return grouped.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey,
                java.util.Map.Entry::getValue,
                (a, b) -> a,
                java.util.LinkedHashMap::new));
    }

    /**
     * 统计未闭环治理任务数量
     *
     * @param taskType 任务类型
     * @return 数量
     */
    private Long countGovernance(String taskType) {
        return governanceTaskMapper.lambda()
            .eq(CmdGovernanceTask::getTaskType, taskType)
            .ne(CmdGovernanceTask::getStatus, "RESOLVED")
            .ne(CmdGovernanceTask::getStatus, "CLOSED")
            .count();
    }
}
