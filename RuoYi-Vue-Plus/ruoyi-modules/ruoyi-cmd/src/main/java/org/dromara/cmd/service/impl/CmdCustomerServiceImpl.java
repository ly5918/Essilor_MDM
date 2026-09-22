package org.dromara.cmd.service.impl;

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
import org.dromara.cmd.domain.MdField;
import org.dromara.cmd.domain.bo.CmdCustomerBo;
import org.dromara.cmd.domain.vo.CmdCustomerStatsVo;
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
import org.dromara.cmd.service.ICmdGovernanceService;
import org.dromara.cmd.service.ICmdPlatformService;
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
    private final ICmdGovernanceService governanceService;
    private final ICmdPlatformService platformService;

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
    public CmdCustomerStatsVo selectCustomerStats(CmdCustomerBo bo) {
        // 与列表共用同一套条件，保证「指标带」与「列表」口径完全一致（不会出现上面 26 下面 10）。
        // POC 取舍：按条件取回明细后在内存统计；数据量上万后应改为 SQL 聚合（count / sum + case when）。
        List<CmdCustomerVo> list = customerMapper.selectVoList(buildQueryWrapper(bo));
        long activeCount = 0L;
        long pendingCount = 0L;
        long crossBuCount = 0L;
        long duplicateCount = 0L;
        long scoredCount = 0L;
        BigDecimal dqSum = BigDecimal.ZERO;
        for (CmdCustomerVo row : list) {
            if (CmdConstants.CUST_STATUS_ACTIVE.equals(row.getStatus())) {
                activeCount++;
            }
            // 待处理口径：待审批（pending）+ 退回待补充（returned）
            if (CmdConstants.CUST_STATUS_PENDING.equals(row.getStatus())
                || CmdConstants.CUST_STATUS_RETURNED.equals(row.getStatus())) {
                pendingCount++;
            }
            if (CmdConstants.YES.equals(row.getGcScopeFlag())) {
                crossBuCount++;
            }
            if (CmdConstants.YES.equals(row.getDuplicateFlag())) {
                duplicateCount++;
            }
            BigDecimal dq = row.getDqScore();
            // 0 分视为「尚未跑 DQ」，不计入平均分分母，避免拉低整体质量分
            if (dq != null && dq.compareTo(BigDecimal.ZERO) > 0) {
                dqSum = dqSum.add(dq);
                scoredCount++;
            }
        }
        CmdCustomerStatsVo stats = new CmdCustomerStatsVo();
        stats.setTotal((long) list.size());
        stats.setActiveCount(activeCount);
        stats.setPendingCount(pendingCount);
        stats.setCrossBuCount(crossBuCount);
        stats.setDuplicateCount(duplicateCount);
        stats.setAvgDqScore(scoredCount == 0L ? 0L : Math.round(dqSum.doubleValue() / scoredCount));
        return stats;
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
     * 提交前必填校验（配置驱动）
     * <p>
     * 必填口径来自「当前已发布元数据版本」的 is_required='Y' 字段，而不是写死在代码里：
     * 管理员在「字段与值集」新增必填字段并发布后，新建客户提交会自动要求填写，
     * 不需要改任何业务代码（总设计 Configuration-first / Master Data Extension）。
     * <p>
     * 修复目标（测试报告 BUG-2）：此前缺失必填字段会一路走到 INSERT，
     * 由 cmd_customer.legal_name 的 NOT NULL 约束抛出未捕获异常（页面提示「发生未知异常」）；
     * 现在在事务开始前拦截，返回可读的字段清单，HTTP 仍是 200 + 业务失败码。
     *
     * @param bo 提交入参
     */
    private void validateRequiredFields(CmdCustomerBo bo) {
        if (bo == null) {
            throw new ServiceException("提交内容为空，请填写客户信息后重试");
        }
        Map<String, String> values = resolveFieldValues(bo);
        List<String> missing = new java.util.ArrayList<>();
        for (MdField field : platformService.selectPublishedFields()) {
            if (!CmdConstants.YES.equalsIgnoreCase(field.getIsRequired())) {
                continue;
            }
            String code = field.getFieldCode();
            // 由系统托管、不出现在动态表单里的字段（生命周期状态）不参与人工必填校验
            if ("status".equals(code)) {
                continue;
            }
            if (StringUtils.isBlank(values.get(code))) {
                missing.add(StringUtils.blankToDefault(field.getFieldName(), code));
            }
        }
        if (!missing.isEmpty()) {
            throw new ServiceException("提交失败：以下必填字段尚未填写 —— " + String.join("、", missing)
                + "。请在表单中补齐（字段旁红字标注）或先通过 OCR 识别回填。");
        }
    }

    /**
     * 把提交入参摊平成「元数据字段编码 → 取值」，供必填校验使用。
     * <p>
     * 核心字段落在主档实体列上（前端已映射），其余动态字段整体以 extJson 透传，
     * 两边都要读，否则动态必填字段永远校验不到。
     *
     * @param bo 提交入参
     * @return 字段编码到取值的映射
     */
    private Map<String, String> resolveFieldValues(CmdCustomerBo bo) {
        Map<String, String> values = new java.util.HashMap<>();
        putValue(values, "legal_name", bo.getLegalName());
        putValue(values, "legal_name_en", bo.getLegalNameEn());
        putValue(values, "short_name", bo.getShortName());
        putValue(values, "credit_code", bo.getCreditCode());
        putValue(values, "tax_no", bo.getTaxNo());
        putValue(values, "customer_type", bo.getCustomerType());
        putValue(values, "customer_level", bo.getCustomerLevel());
        putValue(values, "product_line", bo.getProductLine());
        putValue(values, "bu_scope", bo.getBuScope());
        putValue(values, "country", bo.getCountry());
        putValue(values, "province", bo.getProvince());
        putValue(values, "city", bo.getCity());
        putValue(values, "address", bo.getAddress());
        putValue(values, "payer_id", bo.getPayerId());
        putValue(values, "postal_code", bo.getPostalCode());
        putValue(values, "contact_name", bo.getContactName());
        putValue(values, "contact_phone", bo.getContactPhone());
        putValue(values, "contact_email", bo.getContactEmail());
        putValue(values, "source_system", bo.getSourceSystem());
        putValue(values, "status", bo.getStatus());
        if (StringUtils.isNotBlank(bo.getExtJson())) {
            try {
                cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(bo.getExtJson());
                obj.forEach((key, value) -> putValue(values, key, value == null ? null : String.valueOf(value)));
            } catch (Exception ignore) {
                // 扩展属性不是合法 JSON 时忽略，不阻断提交（主档列校验仍然生效）
            }
        }
        return values;
    }

    /**
     * 写入字段取值映射（空串按未填写处理）
     */
    private void putValue(Map<String, String> values, String code, String value) {
        if (StringUtils.isNotBlank(value)) {
            values.put(code, value.trim());
        }
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
        // 0) 提交前业务校验：按「当前已发布元数据版本」的必填字段逐项检查，
        //    缺失时直接抛业务异常（前端按字段名红字标注），而不是让 NOT NULL 列抛出 500。
        validateRequiredFields(bo);
        // 1) 主档落库（One ID 服务端生成 + 首版本快照）
        CmdCustomer customer = insertCustomerEntity(bo);

        // 1.1) 必填校验：法定名称为客户主数据核心标识，缺失直接业务报错（返回规范错误而非 500，BUG-05）
        if (StringUtils.isBlank(customer.getLegalName())) {
            throw new ServiceException("客户法定名称不能为空");
        }

        // 2) 自动检查（对应泳道图「技术/业务 DQ」与「Duplicate Check」两个自动阶段）：
        //    POC 阶段用确定性规则替代独立校验引擎，接入规则引擎后此处改为调用其接口，落库字段不变。
        BigDecimal dqScore = calcDqScore(customer);
        // Duplicate Check（总设计「匹配分流」网关）：信用代码命中 active 主档 → EXACT（精准重复）；
        // 名称命中 → SUSPECTED（疑似重复，进入人工治理，跨BU 时按 MERGE 场景处理）。
        CmdCustomer candidate = matchExistingCustomer(customer);
        String matchState = CmdConstants.MATCH_NEW;
        if (candidate != null) {
            // 信用代码同值 → EXACT（精准重复）；仅名称同值 → SUSPECTED（疑似重复）
            matchState = StringUtils.isNotBlank(customer.getCreditCode())
                && customer.getCreditCode().equals(candidate.getCreditCode())
                ? CmdConstants.MATCH_EXACT : CmdConstants.MATCH_SUSPECTED;
        }
        String riskLevel = resolveRisk(dqScore);
        boolean crossBu = candidate != null && !java.util.Objects.equals(
            StringUtils.defaultString(customer.getBuScope()), StringUtils.defaultString(candidate.getBuScope()));
        String candidateOneId = candidate == null ? null : candidate.getOneId();

        CmdCustomer checkPatch = new CmdCustomer();
        checkPatch.setId(customer.getId());
        checkPatch.setStatus(CmdConstants.CUST_STATUS_PENDING);
        checkPatch.setDqScore(dqScore);
        checkPatch.setDqGrade(gradeOf(dqScore));
        checkPatch.setMatchState(matchState);
        checkPatch.setDuplicateFlag(candidate == null ? CmdConstants.NO : CmdConstants.YES);
        customerMapper.updateById(checkPatch);
        customer.setStatus(CmdConstants.CUST_STATUS_PENDING);
        customer.setDqScore(dqScore);
        customer.setMatchState(matchState);
        customer.setDuplicateFlag(candidate == null ? CmdConstants.NO : CmdConstants.YES);

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
        task.setCrossBuFlag(candidate == null ? CmdConstants.NO : crossBu ? CmdConstants.YES : CmdConstants.NO);
        task.setSubmitTime(now);
        task.setSlaDue(now.plusHours(slaHours));
        task.setSlaState(CmdConstants.SLA_NORMAL);
        task.setEvidenceJson(evidenceOf(customer, dqScore, matchState, candidate));
        task.setBizSnapshotJson(JsonUtils.toJsonString(customer));
        task.setRemark(bo.getRemark());
        approvalTaskMapper.insert(task);

        // 3.1) Duplicate Check 命中 → 生成疑似重复治理任务（总设计 MERGE 场景「发现候选」）。
        //      Exact 由系统在批准后自动关联；Suspected 需人工治理（关联已有 = 发起合并请求）。
        if (candidate != null) {
            governanceService.createDuplicateTask(task.getTaskNo(), customer.getOneId(), customer.getLegalName(),
                customer.getBuScope(), crossBu, matchState, task.getEvidenceJson());
        }

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
        String candidateOneId = null;
        if (task.getEvidenceJson() != null) {
            try {
                Object v = JsonUtils.parseMap(task.getEvidenceJson()).get("候选One ID");
                candidateOneId = v == null ? null : String.valueOf(v);
            } catch (Exception ignored) {
                // 证据快照解析失败不影响步骤日志
            }
        }
        recordSystemStep(oneId, taskNo, flowInstanceId, "INPUT", "数据装配", now, dqScore, matchState, null);
        recordSystemStep(oneId, taskNo, flowInstanceId, "OCR", "OCR 与智能补全", now, dqScore, matchState, null);
        recordSystemStep(oneId, taskNo, flowInstanceId, "DQ", "技术与业务 DQ", now, dqScore, matchState, null);
        recordSystemStep(oneId, taskNo, flowInstanceId, "DUP", "Duplicate Check", now, dqScore, matchState, candidateOneId);
    }

    /**
     * 记录一条系统自动步骤（OCR / DQ / 查重 等）
     */
    private void recordSystemStep(String oneId, String taskNo, Long flowInstanceId, String nodeCode,
                                  String nodeName, LocalDateTime now, BigDecimal dqScore, String matchState,
                                  String candidateOneId) {
        CmdWorkflowStepLog step = new CmdWorkflowStepLog();
        step.setOneId(oneId);
        step.setTaskNo(taskNo);
        step.setFlowInstanceId(flowInstanceId);
        step.setStepType(CmdConstants.STEP_SYSTEM);
        step.setNodeCode(nodeCode);
        step.setNodeName(nodeName);
        step.setOperatorName("系统自动处理");
        step.setOperatorRole("SYS");
        step.setOpinion(buildSystemOpinion(nodeCode, dqScore, matchState, candidateOneId));
        step.setCreateTime(now);
        stepLogService.recordStep(step);
    }

    /**
     * 系统自动步骤的展示意见
     */
    private String buildSystemOpinion(String nodeCode, BigDecimal dqScore, String matchState, String candidateOneId) {
        return switch (nodeCode) {
            case "DQ" -> "DQ 质量分=" + (dqScore == null ? "-" : dqScore) + "，自动校验通过";
            case "DUP" -> candidateOneId == null
                ? "匹配结论=" + matchState + "，未发现存量重复"
                : "匹配结论=" + matchState + "，命中存量主档 " + candidateOneId + "，进入人工治理";
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
    private String evidenceOf(CmdCustomer customer, BigDecimal dqScore, String matchState, CmdCustomer candidate) {
        Map<String, Object> evidence = new java.util.LinkedHashMap<>(8);
        evidence.put("自动检查", "必填 / 格式 / 值集校验");
        evidence.put("质量分", dqScore);
        evidence.put("重复检查", matchState);
        // 申请侧字段（与候选侧成对，供审批详情「治理证据」区渲染字段级命中高亮对比）
        evidence.put("申请名称", StringUtils.blankToDefault(customer.getLegalName(), "未提供"));
        evidence.put("申请BU", StringUtils.blankToDefault(customer.getBuScope(), "—"));
        evidence.put("申请来源系统", StringUtils.blankToDefault(customer.getSourceSystem(), "—"));
        evidence.put("信用代码", StringUtils.blankToDefault(customer.getCreditCode(), "未提供"));
        evidence.put("注册地址", StringUtils.blankToDefault(customer.getAddress(), "未提供"));
        if (candidate != null) {
            // 候选对比证据（总设计 MERGE 场景「证据准备」：信用代码 / 经营地址 / 名称 / 来源）
            evidence.put("候选One ID", candidate.getOneId());
            evidence.put("候选名称", candidate.getLegalName());
            evidence.put("候选信用代码", StringUtils.blankToDefault(candidate.getCreditCode(), "未提供"));
            evidence.put("候选经营地址", StringUtils.blankToDefault(candidate.getAddress(), "未提供"));
            evidence.put("候选BU", StringUtils.blankToDefault(candidate.getBuScope(), "—"));
            evidence.put("候选来源系统", StringUtils.blankToDefault(candidate.getSourceSystem(), "—"));
            evidence.put("跨BU", java.util.Objects.equals(
                StringUtils.defaultString(customer.getBuScope()), StringUtils.defaultString(candidate.getBuScope()))
                ? "N" : "Y");
        }
        return JsonUtils.toJsonString(evidence);
    }

    /**
     * Duplicate Check：与存量 active 主档比对（总设计「匹配分流」网关的 POC 确定性实现）。
     * <p>信用代码（主依据）同值 → 返回候选（EXACT）；否则客户名称（辅助线索）同值 → 返回候选（SUSPECTED）；
     * 均未命中返回 null（NEW）。已合并（merged） / 草稿 / 待审中的主档不参与比对。
     *
     * @param customer 新申请客户
     * @return 命中的存量主档候选，未命中返回 null
     */
    /**
     * Duplicate Check：与存量 active 主档比对（总设计「匹配分流」网关的 POC 确定性实现）。
     * <p>信用代码（主依据）同值 → 返回候选（EXACT）；否则客户名称（辅助线索）规范化相等或高相似度
     * → 返回候选（SUSPECTED）。已合并 / 草稿 / 待审中的主档不参与比对。
     * 名称匹配采用「去法律后缀 + 去括号内容」规范化，并辅以 bigram Dice 相似度，
     * 以解决「上海清视眼镜有限公司」与「上海清视眼镜」因名称不完全一致而漏判跨 BU 同名重复的问题（BUG-02）。
     *
     * @param customer 新申请客户
     * @return 命中的存量主档候选，未命中返回 null
     */
    private CmdCustomer matchExistingCustomer(CmdCustomer customer) {
        // 主依据：统一社会信用代码（精确匹配，命中即 EXACT）
        if (StringUtils.isNotBlank(customer.getCreditCode())) {
            List<CmdCustomer> byCredit = customerMapper.selectList(new LambdaQueryWrapper<CmdCustomer>()
                .eq(CmdCustomer::getCreditCode, customer.getCreditCode())
                .eq(CmdCustomer::getStatus, CmdConstants.CUST_STATUS_ACTIVE)
                .ne(customer.getId() != null, CmdCustomer::getId, customer.getId())
                .orderByDesc(CmdCustomer::getCreateTime)
                .last("LIMIT 1"));
            if (!byCredit.isEmpty()) {
                return byCredit.get(0);
            }
        }
        // 辅助线索：客户名称（总设计「名称仅作为辅助线索」）。规范化相等或相似度 ≥ 0.85 即视为疑似同名。
        if (StringUtils.isNotBlank(customer.getLegalName())) {
            final String normNew = normalizeCustomerName(customer.getLegalName());
            List<CmdCustomer> active = customerMapper.selectList(new LambdaQueryWrapper<CmdCustomer>()
                .eq(CmdCustomer::getStatus, CmdConstants.CUST_STATUS_ACTIVE)
                .ne(customer.getId() != null, CmdCustomer::getId, customer.getId())
                .orderByDesc(CmdCustomer::getCreateTime));
            for (CmdCustomer c : active) {
                if (StringUtils.isBlank(c.getLegalName())) {
                    continue;
                }
                final String normExist = normalizeCustomerName(c.getLegalName());
                if (normNew.equals(normExist) || diceSimilarity(normNew, normExist) >= 0.85) {
                    return c;
                }
            }
        }
        return null;
    }

    /** 客户名称规范化：去括号内容 + 去常见法律后缀，用于同名重复比对（BUG-02） */
    private String normalizeCustomerName(String name) {
        String s = name == null ? "" : name.trim();
        // 去括号及其中内容：（上海）(浦东)【】[] 等
        s = s.replaceAll("[（(【\\[].*?[）)】\\]]", "");
        // 去常见法律 / 组织后缀（长后缀优先，避免「有限公司」误切「公司」）
        String[] suffixes = {"股份有限公司", "有限责任公司", "有限公司", "集团公司", "集团", "分公司", "分店", "总部", "中心", "工厂", "公司"};
        for (String suf : suffixes) {
            if (s.endsWith(suf)) {
                s = s.substring(0, s.length() - suf.length());
                break;
            }
        }
        return s.trim();
    }

    /** bigram Dice 相似度（0~1），用于名称模糊比对兜底（BUG-02） */
    private double diceSimilarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        java.util.Set<String> ba = bigrams(a);
        java.util.Set<String> bb = bigrams(b);
        int overlap = 0;
        for (String g : ba) {
            if (bb.contains(g)) {
                overlap++;
            }
        }
        return (2.0 * overlap) / (ba.size() + bb.size());
    }

    private java.util.Set<String> bigrams(String s) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
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
            .eqIfText(CmdCustomer::getCustomerType, bo.getCustomerType())
            .eqIfText(CmdCustomer::getProductLine, bo.getProductLine())
            .eqIfText(CmdCustomer::getStatus, bo.getStatus())
            .eqIfText(CmdCustomer::getMatchState, bo.getMatchState())
            .eqIfText(CmdCustomer::getSourceSystem, bo.getSourceSystem())
            .betweenParams(CmdCustomer::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByDesc(CmdCustomer::getCreateTime)
            .build();
        // 贯通查询：客户名称（中/英/简称）/ One ID / 统一社会信用代码 / Payer 编码 模糊匹配
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String kw = bo.getKeyword().trim();
            lqw.and(w -> w.like(CmdCustomer::getLegalName, kw)
                .or().like(CmdCustomer::getLegalNameEn, kw)
                .or().like(CmdCustomer::getShortName, kw)
                .or().like(CmdCustomer::getOneId, kw)
                .or().like(CmdCustomer::getCreditCode, kw)
                .or().like(CmdCustomer::getPayerId, kw));
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
     * 生成 One ID（按 oneid_rule + cfg_sequence 规则生成）
     * <p>
     * 读取平台管理中配置的 One ID 规则（前缀 / 分隔符 / 流水长度 / 序列编码），
     * 从 cfg_sequence 原子取号，保证全局唯一与顺序递增；规则修改后即时生效。
     *
     * @return One ID
     */
    private String generateOneId() {
        return platformService.generateOneId();
    }
}
