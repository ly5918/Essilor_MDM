package org.dromara.cmd.service;

import org.dromara.cmd.domain.vo.CmdFlowSceneVO;
import org.dromara.cmd.domain.vo.CmdFlowTraceVo;

import java.util.List;
import java.util.Set;

/**
 * Warm-Flow 引擎联动服务
 * <p>
 * 职责（业务模块不自建引擎，一律调用 Warm-Flow 原生 API）：
 * <ul>
 *   <li>deploy：把业务场景（cmd_flow_scene.flow_code）对应的审批流部署为引擎流程定义</li>
 *   <li>start：业务单据提交后启动流程实例，回写 flow_instance_id 到 cmd_approval_task</li>
 *   <li>advance：审批动作（批准/升级/退回）调用引擎 taskService 推进节点</li>
 *   <li>graph：读取引擎节点/连线/历史，供前端渲染 BPMN 风格流程图并高亮进度</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
public interface ICmdFlowEngineService {

    /**
     * 部署场景对应的流程定义（幂等：已存在则直接返回定义 ID），并发布
     *
     * @param sceneCode 场景编码（CUSTOMER_CREATE / MERGE ...）
     * @return 流程定义 ID
     */
    Long deployScene(String sceneCode);

    /**
     * 启动流程实例并回写业务表
     *
     * @param taskNo 任务编号（cmd_approval_task.task_no）
     * @return 流程实例 ID
     */
    Long startInstance(String taskNo);

    /**
     * 推进流程（审批动作落到引擎）
     *
     * @param taskNo     任务编号
     * @param actionType 动作类型（APPROVE / ESCALATE / REJECT / RETURN）
     * @param opinion    审批意见
     * @return 推进后的当前节点名称
     */
    String advance(String taskNo, String actionType, String opinion);

    /**
     * 查询流程图形 + 引擎侧节点状态（BPMN 风格渲染数据）
     *
     * @param taskNo 任务编号
     * @return 图形视图（节点 / 连线 / 状态）
     */
    CmdFlowTraceVo.GraphVo graph(String taskNo);

    /**
     * 列出全部 CMD 业务场景（流程中心页），并给出每个场景在 Warm-Flow 的部署状态
     *
     * @return 场景视图列表
     */
    List<CmdFlowSceneVO> listScenes();

    /**
     * 按场景查询流程图形（部署并发布后读取引擎节点/连线，定义视图，节点均为待执行）
     *
     * @param sceneCode 场景编码
     * @return 图形视图（节点 / 连线）
     */
    CmdFlowTraceVo.GraphVo graphByScene(String sceneCode);

    /**
     * 按场景查询泳道图，并按流程实例进度点亮节点
     * <p>
     * taskNo 为空 = 定义视图（蓝图，全部待执行）；
     * taskNo 非空 = 实例视图（按该任务的实际流转进度标记 COMPLETED / CURRENT / PENDING）。
     *
     * @param sceneCode 场景编码（CUSTOMER_CREATE / MERGE ...）
     * @param taskNo    任务编号（可空）
     * @return 图形视图（节点 / 连线 / 状态）
     */
    CmdFlowTraceVo.GraphVo graphByScene(String sceneCode, String taskNo);

    /**
     * 构建场景的泳道图步骤模板（总设计泳道图：7 阶段 × 6 泳道，全部为待执行状态）
     * <p>
     * 同时供「流程中心 → 流程图」与「流程跟踪」两处复用，保证节点口径一致。
     *
     * @param sceneCode 场景编码（CUSTOMER_CREATE 使用完整 11 步模板，其余使用通用骨架）
     * @return 泳道步骤（含 phase / lane / nodeType / note）
     */
    List<CmdFlowTraceVo.StepVo> buildSwimlane(String sceneCode);

    /**
     * 按任务实际进度推导泳道步骤状态（「流程图」与「流程跟踪」共用，禁止两处各写一份）
     *
     * @param steps           泳道步骤（buildSwimlane 产出，就地修改 status）
     * @param taskStatus      任务状态（PENDING / APPROVED / REJECTED / RETURNED / ESCALATED / CANCELLED）
     * @param currentNodeName 当前节点名称（引擎或业务侧镜像）
     * @param touchedNodes    已有真实轨迹的节点编码（引擎 flow_his_task 等），可空
     */
    void applyStepStatus(List<CmdFlowTraceVo.StepVo> steps, String taskStatus,
                         String currentNodeName, Set<String> touchedNodes);
}
