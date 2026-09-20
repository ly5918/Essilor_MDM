package org.dromara.cmd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.bo.CmdFlowInstanceBo;
import org.dromara.cmd.domain.vo.CmdFlowInstanceVo;
import org.dromara.cmd.domain.vo.CmdFlowTraceVo;
import org.dromara.cmd.mapper.CmdApprovalActionMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdFlowSceneMapper;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.cmd.service.ICmdFlowTraceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程跟踪 服务实现
 * <p>
 * 设计要点（对应总设计泳道图 + Warm-Flow 集成约定）：
 * <ul>
 *   <li>自动校验类节点（OCR / DQ / Duplicate Check）不进流程引擎本体，在步骤模板中仅作进度展示，
 *       实际执行由 cmd 模块 Service 完成——与「泳道图 → RuoYi 工作流映射分析」结论一致；</li>
 *   <li>人工审批节点（BU 初审 / GC 决策）对应 Warm-Flow 用户任务，
 *       审批人规则从 cmd_flow_node_rule 读取（ROLE=BU_STEWARD / GC_STEWARD）；</li>
 *   <li>步骤实时状态由 cmd_approval_task.current_node_name + cmd_approval_action 轨迹推导，
 *       引擎侧进度以 flow_instance_id / flow_status 镜像字段透出，不直接查 flow_* 表（解耦约定）。</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdFlowTraceServiceImpl implements ICmdFlowTraceService {

    private final CmdApprovalTaskMapper taskMapper;
    private final CmdApprovalActionMapper actionMapper;
    private final CmdFlowSceneMapper flowSceneMapper;
    private final ICmdFlowEngineService flowEngineService;

    /** 泳道名称常量（与总设计泳道图一致；步骤模板见 ICmdFlowEngineService#buildSwimlane） */
    private static final String LANE_ADMIN = "Platform Admin";

    /** 关键节点编码（用于当前节点匹配；审批人规则来源：cmd_flow_node_rule） */
    private static final String NODE_BU_REVIEW = "BU_REVIEW";
    private static final String NODE_GC_REVIEW = "GC_REVIEW";
    private static final Set<String> MANUAL_REVIEW_NODES = Set.of(NODE_BU_REVIEW, NODE_GC_REVIEW);

    /** 业务终态（流程实例记录列表按此区分「已完成」与「进行中」） */
    private static final Set<String> FINAL_STATUSES = Set.of("APPROVED", "REJECTED", "CANCELLED", "COMPLETED");

    @Override
    public CmdFlowTraceVo selectTraceByTaskNo(String taskNo) {
        CmdApprovalTask task = taskMapper.selectOne(new LambdaQueryWrapper<CmdApprovalTask>()
            .eq(CmdApprovalTask::getTaskNo, taskNo)
            .last("LIMIT 1"));
        if (task == null) {
            return null;
        }

        CmdFlowTraceVo vo = new CmdFlowTraceVo();
        fillTaskBase(vo, task);
        fillSceneMapping(vo, task);
        fillSteps(vo, task);
        fillContextVars(vo, task);
        fillActions(vo, task);
        fillProgress(vo);
        // Warm-Flow 引擎图形（未启动实例时返回空图，页面回退到业务侧步骤条）
        if (task.getFlowInstanceId() != null) {
            vo.setEngineBound(Boolean.TRUE);
            vo.setGraph(flowEngineService.graph(task.getTaskNo()));
        }
        return vo;
    }

    // ------------------------------------------------------------------
    // 流程实例记录（流程中心列表：每一次执行过的工作流）
    // ------------------------------------------------------------------

    @Override
    public PageResult<CmdFlowInstanceVo> listInstances(CmdFlowInstanceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CmdApprovalTask> lqw = new LambdaQueryWrapper<CmdApprovalTask>()
            .eq(StringUtils.isNotBlank(bo.getStatus()), CmdApprovalTask::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getBizType()), CmdApprovalTask::getBizType, bo.getBizType())
            .orderByDesc(CmdApprovalTask::getCreateTime);
        // 贯通查询：申请编号 / One ID / 业务标题 三列模糊匹配
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String kw = bo.getKeyword().trim();
            lqw.and(w -> w.like(CmdApprovalTask::getTaskNo, kw)
                .or().like(CmdApprovalTask::getBizTitle, kw)
                .or().like(CmdApprovalTask::getOneId, kw));
        }
        // 运行状态：RUNNING 引擎已启动且未终态 / DONE 业务终态 / NEW 尚未启动实例
        String runState = bo.getRunState() == null ? "" : bo.getRunState().toUpperCase();
        switch (runState) {
            case "RUNNING" -> lqw.isNotNull(CmdApprovalTask::getFlowInstanceId)
                .notIn(CmdApprovalTask::getStatus, FINAL_STATUSES);
            case "DONE" -> lqw.in(CmdApprovalTask::getStatus, FINAL_STATUSES);
            case "NEW" -> lqw.isNull(CmdApprovalTask::getFlowInstanceId);
            default -> {
                // 不筛选
            }
        }

        Page<CmdApprovalTask> page = taskMapper.selectPage(pageQuery.build(), lqw);
        List<CmdFlowInstanceVo> rows = new ArrayList<>();
        for (CmdApprovalTask task : page.getRecords()) {
            rows.add(toInstanceVo(task));
        }
        return PageResult.build(rows, page.getTotal());
    }

    /** 任务 → 实例记录视图（含泳道图口径的进度百分比） */
    private CmdFlowInstanceVo toInstanceVo(CmdApprovalTask task) {
        String sceneCode = normalizeSceneCode(task.getSceneCode(), task.getBizType());
        CmdFlowInstanceVo vo = new CmdFlowInstanceVo();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setOneId(task.getOneId());
        vo.setBizTitle(task.getBizTitle());
        vo.setBizType(task.getBizType());
        vo.setSceneCode(sceneCode);
        Map<String, Object> scene = flowSceneMapper.selectSceneByCode(sceneCode);
        if (scene != null) {
            vo.setSceneName(str(scene.get("scene_name")));
            vo.setFlowName(str(scene.get("flow_name")));
            vo.setFlowCode(str(scene.get("flow_code")));
        }
        vo.setFlowInstanceId(task.getFlowInstanceId());
        vo.setEngineBound(task.getFlowInstanceId() != null);
        vo.setApplicantName(task.getApplicantName());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setAssigneeRole(task.getAssigneeRole());
        vo.setPriority(task.getRiskLevel());
        vo.setStatus(task.getStatus());
        vo.setCurrentNodeName(task.getCurrentNodeName());
        vo.setSlaState(task.getSlaState());
        vo.setSubmitTime(task.getSubmitTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setDurationHours(task.getDurationHours());
        vo.setCreateTime(task.getCreateTime());

        // 进度：复用泳道图步骤模板 + 状态推导，与「流程图 / 流程跟踪」口径一致
        List<CmdFlowTraceVo.StepVo> steps = flowEngineService.buildSwimlane(sceneCode);
        flowEngineService.applyStepStatus(steps, task.getStatus(), task.getCurrentNodeName(), Set.of());
        long done = steps.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        vo.setTotalSteps(steps.size());
        vo.setCompletedSteps((int) done);
        vo.setProgressPercent(steps.isEmpty() ? 0 : (int) Math.round(done * 100.0 / steps.size()));
        return vo;
    }

    // ------------------------------------------------------------------
    // 基础信息
    // ------------------------------------------------------------------

    private void fillTaskBase(CmdFlowTraceVo vo, CmdApprovalTask task) {
        vo.setTaskNo(task.getTaskNo());
        vo.setBizTitle(task.getBizTitle());
        vo.setBizType(task.getBizType());
        vo.setStatus(task.getStatus());
        vo.setCurrentNodeName(task.getCurrentNodeName());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setAssigneeRole(task.getAssigneeRole());
        vo.setBuScope(task.getBuScope());
        vo.setRiskLevel(task.getRiskLevel());
        vo.setSlaState(task.getSlaState());
        vo.setSubmitTime(task.getSubmitTime());
        vo.setSlaDue(task.getSlaDue());
        vo.setFlowInstanceId(task.getFlowInstanceId());
        vo.setFlowTaskId(task.getFlowTaskId());
        vo.setFlowDefinitionId(task.getFlowDefinitionId());
        vo.setFlowStatus(task.getFlowStatus());
    }

    /**
     * 场景 → Warm-Flow 流程映射（cmd_flow_scene）。
     * 种子数据 biz_type 存中文业务类型，做一次中文 → scene_code 归一；
     * task.scene_code 有值时优先。
     */
    private void fillSceneMapping(CmdFlowTraceVo vo, CmdApprovalTask task) {
        String sceneCode = normalizeSceneCode(task.getSceneCode(), task.getBizType());
        vo.setSceneCode(sceneCode);
        Map<String, Object> scene = flowSceneMapper.selectSceneByCode(sceneCode);
        if (scene != null) {
            vo.setSceneName(str(scene.get("scene_name")));
            vo.setFlowCode(str(scene.get("flow_code")));
            vo.setFlowName(str(scene.get("flow_name")));
            Object slaHours = scene.get("sla_hours");
            vo.setSlaHours(slaHours instanceof Number n ? n.intValue() : null);
        } else {
            vo.setSceneName(task.getBizType());
        }
    }

    private String normalizeSceneCode(String sceneCode, String bizType) {
        if (sceneCode != null && sceneCode.matches("[A-Z_]+")) {
            return sceneCode;
        }
        String biz = bizType == null ? "" : bizType;
        if (biz.contains("创建")) {
            return "CUSTOMER_CREATE";
        }
        if (biz.contains("变更")) {
            return "CUSTOMER_CHANGE";
        }
        if (biz.contains("停用")) {
            return "DEACTIVATE";
        }
        if (biz.contains("层级")) {
            return "HIER_RELATION";
        }
        if (biz.contains("批量")) {
            return "IMPORT_BATCH";
        }
        if (biz.contains("合并") || biz.contains("重复")) {
            return "MERGE";
        }
        return "CUSTOMER_CREATE";
    }

    // ------------------------------------------------------------------
    // 步骤模板 + 状态推导
    // ------------------------------------------------------------------

    /**
     * 构建泳道图步骤模板并推导实时状态。
     * 场景一（CUSTOMER_CREATE）使用完整 11 步模板；
     * 其余场景复用同一 7 阶段骨架，节点名做通用化替换。
     */
    private void fillSteps(CmdFlowTraceVo vo, CmdApprovalTask task) {
        // 复用引擎服务的泳道图模板，保证「流程图」与「流程跟踪」节点口径一致
        List<CmdFlowTraceVo.StepVo> steps = flowEngineService.buildSwimlane(vo.getSceneCode());

        // 轨迹：按 node_code 汇总操作（自动节点由系统轨迹写入，人工节点由审批动作写入）
        Map<String, CmdApprovalAction> actionByNode = indexActionsByNode(task.getId());

        for (CmdFlowTraceVo.StepVo s : steps) {
            CmdApprovalAction act = actionByNode.get(s.getNodeCode());
            if (act != null) {
                s.setOperator(act.getOperatorName());
                s.setActionTime(act.getActionTime());
                s.setOpinion(act.getOpinion());
            }

            // 人工节点审批人（cmd_flow_node_rule：assignee_value = 角色）
            if (MANUAL_REVIEW_NODES.contains(s.getNodeCode())) {
                s.setAssignee(NODE_GC_REVIEW.equals(s.getNodeCode()) ? "GC_STEWARD" : "BU_STEWARD");
            }
        }

        // 状态推导与「流程图（Graph）」共用同一实现：任务状态 + 当前节点 + 真实轨迹
        flowEngineService.applyStepStatus(steps, task.getStatus(), task.getCurrentNodeName(), actionByNode.keySet());

        vo.setSteps(steps);

        // 泳道图旁路节点：规则与参数配置（Platform Admin，虚线，不打断主流程）
        CmdFlowTraceVo.BypassNodeVo bypass = new CmdFlowTraceVo.BypassNodeVo();
        bypass.setLane(LANE_ADMIN);
        bypass.setNodeName("规则与参数配置");
        bypass.setNote("配置 DQ 规则、匹配规则和审批试验，参数配置不打断主流程");
        vo.setBypass(bypass);
    }

    /** 轨迹按 node_code 归并：优先取 to_node_code，其次 from_node_code 匹配模板编码 */
    private Map<String, CmdApprovalAction> indexActionsByNode(Long taskId) {
        List<CmdApprovalAction> actions = actionMapper.selectList(new LambdaQueryWrapper<CmdApprovalAction>()
            .eq(CmdApprovalAction::getTaskId, taskId)
            .orderByAsc(CmdApprovalAction::getActionTime));
        Map<String, CmdApprovalAction> map = new HashMap<>();
        for (CmdApprovalAction a : actions) {
            String node = a.getToNodeCode();
            if (node == null || node.isBlank()) {
                node = mapByActionType(a);
            }
            if (node != null && !map.containsKey(node)) {
                map.put(node, a);
            }
        }
        return map;
    }

    /** 无显式 node_code 时按动作类型推断所属步骤 */
    private String mapByActionType(CmdApprovalAction a) {
        String type = a.getActionType() == null ? "" : a.getActionType().toUpperCase();
        return switch (type) {
            case "SUBMIT", "CLAIM" -> "APPLY";
            case "APPROVE" -> NODE_BU_REVIEW;
            case "ESCALATE" -> NODE_GC_REVIEW;
            case "RETURN", "REJECT" -> NODE_BU_REVIEW;
            default -> null;
        };
    }

    private void fillProgress(CmdFlowTraceVo vo) {
        List<CmdFlowTraceVo.StepVo> steps = vo.getSteps();
        vo.setTotalSteps(steps.size());
        long done = steps.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        vo.setCompletedSteps((int) done);
        vo.setProgressPercent(steps.isEmpty() ? 0 : (int) Math.round(done * 100.0 / steps.size()));
    }

    // ------------------------------------------------------------------
    // 上下文变量（Data context state）
    // ------------------------------------------------------------------

    private void fillContextVars(CmdFlowTraceVo vo, CmdApprovalTask task) {
        List<CmdFlowTraceVo.VarVo> vars = new ArrayList<>();
        vars.add(var("record", task.getTaskNo() + " / " + task.getBizTitle()));
        vars.add(var("dataset", "customer"));
        vars.add(var("scene", vo.getSceneCode()));
        vars.add(var("flowDefinition", vo.getFlowCode()));
        vars.add(var("buScope", task.getBuScope()));
        vars.add(var("riskLevel", task.getRiskLevel()));
        vars.add(var("dqScore", task.getDqScore() == null ? null : task.getDqScore().toPlainString()));
        vars.add(var("duplicateState", task.getDuplicateState()));
        vars.add(var("crossBu", task.getCrossBuFlag()));
        vars.add(var("mergeRequestStatus", task.getStatus()));
        vars.add(var("oneId", task.getOneId()));
        vars.add(var("assignee", task.getAssigneeName()));
        vars.add(var("submitTime", task.getSubmitTime() == null ? null : task.getSubmitTime().toString()));
        vars.add(var("slaDue", task.getSlaDue() == null ? null : task.getSlaDue().toString()));
        vars.add(var("flowInstanceId", task.getFlowInstanceId() == null ? null : String.valueOf(task.getFlowInstanceId())));
        vo.setContextVars(vars);
    }

    private CmdFlowTraceVo.VarVo var(String name, String value) {
        CmdFlowTraceVo.VarVo v = new CmdFlowTraceVo.VarVo();
        v.setName(name);
        v.setValue(value);
        return v;
    }

    // ------------------------------------------------------------------
    // 轨迹
    // ------------------------------------------------------------------

    private void fillActions(CmdFlowTraceVo vo, CmdApprovalTask task) {
        List<CmdApprovalAction> actions = actionMapper.selectList(new LambdaQueryWrapper<CmdApprovalAction>()
            .eq(CmdApprovalAction::getTaskId, task.getId())
            .orderByAsc(CmdApprovalAction::getActionTime));
        for (CmdApprovalAction a : actions) {
            CmdFlowTraceVo.ActionTraceVo t = new CmdFlowTraceVo.ActionTraceVo();
            t.setActionType(a.getActionType());
            t.setActionName(a.getActionName());
            t.setOperatorName(a.getOperatorName());
            t.setOperatorRole(a.getOperatorRole());
            t.setActionTime(a.getActionTime());
            t.setOpinion(a.getOpinion());
            vo.getActions().add(t);
        }
    }

    // 说明：泳道图步骤模板已上移至 ICmdFlowEngineService#buildSwimlane，
    // 「流程图」与「流程跟踪」共用同一口径，避免两处维护。

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
