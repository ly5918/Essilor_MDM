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
import org.dromara.cmd.mapper.CmdFlowStepDetailMapper;
import org.dromara.cmd.domain.vo.CmdOcrRecognizeVo;
import org.dromara.cmd.domain.vo.CmdOcrResultVo;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.cmd.service.ICmdOcrService;
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
    private final CmdFlowStepDetailMapper stepDetailMapper;
    private final ICmdFlowEngineService flowEngineService;
    private final ICmdOcrService ocrService;

    /** 泳道名称常量（与总设计泳道图一致；步骤模板见 ICmdFlowEngineService#buildSwimlane） */
    private static final String LANE_ADMIN = "Platform Admin";

    /** 关键节点编码（用于当前节点匹配；审批人规则来源：cmd_flow_node_rule） */
    private static final String NODE_BU_REVIEW = "BU_REVIEW";
    private static final String NODE_GC_REVIEW = "GC_REVIEW";
    private static final Set<String> MANUAL_REVIEW_NODES = Set.of(NODE_BU_REVIEW, NODE_GC_REVIEW);

    /** 空值占位（字段与表格统一，避免前端出现空白单元格） */
    private static final String DASH = "—";

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
        fillStepDetails(vo, task);
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

    // ------------------------------------------------------------------
    // 分步骤明细（「流程跟踪」点击某个泳道节点后，在步骤条下方动态展示该节点的相关内容）
    // ------------------------------------------------------------------

    /**
     * 为每个泳道步骤装配节点级明细。
     * <p>
     * 原则：<b>只读真实业务表，不臆造数据</b>。某个来源在 POC 环境尚无数据时，
     * 用 notes 说明口径（例如「POC 走预置识别、未落 cmd_ocr_result」），而不是编内容。
     */
    private void fillStepDetails(CmdFlowTraceVo vo, CmdApprovalTask task) {
        String oneId = task.getOneId();
        Map<String, Object> customer = StringUtils.isNotBlank(oneId) ? stepDetailMapper.selectCustomer(oneId) : null;

        // 批量导入场景：申请不挂单一 One ID，业务主键是批次号（cmd_approval_task.biz_id = cmd_import_job.job_code）。
        // 不解析的话「数据装配 / 自动校验」会整屏显示「—」，看着像没数据，实际是取错了数据源。
        Map<String, Object> importJob = null;
        List<Map<String, Object>> importRows = new ArrayList<>();
        if (customer == null && StringUtils.isNotBlank(task.getBizId())) {
            importJob = stepDetailMapper.selectImportJob(task.getBizId());
            if (importJob != null) {
                importRows = stepDetailMapper.selectImportRows(task.getBizId());
            }
        }

        List<Map<String, Object>> actions = stepDetailMapper.selectApprovalActions(task.getId());

        for (CmdFlowTraceVo.StepVo step : vo.getSteps()) {
            vo.getStepDetails().add(buildStepDetail(vo, task, step, customer, actions, importJob, importRows));
        }
    }

    /** 按节点编码分发到各自的明细装配逻辑（节点语义留在服务端，前端只负责渲染） */
    private CmdFlowTraceVo.StepDetailVo buildStepDetail(CmdFlowTraceVo vo, CmdApprovalTask task,
                                                       CmdFlowTraceVo.StepVo step,
                                                       Map<String, Object> customer,
                                                       List<Map<String, Object>> actions,
                                                       Map<String, Object> importJob,
                                                       List<Map<String, Object>> importRows) {
        CmdFlowTraceVo.StepDetailVo d = new CmdFlowTraceVo.StepDetailVo();
        d.setNodeCode(step.getNodeCode());
        d.setNodeName(step.getNodeName());
        d.setPhaseName(step.getPhaseName());
        d.setLane(step.getLane());
        d.setStatus(step.getStatus());

        String node = step.getNodeCode() == null ? "" : step.getNodeCode().toUpperCase();
        switch (node) {
            case "APPLY" -> applyDetail(d, vo, task, importJob);
            case "INPUT" -> inputDetail(d, task, customer, importJob, importRows);
            case "OCR" -> ocrDetail(d, task, customer);
            case "DQ" -> dqDetail(d, task, customer, importJob, importRows);
            case "DUP" -> dupDetail(d, task, customer);
            case NODE_BU_REVIEW, NODE_GC_REVIEW -> reviewDetail(d, task, step, actions);
            case "RESULT" -> resultDetail(d, task, customer);
            case "PUBLISH" -> publishDetail(d, task);
            case "TRACE" -> traceDetail(d, task);
            case "AUDIT" -> auditDetail(d, task);
            default -> {
                d.setSummary("该节点暂无节点级明细模板");
                d.getNotes().add("节点编码 " + s(step.getNodeCode())
                    + " 未配置明细来源，可在「平台管理 › Workflow › 配置」中补充节点规则。");
            }
        }
        return d;
    }

    /** 发起：申请信息 + 提交动作（cmd_approval_task + cmd_customer_version 口径） */
    private void applyDetail(CmdFlowTraceVo.StepDetailVo d, CmdFlowTraceVo vo, CmdApprovalTask task,
                             Map<String, Object> importJob) {
        d.setSummary(s(task.getApplicantName()) + " 于 " + dt(task.getSubmitTime()) + " 提交「"
            + s(task.getBizTitle()) + "」的" + s(vo.getSceneName()) + "申请");
        field(d, "申请编号", task.getTaskNo());
        field(d, "客户主题", task.getBizTitle());
        field(d, "业务类型", task.getBizType());
        field(d, "归属 BU", task.getBuScope());
        field(d, "风险等级", task.getRiskLevel(), riskTone(task.getRiskLevel()));
        field(d, "申请人", task.getApplicantName());
        field(d, "提交时间", dt(task.getSubmitTime()));
        field(d, "场景流程", s(vo.getFlowName()) + "（" + s(vo.getFlowCode()) + "）");
        d.getNotes().add("提交同时生成 One ID 与首版本快照（cmd_customer / cmd_customer_version），并写入 SUBMIT 轨迹。");
        d.getNotes().add("OCR / DQ / Duplicate Check 为系统自动节点，随提交一次执行完，不占用流程引擎用户任务。");
        if (importJob != null) {
            d.getNotes().add("本次为批量导入申请：业务主键是批次号 " + s(importJob.get("jobCode"))
                + "（cmd_import_job），One ID 由 New 行在批准后逐行生成，因此该申请本身不挂单一 One ID。");
        }
    }

    /** 录入与附件：主档录入字段 + 附件清单（cmd_customer + cmd_attachment） */
    private void inputDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task, Map<String, Object> c,
                             Map<String, Object> importJob, List<Map<String, Object>> importRows) {
        // 批量导入：没有单一客户主档，装配的是「批次 + 行」，取批次与行级真实数据
        if (c == null && importJob != null) {
            inputDetailForImport(d, task, importJob, importRows);
            return;
        }
        d.setSummary("录入客户主档核心字段并上传营业执照等证明材料，提交时完成落库与首版本快照");
        if (c == null) {
            d.getNotes().add("该申请未关联客户主档（cmd_approval_task.one_id 为空），因此无法展示主档字段——"
                + "这不是数据丢失，而是该场景本身不以单一客户为装配对象。");
        }
        field(d, "法定名称", cv(c, "legalName"));
        field(d, "英文名称", cv(c, "legalNameEn"));
        field(d, "统一社会信用代码", cv(c, "creditCode"), isBlank(cv(c, "creditCode")) ? "warning" : null);
        field(d, "税号", cv(c, "taxNo"));
        field(d, "客户类型", cv(c, "customerType"));
        field(d, "产品线", cv(c, "productLine"));
        field(d, "国家 / 省 / 市", s(cv(c, "country")) + " / " + s(cv(c, "province")) + " / " + s(cv(c, "city")));
        field(d, "注册地址", cv(c, "address"));
        field(d, "联系人", cv(c, "contactName"));
        field(d, "联系电话", cv(c, "contactPhone"));
        field(d, "联系邮箱", cv(c, "contactEmail"));
        field(d, "来源系统", cv(c, "sourceSystem"));

        List<Map<String, Object>> files = stepDetailMapper.selectAttachments(task.getOneId());
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> f : files) {
            rows.add(List.of(s(f.get("fileName")), s(f.get("category")), s(f.get("ocrStatus")),
                s(f.get("fileType")), s(f.get("fileSize")), s(f.get("createTime"))));
        }
        table(d, "附件清单（cmd_attachment，biz_id = One ID）",
            List.of("文件名", "分类", "OCR 状态", "类型", "大小(字节)", "上传时间"), rows);
        if (rows.isEmpty()) {
            d.getNotes().add("该实例未落附件记录：POC 演示环境走「按文件名预置识别」的模拟 OCR，不生成实体附件；"
                + "生产接入后由 OCR Core 写入 cmd_attachment（biz_type=CUSTOMER）。");
        }
        d.getNotes().add("展示的是主档当前值：本次未录入的字段（如英文名称 / 税号 / 联系邮箱）在 cmd_customer 中确为 NULL，"
            + "因此显示「—」；这些也正是 DQ 的扣分项来源（见「自动校验」节点）。");
    }

    /**
     * 数据装配（批量导入口径）：批次字段 + 行级实际录入值。
     * <p>
     * 批次内的每条行都带 parsed_json（模板映射后的字段），这里取<b>典型行</b>（优先审批通过生成 One ID 的 NEW 行）
     * 的字段值填入法定名称 / 信用代码 / 地址 / 电话等栏位，保证「数据装配」看到的是真实导入内容而不是「—」。
     */
    private void inputDetailForImport(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task,
                                      Map<String, Object> job, List<Map<String, Object>> importRows) {
        Map<String, Object> sample = pickSampleRow(importRows);
        boolean hasSample = !sample.isEmpty();

        d.setSummary("按模板解析导入文件并按行装配数据：批次 " + s(job.get("jobCode")) + "（"
            + s(job.get("fileName")) + "），共 " + s(job.get("totalCount")) + " 行");

        field(d, "导入批次", s(job.get("jobCode")) + " · " + s(job.get("jobName")));
        field(d, "导入文件", job.get("fileName"));
        field(d, "导入模板", s(job.get("templateCode")) + " " + s(job.get("templateVersion")));
        field(d, "业务场景", job.get("scene"));
        field(d, "归属 BU", job.get("buScope"));
        field(d, "总行数 / 成功", s(job.get("totalCount")) + " / " + s(job.get("successCount")));
        field(d, "分流结果", "Exact " + s(job.get("exactCount")) + " · New " + s(job.get("newCount"))
            + " · Suspected " + s(job.get("suspectedCount")) + " · Invalid " + s(job.get("invalidCount")),
            num(job.get("invalidCount")) > 0 ? "warning" : null);
        field(d, "错误策略 / 重复策略", s(job.get("errorStrategy")) + " / " + s(job.get("duplicateStrategy")));
        field(d, "提交人", job.get("submitBy"));
        field(d, "提交时间", dt(job.get("submitTime")));

        if (hasSample) {
            field(d, "法定名称（样例行）", sample.get("legalName"));
            field(d, "英文名称（样例行）", sample.get("legalNameEn"));
            field(d, "统一社会信用代码（样例行）", sample.get("creditCode"));
            field(d, "税号（样例行）", sample.get("taxNo"));
            field(d, "产品线（样例行）", sample.get("productLine"));
            field(d, "城市 / 省份（样例行）", s(sample.get("city")) + " / " + s(sample.get("province")));
            field(d, "注册地址（样例行）", sample.get("address"));
            field(d, "联系人（样例行）", sample.get("contactName"));
            field(d, "联系电话（样例行）", sample.get("contactPhone"));
            field(d, "联系邮箱（样例行）", sample.get("contactEmail"));
        }
        field(d, "来源系统", "EXCEL 批量导入（cmd_import_row）");

        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> r : importRows) {
            rows.add(List.of(s(r.get("rowNo")), s(r.get("legalName")), s(r.get("creditCode")),
                s(r.get("resultType")), s(r.get("rowStatus")), s(r.get("oneId")), s(r.get("dqScore")),
                s(r.get("errorCount")), s(r.get("errorSummary"))));
        }
        table(d, "导入行明细（cmd_import_row）",
            List.of("行号", "客户名称", "统一社会信用代码", "分流结果", "行状态", "生成 One ID", "DQ 分", "错误数", "错误摘要"), rows);

        d.getNotes().add("批量导入申请不挂单一 One ID：装配对象是「批次 + 行」，"
            + "上表每一行都是文件里的真实数据行，DQ 分与错误数由导入引擎逐行计算后落库。");
        d.getNotes().add("样例行取「审批通过并生成 One ID」的那一行；该行的字段值由模板映射写入 cmd_import_row.parsed_json。");
    }

    /**
     * 自动校验（批量导入口径）：逐行 DQ 分与错误明细。
     * <p>
     * 与单客户口径保持一致（100 起、缺关键字段扣分、≥90 A / ≥75 B / ≥60 C / &lt;60 D），
     * 但校验对象是<b>每一条导入行</b>：分值、错误数、错误摘要全部读 cmd_import_row 的真实落库值，
     * 不做任何二次估算，避免「当前值」与「扣分」对不上。
     */
    private void dqDetailForImport(CmdFlowTraceVo.StepDetailVo d, Map<String, Object> job,
                                   List<Map<String, Object>> importRows) {
        List<List<String>> rows = new ArrayList<>();
        int errorTotal = 0;
        int invalidRows = 0;
        int minScore = 100;
        double sum = 0;
        int scored = 0;
        for (Map<String, Object> r : importRows) {
            int err = num(r.get("errorCount"));
            errorTotal += err;
            if (err > 0 || "INVALID".equalsIgnoreCase(str(r.get("resultType")))) {
                invalidRows++;
            }
            double score = toDouble(r.get("dqScore"));
            if (score > 0) {
                sum += score;
                scored++;
                minScore = (int) Math.min(minScore, score);
            }
            rows.add(List.of(s(r.get("rowNo")), s(r.get("legalName")), s(r.get("creditCode")),
                s(r.get("dqScore")), gradeOf(score),
                err == 0 ? "通过" : "扣分", err == 0 ? "0" : "-" + err * 4,
                s(r.get("errorSummary"))));
        }
        double avg = scored == 0 ? 0 : sum / scored;

        d.setSummary("按行校验 " + importRows.size() + " 条：平均 DQ " + String.format("%.2f", avg)
            + "（最低 " + (scored == 0 ? DASH : minScore) + "），命中问题行 " + invalidRows + " 条、错误合计 "
            + errorTotal + " 项");
        field(d, "校验口径", "逐行 DQ（100 起，按缺失 / 格式错误扣分）");
        field(d, "平均 DQ 分", String.format("%.2f", avg), avg >= 90 ? "success" : "warning");
        field(d, "最低 DQ 分", scored == 0 ? DASH : String.valueOf(minScore), minScore < 60 ? "danger" : null);
        field(d, "问题行数", invalidRows + " / " + importRows.size(), invalidRows > 0 ? "warning" : "success");
        field(d, "错误合计", errorTotal + " 项", errorTotal > 0 ? "warning" : "success");
        field(d, "Invalid 行数", s(job.get("invalidCount")), num(job.get("invalidCount")) > 0 ? "danger" : null);
        field(d, "错误策略", s(job.get("errorStrategy")));
        field(d, "路由结论", invalidRows == 0
            ? "全部通过，New 行生成 One ID 后发布"
            : "存在 Invalid / Suspected 行：Invalid 退回修复，Suspected 进入治理，其余正常发布");
        table(d, "逐行校验明细（cmd_import_row）",
            List.of("行号", "客户名称", "统一社会信用代码", "DQ 分", "等级", "结果", "扣分", "错误摘要"), rows);

        d.getNotes().add("DQ 分与 error_count / error_summary 由导入引擎逐行落库（cmd_import_row），"
            + "本节点只做汇总展示，不做二次估算。");
        d.getNotes().add("等级口径与单客户一致：≥90 A、≥75 B、≥60 C、<60 D；"
            + "每行每命中一项校验错误扣 4 分（缺必填列 / 格式错误各计 1 项）。");
        d.getNotes().add("错误策略 " + s(job.get("errorStrategy")) + "：Invalid 行按策略退回修复（REJECT_ROW）或整批中止（REJECT_BATCH）。");
    }

    /** 取样例行：优先 NEW（生成了 One ID），其次 EXACT，最后取第一行 */
    private Map<String, Object> pickSampleRow(List<Map<String, Object>> importRows) {
        if (importRows == null || importRows.isEmpty()) {
            return new HashMap<>();
        }
        for (String type : new String[]{"NEW", "EXACT", "SUSPECTED"}) {
            for (Map<String, Object> r : importRows) {
                if (type.equalsIgnoreCase(str(r.get("resultType")))) {
                    return r;
                }
            }
        }
        return importRows.get(0);
    }

    /** OCR 与智能补全：识别字段与回填结果（cmd_ocr_result，缺失时展示主档实际回填值） */
    private void ocrDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task, Map<String, Object> c) {
        d.setSummary("营业执照识别完成，抽取客户法定名称 / 统一社会信用代码 / 注册地址 / 省份 / 城市 并回填主档");
        List<Map<String, Object>> ocrRows = stepDetailMapper.selectOcrResults(task.getOneId());
        List<List<String>> rows = new ArrayList<>();
        boolean persisted = !ocrRows.isEmpty();
        for (Map<String, Object> r : ocrRows) {
            Object confirmed = r.get("confirmedValue") == null ? r.get("ocrValue") : r.get("confirmedValue");
            rows.add(List.of(s(r.get("fieldName")), s(confirmed),
                "Y".equalsIgnoreCase(str(r.get("needsReview"))) ? "待人工复核" : "已回填主档",
                s(r.get("confidence"))));
        }
        if (!persisted) {
            // POC 的 OCR 走预置结果、不落 cmd_ocr_result：
            // 这里展示识别后**实际回填到主档**的字段值（真实数据，非编造）
            // 置信度不是编的：按客户主档反查生成这批数据的预置素材，取其各字段原值
            Map<String, String> confByField = new HashMap<>();
            CmdOcrRecognizeVo preset = ocrService.recognizeOfCustomer(str(cv(c, "legalName")), str(cv(c, "creditCode")));
            if (preset != null && preset.getFields() != null) {
                for (CmdOcrResultVo f : preset.getFields()) {
                    confByField.put(f.getField(), f.getConfidence());
                }
            }
            rows.add(List.of("客户法定名称", s(cv(c, "legalName")), "已回填主档",
                confByField.getOrDefault("客户法定名称", DASH)));
            rows.add(List.of("统一社会信用代码", s(cv(c, "creditCode")), "已回填主档",
                confByField.getOrDefault("统一社会信用代码", DASH)));
            rows.add(List.of("注册地址", s(cv(c, "address")), "已回填主档",
                confByField.getOrDefault("注册地址", DASH)));
            rows.add(List.of("省份", s(cv(c, "province")), "已回填主档",
                confByField.getOrDefault("省份", DASH)));
            rows.add(List.of("城市", s(cv(c, "city")), "已回填主档",
                confByField.getOrDefault("城市", DASH)));
        }
        field(d, "识别引擎", persisted ? s(ocrRows.get(0).get("ocrEngine")) : "POC 预置识别（CmdOcrServiceImpl）");
        field(d, "识别字段数", rows.size());
        field(d, "识别结果来源", persisted ? "cmd_ocr_result" : "主档回填值（未落识别明细表）");
        table(d, "识别字段与回填结果", List.of("字段", "识别值", "回填状态", "置信度"), rows);
        if (!persisted) {
            d.getNotes().add("POC 环境 OCR 按文件名匹配预置结果（license_0x.png），因此 cmd_ocr_result 为空；"
                + "上表「识别值」是识别后实际写入客户主档的字段值（cmd_customer），"
                + "「置信度」取生成这批数据的预置素材原值（按法定名称 / 信用代码反查同一素材），非编造。");
        }
        d.getNotes().add("人工复核：置信度低于阈值的字段由 OCR Core 置 needs_review=Y，在「新建客户申请」页提示确认后才允许提交。");
    }

    /** 技术与业务 DQ：检查项明细 + 分数等级（cmd_customer.dq_score 冗余分值） */
    private void dqDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task, Map<String, Object> c,
                          Map<String, Object> importJob, List<Map<String, Object>> importRows) {
        // 批量导入：按行校验，扣分来自 cmd_import_row 的逐行 DQ 分与 error_count（真实落库值）
        if (c == null && importJob != null) {
            dqDetailForImport(d, importJob, importRows);
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        int deducted = 0;
        int hits = 0;

        int hit = dqRow(rows, "统一社会信用代码", "必填（关键匹配字段）", cv(c, "creditCode"), 12);
        deducted += hit;
        hits += hit > 0 ? 1 : 0;
        hit = dqRow(rows, "注册地址", "必填完整性", cv(c, "address"), 8);
        deducted += hit;
        hits += hit > 0 ? 1 : 0;

        boolean regionMissing = isBlank(cv(c, "province")) || isBlank(cv(c, "city"));
        rows.add(List.of("省份 + 城市", "值域规范性", s(cv(c, "province")) + " / " + s(cv(c, "city")),
            regionMissing ? "扣分" : "通过", regionMissing ? "-4" : "0"));
        if (regionMissing) {
            deducted += 4;
            hits++;
        }
        hit = dqRow(rows, "联系人", "完整性", cv(c, "contactName"), 4);
        deducted += hit;
        hits += hit > 0 ? 1 : 0;
        hit = dqRow(rows, "联系电话", "完整性", cv(c, "contactPhone"), 4);
        deducted += hit;
        hits += hit > 0 ? 1 : 0;

        // 提示类检查：同样展示主档当前值，但不参与打分（保持与引擎打分口径一致，避免复算分值与存储分值对不上）
        int infoMiss = 0;
        infoMiss += infoRow(rows, "法定名称", "必填（主档唯一标识）", cv(c, "legalName"));
        infoMiss += infoRow(rows, "客户类型", "值域规范性", cv(c, "customerType"));
        infoMiss += infoRow(rows, "税号", "完整性（提示）", cv(c, "taxNo"));
        infoMiss += infoRow(rows, "联系邮箱", "完整性（提示）", cv(c, "contactEmail"));
        infoMiss += infoRow(rows, "产品线", "完整性（提示）", cv(c, "productLine"));

        int recomputed = Math.max(0, 100 - deducted);
        int stored = task.getDqScore() == null ? recomputed : task.getDqScore().intValue();

        d.setSummary("DQ 质量分 " + s(task.getDqScore()) + "（等级 " + s(cv(c, "dqGrade")) + "）："
            + (hits == 0 ? "5 项计分检查全部通过" : "命中 " + hits + " 项扣分，共扣 " + deducted + " 分")
            + (infoMiss == 0 ? "" : "；另有 " + infoMiss + " 项提示缺失（不计分）"));
        field(d, "DQ 总分", task.getDqScore(), hits == 0 ? "success" : "warning");
        field(d, "质量等级", cv(c, "dqGrade"));
        field(d, "扣分合计", "-" + deducted);
        field(d, "提示项", infoMiss + " 项（不计分）", infoMiss > 0 ? "info" : "success");
        field(d, "路由结论", hits == 0 ? "自动校验通过，进入匹配分流" : "存在扣分项，进入人工治理时需重点复核");
        table(d, "DQ 检查项明细", List.of("检查维度", "规则", "当前值", "结果", "扣分"), rows);
        d.getNotes().add("分值与等级来自 cmd_customer.dq_score / dq_grade（提交时确定性打分：缺信用代码 -12、"
            + "缺地址 -8、缺省市 -4、缺联系人 -4、缺电话 -4；≥90→A、≥75→B、≥60→C、<60→D）。"
            + "「当前值」列直接读主档当前值，缺失即显示 —，与扣分一一对应。");
        d.getNotes().add("按同一规则对当前主档复算得 " + recomputed + " 分，"
            + (recomputed == stored ? "与存储分值一致。" : "与存储分值（" + stored + "）不一致——说明主档在打分后被修改过。")
            + " POC 未落 dq_result / dq_result_detail 明细表。");
        d.getNotes().add("计分项之外的维度（法定名称 / 客户类型 / 税号 / 联系邮箱 / 产品线）作为提示项展示："
            + "它们在主档里确为 NULL 时同样显示 —，但不扣分，避免与引擎分值口径冲突。");
    }

    /** 提示类检查行（不计分）：返回 1 表示缺失 */
    private static int infoRow(List<List<String>> rows, String dimension, String rule, Object value) {
        boolean missing = value == null || String.valueOf(value).isBlank();
        rows.add(List.of(dimension, rule, s(value), missing ? "缺失（提示）" : "通过", "0"));
        return missing ? 1 : 0;
    }

    /** Duplicate Check：匹配结论 + 候选清单（cmd_match_candidate，缺失时按信用代码复算） */
    private void dupDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task, Map<String, Object> c) {
        String matchState = str(cv(c, "matchState"));
        String creditCode = str(cv(c, "creditCode"));
        List<Map<String, Object>> candidates = stepDetailMapper.selectMatchCandidates(task.getOneId());
        boolean persisted = !candidates.isEmpty();
        if (!persisted && StringUtils.isNotBlank(creditCode)) {
            candidates = stepDetailMapper.selectSameCreditCandidates(task.getOneId(), creditCode);
        }

        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> m : candidates) {
            rows.add(List.of(s(m.get("oneId")), s(m.get("legalName")), s(m.get("creditCode")),
                s(m.get("buScope")), s(m.get("totalScore") == null ? m.get("dqScore") : m.get("totalScore")),
                persisted && "Y".equalsIgnoreCase(str(m.get("isBest"))) ? "最佳候选" : "候选"));
        }

        d.setSummary("匹配结论 " + s(matchState) + "，候选 " + rows.size() + " 条");
        field(d, "匹配状态", StringUtils.isNotBlank(matchState) ? matchState : task.getDuplicateState(),
            "SUSPECT".equalsIgnoreCase(matchState) || "EXACT".equalsIgnoreCase(matchState) ? "warning" : "success");
        field(d, "疑似重复标记", "Y".equalsIgnoreCase(str(cv(c, "duplicateFlag"))) ? "是" : "否");
        field(d, "路由结论", routingOf(matchState));
        table(d, persisted ? "匹配候选（cmd_match_candidate）" : "匹配候选（按统一社会信用代码复算）",
            List.of("One ID", "法定名称", "统一社会信用代码", "归属 BU", "匹配分", "判定"), rows);
        if (rows.isEmpty()) {
            d.getNotes().add(StringUtils.isBlank(creditCode)
                ? "该客户未填统一社会信用代码，无法做精确匹配（对应 DQ 扣 12 分），只能按名称相似度人工判断。"
                : "未发现同统一社会信用代码的其他主数据，按 NEW 直接进入人工初审。");
        }
        if (!persisted) {
            d.getNotes().add("POC 环境 cmd_match_candidate 未落库，上表按统一社会信用代码精确匹配复算；"
                + "生产接入后由匹配引擎写入候选与字段级比对结果（field_compare_json）。");
        }
        d.getNotes().add("SUSPECTED 候选必须经 BU Scope 初审确认（Same-BU 关联）或升级 GC 做跨 BU 决策，不允许自动合并。");
    }

    /** 人工治理（BU 初审 / GC 决策）：办理信息 + 会签方式 + 审批动作（cmd_approval_action） */
    private void reviewDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task, CmdFlowTraceVo.StepVo step,
                              List<Map<String, Object>> actions) {
        String node = step.getNodeCode().toUpperCase();
        boolean gc = NODE_GC_REVIEW.equals(node);

        d.setSummary(step.getOpinion() != null && !step.getOpinion().isBlank()
            ? step.getOpinion()
            : (gc ? "等待 GC Steward 做跨 BU 证据核对与 One ID 决策" : "等待 BU Steward 初审：确认 Same-BU 证据或升级 Cross-BU"));

        field(d, "节点", step.getNodeName());
        field(d, "泳道", step.getLane());
        field(d, "办理人", step.getAssignee() == null ? task.getAssigneeName() : step.getAssignee());
        field(d, "办理角色", gc ? "GC_STEWARD" : "BU_STEWARD");
        field(d, "决策时限（SLA）", dt(task.getSlaDue()));
        field(d, "到达时间", dt(step.getActionTime()));
        field(d, "执行状态", statusLabel(step.getStatus()), statusTone(step.getStatus()));

        // 会签 / 或签 与默认办理角色（cmd_flow_node_rule）
        Map<String, Object> rule = flowSceneMapper.selectNodeDefault(node.toLowerCase());
        if (rule != null) {
            String multi = str(rule.get("multi_mode"));
            field(d, "会签方式", "ALL".equalsIgnoreCase(multi) ? "会签（全部办理人通过）" : "或签（任一办理人通过）");
            field(d, "适用范围", rule.get("scope_type"));
            field(d, "规则默认角色", rule.get("assignee_value"));
        }

        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> a : actions) {
            String to = a.get("toNodeCode") == null ? "" : String.valueOf(a.get("toNodeCode")).toUpperCase();
            String mapped = nodeOfActionType(str(a.get("actionType")));
            if (!node.equals(to) && !node.equals(mapped)) {
                continue;
            }
            rows.add(List.of(s(a.get("actionTime")), s(a.get("actionName") == null ? a.get("actionType") : a.get("actionName")),
                s(a.get("operatorName")), s(a.get("operatorRole")),
                s(a.get("beforeState")) + " → " + s(a.get("afterState")), s(a.get("opinion"))));
        }
        table(d, (gc ? "GC 决策动作" : "BU 初审动作") + "（cmd_approval_action）",
            List.of("时间", "动作", "操作人", "角色", "状态流转", "意见"), rows);
        if (rows.isEmpty()) {
            d.getNotes().add("该节点尚未产生审批动作，当前为待办状态（工作项列表可领取处理）。");
        }
        d.getNotes().add(gc
            ? "GC 决策结果：关联已有 One ID（LINK）/ 新创主数据（CREATE_NEW）/ 确认合并（MERGE）。"
            : "BU 初审结果：确认 Same-BU（关联本地组织）/ 升级 GC（Cross-BU）/ 退回补充证据。");
    }

    /** 生成 / 关联结果：主档结果状态 + 来源系统编码映射（cmd_customer + cmd_legacy_mapping） */
    private void resultDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task, Map<String, Object> c) {
        d.setSummary("生成 / 关联结果：One ID " + s(task.getOneId()) + "，主数据状态 " + s(cv(c, "status")));
        field(d, "One ID", task.getOneId());
        field(d, "主数据状态", cv(c, "status"));
        field(d, "质量等级", cv(c, "dqGrade"));
        field(d, "匹配状态", cv(c, "matchState"));
        field(d, "版本号", cv(c, "versionNo"));
        field(d, "生效时间", cv(c, "effectiveFrom"));
        field(d, "流程实例 ID", cv(c, "flowInstanceId"));
        field(d, "引擎状态镜像", cv(c, "flowStatus"));

        List<Map<String, Object>> mappings = stepDetailMapper.selectLegacyMappings(task.getOneId());
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> m : mappings) {
            rows.add(List.of(s(m.get("sourceSystem")), s(m.get("sourceCode")), s(m.get("sourceName")),
                s(m.get("buScope")), s(m.get("mappingType")), "0".equals(str(m.get("status"))) ? "有效" : "停用"));
        }
        table(d, "来源系统编码映射（cmd_legacy_mapping）", List.of("来源系统", "来源编码", "来源名称", "BU", "类型", "状态"), rows);
        if (rows.isEmpty()) {
            d.getNotes().add("该客户暂无本地编码映射；建立后 One ID 与 DMS+ / SAP / Cloud 编码一一对应，避免多系统重复建档。");
        }
        d.getNotes().add("批准后调用层级服务登记节点（hierarchy_type=UNASSIGNED 占位），归位由 Steward 在「客户层级」页完成。");
    }

    /** 发布到下游：集成通道运行记录（int_run） */
    private void publishDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task) {
        d.setSummary("按主数据发布契约，把 One ID 与编码映射下发到下游系统（DMS+ / SAP / Cloud）");
        List<Map<String, Object>> runs = stepDetailMapper.selectRecentIntegrationRuns();
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> r : runs) {
            rows.add(List.of(s(r.get("runCode")), s(r.get("endpointName")), s(r.get("targetSystem")),
                s(r.get("direction")), s(r.get("runStatus")),
                s(r.get("successCount")) + " / " + s(r.get("totalCount")), s(r.get("failedCount")),
                s(r.get("attemptCount")), s(r.get("durationMs")), s(r.get("startTime"))));
        }
        table(d, "集成通道运行记录（int_run，按通道最近 5 次）",
            List.of("运行编号", "端点", "目标系统", "方向", "状态", "成功/总数", "失败", "重试", "耗时(ms)", "开始时间"), rows);
        d.getNotes().add("int_run 为通道级运行记录（POC 未建客户级下发明细 int_message），用于说明下发通路的实时健康度。");
        d.getNotes().add("下发失败不阻塞主流程，由 Retry / Resubmit 机制重投；可在「集成监控」页下钻失败原因并手动重试。");
    }

    /** 运行追踪：按 One ID 串联的步骤执行总账（cmd_workflow_step_log） */
    private void traceDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task) {
        List<Map<String, Object>> logs = stepDetailMapper.selectStepLogs(task.getOneId());
        d.setSummary("按 One ID 串联的步骤执行总账共 " + logs.size() + " 条，任务状态 " + s(task.getStatus()));
        field(d, "One ID", task.getOneId());
        field(d, "任务编号", task.getTaskNo());
        field(d, "流程实例 ID", task.getFlowInstanceId());
        field(d, "引擎状态镜像", task.getFlowStatus());
        field(d, "当前节点", task.getCurrentNodeName());
        field(d, "当前处理人", task.getAssigneeName());

        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> l : logs) {
            rows.add(List.of(s(l.get("stepSeq")), s(l.get("stepType")), s(l.get("nodeName")),
                s(l.get("actionName")), s(l.get("operatorName")), s(l.get("operatorRole")),
                s(l.get("createTime")), s(l.get("opinion"))));
        }
        table(d, "步骤执行日志（cmd_workflow_step_log）",
            List.of("序号", "类型", "节点", "动作", "操作人", "角色", "时间", "意见"), rows);
        if (rows.isEmpty()) {
            d.getNotes().add("该 One ID 暂无步骤日志；每次提交 / 系统自动检查 / 人工决策都会写入一条，作为跨系统追溯主键。");
        }
        d.getNotes().add("cmd_workflow_step_log 以 One ID 为追溯主键，是 CMD / Warm-Flow / 下游对账时对齐进度的唯一依据。");
    }

    /** 审计查询：Before / After 证据链（audit_event） */
    private void auditDetail(CmdFlowTraceVo.StepDetailVo d, CmdApprovalTask task) {
        List<Map<String, Object>> events = stepDetailMapper.selectAuditEvents(task.getOneId());
        d.setSummary("该 One ID 相关审计事件共 " + events.size() + " 条（含变更、审批、合并、权限与集成事件）");
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> e : events) {
            rows.add(List.of(s(e.get("eventId")), s(e.get("eventType")), s(e.get("eventName")),
                s(e.get("operatorName")), s(e.get("result")), s(e.get("riskLevel")), s(e.get("eventTime"))));
        }
        table(d, "审计事件（audit_event，按 One ID）",
            List.of("事件编号", "类型", "事件", "操作人", "结果", "风险", "时间"), rows);
        if (rows.isEmpty()) {
            d.getNotes().add("该 One ID 暂无审计事件记录。");
        }
        d.getNotes().add("Before / After 快照存 audit_event.before_json / after_json，审计数据只增不改（del_flag 逻辑保留），Auditor 只读。");
    }

    /** DQ 检查项行：value 为空即判扣分，返回本次扣分 */
    private static int dqRow(List<List<String>> rows, String dimension, String rule, Object value, int deduction) {
        boolean missing = isBlank(value);
        rows.add(List.of(dimension, rule, s(value), missing ? "扣分" : "通过", missing ? "-" + deduction : "0"));
        return missing ? deduction : 0;
    }

    /** 匹配状态 → 路由结论 */
    private static String routingOf(String matchState) {
        String state = matchState == null ? "" : matchState.toUpperCase();
        return switch (state) {
            case "EXACT" -> "命中精确匹配：强制关联已有 One ID，不允许新创主数据";
            case "SUSPECT", "SUSPECTED" -> "疑似重复：转入 BU Scope 人工治理，确认 Same-BU 或升级 Cross-BU";
            case "REVIEW" -> "需人工复核：证据不足，退回补充材料";
            case "INVALID" -> "无效匹配：数据不合法，进入治理任务";
            default -> "未发现重复（NEW）：直接进入人工初审";
        };
    }

    /** 动作类型 → 所属节点（无显式 node_code 时的推断口径，与 indexActionsByNode 保持一致） */
    private static String nodeOfActionType(String actionType) {
        String type = actionType == null ? "" : actionType.toUpperCase();
        return switch (type) {
            case "SUBMIT", "CLAIM" -> "APPLY";
            case "APPROVE" -> NODE_BU_REVIEW;
            case "ESCALATE" -> NODE_GC_REVIEW;
            case "RETURN", "REJECT" -> NODE_BU_REVIEW;
            default -> null;
        };
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "COMPLETED" -> "已完成";
            case "CURRENT" -> "进行中";
            case "PENDING" -> "待执行";
            case "TERMINATED" -> "已终止";
            default -> s(status);
        };
    }

    private static String statusTone(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "COMPLETED" -> "success";
            case "CURRENT" -> "warning";
            case "TERMINATED" -> "danger";
            default -> "info";
        };
    }

    private static String riskTone(String risk) {
        if (risk == null) {
            return "info";
        }
        return switch (risk.toUpperCase()) {
            case "HIGH" -> "danger";
            case "MEDIUM" -> "warning";
            case "LOW" -> "success";
            default -> "info";
        };
    }

    private static void field(CmdFlowTraceVo.StepDetailVo d, String label, Object value) {
        field(d, label, value, null);
    }

    private static void field(CmdFlowTraceVo.StepDetailVo d, String label, Object value, String tone) {
        CmdFlowTraceVo.FieldVo f = new CmdFlowTraceVo.FieldVo();
        f.setLabel(label);
        f.setValue(s(value));
        f.setTone(tone);
        d.getFields().add(f);
    }

    /** 表格装配：无数据时不建表，由调用方改用 notes 说明口径 */
    private static void table(CmdFlowTraceVo.StepDetailVo d, String title, List<String> columns, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        CmdFlowTraceVo.TableVo t = new CmdFlowTraceVo.TableVo();
        t.setTitle(title);
        t.setColumns(columns);
        t.setRows(rows);
        d.getTables().add(t);
    }

    /** 展示值：null / 空串 → '—' */
    private static String s(Object o) {
        if (o == null) {
            return DASH;
        }
        String v = String.valueOf(o);
        return v.isBlank() ? DASH : v;
    }

    /** 判空（接受任意对象） */
    private static boolean isBlank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    /** 时间本地化：ISO → 'yyyy-MM-dd HH:mm'（与前端 formatTime 口径一致） */
    private static String dt(Object o) {
        if (o == null) {
            return DASH;
        }
        String v = String.valueOf(o).replace('T', ' ');
        return v.length() > 16 ? v.substring(0, 16) : v;
    }

    /** 主档字段取值（customer 可能为 null，例如任务无 One ID 时） */
    private static Object cv(Map<String, Object> row, String key) {
        return row == null ? null : row.get(key);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 数值型字符串 → int（空 / 非数字 → 0） */
    private static int num(Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 数值型字符串 → double（空 / 非数字 → 0） */
    private static double toDouble(Object o) {
        if (o == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    /** DQ 等级：与单客户口径一致（≥90 A、≥75 B、≥60 C、<60 D） */
    private static String gradeOf(double score) {
        if (score <= 0) {
            return DASH;
        }
        if (score >= 90) {
            return "A";
        }
        if (score >= 75) {
            return "B";
        }
        if (score >= 60) {
            return "C";
        }
        return "D";
    }
}
