package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdFlowInstanceBo;
import org.dromara.cmd.domain.vo.CmdFlowInstanceVo;
import org.dromara.cmd.domain.vo.CmdFlowTraceVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 流程跟踪 服务接口
 * <p>
 * 对应页面：治理与审批 approval 详情「流程跟踪」弹窗 + 流程中心「流程实例记录」列表；
 * 将总设计泳道图（7 阶段 × 6 泳道）与 Warm-Flow 流程实例进度合并呈现。
 *
 * @author Essilor CMD POC
 */
public interface ICmdFlowTraceService {

    /**
     * 按任务编号查询流程跟踪视图
     *
     * @param taskNo 任务编号（cmd_approval_task.task_no）
     * @return 流程跟踪视图（步骤状态 + 上下文变量 + 轨迹）
     */
    CmdFlowTraceVo selectTraceByTaskNo(String taskNo);

    /**
     * 分页查询流程实例记录（每一次执行过的工作流都留一条记录，可查看 / 用 Graph 回看泳道图）
     *
     * @param bo        查询条件（关键字 / 状态 / 业务类型 / 运行状态）
     * @param pageQuery 分页参数
     * @return 流程实例记录分页结果
     */
    PageResult<CmdFlowInstanceVo> listInstances(CmdFlowInstanceBo bo, PageQuery pageQuery);
}
