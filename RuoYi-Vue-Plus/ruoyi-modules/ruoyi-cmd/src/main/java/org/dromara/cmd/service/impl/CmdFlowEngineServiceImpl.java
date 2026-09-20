package org.dromara.cmd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.vo.CmdFlowSceneVO;
import org.dromara.cmd.domain.vo.CmdFlowTraceVo;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdFlowEngineMapper;
import org.dromara.cmd.mapper.CmdFlowSceneMapper;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.orm.mapper.FlowNodeMapper;
import org.dromara.warm.flow.orm.mapper.FlowSkipMapper;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.dromara.warm.flow.orm.entity.FlowSkip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Warm-Flow 引擎联动服务实现
 * <p>
 * 设计约定（对应「泳道图 → RuoYi 工作流映射分析」）：
 * <ul>
 *   <li>自动校验（OCR / DQ / Duplicate Check）不进引擎，在 cmd 模块 Service 执行，
 *       结果作为流程变量传入实例（变量驱动路由），与泳道图一致；</li>
 *   <li>引擎只建模人工审批节点：APPLY → BU_REVIEW → GC_REVIEW → END，
 *       「升级 GC」= 流转到 GC_REVIEW 节点，「批准」= 直接流转到 END（跳过 GC）；</li>
 *   <li>实例 ID / 任务 ID 回写 cmd_approval_task，业务表与引擎解耦。</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdFlowEngineServiceImpl implements ICmdFlowEngineService {

    private final DefService defService;
    private final InsService insService;
    private final FlowNodeMapper flowNodeMapper;
    private final FlowSkipMapper flowSkipMapper;
    private final TaskService taskService;
    private final CmdApprovalTaskMapper taskMapper;
    private final CmdFlowSceneMapper flowSceneMapper;
    private final CmdFlowEngineMapper engineMapper;

    /** 场景 → 流程编码（与 cmd_flow_scene.flow_code 一致） */
    private static final Map<String, String> SCENE_FLOW_CODE = Map.of(
        "CUSTOMER_CREATE", "cmd_customer_create",
        "CUSTOMER_CHANGE", "cmd_customer_change",
        "DEACTIVATE", "cmd_customer_deactivate",
        "HIER_RELATION", "cmd_hier_relation",
        "IMPORT_BATCH", "cmd_import_batch",
        "MERGE", "cmd_customer_merge"
    );

    /** 引擎节点编码 */
    private static final String NODE_APPLY = "APPLY";
    private static final String NODE_BU_REVIEW = "BU_REVIEW";
    private static final String NODE_GC_REVIEW = "GC_REVIEW";
    private static final String NODE_END = "END";
    private static final String NODE_START = "START";

    /** 处理人角色编码 / 名称（与 cmd_approval_task 镜像字段保持一致） */
    private static final String ROLE_BU_STEWARD = "BU_STEWARD";
    private static final String NAME_BU_STEWARD = "BU Steward";
    private static final String ROLE_GC_STEWARD = "GC_STEWARD";
    private static final String NAME_GC_STEWARD = "GC Steward";

    /** 泳道名称常量（与总设计泳道图一致） */
    private static final String LANE_BU_USER = "Business User";
    private static final String LANE_SYS = "系统自动处理";
    private static final String LANE_BU_STEWARD = "Data Steward BU Scope";
    private static final String LANE_GC_STEWARD = "Data Steward GC Scope";
    private static final String LANE_ADMIN = "Platform Admin";
    private static final String LANE_AUDITOR = "Auditor（只读）";

    /** 泳道步骤节点类型（区别于引擎 node_type） */
    private static final String STEP_GATEWAY = "GATEWAY";

    /** Warm-Flow 引擎 node_type：任务节点 / 网关节点 */
    private static final Integer TASK_NODE_TYPE = 1;
    private static final Integer GATEWAY_NODE_TYPE = 3;

    /**
     * 泳道自上而下顺序（与总设计泳道图一致：6 条泳道）
     * <p>泳道图布局：横向为阶段（列），纵向为泳道（行）。
     */
    private static final List<String> LANE_ORDER = List.of(
        LANE_BU_USER, LANE_SYS, LANE_BU_STEWARD, LANE_GC_STEWARD, LANE_ADMIN, LANE_AUDITOR);

    /** 泳道图定义视图布局参数（阶段分列 × 泳道分行） */
    private static final int BASE_X = 300;
    private static final int COL_GAP = 168;
    private static final int BASE_Y = 70;
    private static final int LANE_GAP = 92;
    /** 同一阶段同一泳道出现多个节点时的纵向微调步长 */
    private static final int SUB_ROW_GAP = 46;

    /** 泳道图步骤模板 */
    private record StepTpl(int phase, String phaseName, String lane, String nodeCode,
                           String nodeName, String nodeType, String note) {
    }

    /** 升级类动作 → 流转到 GC 决策节点 */
    private static final Set<String> ESCALATE_ACTIONS = Set.of("ESCALATE", "升级GC", "escalate");

    /** 终态任务状态（泳道步骤全部点亮为已完成） */
    private static final Set<String> FINAL_OK_STATUS = Set.of("APPROVED", "COMPLETED");

    /** 终止态任务状态（后续步骤标记为已终止） */
    private static final Set<String> FINAL_STOP_STATUS = Set.of("REJECTED", "CANCELLED");

    /**
     * 兜底办理人：Warm-Flow 创建任务时办理人不能为空。
     * 演示数据未维护 applicantId / assigneeId，且 POC 免登录（无 SaToken 会话），
     * 因此按 处理人 → 申请人 → 当前登录人 → 系统管理员 依次取值。
     */
    private static final String DEFAULT_HANDLER = "1";

    /**
     * 节点办理方式（node_ratio）：「0」= 或签，即任一办理人处理后即流转。
     * 必须显式写入：缺失时 Warm-Flow 判定会签/票签会拿到 null，最终在
     * TaskServiceImpl.cooperate 里 new BigDecimal(null) 抛 NPE。
     */
    private static final String NODE_RATIO_OR_SIGN = "0";

    @Override
    public Long deployScene(String sceneCode) {
        String flowCode = SCENE_FLOW_CODE.getOrDefault(sceneCode, "cmd_customer_create");
        Long existId = engineMapper.selectPublishedDefinitionId(flowCode);
        if (existId != null && !isDefinitionIncomplete(existId)) {
            return existId;
        }
        if (existId != null) {
            // 定义完全由代码生成：早期版本建节点时未写 node_ratio，属于历史脏数据，清理后重建
            log.warn("[CMD][FLOW] 流程定义缺少 node_ratio，清理重建：flowCode={} definitionId={}", flowCode, existId);
            defService.removeDef(List.of(existId));
        }
        FlowDefinition definition = new FlowDefinition();
        definition.setFlowCode(flowCode);
        definition.setFlowName(sceneName(sceneCode));
        definition.setVersion("v1.0");
        definition.setCategory("cmd-poc");
        // 1) 先落流程定义（UNPUBLISHED 状态），保存后按 flow_code 反查主键（忽略发布状态）
        defService.save(definition);
        Long definitionId = engineMapper.selectDefinitionId(flowCode);
        if (definitionId == null) {
            throw new ServiceException("流程定义保存失败：" + flowCode);
        }

        // 2) 节点 / 连线挂到定义上（引擎自带 Mapper 直接落库，insertFlow 不会回填 definition_id）
        for (FlowNode n : buildNodes(definitionId)) {
            flowNodeMapper.insert(n);
        }
        for (FlowSkip s : buildSkips(definitionId)) {
            flowSkipMapper.insert(s);
        }

        // 3) 发布（未发布的定义无法启动实例）
        boolean published = defService.publish(definitionId);
        log.info("[CMD][FLOW] 流程定义部署成功：scene={} flowCode={} definitionId={} published={}",
            sceneCode, flowCode, definitionId, published);
        return definitionId;
    }

    private List<FlowNode> buildNodes(Long definitionId) {
        List<FlowNode> nodes = new ArrayList<>();
        nodes.add(node(definitionId, NODE_START, "开始", NodeType.START, "60,90"));
        nodes.add(node(definitionId, NODE_APPLY, "创建客户申请", NodeType.BETWEEN, "180,90"));
        nodes.add(node(definitionId, NODE_BU_REVIEW, "BU Scope 初审", NodeType.BETWEEN, "310,90"));
        nodes.add(node(definitionId, NODE_GC_REVIEW, "GC Scope 决策", NodeType.BETWEEN, "440,90"));
        nodes.add(node(definitionId, NODE_END, "结束", NodeType.END, "570,90"));
        return nodes;
    }

    private FlowNode node(Long definitionId, String code, String name, NodeType type, String coordinate) {
        FlowNode n = new FlowNode();
        n.setDefinitionId(definitionId);
        n.setNodeCode(code);
        n.setNodeName(name);
        n.setNodeType(type.getKey());
        n.setCoordinate(coordinate);
        n.setVersion("v1.0");
        // 或签：POC 阶段每个节点只有一个办理人，不涉及会签/票签
        n.setNodeRatio(NODE_RATIO_OR_SIGN);
        return n;
    }

    /** 是否缺少办理方式配置（历史版本部署的定义）：缺了就重建，避免引擎在会签判定处 NPE */
    private boolean isDefinitionIncomplete(Long definitionId) {
        List<FlowNode> nodes = flowNodeMapper.selectList(new LambdaQueryWrapper<FlowNode>()
            .eq(FlowNode::getDefinitionId, definitionId)
            .and(w -> w.isNull(FlowNode::getNodeRatio).or().eq(FlowNode::getNodeRatio, "")));
        return !nodes.isEmpty();
    }

    private List<FlowSkip> buildSkips(Long definitionId) {
        List<FlowSkip> skips = new ArrayList<>();
        skips.add(skip(definitionId, NODE_START, NODE_APPLY, "提交", NodeType.START, NodeType.BETWEEN));
        skips.add(skip(definitionId, NODE_APPLY, NODE_BU_REVIEW, "进入 BU 初审", NodeType.BETWEEN, NodeType.BETWEEN));
        skips.add(skip(definitionId, NODE_BU_REVIEW, NODE_GC_REVIEW, "升级 Cross-BU", NodeType.BETWEEN, NodeType.BETWEEN));
        skips.add(skip(definitionId, NODE_BU_REVIEW, NODE_END, "批准", NodeType.BETWEEN, NodeType.END));
        skips.add(skip(definitionId, NODE_GC_REVIEW, NODE_END, "决策完成", NodeType.BETWEEN, NodeType.END));
        return skips;
    }

    private FlowSkip skip(Long definitionId, String from, String to, String name, NodeType fromType, NodeType toType) {
        FlowSkip s = new FlowSkip();
        s.setDefinitionId(definitionId);
        s.setNowNodeCode(from);
        s.setNowNodeType(fromType.getKey());
        s.setNextNodeCode(to);
        s.setNextNodeType(toType.getKey());
        s.setSkipName(name);
        s.setSkipType(SkipType.PASS.getKey());
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startInstance(String taskNo) {
        CmdApprovalTask task = requireTask(taskNo);
        if (task.getFlowInstanceId() != null) {
            return task.getFlowInstanceId();
        }
        String sceneCode = normalizeScene(task);
        Long definitionId = deployScene(sceneCode);
        String flowCode = SCENE_FLOW_CODE.getOrDefault(sceneCode, "cmd_customer_create");

        Map<String, Object> variable = new HashMap<>();
        variable.put("taskNo", task.getTaskNo());
        variable.put("buScope", task.getBuScope());
        variable.put("riskLevel", task.getRiskLevel());
        variable.put("duplicateState", task.getDuplicateState());
        variable.put("crossBu", "Y".equals(task.getCrossBuFlag()));
        variable.put("dqScore", task.getDqScore());

        // 说明：insService.start(业务ID, FlowParams)，flowCode 由 params 传入
        String handler = resolveHandler(task);
        FlowParams params = FlowParams.build()
            .flowCode(flowCode)
            .variable(variable)
            .handler(handler)
            // POC 免登录（/cmd/** 在安全白名单），没有 SaToken 会话，
            // PermissionHandler.permissions() 取不到人，这里显式把办理人权限标识传给引擎
            .permissionFlag(List.of(handler));

        Instance instance = insService.start(task.getTaskNo(), params);
        if (instance == null) {
            throw new ServiceException("流程实例启动失败：" + taskNo);
        }
        // 待办单据通常已经流转到中间节点（如「GC Steward 决策」）：
        // 实例启动后先按业务节点补齐路径，否则引擎停在 APPLY，泳道图会「回退」。
        for (String nodeCode : enginePathOfBusiness(task.getCurrentNodeName())) {
            List<Task> pending = taskService.getByInsId(instance.getId());
            if (pending.isEmpty()) {
                break;
            }
            instance = taskService.skip(pending.get(0).getId(), FlowParams.build()
                .message("实例定位至业务当前节点")
                .handler(handler)
                .permissionFlag(List.of(handler))
                .nodeCode(nodeCode)
                .skipType(SkipType.PASS.getKey()));
        }

        CmdApprovalTask update = new CmdApprovalTask();
        update.setId(task.getId());
        update.setFlowInstanceId(instance.getId());
        update.setFlowDefinitionId(definitionId);
        update.setFlowStatus(instance.getFlowStatus());
        update.setCurrentNodeCode(instance.getNodeCode());
        update.setCurrentNodeName(instance.getNodeName());
        List<Map<String, Object>> tasks = engineMapper.selectTasks(instance.getId());
        if (!tasks.isEmpty()) {
            update.setFlowTaskId(((Number) tasks.get(0).get("id")).longValue());
        }
        taskMapper.updateById(update);
        log.info("[CMD][FLOW] 流程实例已启动：taskNo={} instanceId={} node={}", taskNo, instance.getId(), instance.getNodeCode());
        return instance.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String advance(String taskNo, String actionType, String opinion) {
        CmdApprovalTask task = requireTask(taskNo);
        if (task.getFlowInstanceId() == null) {
            throw new ServiceException("任务尚未启动流程实例：" + taskNo);
        }
        String type = actionType == null ? "" : actionType.toUpperCase();
        List<Task> tasks = taskService.getByInsId(task.getFlowInstanceId());
        if (tasks.isEmpty()) {
            // 实例已结束（或当前节点无待办）：无需推进，返回镜像当前节点名
            return task.getCurrentNodeName() == null ? "" : task.getCurrentNodeName();
        }
        Task current = tasks.get(0);

        // 仅「通过类」动作落到引擎：
        //   升级 GC → 流转到 GC 决策节点；批准 / 确认新建 / 确认合并 → 流转到结束节点。
        //   退回 / 拒绝 / 转办 / 认领 不改引擎节点，由业务状态驱动泳道图（步骤标记为已终止 / 进行中）。
        String target = ESCALATE_ACTIONS.contains(actionType) || ESCALATE_ACTIONS.contains(type)
            ? NODE_GC_REVIEW : NODE_END;
        String handler = resolveHandler(task);
        FlowParams params = FlowParams.build()
            .message(opinion)
            .handler(handler)
            // 免登录场景下必须显式传权限标识，否则 TaskServiceImpl.checkAuth 报「无法跳转到该节点」
            .permissionFlag(List.of(handler))
            .nodeCode(target)
            .skipType(SkipType.PASS.getKey());

        Instance instance = taskService.skip(current.getId(), params);
        CmdApprovalTask update = new CmdApprovalTask();
        update.setId(task.getId());
        update.setFlowStatus(instance == null ? "2" : instance.getFlowStatus());
        if (instance != null) {
            update.setCurrentNodeCode(instance.getNodeCode());
            update.setCurrentNodeName(instance.getNodeName());
            update.setScope(resolveScope(instance.getNodeCode(), task.getScope()));
            update.setAssigneeRole(resolveAssigneeRole(instance.getNodeCode(), task.getAssigneeRole()));
            update.setAssigneeName(resolveAssigneeName(instance.getNodeCode(), task.getAssigneeName()));
        }
        List<Task> nextTasks = taskService.getByInsId(task.getFlowInstanceId());
        update.setFlowTaskId(nextTasks.isEmpty() ? null : nextTasks.get(0).getId());
        taskMapper.updateById(update);
        return instance == null ? "" : instance.getNodeName();
    }

    /**
     * 根据引擎节点推导审批 Scope（BU/GC）
     * <p>终止节点（END）保持原有 Scope：GC 决策完成后不应回退成 BU，
     * 否则「我已处理 / 治理复核」的归属口径会对不上。</p>
     */
    private String resolveScope(String nodeCode, String currentScope) {
        if (NODE_GC_REVIEW.equals(nodeCode)) {
            return CmdConstants.SCOPE_GC;
        }
        if (NODE_BU_REVIEW.equals(nodeCode)) {
            return CmdConstants.SCOPE_BU;
        }
        return StringUtils.isNotBlank(currentScope) ? currentScope : CmdConstants.SCOPE_BU;
    }

    /** 根据引擎节点推导处理人角色编码（终止节点保持原办理人） */
    private String resolveAssigneeRole(String nodeCode, String currentRole) {
        if (NODE_GC_REVIEW.equals(nodeCode)) {
            return ROLE_GC_STEWARD;
        }
        if (NODE_BU_REVIEW.equals(nodeCode)) {
            return ROLE_BU_STEWARD;
        }
        return StringUtils.isNotBlank(currentRole) ? currentRole : ROLE_BU_STEWARD;
    }

    /** 根据引擎节点推导处理人显示名称（终止节点保持原办理人） */
    private String resolveAssigneeName(String nodeCode, String currentName) {
        if (NODE_GC_REVIEW.equals(nodeCode)) {
            return NAME_GC_STEWARD;
        }
        if (NODE_BU_REVIEW.equals(nodeCode)) {
            return NAME_BU_STEWARD;
        }
        return StringUtils.isNotBlank(currentName) ? currentName : NAME_BU_STEWARD;
    }

    @Override
    public CmdFlowTraceVo.GraphVo graph(String taskNo) {
        CmdApprovalTask task = requireTask(taskNo);
        CmdFlowTraceVo.GraphVo graph = new CmdFlowTraceVo.GraphVo();
        Long instanceId = task.getFlowInstanceId();
        if (instanceId == null) {
            return graph;
        }
        Map<String, Object> instance = engineMapper.selectInstance(instanceId);
        if (instance == null) {
            return graph;
        }
        Long definitionId = ((Number) instance.get("definition_id")).longValue();
        graph.setDefinitionId(definitionId);
        graph.setFlowCode(task.getSceneCode());

        // 引擎侧状态：历史（已完成）+ 待办（当前）
        Map<String, Map<String, Object>> his = new HashMap<>();
        for (Map<String, Object> row : engineMapper.selectHisTasks(instanceId)) {
            his.putIfAbsent(String.valueOf(row.get("node_code")), row);
        }
        Map<String, Map<String, Object>> running = new HashMap<>();
        for (Map<String, Object> row : engineMapper.selectTasks(instanceId)) {
            running.put(String.valueOf(row.get("node_code")), row);
        }

        for (Map<String, Object> row : engineMapper.selectNodes(definitionId)) {
            CmdFlowTraceVo.GraphNodeVo node = new CmdFlowTraceVo.GraphNodeVo();
            String code = String.valueOf(row.get("node_code"));
            node.setNodeCode(code);
            node.setNodeName(String.valueOf(row.get("node_name")));
            Integer nodeType = row.get("node_type") == null ? null : ((Number) row.get("node_type")).intValue();
            node.setNodeType(nodeType);
            node.setShape(shapeOf(code, nodeType));
            String[] xy = StringUtils.isNotBlank(String.valueOf(row.get("coordinate")))
                ? String.valueOf(row.get("coordinate")).split(",") : new String[]{"0", "0"};
            node.setX(Integer.parseInt(xy[0].trim()));
            node.setY(xy.length > 1 ? Integer.parseInt(xy[1].trim()) : 0);
            if (running.containsKey(code)) {
                node.setStatus("CURRENT");
            } else if (his.containsKey(code)) {
                node.setStatus("COMPLETED");
                Map<String, Object> h = his.get(code);
                node.setApprover(h.get("approver") == null ? null : String.valueOf(h.get("approver")));
                if (h.get("create_time") instanceof LocalDateTime t) {
                    node.setActionTime(t);
                }
            } else {
                node.setStatus("PENDING");
            }
            graph.getNodes().add(node);
        }

        for (Map<String, Object> row : engineMapper.selectSkips(definitionId)) {
            CmdFlowTraceVo.GraphEdgeVo edge = new CmdFlowTraceVo.GraphEdgeVo();
            String from = String.valueOf(row.get("now_node_code"));
            String to = String.valueOf(row.get("next_node_code"));
            edge.setFrom(from);
            edge.setTo(to);
            edge.setLabel(row.get("skip_name") == null ? "" : String.valueOf(row.get("skip_name")));
            edge.setSkipType(row.get("skip_type") == null ? "" : String.valueOf(row.get("skip_type")));
            edge.setCondition(row.get("skip_condition") == null ? null : String.valueOf(row.get("skip_condition")));
            edge.setPassed(his.containsKey(from) || running.containsKey(to));
            graph.getEdges().add(edge);
        }
        return graph;
    }

    @Override
    public List<CmdFlowSceneVO> listScenes() {
        List<Map<String, Object>> scenes = flowSceneMapper.selectAllScenes();
        List<CmdFlowSceneVO> result = new ArrayList<>();
        for (Map<String, Object> s : scenes) {
            CmdFlowSceneVO vo = new CmdFlowSceneVO();
            String sceneCode = String.valueOf(s.get("scene_code"));
            String flowCode = s.get("flow_code") == null ? null : String.valueOf(s.get("flow_code"));
            vo.setSceneCode(sceneCode);
            vo.setSceneName(s.get("scene_name") == null ? null : String.valueOf(s.get("scene_name")));
            vo.setFlowCode(flowCode);
            vo.setFlowName(s.get("flow_name") == null ? null : String.valueOf(s.get("flow_name")));
            vo.setSlaHours(s.get("sla_hours") == null ? null : ((Number) s.get("sla_hours")).intValue());
            if (StringUtils.isNotBlank(flowCode)) {
                Long defId = engineMapper.selectPublishedDefinitionId(flowCode);
                vo.setDeployed(defId != null);
                vo.setDefinitionId(defId);
                // 节点数取「总设计泳道图步骤数」（业务蓝图口径），与流程中心泳道图弹窗一致
                vo.setNodeCount(buildSwimlane(sceneCode).size());
                if (defId != null) {
                    vo.setVersion(parseVersion(engineMapper.selectVersion(defId)));
                }
            } else {
                vo.setDeployed(Boolean.FALSE);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public CmdFlowTraceVo.GraphVo graphByScene(String sceneCode) {
        return graphByScene(sceneCode, null);
    }

    @Override
    public CmdFlowTraceVo.GraphVo graphByScene(String sceneCode, String taskNo) {
        // 确保场景已部署并发布到引擎（幂等）
        Long definitionId = deployScene(sceneCode);
        String flowCode = SCENE_FLOW_CODE.getOrDefault(sceneCode, "cmd_customer_create");
        CmdFlowTraceVo.GraphVo graph = new CmdFlowTraceVo.GraphVo();
        graph.setDefinitionId(definitionId);
        graph.setFlowCode(flowCode);

        // 定义视图：以总设计泳道图步骤为节点（含自动节点 + 人工审批节点）。
        // 布局：横向 = 阶段（7 列），纵向 = 泳道（6 行），即泳道图网格。
        List<CmdFlowTraceVo.StepVo> steps = buildSwimlane(sceneCode);

        // 实例视图：taskNo 非空时按该任务的实际进度点亮节点
        if (StringUtils.isNotBlank(taskNo)) {
            CmdApprovalTask task = requireTask(taskNo);
            applyStepStatus(steps, task.getStatus(), task.getCurrentNodeName(), touchedNodes(task));
        }

        graph.setLanes(LANE_ORDER);
        Map<String, Integer> laneSubRow = new HashMap<>();
        List<CmdFlowTraceVo.GraphNodeVo> nodes = graph.getNodes();
        for (int i = 0; i < steps.size(); i++) {
            CmdFlowTraceVo.StepVo s = steps.get(i);
            int phase = s.getPhase() == null ? 1 : s.getPhase();
            int laneIdx = Math.max(0, LANE_ORDER.indexOf(s.getLane()));
            // 同泳道同阶段若出现多个节点，纵向错开避免重叠
            int sub = laneSubRow.merge(laneIdx + "#" + phase, 1, Integer::sum) - 1;
            CmdFlowTraceVo.GraphNodeVo node = new CmdFlowTraceVo.GraphNodeVo();
            node.setNodeCode(s.getNodeCode());
            node.setNodeName(s.getNodeName());
            node.setNodeType(STEP_GATEWAY.equals(s.getNodeType()) ? GATEWAY_NODE_TYPE : TASK_NODE_TYPE);
            node.setShape(stepShape(s.getNodeType(), i, steps.size()));
            node.setLane(s.getLane());
            node.setPhase(phase);
            node.setPhaseName(s.getPhaseName());
            node.setNote(s.getNote());
            node.setX(BASE_X + (phase - 1) * COL_GAP);
            node.setY(BASE_Y + laneIdx * LANE_GAP + sub * SUB_ROW_GAP);
            node.setStatus(s.getStatus());
            node.setApprover(s.getOperator());
            node.setActionTime(s.getActionTime());
            nodes.add(node);
        }

        // 顺序连线（泳道图主流程）：已完成区段连线同步点亮
        for (int k = 0; k + 1 < nodes.size(); k++) {
            CmdFlowTraceVo.GraphEdgeVo edge = new CmdFlowTraceVo.GraphEdgeVo();
            edge.setFrom(nodes.get(k).getNodeCode());
            edge.setTo(nodes.get(k + 1).getNodeCode());
            edge.setLabel("");
            edge.setSkipType(SkipType.PASS.getKey());
            edge.setPassed("COMPLETED".equals(nodes.get(k).getStatus()));
            graph.getEdges().add(edge);
        }
        return graph;
    }

    @Override
    public void applyStepStatus(List<CmdFlowTraceVo.StepVo> steps, String taskStatus,
                                String currentNodeName, Set<String> touchedNodes) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        String status = taskStatus == null ? "" : taskStatus;
        String name = currentNodeName == null ? "" : currentNodeName;
        boolean finished = FINAL_OK_STATUS.contains(status) || name.contains("已完成");
        boolean stopped = FINAL_STOP_STATUS.contains(status);
        String currentCode = resolveCurrentNode(taskStatus, currentNodeName);

        int currentIdx = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getNodeCode().equals(currentCode)) {
                currentIdx = i;
                break;
            }
        }

        for (int i = 0; i < steps.size(); i++) {
            CmdFlowTraceVo.StepVo s = steps.get(i);
            boolean touched = touchedNodes != null && touchedNodes.contains(s.getNodeCode());
            if (finished) {
                s.setStatus("COMPLETED");
            } else if (stopped && i > currentIdx) {
                s.setStatus("TERMINATED");
            } else if (i == currentIdx) {
                s.setStatus("CURRENT");
            } else if (i < currentIdx || touched) {
                s.setStatus("COMPLETED");
            } else {
                s.setStatus("PENDING");
            }
        }
    }

    /** 当前节点编码：任务状态 + 当前节点名 → 泳道模板节点编码 */
    private String resolveCurrentNode(String taskStatus, String currentNodeName) {
        String name = currentNodeName == null ? "" : currentNodeName;
        String status = taskStatus == null ? "" : taskStatus;
        if (FINAL_OK_STATUS.contains(status) || name.contains("已完成") || name.contains("发布")) {
            return "_DONE_";
        }
        if (name.contains("GC")) {
            return NODE_GC_REVIEW;
        }
        if (name.contains("BU")) {
            return NODE_BU_REVIEW;
        }
        if (FINAL_STOP_STATUS.contains(status) || name.contains("退回")) {
            return "INPUT";
        }
        return "APPLY";
    }

    /** 引擎侧已有轨迹的节点编码集合（flow_his_task），用于点亮自动节点 */
    private Set<String> touchedNodes(CmdApprovalTask task) {
        if (task.getFlowInstanceId() == null) {
            return Set.of();
        }
        Set<String> codes = new HashSet<>();
        for (Map<String, Object> row : engineMapper.selectHisTasks(task.getFlowInstanceId())) {
            if (row.get("node_code") != null) {
                codes.add(String.valueOf(row.get("node_code")));
            }
        }
        return codes;
    }

    @Override
    public List<CmdFlowTraceVo.StepVo> buildSwimlane(String sceneCode) {
        boolean isCreateScene = "CUSTOMER_CREATE".equals(sceneCode);
        List<StepTpl> tpl = isCreateScene ? createSceneTemplate() : genericSceneTemplate();
        List<CmdFlowTraceVo.StepVo> steps = new ArrayList<>();
        int order = 1;
        for (StepTpl t : tpl) {
            CmdFlowTraceVo.StepVo s = new CmdFlowTraceVo.StepVo();
            s.setOrder(order++);
            s.setPhase(t.phase());
            s.setPhaseName(t.phaseName());
            s.setLane(t.lane());
            s.setNodeCode(t.nodeCode());
            s.setNodeName(t.nodeName());
            s.setNodeType(t.nodeType());
            s.setNote(t.note());
            s.setStatus("PENDING");
            steps.add(s);
        }
        return steps;
    }

    /** 人工审核节点 → 泳道节点形状（首尾为开始/结束圆，网关为菱形，其余为任务矩形） */
    private static String stepShape(String nodeType, int index, int size) {
        if (index == 0 || index == size - 1) {
            return "CIRCLE";
        }
        if (STEP_GATEWAY.equals(nodeType)) {
            return "DIAMOND";
        }
        return "RECT";
    }

    /**
     * 场景一：单条客户创建（总设计泳道图：7 阶段 × 6 泳道，11 步）
     */
    private List<StepTpl> createSceneTemplate() {
        List<StepTpl> list = new ArrayList<>();
        list.add(new StepTpl(1, "发起", LANE_BU_USER, "APPLY", "创建客户申请", "MANUAL", "选择客户类型、BU、产品线与来源系统，保存草稿或提交"));
        list.add(new StepTpl(2, "数据准备", LANE_BU_USER, "INPUT", "录入与附件", "MANUAL", "填写 GC Core、BU 与来源系统字段，上传营业执照"));
        list.add(new StepTpl(2, "数据准备", LANE_SYS, "OCR", "OCR 与智能补全", "AUTO", "提取工商名称、信用代码和地址；底表检索与地址标准化"));
        list.add(new StepTpl(3, "自动校验", LANE_SYS, "DQ", "技术与业务 DQ", "AUTO", "必填、格式、值集、GC Core、层级与 Payer 校验"));
        list.add(new StepTpl(4, "匹配分流", LANE_SYS, "DUP", "Duplicate Check", "GATEWAY", "以信用代码和经营地址为主依据；名称仅作为辅助线索；SUSPECT 进入人工治理"));
        list.add(new StepTpl(5, "人工治理", LANE_BU_STEWARD, NODE_BU_REVIEW, "BU Scope 初审", "MANUAL", "查看申请与候选证据；确认 Same-BU 或升级 Cross-BU"));
        list.add(new StepTpl(5, "人工治理", LANE_GC_STEWARD, NODE_GC_REVIEW, "GC Scope 决策", "MANUAL", "核对跨 BU 证据；决定关联现有或创建新 One ID"));
        list.add(new StepTpl(6, "审批发布", LANE_SYS, "RESULT", "生成 / 关联结果", "AUTO", "关联已有 One ID；建立 BU 本地码映射并激活主档"));
        list.add(new StepTpl(6, "审批发布", LANE_ADMIN, "PUBLISH", "发布到下游", "MANUAL", "通过 API / 实时 / 批量发布；失败时 Retry / Resubmit"));
        list.add(new StepTpl(7, "追踪审计", LANE_ADMIN, "TRACE", "运行追踪", "AUTO", "查看任务状态、失败原因、重试与发布记录"));
        list.add(new StepTpl(7, "追踪审计", LANE_AUDITOR, "AUDIT", "审计查询", "AUTO", "记录谁、何时、做了什么及 Before / After 与审批证据"));
        return list;
    }

    /**
     * 其余场景：同一 7 阶段骨架的通用模板
     */
    private List<StepTpl> genericSceneTemplate() {
        List<StepTpl> list = new ArrayList<>();
        list.add(new StepTpl(1, "发起", LANE_BU_USER, "APPLY", "提交业务申请", "MANUAL", "Business User 发起申请"));
        list.add(new StepTpl(2, "数据准备", LANE_SYS, "INPUT", "数据装配", "AUTO", "装配申请数据与证据快照"));
        list.add(new StepTpl(3, "自动校验", LANE_SYS, "DQ", "自动校验", "AUTO", "DQ 规则与前置校验（Loop Check / 关联检查等）"));
        list.add(new StepTpl(4, "匹配分流", LANE_SYS, "DUP", "条件分流", "GATEWAY", "按规则变量路由（Same-BU / Cross-BU / 风险等级）"));
        list.add(new StepTpl(5, "人工治理", LANE_BU_STEWARD, NODE_BU_REVIEW, "BU Scope 初审", "MANUAL", "本 BU 数据管家初审"));
        list.add(new StepTpl(5, "人工治理", LANE_GC_STEWARD, NODE_GC_REVIEW, "GC Scope 决策", "MANUAL", "跨 BU 或高风险升级 GC 决策"));
        list.add(new StepTpl(6, "审批发布", LANE_SYS, "RESULT", "执行与生效", "AUTO", "审批通过后执行业务结果并生成版本"));
        list.add(new StepTpl(6, "审批发布", LANE_ADMIN, "PUBLISH", "发布下游", "MANUAL", "通知 API / 文件 / 批量发布；失败即 Retry / Resubmit"));
        list.add(new StepTpl(7, "追踪审计", LANE_ADMIN, "TRACE", "运行追踪", "AUTO", "任务状态与失败原因追踪"));
        list.add(new StepTpl(7, "追踪审计", LANE_AUDITOR, "AUDIT", "审计查询", "AUTO", "Before / After 证据审查"));
        return list;
    }

    // ------------------------------------------------------------------

    private CmdApprovalTask requireTask(String taskNo) {
        CmdApprovalTask task = taskMapper.selectOne(new LambdaQueryWrapper<CmdApprovalTask>()
            .eq(CmdApprovalTask::getTaskNo, taskNo)
            .last("LIMIT 1"));
        if (task == null) {
            throw new ServiceException("任务不存在：" + taskNo);
        }
        return task;
    }

    /** 办理人：Warm-Flow 创建任务时不能为空（处理人 → 申请人 → 登录人 → 管理员） */
    private String resolveHandler(CmdApprovalTask task) {
        Long uid = task.getAssigneeId() != null ? task.getAssigneeId() : task.getApplicantId();
        if (uid == null) {
            try {
                uid = LoginHelper.getUserId();
            } catch (Exception ignored) {
                uid = null;
            }
        }
        return uid == null ? DEFAULT_HANDLER : String.valueOf(uid);
    }

    /**
     * 业务当前节点名 → 引擎需要补齐的节点路径（按顺序跳过）。
     * <p>
     * 业务单据的 current_node_name 由业务侧维护（如「BU Steward 初审」「GC Steward 决策」），
     * 引擎实例固定从 APPLY 起步，需要按业务口径走到对应节点，两边镜像才一致。
     *
     * @param currentNodeName 业务当前节点名
     * @return 需要依次跳转到的引擎节点编码，已一致时返回空列表
     */
    private List<String> enginePathOfBusiness(String currentNodeName) {
        String name = currentNodeName == null ? "" : currentNodeName;
        if (name.contains("GC")) {
            return List.of(NODE_BU_REVIEW, NODE_GC_REVIEW);
        }
        if (name.contains("BU")) {
            return List.of(NODE_BU_REVIEW);
        }
        return List.of();
    }

    private String normalizeScene(CmdApprovalTask task) {
        String scene = task.getSceneCode();
        if (StringUtils.isNotBlank(scene) && SCENE_FLOW_CODE.containsKey(scene)) {
            return scene;
        }
        String biz = task.getBizType() == null ? "" : task.getBizType();
        if (biz.contains("合并") || biz.contains("重复")) {
            return "MERGE";
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
        return "CUSTOMER_CREATE";
    }

    private String sceneName(String sceneCode) {
        Map<String, Object> scene = flowSceneMapper.selectSceneByCode(sceneCode);
        Object name = scene == null ? null : scene.get("flow_name");
        return name == null ? sceneCode : String.valueOf(name);
    }

    private static String shapeOf(String code, Integer nodeType) {
        if (NODE_START.equals(code) || NODE_END.equals(code)) {
            return "CIRCLE";
        }
        if (NodeType.isGateWay(nodeType) != null && NodeType.isGateWay(nodeType)) {
            return "DIAMOND";
        }
        return "RECT";
    }

    /** 从版本字符串（如 v1.0）中解析出主版本号整数，无法解析时返回 1 */
    private static Integer parseVersion(String version) {
        if (StringUtils.isBlank(version)) {
            return 1;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : version.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 1;
    }
}
