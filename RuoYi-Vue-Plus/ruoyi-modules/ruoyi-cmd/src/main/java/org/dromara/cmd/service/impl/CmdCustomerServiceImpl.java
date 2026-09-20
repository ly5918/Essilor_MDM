package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdCustomerVersion;
import org.dromara.cmd.domain.CmdWorkflowStepLog;
import org.dromara.cmd.domain.bo.CmdCustomerBo;
import org.dromara.cmd.domain.vo.CmdCustomerSubmitVo;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.cmd.domain.vo.CmdCustomerVo;
import org.dromara.cmd.mapper.CmdApprovalActionMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdCustomerVersionMapper;
import org.dromara.cmd.mapper.CmdFlowSceneMapper;
import org.dromara.cmd.service.ICmdAuditService;
import org.dromara.cmd.service.ICmdCustomerService;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.cmd.service.ICmdWorkflowStepLogService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 客户主档 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>统一使用框架主库 ruoyi_plus（master 数据源）：业务表以 cmd_ / md_ / dq_ 等前缀
 *       与框架 sys_ / flow_ 表隔离，同库同事务，可直接 JOIN Warm-Flow 流程表与系统用户表</li>
 *   <li>One ID 生成后永不变更：updateCustomer 内部不会覆盖 oneId</li>
 *   <li>每次变更追加 cmd_customer_version 版本快照，支持 Before / After 审计</li>
 *   <li>停用为逻辑停用（status=inactive + effectiveTo），不做物理删除</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdCustomerServiceImpl implements ICmdCustomerService {

    private final CmdCustomerMapper customerMapper;
    private final CmdCustomerVersionMapper versionMapper;
    private final CmdApprovalTaskMapper approvalTaskMapper;
    private final CmdApprovalActionMapper approvalActionMapper;
    private final CmdFlowSceneMapper flowSceneMapper;
    private final ICmdFlowEngineService flowEngineService;
    private final ICmdWorkflowStepLogService stepLogService;
    private final ICmdAuditService auditService;

    /** 新建客户申请的业务场景（cmd_flow_scene.scene_code，同时决定泳道图模板与流程定义） */
    private static final String SCENE_CUSTOMER_CREATE = CmdConstants.SCENE_CUSTOMER_CREATE;

    /** 首次提交后的业务节点名（含 BU 关键字，引擎启动后据此定位到 BU_REVIEW 节点） */
    private static final String NODE_NAME_BU_REVIEW = "BU Scope 初审";

    /** 首次提交的处理角色（Data Steward BU Scope） */
    private static final String ROLE_BU_STEWARD = "BU_STEWARD";
    private static final String NAME_BU_STEWARD = "BU Steward";

    /** 场景未配置 SLA 时的兜底时长（小时） */
    private static final long DEFAULT_SLA_HOURS = 48L;

    /** POC 免登录：申请人取当前登录人，取不到时用演示账号兜底 */
    private static final Long DEMO_APPLICANT_ID = 1L;
    private static final String DEMO_APPLICANT_NAME = "Business User";

    /** 申请编号日期段格式（AP-yyyyMMdd-0001） */
    private static final DateTimeFormatter TASK_NO_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CmdCustomerVo> selectPageCustomerList(CmdCustomerBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CmdCustomer> lqw = buildQueryWrapper(bo);
        Page<CmdCustomerVo> page = customerMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdCustomerVo> selectCustomerList(CmdCustomerBo bo) {
        return customerMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdCustomerVo selectCustomerById(Long id) {
        return customerMapper.selectVoById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdCustomerVo selectCustomerByOneId(String oneId) {
        List<CmdCustomerVo> list = customerMapper.selectVoList(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, oneId));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String insertCustomer(CmdCustomerBo bo) {
        return insertCustomerEntity(bo).getOneId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CmdCustomerSubmitVo submitApplication(CmdCustomerBo bo) {
        // 1) 主档落库（One ID 服务端生成 + 首版本快照）
        CmdCustomer customer = insertCustomerEntity(bo);

        // 2) 自动检查（对应泳道图「技术/业务 DQ」与「Duplicate Check」两个自动阶段）：
        //    POC 阶段用确定性规则替代独立校验引擎，接入规则引擎后此处改为调用其接口，落库字段不变。
        BigDecimal dqScore = calcDqScore(customer);
        String matchState = CmdConstants.MATCH_NEW;
        String riskLevel = resolveRisk(dqScore);

        CmdCustomer checkPatch = new CmdCustomer();
        checkPatch.setId(customer.getId());
        checkPatch.setStatus(CmdConstants.CUST_STATUS_PENDING);
        checkPatch.setDqScore(dqScore);
        checkPatch.setDqGrade(gradeOf(dqScore));
        checkPatch.setMatchState(matchState);
        checkPatch.setDuplicateFlag(CmdConstants.NO);
        customerMapper.updateById(checkPatch);
        customer.setStatus(CmdConstants.CUST_STATUS_PENDING);
        customer.setDqScore(dqScore);
        customer.setMatchState(matchState);

        // 3) 生成统一待办（进入 Data Steward BU Scope 队列）
        Applicant applicant = resolveApplicant();
        long slaHours = resolveSlaHours();
        LocalDateTime now = LocalDateTime.now();
        CmdApprovalTask task = new CmdApprovalTask();
        task.setTaskNo(generateTaskNo());
        task.setTaskCategory(CmdConstants.APPR_CAT_APPROVAL);
        task.setBizType(bizTypeOf(bo));
        task.setBizId(String.valueOf(customer.getId()));
        task.setBizTitle(customer.getLegalName());
        task.setOneId(customer.getOneId());
        task.setSceneCode(SCENE_CUSTOMER_CREATE);
        task.setApplicantId(applicant.id());
        task.setApplicantName(applicant.name());
        task.setBuScope(customer.getBuScope());
        task.setScope(CmdConstants.SCOPE_BU);
        task.setCurrentNodeCode("BU_REVIEW");
        task.setCurrentNodeName(NODE_NAME_BU_REVIEW);
        task.setAssigneeName(NAME_BU_STEWARD);
        task.setAssigneeRole(ROLE_BU_STEWARD);
        task.setStatus(CmdConstants.APPR_STATUS_PENDING);
        task.setRiskLevel(riskLevel);
        task.setDqScore(dqScore);
        task.setDuplicateState(matchState);
        task.setCrossBuFlag(CmdConstants.NO);
        task.setSubmitTime(now);
        task.setSlaDue(now.plusHours(slaHours));
        task.setSlaState(CmdConstants.SLA_NORMAL);
        task.setEvidenceJson(evidenceOf(customer, dqScore, matchState));
        task.setBizSnapshotJson(JsonUtils.toJsonString(customer));
        task.setRemark(bo.getRemark());
        approvalTaskMapper.insert(task);

        // 4) 业务侧轨迹：提交申请（与引擎 flow_his_task 互补）
        CmdApprovalAction action = new CmdApprovalAction();
        action.setTaskId(task.getId());
        action.setTaskNo(task.getTaskNo());
        action.setOneId(customer.getOneId());
        action.setActionType(CmdConstants.ACTION_SUBMIT);
        action.setActionName("提交申请");
        action.setFromNodeCode("APPLY");
        action.setToNodeCode("BU_REVIEW");
        action.setOperatorId(applicant.id());
        action.setOperatorName(applicant.name());
        action.setOperatorRole(ROLE_BU_STEWARD);
        action.setActionTime(now);
        action.setBeforeState(CmdConstants.APPR_STATUS_DRAFT);
        action.setAfterState(CmdConstants.APPR_STATUS_PENDING);
        action.setEvidenceJson(task.getEvidenceJson());
        approvalActionMapper.insert(action);

        // 4.1) 工作流步骤总账：把「提交 + 系统自动检查（OCR/DQ/查重）」每一步都打到客户 One ID 上
        recordSubmitSteps(task, applicant, now, dqScore, matchState);

        // 4.1) 审计留痕：提交客户新建申请（审计中心可按 One ID / 申请编号检索到本次动作）
        AuditEvent audit = new AuditEvent();
        audit.setEventType("CREATE");
        audit.setEventName("提交客户新建申请：" + customer.getLegalName());
        audit.setBizType(CmdConstants.SCENE_CUSTOMER_CREATE);
        audit.setBizId(task.getTaskNo());
        audit.setOneId(customer.getOneId());
        audit.setOperatorId(applicant.id());
        audit.setOperatorName(applicant.name());
        audit.setOperatorRole("BUSINESS_USER");
        audit.setEventTime(now);
        audit.setResult("SUCCESS");
        audit.setRiskLevel(resolveRisk(dqScore));
        audit.setAfterJson(JsonUtils.toJsonString(customer));
        auditService.record(audit);

        // 5) 拉起 Warm-Flow：部署流程定义 → 启动实例 → 定位到业务当前节点 → 回写 flow_* 镜像
        flowEngineService.startInstance(task.getTaskNo());
        CmdApprovalTask started = approvalTaskMapper.selectOne(new LambdaQueryWrapper<CmdApprovalTask>()
            .eq(CmdApprovalTask::getTaskNo, task.getTaskNo())
            .last("LIMIT 1"));
        if (started != null) {
            task = started;
        }

        // 6) 主档回写流程实例 ID：客户详情可直接跳「流程跟踪」
        CmdCustomer flowPatch = new CmdCustomer();
        flowPatch.setId(customer.getId());
        flowPatch.setFlowInstanceId(task.getFlowInstanceId());
        customerMapper.updateById(flowPatch);
        log.info("[CMD][CUSTOMER] 客户新建申请已提交：oneId={} taskNo={} node={}",
            customer.getOneId(), task.getTaskNo(), task.getCurrentNodeName());

        CmdCustomerSubmitVo vo = new CmdCustomerSubmitVo();
        vo.setCustomerId(customer.getId());
        vo.setOneId(customer.getOneId());
        vo.setTaskNo(task.getTaskNo());
        vo.setSceneCode(SCENE_CUSTOMER_CREATE);
        vo.setSceneName(sceneName());
        vo.setStatus(customer.getStatus());
        vo.setTaskStatus(task.getStatus());
        vo.setCurrentNodeCode(task.getCurrentNodeCode());
        vo.setCurrentNodeName(task.getCurrentNodeName());
        vo.setAssigneeRole(task.getAssigneeRole());
        vo.setDqScore(dqScore);
        vo.setRiskLevel(riskLevel);
        vo.setSlaDue(task.getSlaDue());
        vo.setFlowInstanceId(task.getFlowInstanceId());
        vo.setFlowStatus(task.getFlowStatus());
        return vo;
    }

    /**
     * 主档落库（One ID 由服务端生成，前端不可指定；同时写入首版本快照）
     *
     * @param bo 客户信息
     * @return 落库后的客户实体（含主键 / One ID）
     */
    private CmdCustomer insertCustomerEntity(CmdCustomerBo bo) {
        CmdCustomer customer = MapstructUtils.convert(bo, CmdCustomer.class);
        customer.setId(null);
        customer.setOneId(generateOneId());
        customer.setVersionNo(1);
        customer.setStatus(StringUtils.blankToDefault(bo.getStatus(), CmdConstants.CUST_STATUS_DRAFT));
        if (customer.getEffectiveFrom() == null) {
            customer.setEffectiveFrom(LocalDateTime.now());
        }
        customerMapper.insert(customer);
        // 首版本快照：beforeJson 为空
        appendVersion(customer, null, "CREATE", bo.getRemark());
        return customer;
    }

    /**
     * 提交时的质量分（POC 确定性规则：关键字段缺失即扣分，最低 0 分）
     * <p>
     * 生产环境应由 DQ 规则引擎（dq_rule）计算，此处仅保证演示数据有可解释的分值。
     *
     * @param customer 客户实体
     * @return 质量分
     */
    private BigDecimal calcDqScore(CmdCustomer customer) {
        BigDecimal score = new BigDecimal("100");
        if (StringUtils.isBlank(customer.getCreditCode())) {
            score = score.subtract(new BigDecimal("12"));
        }
        if (StringUtils.isBlank(customer.getAddress())) {
            score = score.subtract(new BigDecimal("8"));
        }
        if (StringUtils.isBlank(customer.getProvince()) || StringUtils.isBlank(customer.getCity())) {
            score = score.subtract(new BigDecimal("4"));
        }
        if (StringUtils.isBlank(customer.getContactName())) {
            score = score.subtract(new BigDecimal("4"));
        }
        if (StringUtils.isBlank(customer.getContactPhone())) {
            score = score.subtract(new BigDecimal("4"));
        }
        return score.max(BigDecimal.ZERO);
    }

    /**
     * 质量分 → 等级
     *
     * @param score 质量分
     * @return A / B / C / D
     */
    private String gradeOf(BigDecimal score) {
        double value = score.doubleValue();
        if (value >= 90) {
            return "A";
        }
        return value >= 75 ? "B" : value >= 60 ? "C" : "D";
    }

    /**
     * 质量分 → 风险等级（风险等级决定审批页「BU初审判断」提示与是否建议升级 GC）
     *
     * @param score 质量分
     * @return High / Medium / Low
     */
    private String resolveRisk(BigDecimal score) {
        double value = score.doubleValue();
        if (value < 60) {
            return CmdConstants.RISK_HIGH;
        }
        return value < 85 ? CmdConstants.RISK_MEDIUM : CmdConstants.RISK_LOW;
    }

    /**
     * 生成申请编号：AP-yyyyMMdd-####（当日流水，从 0001 起递增且保证唯一）
     *
     * @return 申请编号
     */
    private String generateTaskNo() {
        String prefix = "AP-" + LocalDate.now().format(TASK_NO_DAY) + "-";
        for (int seq = 1; seq <= 9999; seq++) {
            String taskNo = prefix + String.format("%04d", seq);
            Long exists = approvalTaskMapper.lambda().eq(CmdApprovalTask::getTaskNo, taskNo).count();
            if (exists == null || exists == 0) {
                return taskNo;
            }
        }
        throw new ServiceException("申请编号生成失败：当日流水号已用尽");
    }

    /**
     * 解析申请人：优先当前登录人，POC 免登录取不到时使用演示账号
     *
     * @return 申请人（ID + 姓名）
     */
    private Applicant resolveApplicant() {
        try {
            if (LoginHelper.isLogin()) {
                return new Applicant(LoginHelper.getUserId(),
                    StringUtils.blankToDefault(LoginHelper.getUsername(), DEMO_APPLICANT_NAME));
            }
        } catch (Exception e) {
            log.debug("[CMD][CUSTOMER] 未获取到登录人，使用演示申请人：{}", e.getMessage());
        }
        return new Applicant(DEMO_APPLICANT_ID, DEMO_APPLICANT_NAME);
    }

    /**
     * 读取场景 SLA 时长（cmd_flow_scene.sla_hours）
     *
     * @return SLA 时长（小时）
     */
    private long resolveSlaHours() {
        Map<String, Object> scene = flowSceneMapper.selectSceneByCode(SCENE_CUSTOMER_CREATE);
        Object hours = scene == null ? null : scene.get("sla_hours");
        return hours instanceof Number number ? number.longValue() : DEFAULT_SLA_HOURS;
    }

    /**
     * 记录提交阶段的步骤日志：提交申请 + 系统自动检查（数据装配 / OCR / DQ / 查重）
     * <p>每一步都带客户 one_id，使新建客户的标识在「所有工作流步骤」中可关联回溯。</p>
     *
     * @param task       审批任务（含 one_id / task_no / flow_instance_id）
     * @param applicant  申请人
     * @param now        提交时间
     * @param dqScore    DQ 质量分
     * @param matchState 匹配结论
     */
    private void recordSubmitSteps(CmdApprovalTask task, Applicant applicant, LocalDateTime now,
                                   BigDecimal dqScore, String matchState) {
        String oneId = task.getOneId();
        String taskNo = task.getTaskNo();
        Long flowInstanceId = task.getFlowInstanceId();

        // 提交申请（APPLY → BU_REVIEW）
        CmdWorkflowStepLog submit = new CmdWorkflowStepLog();
        submit.setOneId(oneId);
        submit.setTaskNo(taskNo);
        submit.setFlowInstanceId(flowInstanceId);
        submit.setStepType(CmdConstants.STEP_SUBMIT);
        submit.setNodeCode("APPLY");
        submit.setNodeName(CmdConstants.nodeName("APPLY"));
        submit.setActionType(CmdConstants.ACTION_SUBMIT);
        submit.setActionName("提交申请");
        submit.setOperatorId(applicant.id());
        submit.setOperatorName(applicant.name());
        submit.setOperatorRole(ROLE_BU_STEWARD);
        submit.setFromStatus(CmdConstants.APPR_STATUS_DRAFT);
        submit.setToStatus(CmdConstants.APPR_STATUS_PENDING);
        submit.setCreateTime(now);
        stepLogService.recordStep(submit);

        // 系统自动检查（数据装配 / OCR / DQ / 查重）—— POC 阶段同步完成
        recordSystemStep(oneId, taskNo, flowInstanceId, "INPUT", "数据装配", now, dqScore, matchState);
        recordSystemStep(oneId, taskNo, flowInstanceId, "OCR", "OCR 与智能补全", now, dqScore, matchState);
        recordSystemStep(oneId, taskNo, flowInstanceId, "DQ", "技术与业务 DQ", now, dqScore, matchState);
        recordSystemStep(oneId, taskNo, flowInstanceId, "DUP", "Duplicate Check", now, dqScore, matchState);
    }

    /**
     * 记录一条系统自动步骤（OCR / DQ / 查重 等）
     */
    private void recordSystemStep(String oneId, String taskNo, Long flowInstanceId, String nodeCode,
                                  String nodeName, LocalDateTime now, BigDecimal dqScore, String matchState) {
        CmdWorkflowStepLog step = new CmdWorkflowStepLog();
        step.setOneId(oneId);
        step.setTaskNo(taskNo);
        step.setFlowInstanceId(flowInstanceId);
        step.setStepType(CmdConstants.STEP_SYSTEM);
        step.setNodeCode(nodeCode);
        step.setNodeName(nodeName);
        step.setOperatorName("系统自动处理");
        step.setOperatorRole("SYS");
        step.setOpinion(buildSystemOpinion(nodeCode, dqScore, matchState));
        step.setCreateTime(now);
        stepLogService.recordStep(step);
    }

    /**
     * 系统自动步骤的展示意见
     */
    private String buildSystemOpinion(String nodeCode, BigDecimal dqScore, String matchState) {
        return switch (nodeCode) {
            case "DQ" -> "DQ 质量分=" + (dqScore == null ? "-" : dqScore) + "，自动校验通过";
            case "DUP" -> "匹配结论=" + matchState + "，未发现强制合并项";
            case "OCR" -> "营业执照识别完成，关键字段已抽取";
            default -> "系统自动完成";
        };
    }

    /**
     * 场景名称（cmd_flow_scene.scene_name），用于回执文案
     *
     * @return 场景名称
     */
    private String sceneName() {
        Map<String, Object> scene = flowSceneMapper.selectSceneByCode(SCENE_CUSTOMER_CREATE);
        Object name = scene == null ? null : scene.get("scene_name");
        return name == null ? "客户创建" : String.valueOf(name);
    }

    /**
     * 业务类型（审批页「任务类型」列）：OCR 来源标记为客户新建 - OCR，便于演示区分
     *
     * @param bo 客户信息
     * @return 业务类型
     */
    private String bizTypeOf(CmdCustomerBo bo) {
        return StringUtils.isNotBlank(bo.getSourceSystem()) && "OCR".equalsIgnoreCase(bo.getSourceSystem())
            ? "客户新建 - OCR" : "客户新建";
    }

    /**
     * 治理证据快照（审批详情页「治理证据」区展示）
     *
     * @param customer   客户实体
     * @param dqScore    质量分
     * @param matchState 匹配结论
     * @return JSON 字符串
     */
    private String evidenceOf(CmdCustomer customer, BigDecimal dqScore, String matchState) {
        Map<String, Object> evidence = new java.util.LinkedHashMap<>(8);
        evidence.put("自动检查", "必填 / 格式 / 值集校验");
        evidence.put("质量分", dqScore);
        evidence.put("重复检查", matchState);
        evidence.put("信用代码", StringUtils.blankToDefault(customer.getCreditCode(), "未提供"));
        evidence.put("注册地址", StringUtils.blankToDefault(customer.getAddress(), "未提供"));
        return JsonUtils.toJsonString(evidence);
    }

    /** 申请人（登录人 / 演示账号） */
    private record Applicant(Long id, String name) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCustomer(CmdCustomerBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("修改客户时主键不能为空");
        }
        CmdCustomer before = customerMapper.selectById(bo.getId());
        if (ObjectUtil.isNull(before)) {
            throw new ServiceException("客户不存在或已删除");
        }
        CmdCustomer after = MapstructUtils.convert(bo, CmdCustomer.class);
        // One ID 稳定：任何情况下不允许变更
        after.setOneId(before.getOneId());
        after.setVersionNo(before.getVersionNo() == null ? 1 : before.getVersionNo() + 1);
        int rows = customerMapper.updateById(after);
        if (rows > 0) {
            appendVersion(after, before, "UPDATE", bo.getRemark());
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deactivateCustomer(String oneId, String reason) {
        CmdCustomer before = customerMapper.selectOne(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, oneId));
        if (ObjectUtil.isNull(before)) {
            throw new ServiceException("客户不存在或已删除");
        }
        CmdCustomer after = new CmdCustomer();
        after.setId(before.getId());
        after.setStatus(CmdConstants.CUST_STATUS_INACTIVE);
        after.setEffectiveTo(LocalDateTime.now());
        after.setVersionNo(before.getVersionNo() == null ? 1 : before.getVersionNo() + 1);
        int rows = customerMapper.updateById(after);
        if (rows > 0) {
            CmdCustomer snapshot = customerMapper.selectById(before.getId());
            appendVersion(snapshot, before, "DEACTIVATE", reason);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCustomerByIds(Collection<Long> ids, boolean isValid) {
        if (isValid) {
            // 生效中的客户不允许直接删除，必须先逻辑停用（无物理删除原则）
            long activeCount = customerMapper.lambda()
                .in(CmdCustomer::getId, ids)
                .eq(CmdCustomer::getStatus, CmdConstants.CUST_STATUS_ACTIVE)
                .count();
            if (activeCount > 0) {
                throw new ServiceException("存在{}条生效中的客户，请先执行逻辑停用", activeCount);
            }
        }
        return customerMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdCustomerVersionVo> selectVersionList(String oneId) {
        LambdaQueryWrapper<CmdCustomerVersion> lqw = new LambdaQueryWrapper<CmdCustomerVersion>()
            .eq(CmdCustomerVersion::getOneId, oneId)
            .orderByDesc(CmdCustomerVersion::getVersionNo);
        return versionMapper.selectVoList(lqw);
    }

    /**
     * 构造客户列表查询条件（对应页面筛选行）
     *
     * @param bo 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CmdCustomer> buildQueryWrapper(CmdCustomerBo bo) {
        LambdaQueryWrapper<CmdCustomer> lqw = QueryBuilder.lambda(CmdCustomer.class)
            .likeIfText(CmdCustomer::getLegalName, bo.getLegalName())
            .eqIfText(CmdCustomer::getOneId, bo.getOneId())
            .eqIfText(CmdCustomer::getCreditCode, bo.getCreditCode())
            .eqIfText(CmdCustomer::getBuScope, bo.getBuScope())
            .eqIfText(CmdCustomer::getStatus, bo.getStatus())
            .eqIfText(CmdCustomer::getMatchState, bo.getMatchState())
            .eqIfText(CmdCustomer::getSourceSystem, bo.getSourceSystem())
            .betweenParams(CmdCustomer::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByDesc(CmdCustomer::getCreateTime)
            .build();
        // 贯通查询：客户名称 / One ID / 统一社会信用代码 三列模糊匹配
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String kw = bo.getKeyword().trim();
            lqw.and(w -> w.like(CmdCustomer::getLegalName, kw)
                .or().like(CmdCustomer::getOneId, kw)
                .or().like(CmdCustomer::getCreditCode, kw));
        }
        return lqw;
    }

    /**
     * 追加客户版本快照（不覆盖历史，保证可追溯）
     *
     * @param after   变更后数据
     * @param before  变更前数据（新增时为空）
     * @param type    变更类型
     * @param reason  变更原因
     */
    private void appendVersion(CmdCustomer after, CmdCustomer before, String type, String reason) {
        CmdCustomerVersion version = new CmdCustomerVersion();
        version.setOneId(after.getOneId());
        version.setVersionNo(Objects.requireNonNullElse(after.getVersionNo(), 1));
        version.setChangeType(type);
        version.setChangeReason(reason);
        version.setSnapshotJson(JsonUtils.toJsonString(after));
        version.setBeforeJson(before == null ? null : JsonUtils.toJsonString(before));
        version.setStatus("effective");
        version.setSourceSystem(after.getSourceSystem());
        versionMapper.insert(version);
    }

    /**
     * 生成 One ID（POC 实现）
     * <p>
     * 生产环境应改为读取 oneid_rule 表并按规则生成（编码模式 / 序列 / 校验位），
     * 此处使用 UUID 片段保证 POC 阶段全局唯一且不依赖外部组件。
     *
     * @return One ID
     */
    private String generateOneId() {
        return "GC-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }
}
