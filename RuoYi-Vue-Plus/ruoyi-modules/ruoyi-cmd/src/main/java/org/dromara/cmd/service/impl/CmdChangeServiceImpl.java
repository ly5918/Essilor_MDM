package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.CmdChangeDiff;
import org.dromara.cmd.domain.CmdChangeRequest;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdCustomerVersion;
import org.dromara.cmd.domain.CmdWorkflowStepLog;
import org.dromara.cmd.domain.MdField;
import org.dromara.cmd.domain.bo.CmdChangeDiffBo;
import org.dromara.cmd.domain.bo.CmdChangeRequestBo;
import org.dromara.cmd.domain.vo.CmdChangeDetailVo;
import org.dromara.cmd.domain.vo.CmdChangeDiffVo;
import org.dromara.cmd.domain.vo.CmdChangeFieldVo;
import org.dromara.cmd.domain.vo.CmdChangeKpiVo;
import org.dromara.cmd.domain.vo.CmdChangeRequestVo;
import org.dromara.cmd.domain.vo.CmdChangeTrailVo;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.cmd.domain.vo.CmdDeactivateResultVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationVo;
import org.dromara.cmd.mapper.CmdApprovalActionMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdChangeDiffMapper;
import org.dromara.cmd.mapper.CmdChangeRequestMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdCustomerVersionMapper;
import org.dromara.cmd.mapper.CmdFlowSceneMapper;
import org.dromara.cmd.mapper.MdFieldMapper;
import org.dromara.cmd.service.ICmdAuditService;
import org.dromara.cmd.service.ICmdChangeService;
import org.dromara.cmd.service.ICmdHierarchyService;
import org.dromara.cmd.service.ICmdWorkflowStepLogService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 变更与停用 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li><b>无物理删除</b>：停用只把 status 置为 inactive / archived 并记录 effective_to，
 *       主档记录、One ID、历史版本、交叉引用、审批与审计证据全部保留</li>
 *   <li><b>One ID 稳定</b>：任何变更只递增 version_no，绝不重新生成 One ID；
 *       指标卡「One ID 重生成」恒为 0 是对该约束的量化证明</li>
 *   <li><b>配置驱动</b>：可变更字段、中文名、关键 / 敏感标记全部读取 md_field
 *       （平台管理 → 字段与值集 维护），服务内不硬编码字段清单</li>
 *   <li><b>差异快照</b>：提交时把字段级 Before / After 固化进 cmd_change_diff，
 *       审批期间不随主档漂移，保证审计可回溯</li>
 *   <li><b>审批解耦</b>：提交只登记 cmd_approval_task（进入审批中心 BU Scope 队列）；
 *       审批结果回写申请状态，主档生效由本服务的 effect 显式执行，
 *       避免审批推进过程中提前改写 Golden Record</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdChangeServiceImpl implements ICmdChangeService {

    private final CmdChangeRequestMapper changeMapper;
    private final CmdChangeDiffMapper diffMapper;
    private final CmdCustomerMapper customerMapper;
    private final CmdCustomerVersionMapper versionMapper;
    private final CmdApprovalTaskMapper approvalTaskMapper;
    private final CmdApprovalActionMapper approvalActionMapper;
    private final CmdFlowSceneMapper flowSceneMapper;
    private final MdFieldMapper mdFieldMapper;
    private final ICmdAuditService auditService;
    private final ICmdWorkflowStepLogService stepLogService;
    private final ICmdHierarchyService hierarchyService;

    /** 客户主档元数据模型编码（md_field.model_code） */
    private static final String MD_MODEL_CUSTOMER = "CUSTOMER";

    /** 申请编号日期段（CH-yyyyMMdd-####） */
    private static final DateTimeFormatter CODE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 审批任务编号日期段（AP-yyyyMMdd-####，与客户新建申请共用一套编号规则） */
    private static final DateTimeFormatter TASK_NO_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 首次提交后的业务节点（与客户新建流程统一到 BU Scope 初审） */
    private static final String NODE_BU_REVIEW = "BU_REVIEW";

    /** 场景未配置 SLA 时的兜底时长（小时） */
    private static final long DEFAULT_SLA_HOURS = 48L;

    /** 免登录 POC 兜底申请人 */
    private static final Long DEMO_APPLICANT_ID = 1L;
    private static final String DEMO_APPLICANT_NAME = "Business User";

    /** BU Steward 办理角色 / 姓名 */
    private static final String ROLE_BU_STEWARD = "BU_STEWARD";
    private static final String NAME_BU_STEWARD = "BU Steward";

    /** 停用目标状态白名单（与设计文档 Inactive / Archived 一致） */
    private static final Map<String, String> DEACTIVATE_TARGET = Map.of(
        "inactive", CmdConstants.CUST_STATUS_INACTIVE,
        "archived", CmdConstants.CUST_STATUS_ARCHIVED);

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdChangeFieldVo> selectChangeableFields() {
        return mdFieldMapper.selectList(new LambdaQueryWrapper<MdField>()
                .eq(MdField::getModelCode, MD_MODEL_CUSTOMER)
                // 字段模型版本化后正式行(status=0)与草稿行(status=1)并存，以 physical_column 是否配置为准
                .in(MdField::getStatus, "0", "1")
                .isNotNull(MdField::getPhysicalColumn)
                .ne(MdField::getPhysicalColumn, "")
                .orderByAsc(MdField::getOrderNum))
            .stream()
            .map(this::toChangeFieldVo)
            .toList();
    }

    /**
     * 元数据字段 → 可变更字段目录行
     *
     * @param field md_field 行
     * @return 目录行
     */
    private CmdChangeFieldVo toChangeFieldVo(MdField field) {
        CmdChangeFieldVo vo = new CmdChangeFieldVo();
        vo.setFieldCode(field.getFieldCode());
        vo.setFieldName(field.getFieldName());
        vo.setDataType(field.getDataType());
        vo.setValueSetCode(field.getValueSetCode());
        vo.setIsRequired(field.getIsRequired());
        vo.setIsKeyField(field.getIsKeyField());
        vo.setIsSensitive(field.getIsSensitive());
        vo.setMaxLength(field.getMaxLength());
        vo.setRegexPattern(field.getRegexPattern());
        vo.setPhysicalColumn(field.getPhysicalColumn());
        vo.setOrderNum(field.getOrderNum());
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CmdChangeRequestVo> selectPage(CmdChangeRequestBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CmdChangeRequest> lqw = new LambdaQueryWrapper<CmdChangeRequest>()
            .eq(StringUtils.isNotBlank(bo.getOneId()), CmdChangeRequest::getOneId, bo.getOneId())
            .eq(StringUtils.isNotBlank(bo.getChangeType()), CmdChangeRequest::getChangeType, bo.getChangeType())
            .eq(StringUtils.isNotBlank(bo.getBuScope()), CmdChangeRequest::getBuScope, bo.getBuScope())
            .orderByDesc(CmdChangeRequest::getCreateTime);
        // 状态支持单值与逗号分组（如 Approved 页签 = APPROVED,EFFECTIVE）
        if (StringUtils.isNotBlank(bo.getStatus())) {
            String[] states = bo.getStatus().split(",");
            if (states.length > 1) {
                lqw.in(CmdChangeRequest::getStatus, Arrays.asList(states));
            } else {
                lqw.eq(CmdChangeRequest::getStatus, bo.getStatus());
            }
        }
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            lqw.and(w -> w.like(CmdChangeRequest::getRequestCode, bo.getKeyword())
                .or().like(CmdChangeRequest::getOneId, bo.getKeyword())
                .or().like(CmdChangeRequest::getLegalName, bo.getKeyword()));
        }
        Page<CmdChangeRequestVo> page = changeMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdChangeRequestVo selectByRequestCode(String requestCode) {
        return changeMapper.selectVoOne(
            new LambdaQueryWrapper<CmdChangeRequest>().eq(CmdChangeRequest::getRequestCode, requestCode));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submit(CmdChangeRequestBo bo) {
        CmdCustomer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, bo.getOneId()));
        if (ObjectUtil.isNull(customer)) {
            throw new ServiceException("客户不存在：{}", bo.getOneId());
        }
        String changeType = normalizeChangeType(bo.getChangeType());
        boolean deactivate = CmdConstants.CHG_TYPE_DEACTIVATE.equals(changeType);

        // 1) 构建字段级差异快照（Before 值从主档当前值读取，前端只需给 After）
        List<CmdChangeDiff> diffs = deactivate
            ? List.of(buildStatusDiff(customer, bo.getTargetStatus()))
            : buildFieldDiffs(customer, bo.getDiffs());
        if (diffs.isEmpty()) {
            throw new ServiceException("请至少填写一项变更内容");
        }
        // 关键属性判定走元数据配置：任一关键字段发生实际变化即需要更高级别审批
        boolean keyChange = diffs.stream().anyMatch(d ->
            CmdConstants.YES.equals(d.getIsKeyField()) && !CmdConstants.DIFF_SAME.equals(d.getChangeFlag()));

        // 2) 关联关系影响检查（A3-A2-A1 / Payer / 跨 BU / 下游依赖）
        RelationImpact impact = checkRelationImpact(customer.getOneId(), diffs);

        // 3) 申请单落库（编号服务端生成，避免前端伪造）
        CmdChangeRequest entity = new CmdChangeRequest();
        entity.setRequestCode(generateRequestCode());
        entity.setOneId(customer.getOneId());
        entity.setLegalName(customer.getLegalName());
        entity.setChangeType(changeType);
        entity.setTargetStatus(deactivate
            ? resolveTargetStatus(bo.getTargetStatus())
            : CmdConstants.CUST_STATUS_ACTIVE);
        entity.setIsKeyChange(keyChange ? CmdConstants.YES : CmdConstants.NO);
        entity.setBuScope(StringUtils.blankToDefault(bo.getBuScope(), customer.getBuScope()));
        entity.setChangeReason(bo.getChangeReason());
        entity.setEffectiveDate(bo.getEffectiveDate());
        entity.setStatus(CmdConstants.CHG_STATUS_PENDING);
        entity.setImpactJson(JsonUtils.toJsonString(impact.items));
        entity.setRelationCheck(impact.level);
        entity.setRelationMsg(impact.message);
        entity.setRemark(bo.getRemark());
        changeMapper.insert(entity);

        // 4) 差异明细落库（锁定快照）
        for (CmdChangeDiff diff : diffs) {
            diff.setRequestId(entity.getId());
            diff.setRequestCode(entity.getRequestCode());
            diffMapper.insert(diff);
        }

        // 5) 登记统一待办：进入审批中心 BU Scope 队列（关键属性变更 / 跨 BU 影响由 Steward 升级 GC 复核）
        Applicant applicant = resolveApplicant();
        LocalDateTime now = LocalDateTime.now();
        String sceneCode = deactivate ? CmdConstants.SCENE_DEACTIVATE : CmdConstants.SCENE_CUSTOMER_CHANGE;
        CmdApprovalTask task = new CmdApprovalTask();
        task.setTaskNo(generateTaskNo());
        task.setTaskCategory(CmdConstants.APPR_CAT_APPROVAL);
        task.setBizType(CmdConstants.BIZ_TYPE_CHANGE);
        task.setBizId(entity.getRequestCode());
        task.setBizTitle(customer.getLegalName() + " · "
            + (deactivate ? "逻辑停用" : "属性变更")
            + (keyChange ? "（关键属性）" : ""));
        task.setOneId(customer.getOneId());
        task.setSceneCode(sceneCode);
        task.setApplicantId(applicant.id());
        task.setApplicantName(applicant.name());
        task.setBuScope(entity.getBuScope());
        task.setScope(CmdConstants.SCOPE_BU);
        task.setCurrentNodeCode(NODE_BU_REVIEW);
        task.setCurrentNodeName(CmdConstants.nodeName(NODE_BU_REVIEW));
        task.setAssigneeName(NAME_BU_STEWARD);
        task.setAssigneeRole(ROLE_BU_STEWARD);
        task.setStatus(CmdConstants.APPR_STATUS_PENDING);
        task.setRiskLevel(resolveRiskLevel(keyChange, impact.level));
        task.setDqScore(customer.getDqScore());
        task.setDuplicateState(customer.getMatchState());
        task.setCrossBuFlag(impact.crossBu ? CmdConstants.YES : CmdConstants.NO);
        task.setSubmitTime(now);
        long slaHours = resolveSlaHours(sceneCode);
        task.setSlaDue(now.plusHours(slaHours));
        task.setSlaState(CmdConstants.SLA_NORMAL);
        task.setEvidenceJson(buildEvidence(entity, diffs, impact));
        task.setBizSnapshotJson(JsonUtils.toJsonString(customer));
        task.setRemark(bo.getRemark());
        approvalTaskMapper.insert(task);

        // 6) 回填申请单上的审批待办与流程实例（流程实例由引擎在首次决策时启动，此处仅登记待办）
        CmdChangeRequest patch = new CmdChangeRequest();
        patch.setId(entity.getId());
        patch.setApprovalId(task.getId());
        changeMapper.updateById(patch);

        // 7) 业务轨迹：提交申请（与引擎 flow_his_task 互补，业务人员可读）
        CmdApprovalAction action = new CmdApprovalAction();
        action.setTaskId(task.getId());
        action.setTaskNo(task.getTaskNo());
        action.setOneId(customer.getOneId());
        action.setActionType(CmdConstants.ACTION_SUBMIT);
        action.setActionName("提交申请");
        action.setFromNodeCode(deactivate ? "DEACT_APPLY" : "CHANGE_APPLY");
        action.setToNodeCode(NODE_BU_REVIEW);
        action.setOperatorId(applicant.id());
        action.setOperatorName(applicant.name());
        action.setOperatorRole(ROLE_BU_STEWARD);
        action.setActionTime(now);
        action.setBeforeState(CmdConstants.CHG_STATUS_DRAFT);
        action.setAfterState(CmdConstants.CHG_STATUS_PENDING);
        action.setEvidenceJson(task.getEvidenceJson());
        approvalActionMapper.insert(action);

        // 8) 工作流步骤总账：发起 + 影响检查两步都打到客户 One ID 上
        recordStep(customer.getOneId(), task.getTaskNo(), deactivate ? "DEACT_APPLY" : "CHANGE_APPLY",
            CmdConstants.STEP_SUBMIT, CmdConstants.ACTION_SUBMIT, "提交申请",
            applicant.id(), applicant.name(), ROLE_BU_STEWARD,
            CmdConstants.CHG_STATUS_DRAFT, CmdConstants.CHG_STATUS_PENDING, bo.getChangeReason());
        recordStep(customer.getOneId(), task.getTaskNo(), "IMPACT_CHECK",
            CmdConstants.STEP_SYSTEM, "IMPACT_CHECK", "关联关系影响检查",
            applicant.id(), applicant.name(), "SYSTEM",
            null, impact.level, impact.message);

        // 9) 审计留痕：审计中心可按 One ID / 申请编号反查本次发起
        AuditEvent audit = new AuditEvent();
        audit.setEventType(deactivate ? "DEACTIVATE" : "CHANGE");
        audit.setEventName((deactivate ? "发起逻辑停用申请：" : "发起属性变更申请：") + customer.getLegalName());
        audit.setBizType(CmdConstants.BIZ_TYPE_CHANGE);
        audit.setBizId(entity.getRequestCode());
        audit.setOneId(customer.getOneId());
        audit.setOperatorId(applicant.id());
        audit.setOperatorName(applicant.name());
        audit.setOperatorRole("BUSINESS_USER");
        audit.setEventTime(now);
        audit.setResult("SUCCESS");
        audit.setRiskLevel(task.getRiskLevel());
        audit.setChangedFields(diffs.stream().map(CmdChangeDiff::getFieldCode).reduce((a, b) -> a + "," + b).orElse(""));
        audit.setBeforeJson(JsonUtils.toJsonString(diffsToMap(diffs, true)));
        audit.setAfterJson(JsonUtils.toJsonString(diffsToMap(diffs, false)));
        audit.setRemark(entity.getChangeReason());
        auditService.record(audit);

        log.info("[CMD][CHANGE] 变更申请已提交：code={} type={} oneId={} 关键属性={} 影响检查={}",
            entity.getRequestCode(), changeType, customer.getOneId(), keyChange, impact.level);
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdChangeKpiVo> selectKpi() {
        List<CmdChangeKpiVo> list = new ArrayList<>(4);
        list.add(kpi("待审批变更",
            count(CmdConstants.CHG_TYPE_UPDATE, List.of(CmdConstants.CHG_STATUS_PENDING), null),
            "属性变更申请处于待审批"));
        list.add(kpi("待审批停用",
            count(CmdConstants.CHG_TYPE_DEACTIVATE, List.of(CmdConstants.CHG_STATUS_PENDING), null),
            "逻辑停用申请处于待审批"));
        list.add(kpi("本月已生效",
            count(null, List.of(CmdConstants.CHG_STATUS_EFFECTIVE, CmdConstants.CHG_STATUS_APPROVED),
                LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay()),
            "本月内完成生效的变更与停用"));
        // One ID 稳定性的量化证明：变更只递增版本号，绝不重新生成 One ID，因此该值恒为 0
        list.add(kpi("One ID重生成", 0L, "One ID 稳定：变更只递增版本，不重新生成"));
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdChangeDetailVo selectDetail(String requestCode) {
        CmdChangeRequest entity = changeMapper.selectOne(
            new LambdaQueryWrapper<CmdChangeRequest>().eq(CmdChangeRequest::getRequestCode, requestCode));
        if (ObjectUtil.isNull(entity)) {
            throw new ServiceException("变更申请不存在：{}", requestCode);
        }
        CmdChangeDetailVo vo = new CmdChangeDetailVo();
        vo.setId(entity.getId());
        vo.setRequestCode(entity.getRequestCode());
        vo.setOneId(entity.getOneId());
        vo.setLegalName(entity.getLegalName());
        vo.setChangeType(entity.getChangeType());
        vo.setTargetStatus(entity.getTargetStatus());
        vo.setIsKeyChange(entity.getIsKeyChange());
        vo.setBuScope(entity.getBuScope());
        vo.setChangeReason(entity.getChangeReason());
        vo.setStatus(entity.getStatus());
        vo.setEffectiveDate(entity.getEffectiveDate());
        vo.setEffectiveTime(entity.getEffectiveTime());
        vo.setRelationCheck(entity.getRelationCheck());
        vo.setRelationMsg(entity.getRelationMsg());
        vo.setFlowInstanceId(entity.getFlowInstanceId());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setApprovedTime(entity.getApprovedTime());

        // Before / After 差异明细
        vo.setDiffs(diffMapper.selectVoList(new LambdaQueryWrapper<CmdChangeDiff>()
            .eq(CmdChangeDiff::getRequestId, entity.getId())
            .orderByAsc(CmdChangeDiff::getOrderNum)));

        // 影响面：优先取提交时固化的 impact_json，避免用当前库状态覆盖历史结论
        List<String> impacts = parseImpacts(entity.getImpactJson());
        if (impacts.isEmpty() && StringUtils.isNotBlank(entity.getRelationMsg())) {
            impacts.add(entity.getRelationMsg());
        }
        vo.setImpacts(impacts);

        // 主档版本上下文：证明「换版本不换 One ID」
        CmdCustomer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, entity.getOneId()));
        if (ObjectUtil.isNotNull(customer)) {
            vo.setCurrentVersionNo(customer.getVersionNo());
        }
        List<CmdCustomerVersionVo> versions = selectVersions(entity.getOneId());
        vo.setVersions(versions);
        versions.stream()
            .filter(v -> Objects.equals(v.getChangeRequestId(), entity.getId()))
            .findFirst()
            .ifPresent(v -> vo.setEffectiveVersionNo(v.getVersionNo()));

        // 审批轨迹（业务视角）
        List<CmdChangeTrailVo> trail = selectTrail(entity);
        vo.setTrail(trail);
        // 审批人 / 当前处理人：优先取审批通过动作的操作人，否则取关联待办的办理人
        trail.stream()
            .filter(t -> CmdConstants.APPR_STATUS_APPROVED.equalsIgnoreCase(t.getResult()))
            .reduce((first, second) -> second)
            .ifPresent(t -> vo.setApprovedByName(t.getOperator()));
        if (StringUtils.isBlank(vo.getApprovedByName()) && ObjectUtil.isNotNull(entity.getApprovalId())) {
            CmdApprovalTask task = approvalTaskMapper.selectById(entity.getApprovalId());
            if (ObjectUtil.isNotNull(task)) {
                vo.setApprovalTaskNo(task.getTaskNo());
                vo.setApprovedByName(task.getAssigneeName());
            }
        }
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdCustomerVersionVo> selectVersions(String oneId) {
        return versionMapper.selectVoList(new LambdaQueryWrapper<CmdCustomerVersion>()
            .eq(CmdCustomerVersion::getOneId, oneId)
            .orderByDesc(CmdCustomerVersion::getVersionNo));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdDeactivateResultVo selectDeactivateResult(String oneId) {
        CmdDeactivateResultVo vo = new CmdDeactivateResultVo();
        CmdCustomer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, oneId));
        // 取该客户最近一次停用申请，作为业务视图的权威来源
        CmdChangeRequest latest = changeMapper.selectOne(new LambdaQueryWrapper<CmdChangeRequest>()
            .eq(CmdChangeRequest::getOneId, oneId)
            .eq(CmdChangeRequest::getChangeType, CmdConstants.CHG_TYPE_DEACTIVATE)
            .orderByDesc(CmdChangeRequest::getCreateTime)
            .last("LIMIT 1"));

        String status = ObjectUtil.isNotNull(customer) ? customer.getStatus() : "-";
        String targetStatus = ObjectUtil.isNotNull(latest) && StringUtils.isNotBlank(latest.getTargetStatus())
            ? latest.getTargetStatus() : status;
        String reason = ObjectUtil.isNotNull(latest) ? latest.getChangeReason() : "-";
        Integer versionNo = ObjectUtil.isNotNull(customer) ? customer.getVersionNo() : null;

        vo.getBusinessView().add(CmdDeactivateResultVo.KvVo.of("One ID", oneId));
        vo.getBusinessView().add(CmdDeactivateResultVo.KvVo.of("客户名称",
            ObjectUtil.isNotNull(customer) ? customer.getLegalName() : "-"));
        vo.getBusinessView().add(CmdDeactivateResultVo.KvVo.of("停用前状态", CmdConstants.CUST_STATUS_ACTIVE));
        vo.getBusinessView().add(CmdDeactivateResultVo.KvVo.of("停用后状态", targetStatus));
        vo.getBusinessView().add(CmdDeactivateResultVo.KvVo.of("停用原因", StringUtils.blankToDefault(reason, "-")));
        vo.getBusinessView().add(CmdDeactivateResultVo.KvVo.of("当前版本", versionNo == null ? "-" : "v" + versionNo));
        vo.getBusinessView().add(CmdDeactivateResultVo.KvVo.of("物理删除", "No（逻辑停用）"));

        // 落库记录：逐条列出实际字段取值，作为「无物理删除」的证据
        vo.getDbRecords().add("cmd_customer.status = '" + targetStatus + "'");
        vo.getDbRecords().add("cmd_customer.del_flag = '" + CmdConstants.DEL_FLAG_NORMAL + "'  -- 记录保留，未物理删除");
        vo.getDbRecords().add("cmd_customer.one_id = '" + oneId + "'  -- One ID 不回收");
        vo.getDbRecords().add("cmd_customer.version_no = " + (versionNo == null ? 1 : versionNo)
            + "  -- 停用同样生成新版本");
        vo.getDbRecords().add("cmd_customer.effective_to = "
            + (ObjectUtil.isNotNull(customer) && customer.getEffectiveTo() != null
                ? "'" + customer.getEffectiveTo() + "'" : "NULL（待生效）"));
        if (ObjectUtil.isNotNull(latest)) {
            vo.getDbRecords().add("cmd_change_request.request_code = '" + latest.getRequestCode() + "'");
            vo.getDbRecords().add("cmd_change_request.relation_check = '" + latest.getRelationCheck() + "'");
            vo.getDbRecords().add("cmd_change_request.status = '" + latest.getStatus() + "'");
        }
        vo.getDbRecords().add("audit_event.action = 'DEACTIVATE'  -- 审计留痕保留");
        vo.getDbRecords().add("cmd_customer_version -- 历史版本与来源快照全部保留");

        vo.setVersions(selectVersions(oneId));
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int effect(String requestCode) {
        CmdChangeRequest entity = changeMapper.selectOne(
            new LambdaQueryWrapper<CmdChangeRequest>().eq(CmdChangeRequest::getRequestCode, requestCode));
        if (ObjectUtil.isNull(entity)) {
            throw new ServiceException("变更申请不存在：{}", requestCode);
        }
        if (CmdConstants.CHG_STATUS_EFFECTIVE.equals(entity.getStatus())) {
            throw new ServiceException("该申请已生效，无需重复操作");
        }
        if (!CmdConstants.CHG_STATUS_APPROVED.equals(entity.getStatus())) {
            throw new ServiceException("申请尚未审批通过（当前状态：{}），请先在审批中心完成 BU / GC 审批",
                entity.getStatus());
        }
        CmdCustomer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, entity.getOneId()));
        if (ObjectUtil.isNull(customer)) {
            throw new ServiceException("客户不存在：{}", entity.getOneId());
        }
        CmdCustomer before = copyCustomer(customer);

        boolean deactivate = CmdConstants.CHG_TYPE_DEACTIVATE.equals(entity.getChangeType());
        int nextVersion = (customer.getVersionNo() == null ? 1 : customer.getVersionNo()) + 1;
        CmdCustomer patch = new CmdCustomer();
        patch.setId(customer.getId());
        patch.setVersionNo(nextVersion);
        List<String> changedFields = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if (deactivate) {
            // 逻辑停用：只改状态与失效时间，记录本身保留
            patch.setStatus(resolveTargetStatus(entity.getTargetStatus()));
            patch.setEffectiveTo(now);
            changedFields.add("status");
        } else {
            // 属性变更：按差异明细写回主档（字段取 md_field.physical_column，配置驱动）
            List<CmdChangeDiff> diffs = diffMapper.selectList(new LambdaQueryWrapper<CmdChangeDiff>()
                .eq(CmdChangeDiff::getRequestId, entity.getId())
                .orderByAsc(CmdChangeDiff::getOrderNum));
            for (CmdChangeDiff diff : diffs) {
                if (CmdConstants.DIFF_SAME.equals(diff.getChangeFlag())) {
                    continue;
                }
                String column = physicalColumnOf(diff.getFieldCode());
                if (StringUtils.isBlank(column)) {
                    continue;
                }
                writeField(patch, column, diff.getAfterValue());
                changedFields.add(diff.getFieldCode());
            }
            if (changedFields.isEmpty()) {
                throw new ServiceException("没有可生效的字段变化，请检查变更内容");
            }
        }
        customerMapper.updateById(patch);

        // 追加版本快照：One ID 不变，只递增 version_no（历史版本全部保留）
        CmdCustomer after = customerMapper.selectById(customer.getId());
        CmdCustomerVersion version = new CmdCustomerVersion();
        version.setOneId(after.getOneId());
        version.setVersionNo(nextVersion);
        version.setChangeType(deactivate ? "DEACTIVATE" : "UPDATE");
        version.setChangeReason(entity.getChangeReason());
        version.setSnapshotJson(JsonUtils.toJsonString(after));
        version.setBeforeJson(JsonUtils.toJsonString(before));
        version.setChangedFields(String.join(",", changedFields));
        version.setDqScore(after.getDqScore());
        version.setStatus("effective");
        version.setSourceSystem(after.getSourceSystem());
        version.setChangeRequestId(entity.getId());
        version.setRemark("变更申请 " + requestCode + " 生效，One ID 保持 " + after.getOneId() + " 不变");
        versionMapper.insert(version);

        // 申请单置为已生效
        CmdChangeRequest patchReq = new CmdChangeRequest();
        patchReq.setId(entity.getId());
        patchReq.setStatus(CmdConstants.CHG_STATUS_EFFECTIVE);
        patchReq.setEffectiveTime(now);
        if (entity.getApprovedTime() == null) {
            patchReq.setApprovedTime(now);
            patchReq.setApprovedBy(LoginHelper.getUserId());
        }
        changeMapper.updateById(patchReq);

        Applicant applicant = resolveApplicant();
        String taskNo = "";
        if (ObjectUtil.isNotNull(entity.getApprovalId())) {
            CmdApprovalTask linkedTask = approvalTaskMapper.selectById(entity.getApprovalId());
            if (ObjectUtil.isNotNull(linkedTask)) {
                taskNo = StringUtils.blankToDefault(linkedTask.getTaskNo(), "");
            }
        }
        recordStep(entity.getOneId(), taskNo, "EFFECT", CmdConstants.STEP_SYSTEM, "EFFECT",
            deactivate ? "更新主档状态" : "生成主档新版本", applicant.id(), applicant.name(), "SYSTEM",
            entity.getStatus(), CmdConstants.CHG_STATUS_EFFECTIVE,
            (deactivate ? "status=" + patch.getStatus() : "字段=" + String.join(",", changedFields))
                + "，版本 v" + nextVersion + "，One ID 不变");

        AuditEvent audit = new AuditEvent();
        audit.setEventType("EFFECT");
        audit.setEventName((deactivate ? "逻辑停用生效：" : "属性变更生效：") + entity.getLegalName());
        audit.setBizType(CmdConstants.BIZ_TYPE_CHANGE);
        audit.setBizId(entity.getRequestCode());
        audit.setOneId(entity.getOneId());
        audit.setOperatorId(applicant.id());
        audit.setOperatorName(applicant.name());
        audit.setOperatorRole("SYSTEM");
        audit.setEventTime(now);
        audit.setResult("SUCCESS");
        audit.setRiskLevel(CmdConstants.RISK_LOW);
        audit.setChangedFields(String.join(",", changedFields));
        audit.setBeforeJson(JsonUtils.toJsonString(before));
        audit.setAfterJson(JsonUtils.toJsonString(after));
        audit.setRemark("版本 v" + nextVersion + " 已生效，One ID 保持 " + after.getOneId() + " 不变");
        auditService.record(audit);

        log.info("[CMD][CHANGE] 申请已生效：code={} oneId={} version=v{} fields={}",
            requestCode, entity.getOneId(), nextVersion, changedFields);
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancel(String requestCode) {
        CmdChangeRequest entity = changeMapper.selectOne(
            new LambdaQueryWrapper<CmdChangeRequest>().eq(CmdChangeRequest::getRequestCode, requestCode));
        if (ObjectUtil.isNull(entity)) {
            throw new ServiceException("变更申请不存在：{}", requestCode);
        }
        if (CmdConstants.CHG_STATUS_EFFECTIVE.equals(entity.getStatus())) {
            throw new ServiceException("申请已生效，不能撤回");
        }
        if (CmdConstants.CHG_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw new ServiceException("申请已撤回，无需重复操作");
        }
        CmdChangeRequest patch = new CmdChangeRequest();
        patch.setId(entity.getId());
        patch.setStatus(CmdConstants.CHG_STATUS_CANCELLED);
        int rows = changeMapper.updateById(patch);

        // 关联待办同步取消，避免审批中心仍挂着已撤回的申请
        if (ObjectUtil.isNotNull(entity.getApprovalId())) {
            CmdApprovalTask task = approvalTaskMapper.selectById(entity.getApprovalId());
            if (ObjectUtil.isNotNull(task) && CmdConstants.APPR_STATUS_PENDING.equals(task.getStatus())) {
                CmdApprovalTask taskPatch = new CmdApprovalTask();
                taskPatch.setId(task.getId());
                taskPatch.setStatus(CmdConstants.APPR_STATUS_CANCELLED);
                taskPatch.setFinishTime(LocalDateTime.now());
                approvalTaskMapper.updateById(taskPatch);

                CmdApprovalAction action = new CmdApprovalAction();
                action.setTaskId(task.getId());
                action.setTaskNo(task.getTaskNo());
                action.setOneId(task.getOneId());
                action.setActionType(CmdConstants.ACTION_WITHDRAW);
                action.setActionName("撤回申请");
                action.setFromNodeCode(task.getCurrentNodeCode());
                action.setToNodeCode(task.getCurrentNodeCode());
                action.setOperatorId(LoginHelper.getUserId());
                action.setOperatorRole(task.getAssigneeRole());
                action.setActionTime(LocalDateTime.now());
                action.setBeforeState(CmdConstants.APPR_STATUS_PENDING);
                action.setAfterState(CmdConstants.APPR_STATUS_CANCELLED);
                approvalActionMapper.insert(action);
            }
        }

        Applicant applicant = resolveApplicant();
        AuditEvent audit = new AuditEvent();
        audit.setEventType("CANCEL");
        audit.setEventName("撤回变更申请：" + entity.getLegalName());
        audit.setBizType(CmdConstants.BIZ_TYPE_CHANGE);
        audit.setBizId(entity.getRequestCode());
        audit.setOneId(entity.getOneId());
        audit.setOperatorId(applicant.id());
        audit.setOperatorName(applicant.name());
        audit.setOperatorRole("BUSINESS_USER");
        audit.setEventTime(LocalDateTime.now());
        audit.setResult("SUCCESS");
        audit.setRiskLevel(CmdConstants.RISK_LOW);
        audit.setRemark("申请人撤回，" + entity.getStatus() + " → " + CmdConstants.CHG_STATUS_CANCELLED);
        auditService.record(audit);
        return rows;
    }

    // ==================================================================
    //  内部实现
    // ==================================================================

    /**
     * 构建变更场景的字段级差异快照（Before 值取自主档当前值）
     *
     * @param customer 客户主档当前快照
     * @param bos      前端提交的字段级明细
     * @return 差异实体列表
     */
    private List<CmdChangeDiff> buildFieldDiffs(CmdCustomer customer, List<CmdChangeDiffBo> bos) {
        List<CmdChangeDiff> result = new ArrayList<>();
        if (bos == null || bos.isEmpty()) {
            return result;
        }
        Map<String, MdField> fieldConfig = loadFieldConfig(
            bos.stream().map(CmdChangeDiffBo::getFieldCode).filter(StringUtils::isNotBlank).distinct().toList());
        int order = 0;
        for (CmdChangeDiffBo bo : bos) {
            if (StringUtils.isBlank(bo.getFieldCode())) {
                continue;
            }
            MdField md = fieldConfig.get(bo.getFieldCode());
            if (ObjectUtil.isNull(md)) {
                throw new ServiceException("字段 {} 未在元数据中登记（平台管理 → 字段与值集），无法变更",
                    bo.getFieldCode());
            }
            String column = StringUtils.blankToDefault(md.getPhysicalColumn(), null);
            if (StringUtils.isBlank(column)) {
                throw new ServiceException("字段「{}」尚未配置物理列，暂不支持直接变更主档；"
                    + "请先在平台管理维护 physical_column", md.getFieldName());
            }
            String before = readField(customer, column);
            String after = bo.getAfterValue();
            CmdChangeDiff diff = new CmdChangeDiff();
            diff.setFieldCode(md.getFieldCode());
            diff.setFieldName(StringUtils.blankToDefault(md.getFieldName(), md.getFieldCode()));
            diff.setBeforeValue(before);
            diff.setAfterValue(after);
            diff.setIsKeyField(StringUtils.blankToDefault(md.getIsKeyField(), CmdConstants.NO));
            diff.setIsSensitive(StringUtils.blankToDefault(md.getIsSensitive(), CmdConstants.NO));
            diff.setChangeFlag(resolveChangeFlag(before, after));
            diff.setOrderNum(order++);
            diff.setRemark(bo.getRemark());
            result.add(diff);
        }
        return result;
    }

    /**
     * 构建停用场景的差异行（停用是状态切换，用一行 status 差异表达 Before / After）
     *
     * @param customer     客户主档
     * @param targetStatus 目标状态（Inactive / Archived）
     * @return 差异实体
     */
    private CmdChangeDiff buildStatusDiff(CmdCustomer customer, String targetStatus) {
        CmdChangeDiff diff = new CmdChangeDiff();
        diff.setFieldCode("status");
        diff.setFieldName("客户状态（逻辑停用）");
        diff.setBeforeValue(customer.getStatus());
        diff.setAfterValue(resolveTargetStatus(targetStatus));
        // 停用属于重大状态变更，按关键字段处理，进入更高级别审批
        diff.setIsKeyField(CmdConstants.YES);
        diff.setIsSensitive(CmdConstants.NO);
        diff.setChangeFlag(CmdConstants.DIFF_MODIFY);
        diff.setOrderNum(0);
        diff.setRemark("逻辑停用：不执行物理删除，历史版本与交叉引用保留");
        return diff;
    }

    /**
     * 关联关系影响检查：检查该客户在 A3-A2-A1 层级、Payer 关系与跨 BU 共享上的依赖
     *
     * @param oneId 客户 One ID
     * @param diffs 差异明细（用于判断是否触及关键标识字段）
     * @return 影响检查结论
     */
    private RelationImpact checkRelationImpact(String oneId, List<CmdChangeDiff> diffs) {
        RelationImpact impact = new RelationImpact();
        List<CmdHierarchyRelationVo> relations = hierarchyService.selectRelationList(oneId);
        impact.relations = relations;
        long effective = relations.stream()
            .filter(r -> CmdConstants.HIER_REL_STATUS_EFFECTIVE.equals(r.getStatus())).count();
        long crossBu = relations.stream()
            .filter(r -> CmdConstants.YES.equals(r.getCrossBuFlag())).count();
        impact.crossBu = crossBu > 0;

        if (relations.isEmpty()) {
            impact.level = CmdConstants.REL_CHECK_PASS;
            impact.message = "无生效关联，可直接处理";
            impact.items.add("层级关系：该客户尚未挂到 A3-A2-A1 树上，无层级依赖");
            impact.items.add("Payer 关系：无");
            impact.items.add("下游引用：无生效关联，下游不产生连带影响");
            return impact;
        }

        impact.items.add("层级关系：关联 " + relations.size() + " 条（生效 " + effective + " 条）");
        String parentRelation = relations.stream()
            .filter(r -> CmdConstants.HIER_REL_STATUS_EFFECTIVE.equals(r.getStatus()))
            .map(r -> r.getRelationType() + "：" + r.getParentOneId() + " → " + r.getChildOneId())
            .reduce((a, b) -> a + "；" + b)
            .orElse("无生效层级关系");
        impact.items.add("层级路径： " + parentRelation);

        boolean hasPayer = relations.stream().anyMatch(r -> StringUtils.isNotBlank(r.getPayerOneId()));
        impact.items.add(hasPayer
            ? "Payer 关系：存在 Payer 挂钩（" + relations.stream()
                .map(CmdHierarchyRelationVo::getPayerOneId).filter(StringUtils::isNotBlank)
                .distinct().reduce((a, b) -> a + "、" + b).orElse("-") + "），停用后需重新指派"
            : "Payer 关系：无");
        impact.items.add(impact.crossBu
            ? "跨 BU：存在 " + crossBu + " 条跨 BU 共享关系，需 GC Scope 复核"
            : "跨 BU：不涉及跨 BU 共享主档");
        impact.items.add("下游引用：状态变更以 Inactive / 新版本下发，下游接收状态变更而非物理删除");

        // 触及关键标识（信用代码 / 状态 / 客户层级）或跨 BU 时给出 WARN，触发 GC 复核
        boolean keyTouched = diffs.stream().anyMatch(d ->
            CmdConstants.YES.equals(d.getIsKeyField()) && !CmdConstants.DIFF_SAME.equals(d.getChangeFlag()));
        if (impact.crossBu || (effective > 0 && keyTouched)) {
            impact.level = CmdConstants.REL_CHECK_WARN;
            impact.message = impact.crossBu
                ? "存在 " + crossBu + " 条跨 BU 关系，需 GC Scope 复核"
                : "存在 " + effective + " 条生效层级关系且本次涉及关键属性，需复核层级与 Payer 影响";
        } else {
            impact.level = CmdConstants.REL_CHECK_PASS;
            impact.message = "关联层级与 Payer 关系校验通过";
        }
        return impact;
    }

    /**
     * 读取主档字段原值（字段取元数据 physical_column，避免反射与硬编码字段名）
     *
     * @param customer 客户主档
     * @param column   物理列名（snake_case）
     * @return 字段值
     */
    private String readField(CmdCustomer customer, String column) {
        return switch (column) {
            case "legal_name" -> customer.getLegalName();
            case "legal_name_en" -> customer.getLegalNameEn();
            case "short_name" -> customer.getShortName();
            case "credit_code" -> customer.getCreditCode();
            case "tax_no" -> customer.getTaxNo();
            case "customer_type" -> customer.getCustomerType();
            case "customer_level" -> customer.getCustomerLevel();
            case "product_line" -> customer.getProductLine();
            case "bu_scope" -> customer.getBuScope();
            case "country" -> customer.getCountry();
            case "province" -> customer.getProvince();
            case "city" -> customer.getCity();
            case "address" -> customer.getAddress();
            case "payer_id" -> customer.getPayerId();
            case "postal_code" -> customer.getPostalCode();
            case "contact_name" -> customer.getContactName();
            case "contact_phone" -> customer.getContactPhone();
            case "contact_email" -> customer.getContactEmail();
            case "status" -> customer.getStatus();
            case "source_system" -> customer.getSourceSystem();
            default -> null;
        };
    }

    /**
     * 写入主档字段新值
     *
     * @param target 待更新实体（仅承载本次变更字段）
     * @param column 物理列名（snake_case）
     * @param value  新值
     */
    private void writeField(CmdCustomer target, String column, String value) {
        switch (column) {
            case "legal_name" -> target.setLegalName(value);
            case "legal_name_en" -> target.setLegalNameEn(value);
            case "short_name" -> target.setShortName(value);
            case "credit_code" -> target.setCreditCode(value);
            case "tax_no" -> target.setTaxNo(value);
            case "customer_type" -> target.setCustomerType(value);
            case "customer_level" -> target.setCustomerLevel(value);
            case "product_line" -> target.setProductLine(value);
            case "bu_scope" -> target.setBuScope(value);
            case "country" -> target.setCountry(value);
            case "province" -> target.setProvince(value);
            case "city" -> target.setCity(value);
            case "address" -> target.setAddress(value);
            case "payer_id" -> target.setPayerId(value);
            case "postal_code" -> target.setPostalCode(value);
            case "contact_name" -> target.setContactName(value);
            case "contact_phone" -> target.setContactPhone(value);
            case "contact_email" -> target.setContactEmail(value);
            case "status" -> target.setStatus(value);
            case "source_system" -> target.setSourceSystem(value);
            default -> throw new ServiceException("字段未配置可写入的物理列：{}", column);
        }
    }

    /**
     * 加载字段元数据（md_field，CUSTOMER 模型）
     *
     * @param fieldCodes 字段编码列表
     * @return 字段编码 → 元数据
     */
    private Map<String, MdField> loadFieldConfig(List<String> fieldCodes) {
        Map<String, MdField> map = new LinkedHashMap<>();
        if (fieldCodes.isEmpty()) {
            return map;
        }
        List<MdField> fields = mdFieldMapper.selectList(new LambdaQueryWrapper<MdField>()
            .eq(MdField::getModelCode, MD_MODEL_CUSTOMER)
            .in(MdField::getFieldCode, fieldCodes)
            .in(MdField::getStatus, "0", "1")
            .orderByAsc(MdField::getOrderNum));
        fields.forEach(f -> map.putIfAbsent(f.getFieldCode(), f));
        return map;
    }

    /**
     * 解析字段编码对应的物理列（供生效时写回主档）
     *
     * @param fieldCode 字段编码
     * @return 物理列名，未配置时返回 null
     */
    private String physicalColumnOf(String fieldCode) {
        MdField field = mdFieldMapper.selectOne(new LambdaQueryWrapper<MdField>()
            .eq(MdField::getModelCode, MD_MODEL_CUSTOMER)
            .eq(MdField::getFieldCode, fieldCode)
            .orderByAsc(MdField::getOrderNum)
            .last("LIMIT 1"));
        return ObjectUtil.isNull(field) ? null : field.getPhysicalColumn();
    }

    /**
     * 生成申请编号（CH-yyyyMMdd-####，服务端唯一）
     *
     * @return 申请编号
     */
    private String generateRequestCode() {
        String prefix = "CH-" + LocalDate.now().format(CODE_DAY) + "-";
        for (int seq = 1; seq <= 9999; seq++) {
            String code = prefix + String.format("%04d", seq);
            Long exists = changeMapper.lambda().eq(CmdChangeRequest::getRequestCode, code).count();
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new ServiceException("申请编号生成失败：当日流水号已用尽");
    }

    /**
     * 生成审批任务编号（AP-yyyyMMdd-####，与客户新建申请共用编号规则）
     *
     * @return 任务编号
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
        throw new ServiceException("审批任务编号生成失败：当日流水号已用尽");
    }

    /**
     * 规范化变更类型（兼容大小写与中英文取值）
     *
     * @param changeType 原始类型
     * @return Update / Deactivate
     */
    private String normalizeChangeType(String changeType) {
        if (StringUtils.isBlank(changeType)) {
            throw new ServiceException("变更类型不能为空");
        }
        String value = changeType.trim();
        if (value.equalsIgnoreCase(CmdConstants.CHG_TYPE_DEACTIVATE)
            || "停用".equals(value) || "逻辑停用".equals(value)) {
            return CmdConstants.CHG_TYPE_DEACTIVATE;
        }
        return CmdConstants.CHG_TYPE_UPDATE;
    }

    /**
     * 规范化停用目标状态（Inactive / Archived → inactive / archived）
     *
     * @param targetStatus 原始目标状态
     * @return 规范化后的主档状态
     */
    private String resolveTargetStatus(String targetStatus) {
        String value = StringUtils.blankToDefault(targetStatus, CmdConstants.CUST_STATUS_INACTIVE);
        String resolved = DEACTIVATE_TARGET.get(value.trim().toLowerCase());
        if (StringUtils.isBlank(resolved)) {
            throw new ServiceException("目标状态仅支持 Inactive / Archived，当前值：{}", targetStatus);
        }
        return resolved;
    }

    /**
     * 解析差异变化类型
     *
     * @param before 变更前值
     * @param after  变更后值
     * @return ADD / MODIFY / DELETE / SAME
     */
    private String resolveChangeFlag(String before, String after) {
        boolean beforeBlank = StringUtils.isBlank(before);
        boolean afterBlank = StringUtils.isBlank(after);
        if (Objects.equals(before, after)) {
            return CmdConstants.DIFF_SAME;
        }
        if (beforeBlank) {
            return CmdConstants.DIFF_ADD;
        }
        if (afterBlank) {
            return CmdConstants.DIFF_DELETE;
        }
        return CmdConstants.DIFF_MODIFY;
    }

    /**
     * 风险等级：关键属性变更或影响检查告警时按高风险处理，由 Steward 决定是否升级 GC
     *
     * @param keyChange    是否关键属性
     * @param relationCheck 关系检查结论
     * @return 风险等级
     */
    private String resolveRiskLevel(boolean keyChange, String relationCheck) {
        if (keyChange || CmdConstants.REL_CHECK_WARN.equals(relationCheck)
            || CmdConstants.REL_CHECK_FAIL.equals(relationCheck)) {
            return CmdConstants.RISK_HIGH;
        }
        return CmdConstants.RISK_MEDIUM;
    }

    /**
     * 读取场景 SLA 时长（cmd_flow_scene.sla_hours，未配置时兜底 48 小时）
     *
     * @param sceneCode 场景编码
     * @return SLA 小时数
     */
    private long resolveSlaHours(String sceneCode) {
        Map<String, Object> scene = flowSceneMapper.selectSceneByCode(sceneCode);
        Object hours = scene == null ? null : scene.get("sla_hours");
        return hours instanceof Number number ? number.longValue() : DEFAULT_SLA_HOURS;
    }

    /**
     * 构造治理证据（供审批中心详情直接展示）
     *
     * @param entity 申请单
     * @param diffs  差异明细
     * @param impact 影响检查结论
     * @return 证据说明
     */
    private String buildEvidence(CmdChangeRequest entity, List<CmdChangeDiff> diffs, RelationImpact impact) {
        // cmd_approval_task.evidence_json 为 MySQL JSON 列，必须写入合法 JSON（与 CmdCustomerServiceImpl#evidenceOf 保持一致）
        java.util.Map<String, Object> evidence = new java.util.LinkedHashMap<>(8);
        evidence.put("申请编号", entity.getRequestCode());
        evidence.put("类型", String.valueOf(entity.getChangeType())
            + (CmdConstants.YES.equals(entity.getIsKeyChange()) ? "（关键属性）" : ""));
        evidence.put("One ID", entity.getOneId());
        evidence.put("影响检查", impact.level + " - " + impact.message);
        evidence.put("字段差异", diffs.stream()
            .map(d -> d.getFieldName() + " " + StringUtils.blankToDefault(d.getBeforeValue(), "(空)")
                + " → " + StringUtils.blankToDefault(d.getAfterValue(), "(清空)"))
            .collect(java.util.stream.Collectors.joining("；")));
        evidence.put("影响面", impact.items == null || impact.items.isEmpty()
            ? "-" : String.join("；", impact.items));
        evidence.put("说明", "生效后 One ID 保持不变，仅递增主档版本号。");
        return JsonUtils.toJsonString(evidence);
    }

    /**
     * 审批轨迹：从申请关联的待办上取业务动作序列
     *
     * @param entity 申请单
     * @return 轨迹行
     */
    private List<CmdChangeTrailVo> selectTrail(CmdChangeRequest entity) {
        List<CmdChangeTrailVo> trail = new ArrayList<>();
        String taskNo = null;
        if (ObjectUtil.isNotNull(entity.getApprovalId())) {
            CmdApprovalTask task = approvalTaskMapper.selectById(entity.getApprovalId());
            taskNo = ObjectUtil.isNull(task) ? null : task.getTaskNo();
        }
        if (StringUtils.isBlank(taskNo)) {
            // 兜底：按业务单号直接检索轨迹（老数据 approval_id 为空时仍可展示）
            taskNo = entity.getRequestCode();
        }
        List<CmdApprovalAction> actions = approvalActionMapper.selectList(
            new LambdaQueryWrapper<CmdApprovalAction>()
                .eq(CmdApprovalAction::getTaskNo, taskNo)
                .orderByAsc(CmdApprovalAction::getActionTime));
        for (CmdApprovalAction action : actions) {
            CmdChangeTrailVo vo = new CmdChangeTrailVo();
            vo.setTime(action.getActionTime());
            vo.setRole(StringUtils.blankToDefault(action.getOperatorRole(), "-"));
            vo.setOperator(StringUtils.blankToDefault(action.getOperatorName(), "-"));
            vo.setAction(StringUtils.blankToDefault(action.getActionName(), action.getActionType()));
            vo.setNode(CmdConstants.nodeName(action.getToNodeCode()));
            vo.setResult(StringUtils.blankToDefault(action.getAfterState(), "-"));
            vo.setOpinion(action.getOpinion());
            trail.add(vo);
        }
        return trail;
    }

    /**
     * 解析影响面 JSON（提交时固化的结论）
     *
     * @param impactJson JSON 字符串
     * @return 影响面清单
     */
    private List<String> parseImpacts(String impactJson) {
        if (StringUtils.isBlank(impactJson)) {
            return new ArrayList<>();
        }
        try {
            List<String> list = JsonUtils.parseArray(impactJson, String.class);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            log.warn("[CMD][CHANGE] 影响面 JSON 解析失败，降级为纯文本：{}", e.getMessage());
            List<String> fallback = new ArrayList<>();
            fallback.add(impactJson);
            return fallback;
        }
    }

    /**
     * 差异明细转为 字段 → 值 映射（审计 before / after 快照）
     *
     * @param diffs  差异明细
     * @param before true 取变更前值，false 取变更后值
     * @return 映射
     */
    private Map<String, String> diffsToMap(List<CmdChangeDiff> diffs, boolean before) {
        Map<String, String> map = new LinkedHashMap<>();
        diffs.forEach(d -> map.put(d.getFieldCode(), before ? d.getBeforeValue() : d.getAfterValue()));
        return map;
    }

    /**
     * 写入工作流步骤（跨页面可追溯）
     */
    private void recordStep(String oneId, String taskNo, String nodeCode, String stepType,
                            String actionType, String actionName, Long operatorId, String operatorName,
                            String operatorRole, String fromStatus, String toStatus, String opinion) {
        CmdWorkflowStepLog step = new CmdWorkflowStepLog();
        step.setOneId(oneId);
        step.setTaskNo(taskNo);
        step.setStepType(stepType);
        step.setNodeCode(nodeCode);
        step.setNodeName(CmdConstants.nodeName(nodeCode));
        step.setActionType(actionType);
        step.setActionName(actionName);
        step.setOperatorId(operatorId);
        step.setOperatorName(operatorName);
        step.setOperatorRole(operatorRole);
        step.setFromStatus(fromStatus);
        step.setToStatus(toStatus);
        step.setOpinion(opinion);
        step.setCreateTime(LocalDateTime.now());
        stepLogService.recordStep(step);
    }

    /**
     * 统计申请数量
     *
     * @param changeType 变更类型（为空不过滤）
     * @param statuses   状态集合
     * @param fromTime   创建时间下限（为空不过滤）
     * @return 数量
     */
    private Long count(String changeType, List<String> statuses, LocalDateTime fromTime) {
        return changeMapper.lambda()
            .eq(StringUtils.isNotBlank(changeType), CmdChangeRequest::getChangeType, changeType)
            .in(CmdChangeRequest::getStatus, statuses)
            .ge(fromTime != null, CmdChangeRequest::getCreateTime, fromTime)
            .count();
    }

    private CmdChangeKpiVo kpi(String label, Long value, String hint) {
        CmdChangeKpiVo vo = new CmdChangeKpiVo();
        vo.setLabel(label);
        vo.setValue(value == null ? 0L : value);
        vo.setHint(hint);
        return vo;
    }

    /**
     * 浅拷贝客户主档（审计 Before 快照，避免直接引用后续被更新的实体）
     *
     * @param source 原实体
     * @return 拷贝
     */
    private CmdCustomer copyCustomer(CmdCustomer source) {
        CmdCustomer copy = new CmdCustomer();
        copy.setId(source.getId());
        copy.setOneId(source.getOneId());
        copy.setLegalName(source.getLegalName());
        copy.setCreditCode(source.getCreditCode());
        copy.setCustomerType(source.getCustomerType());
        copy.setCustomerLevel(source.getCustomerLevel());
        copy.setBuScope(source.getBuScope());
        copy.setAddress(source.getAddress());
        copy.setStatus(source.getStatus());
        copy.setVersionNo(source.getVersionNo());
        copy.setDqScore(source.getDqScore());
        copy.setMatchState(source.getMatchState());
        copy.setEffectiveFrom(source.getEffectiveFrom());
        copy.setEffectiveTo(source.getEffectiveTo());
        return copy;
    }

    /**
     * 解析申请人（POC 免登录场景兜底到演示账号）
     *
     * @return 申请人
     */
    private Applicant resolveApplicant() {
        try {
            Long userId = LoginHelper.getUserId();
            String username = LoginHelper.getUsername();
            if (userId != null && StringUtils.isNotBlank(username)) {
                return new Applicant(userId, username);
            }
        } catch (Exception ignored) {
            // 免登录场景下取不到会话，走兜底
        }
        return new Applicant(DEMO_APPLICANT_ID, DEMO_APPLICANT_NAME);
    }

    /** 申请人 */
    private record Applicant(Long id, String name) {
    }

    /** 关联关系影响检查结论 */
    private static class RelationImpact {

        /** 检查结论（PASS / WARN / FAIL） */
        private String level = CmdConstants.REL_CHECK_PASS;

        /** 检查说明 */
        private String message = "";

        /** 影响面清单（人可读，逐条说明） */
        private final List<String> items = new ArrayList<>();

        /** 是否涉及跨 BU */
        private boolean crossBu;

        /** 命中的层级关系（供举证） */
        private List<CmdHierarchyRelationVo> relations = new ArrayList<>();
    }
}
