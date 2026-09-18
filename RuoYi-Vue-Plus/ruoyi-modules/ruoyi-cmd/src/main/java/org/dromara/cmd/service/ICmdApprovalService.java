package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.ApprovalActionBo;
import org.dromara.cmd.domain.bo.CmdApprovalTaskBo;
import org.dromara.cmd.domain.vo.CmdApprovalActionVo;
import org.dromara.cmd.domain.vo.CmdApprovalTaskVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;
import java.util.Map;

/**
 * 统一待办 / 审批 服务层接口
 * <p>
 * 对应页面：治理与审批 approval（我的队列 / 我已处理 / 升级与退回 三个 Tab）、工作台 dash 待办统计。
 * 说明：审批动作与 Warm-Flow 的对接点在本层；POC 阶段流程实例可为空，
 * 后续接入 Warm-Flow 时只需在 doAction 中补充 InsService 调用即可，业务表结构不变。
 *
 * @author Essilor CMD POC
 */
public interface ICmdApprovalService {

    /**
     * 分页查询待办任务列表（对应页面队列表 + 筛选行）
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 待办分页结果
     */
    PageResult<CmdApprovalTaskVo> selectPageTaskList(CmdApprovalTaskBo bo, PageQuery pageQuery);

    /**
     * 查询待办任务详情
     *
     * @param id 主键
     * @return 待办详情
     */
    CmdApprovalTaskVo selectTaskById(Long id);

    /**
     * 查询待办的审批轨迹
     *
     * @param taskId 待办 ID
     * @return 轨迹列表
     */
    List<CmdApprovalActionVo> selectActionList(Long taskId);

    /**
     * 执行审批动作（批准 / 拒绝 / 退回 / 升级 / 转办 / 认领 ...）
     *
     * @param bo 动作入参
     * @return 影响行数
     */
    int doAction(ApprovalActionBo bo);

    /**
     * 查询当前用户的待办统计（对应工作台顶部指标）
     *
     * @param userId 用户 ID
     * @return 统计结果（key: myTodo / myDone / returned / slaOverdue）
     */
    Map<String, Long> selectTaskStats(Long userId);
}
