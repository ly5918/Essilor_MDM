package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作台统计视图对象（按角色与 Scope 动态返回）
 * <p>
 * 对应页面：各角色工作台 dash —— 顶部指标卡与待办统计。
 * 说明：统计口径全部来自业务表实时聚合，不维护冗余统计表，避免数据不一致。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdDashboardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户总数（当前 Scope 可见） */
    private Long customerTotal;

    /** 生效中客户数（status=active） */
    private Long customerActive;

    /** 待审批客户数（status=pending） */
    private Long customerPending;

    /** 逻辑停用客户数（status=inactive） */
    private Long customerInactive;

    /** 我的待办数（PENDING 且 assigneeId=当前用户） */
    private Long myTodoCount;

    /** 我已处理数 */
    private Long myDoneCount;

    /** 升级与退回数 */
    private Long returnedCount;

    /** SLA 超时数 */
    private Long slaOverdueCount;

    /** 治理-疑似重复 */
    private Long govSuspectCount;

    /** 治理-待复核 */
    private Long govReviewCount;

    /** 治理-新建确认 */
    private Long govNewCount;

    /** 治理-跨BU决策 */
    private Long govCrossBuCount;

    /** 层级节点数 */
    private Long hierarchyNodeCount;
}
