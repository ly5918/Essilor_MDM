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
