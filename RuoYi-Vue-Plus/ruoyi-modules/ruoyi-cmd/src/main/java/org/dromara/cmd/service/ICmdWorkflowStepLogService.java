package org.dromara.cmd.service;

import org.dromara.cmd.domain.CmdWorkflowStepLog;
import org.dromara.cmd.domain.vo.CmdWorkflowStepLogVo;

import java.util.List;

/**
 * 工作流步骤执行日志 服务层接口
 * <p>
 * 以客户 One ID 为主追溯键，记录「提交 → 系统自动检查 → 人工决策 → 引擎推进」的每一步，
 * 供前端「流程跟踪 / 审批详情」顺序展示，并支撑审计中心的跨页面关联查询。
 *
 * @author Essilor CMD POC
 */
public interface ICmdWorkflowStepLogService {

    /**
     * 写入一条步骤日志（自动计算同一 one_id 内的步骤序号）
     *
     * @param step 步骤日志（one_id / task_no / step_type 必填）
     */
    void recordStep(CmdWorkflowStepLog step);

    /**
     * 按客户 One ID 查询其全部工作流步骤（按步骤序号升序）
     *
     * @param oneId 客户主数据标识
     * @return 步骤列表
     */
    List<CmdWorkflowStepLogVo> listByOneId(String oneId);

    /**
     * 按审批任务编号查询步骤（一个客户的一次申请对应一个 task_no）
     *
     * @param taskNo 审批任务编号
     * @return 步骤列表
     */
    List<CmdWorkflowStepLogVo> listByTaskNo(String taskNo);
}
