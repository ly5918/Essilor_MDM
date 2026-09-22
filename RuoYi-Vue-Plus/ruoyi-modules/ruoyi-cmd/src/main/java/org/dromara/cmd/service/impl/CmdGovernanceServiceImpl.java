package org.dromara.cmd.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdGovernanceTask;
import org.dromara.cmd.domain.CmdLegacyMapping;
import org.dromara.cmd.domain.CmdMergeRecord;
import org.dromara.cmd.domain.bo.CmdGovernanceTaskBo;
import org.dromara.cmd.domain.vo.CmdGovernanceTaskVo;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdGovernanceTaskMapper;
import org.dromara.cmd.mapper.CmdLegacyMappingMapper;
import org.dromara.cmd.mapper.CmdMergeRecordMapper;
import org.dromara.cmd.service.ICmdAuditService;
import org.dromara.cmd.service.ICmdGovernanceService;
import org.dromara.cmd.service.ICmdImportService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 治理任务 服务层实现
 * <p>
 * 对应页面：治理任务 gov。指标卡统计直接由本表按 taskType 聚合得到，
 * 不维护冗余统计表，避免统计口径不一致。
 * <p>
 * 同时承载总设计 MERGE 场景（跨 BU 客户合并 / 迁移）的业务编排：
 * 发现候选（createDuplicateTask）→ 发起合并请求（launchMerge，建 MERGE 审批待办）→
 * BU 初审 / GC 决策（走 cmd_approval_task + Warm-Flow 客户合并审批流）→
 * 执行合并 / 新建（execMergeTask：Golden Record 更新、Legacy 交叉引用）→ 追踪审计。
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdGovernanceServiceImpl implements ICmdGovernanceService {

    private final CmdGovernanceTaskMapper taskMapper;
    private final CmdCustomerMapper customerMapper;
    private final CmdLegacyMappingMapper legacyMappingMapper;
    private final CmdMergeRecordMapper mergeRecordMapper;
    private final CmdApprovalTaskMapper approvalTaskMapper;
    private final ICmdAuditService auditService;
    private final ICmdImportService importService;

    /** 治理任务编号日期格式 */
    private static final DateTimeFormatter GOV_NO_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** MERGE 审批任务 bizSnapshotJson：合并模式 —— 存量主档合并 */
    private static final String MERGE_MODE_RECORD = "RECORD_MERGE";
    /** MERGE 审批任务 bizSnapshotJson：合并模式 —— 批量导入行关联已有 One ID */
    private static final String MERGE_MODE_ROW = "ROW_LINK";

    /** 泳道/审批节点：BU Scope 初审 */
    private static final String NODE_BU_REVIEW = "BU_REVIEW";
    private static final String NODE_NAME_BU_REVIEW = "BU Scope 初审";

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CmdGovernanceTaskVo> selectPageTaskList(CmdGovernanceTaskBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CmdGovernanceTask> lqw = buildQueryWrapper(bo);
        Page<CmdGovernanceTaskVo> page = taskMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdGovernanceTaskVo selectTaskById(Long id) {
        return taskMapper.selectVoById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int claimTask(Long id, Long userId) {
        CmdGovernanceTask task = taskMapper.selectById(id);
        if (ObjectUtil.isNull(task)) {
            throw new ServiceException("治理任务不存在或已删除");
        }
        CmdGovernanceTask update = new CmdGovernanceTask();
        update.setId(id);
        update.setStatus("CLAIMED");
        update.setAssigneeId(userId);
        update.setClaimedTime(LocalDateTime.now());
        return taskMapper.updateById(update);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resolveTask(Long id, String resolution, String opinion) {
        CmdGovernanceTask task = taskMapper.selectById(id);
        if (ObjectUtil.isNull(task)) {
            throw new ServiceException("治理任务不存在或已删除");
        }
        CmdGovernanceTask update = new CmdGovernanceTask();
        update.setId(id);
        update.setStatus("RESOLVED");
        update.setResolution(resolution);
        update.setResolvedTime(LocalDateTime.now());
        update.setRemark(opinion);
        int rows = taskMapper.updateById(update);

        // 处理结论 = 关联已有 / 合并：按总设计 MERGE 场景发起正式合并请求
        // （建 MERGE 审批待办 → BU 初审 → GC 决策 → 批准执行合并）。候选 One ID 取自证据快照。
        if (("LINK_EXISTING".equalsIgnoreCase(resolution) || "MERGE".equalsIgnoreCase(resolution))
            && task.getEvidenceJson() != null) {
            String candidate = candidateOneIdOf(task.getEvidenceJson());
            if (candidate != null && task.getOneId() != null && !candidate.equals(task.getOneId())) {
                launchMerge(task.getOneId(), candidate, opinion);
            }
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Long> selectTaskStats(CmdGovernanceTaskBo bo) {
        Map<String, Long> stats = new HashMap<>(8);
        // 未闭环的任务才进入指标卡统计
        LambdaQueryWrapper<CmdGovernanceTask> base = new LambdaQueryWrapper<CmdGovernanceTask>()
            .eq(bo != null && bo.getBuScope() != null, CmdGovernanceTask::getBuScope, bo == null ? null : bo.getBuScope())
            .ne(CmdGovernanceTask::getStatus, "RESOLVED")
            .ne(CmdGovernanceTask::getStatus, "CLOSED");
        stats.put("TOTAL", taskMapper.selectCount(base));
        stats.put(CmdConstants.GOV_TYPE_SUSPECT, countByType(bo, CmdConstants.GOV_TYPE_SUSPECT));
        stats.put(CmdConstants.GOV_TYPE_REVIEW, countByType(bo, CmdConstants.GOV_TYPE_REVIEW));
        stats.put(CmdConstants.GOV_TYPE_NEW, countByType(bo, CmdConstants.GOV_TYPE_NEW));
        stats.put(CmdConstants.GOV_TYPE_CROSS_BU, countByType(bo, CmdConstants.GOV_TYPE_CROSS_BU));
        return stats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdGovernanceTaskVo> selectTaskList(CmdGovernanceTaskBo bo) {
        return taskMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 按任务类型统计未闭环数量
     *
     * @param bo       统计范围
     * @param taskType 任务类型
     * @return 数量
     */
    private Long countByType(CmdGovernanceTaskBo bo, String taskType) {
        return taskMapper.lambda()
            .eq(CmdGovernanceTask::getTaskType, taskType)
            .eq(bo != null && bo.getBuScope() != null, CmdGovernanceTask::getBuScope, bo == null ? null : bo.getBuScope())
            .ne(CmdGovernanceTask::getStatus, "RESOLVED")
            .ne(CmdGovernanceTask::getStatus, "CLOSED")
            .count();
    }

    /**
     * 构造治理任务查询条件
     *
     * @param bo 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CmdGovernanceTask> buildQueryWrapper(CmdGovernanceTaskBo bo) {
        return QueryBuilder.lambda(CmdGovernanceTask.class)
            .eqIfText(CmdGovernanceTask::getTaskType, bo.getTaskType())
            .eqIfText(CmdGovernanceTask::getBuScope, bo.getBuScope())
            .eqIfText(CmdGovernanceTask::getStatus, bo.getStatus())
            .eqIfText(CmdGovernanceTask::getRiskLevel, bo.getRiskLevel())
            .eqIfText(CmdGovernanceTask::getMatchState, bo.getMatchState())
            .eqIfText(CmdGovernanceTask::getCrossBuFlag, bo.getCrossBuFlag())
            .eqIfText(CmdGovernanceTask::getOneId, bo.getOneId())
            .likeIfText(CmdGovernanceTask::getSubject, bo.getSubject())
            .orderByDesc(CmdGovernanceTask::getCreateTime)
            .build();
    }

    // ==================== 总设计 MERGE 场景：发现候选 → 发起 → 执行 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDuplicateTask(String bizId, String oneId, String subject, String buScope,
                                      boolean crossBu, String matchState, String evidenceJson) {
        CmdGovernanceTask task = new CmdGovernanceTask();
        task.setTaskCode(generateGovTaskNo());
        task.setTaskType(crossBu ? CmdConstants.GOV_TYPE_CROSS_BU : CmdConstants.GOV_TYPE_SUSPECT);
        task.setBizType(CmdConstants.SCENE_CUSTOMER_CREATE);
        task.setBizId(bizId);
        task.setOneId(oneId);
        task.setSubject(subject);
        task.setBuScope(buScope);
        task.setCrossBuFlag(crossBu ? CmdConstants.YES : CmdConstants.NO);
        task.setRiskLevel(crossBu ? "High" : "Medium");
        task.setMatchState(matchState);
        task.setPriority(crossBu ? 8 : 5);
        task.setStatus("OPEN");
        task.setDueTime(LocalDateTime.now().plusHours(72));
        task.setEvidenceJson(evidenceJson);
        task.setRemark(crossBu ? "跨BU疑似重复，需 GC Scope 决策" : "Same-BU疑似重复，BU Scope 治理");
        taskMapper.insert(task);
        return task.getTaskCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String launchMerge(String sourceOneId, String targetOneId, String reason) {
        CmdCustomer source = requireCustomerByOneId(sourceOneId, "合并源");
        CmdCustomer target = requireCustomerByOneId(targetOneId, "合并目标");
        if (source.getId().equals(target.getId())) {
            throw new ServiceException("合并源与合并目标不能是同一条主档");
        }
        if (CmdConstants.CUST_STATUS_MERGED.equals(source.getStatus())) {
            throw new ServiceException("合并源 [{}] 已合并至 {}，不能重复发起", sourceOneId, source.getMergedToOneId());
        }
        boolean crossBu = !java.util.Objects.equals(
            StringUtils.defaultString(source.getBuScope()), StringUtils.defaultString(target.getBuScope()));

        // 1) 候选对比证据（总设计「证据准备」：信用代码 / 经营地址 / 名称 / 来源 / 层级 + 业务背景）
        Map<String, Object> snapshot = new HashMap<>(8);
        snapshot.put("mode", MERGE_MODE_RECORD);
        snapshot.put("sourceOneId", source.getOneId());
        snapshot.put("targetOneId", target.getOneId());
        snapshot.put("reason", reason);
        Map<String, Object> evidence = new HashMap<>(8);
        evidence.put("合并模式", "存量主档合并（Golden Record 保持目标 One ID 稳定）");
        evidence.put("发起原因", StringUtils.blankToDefault(reason, "疑似重复，经业务核实确认为同一客户"));
        evidence.put("源记录", source.getOneId() + " · " + source.getLegalName() + " · " + source.getBuScope());
        evidence.put("目标记录", target.getOneId() + " · " + target.getLegalName() + " · " + target.getBuScope());
        evidence.put("源信用代码", StringUtils.blankToDefault(source.getCreditCode(), "—"));
        evidence.put("目标信用代码", StringUtils.blankToDefault(target.getCreditCode(), "—"));
        evidence.put("源经营地址", StringUtils.blankToDefault(source.getAddress(), "—"));
        evidence.put("目标经营地址", StringUtils.blankToDefault(target.getAddress(), "—"));
        evidence.put("跨BU", crossBu ? "Y（GC Scope 决策）" : "N（Same-BU）");

        // 2) MERGE 审批待办（BU Scope 初审起步；跨BU 由 BU 升级 GC 决策）
        CmdApprovalTask task = new CmdApprovalTask();
        task.setTaskNo(generateMergeTaskNo());
        task.setTaskCategory(CmdConstants.APPR_CAT_APPROVAL);
        task.setBizType(CmdConstants.BIZ_TYPE_MERGE);
        task.setBizId(String.valueOf(source.getId()));
        task.setBizTitle("客户合并：" + source.getLegalName() + " → " + target.getLegalName());
        task.setOneId(source.getOneId());
        task.setSceneCode(CmdConstants.SCENE_MERGE);
        task.setApplicantId(LoginHelper.getUserId());
        task.setApplicantName(resolveOperatorName());
        task.setBuScope(source.getBuScope());
        task.setScope(CmdConstants.SCOPE_BU);
        task.setCurrentNodeCode(NODE_BU_REVIEW);
        task.setCurrentNodeName(NODE_NAME_BU_REVIEW);
        task.setAssigneeName("BU Steward");
        task.setAssigneeRole("BU_STEWARD");
        task.setStatus(CmdConstants.APPR_STATUS_PENDING);
        task.setRiskLevel(crossBu ? "High" : "Medium");
        task.setDuplicateState(CmdConstants.MATCH_SUSPECTED);
        task.setCrossBuFlag(crossBu ? CmdConstants.YES : CmdConstants.NO);
        task.setSubmitTime(LocalDateTime.now());
        task.setSlaDue(LocalDateTime.now().plusHours(72));
        task.setSlaState(CmdConstants.SLA_NORMAL);
        task.setEvidenceJson(JsonUtils.toJsonString(evidence));
        task.setBizSnapshotJson(JsonUtils.toJsonString(snapshot));
        task.setRemark(reason);
        approvalTaskMapper.insert(task);

        // 3) 审计留痕：发起合并请求
        AuditEvent audit = new AuditEvent();
        audit.setEventType("MERGE");
        audit.setEventName("发起跨BU客户合并请求：" + source.getLegalName() + " → " + target.getLegalName());
        audit.setBizType(CmdConstants.SCENE_MERGE);
        audit.setBizId(task.getTaskNo());
        audit.setOneId(source.getOneId());
        audit.setOperatorId(LoginHelper.getUserId());
        audit.setOperatorName(resolveOperatorName());
        audit.setEventTime(LocalDateTime.now());
        audit.setResult("SUCCESS");
        audit.setBeforeJson(JsonUtils.toJsonString(source));
        audit.setAfterJson(JsonUtils.toJsonString(target));
        auditService.record(audit);
        return task.getTaskNo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execMergeTask(CmdApprovalTask task) {
        Map<String, Object> snapshot = task.getBizSnapshotJson() == null
            ? new HashMap<>() : JsonUtils.parseMap(task.getBizSnapshotJson());
        String mode = String.valueOf(snapshot.getOrDefault("mode", MERGE_MODE_RECORD));
        String operator = StringUtils.blankToDefault(task.getAssigneeName(), "Data Steward");
        if (MERGE_MODE_ROW.equals(mode)) {
            // 批量导入行：Suspected → Exact（关联已有 One ID），复用行级治理回写
            Long rowId = Long.valueOf(String.valueOf(snapshot.get("rowId")));
            String targetOneId = String.valueOf(snapshot.get("targetOneId"));
            importService.rowAction(rowId, "LINK", targetOneId);
            recordMergeAudit(task, null, null, "导入行关联 One ID " + targetOneId, operator);
            return;
        }
        // 存量主档合并：目标 Golden Record 补全空字段 → 源记录指向目标 → Legacy 交叉引用 → 审计
        String sourceOneId = String.valueOf(snapshot.get("sourceOneId"));
        String targetOneId = String.valueOf(snapshot.get("targetOneId"));
        CmdCustomer source = requireCustomerByOneId(sourceOneId, "合并源");
        CmdCustomer target = requireCustomerByOneId(targetOneId, "合并目标");

        // 1) Golden Record 补全：目标为空的字段用源的值补齐（不覆盖已有标准属性）
        CmdCustomer goldenPatch = new CmdCustomer();
        goldenPatch.setId(target.getId());
        Map<String, String> enrichedFields = new LinkedHashMap<>();
        boolean enriched = false;
        if (StringUtils.isBlank(target.getContactName()) && StringUtils.isNotBlank(source.getContactName())) {
            goldenPatch.setContactName(source.getContactName());
            enrichedFields.put("contactName", StringUtils.defaultString(source.getContactName()));
            enriched = true;
        }
        if (StringUtils.isBlank(target.getContactPhone()) && StringUtils.isNotBlank(source.getContactPhone())) {
            goldenPatch.setContactPhone(source.getContactPhone());
            enrichedFields.put("contactPhone", StringUtils.defaultString(source.getContactPhone()));
            enriched = true;
        }
        if (StringUtils.isBlank(target.getAddress()) && StringUtils.isNotBlank(source.getAddress())) {
            goldenPatch.setAddress(source.getAddress());
            enrichedFields.put("address", StringUtils.defaultString(source.getAddress()));
            enriched = true;
        }
        if (StringUtils.isBlank(target.getPostalCode()) && StringUtils.isNotBlank(source.getPostalCode())) {
            goldenPatch.setPostalCode(source.getPostalCode());
            enrichedFields.put("postalCode", StringUtils.defaultString(source.getPostalCode()));
            enriched = true;
        }
        if (enriched) {
            customerMapper.updateById(goldenPatch);
        }

        // 2) 源记录合并指向目标（One ID 保持稳定 = 目标 One ID；保留 Source Snapshot 于审计）
        CmdCustomer sourcePatch = new CmdCustomer();
        sourcePatch.setId(source.getId());
        sourcePatch.setStatus(CmdConstants.CUST_STATUS_MERGED);
        sourcePatch.setMergedToOneId(target.getOneId());
        sourcePatch.setDuplicateFlag(CmdConstants.NO);
        customerMapper.updateById(sourcePatch);

        // 3) 建立交叉引用：保留源 One ID → 目标 One ID 的 Legacy 映射（下游按旧编码仍可路由）
        CmdLegacyMapping mapping = new CmdLegacyMapping();
        mapping.setOneId(target.getOneId());
        mapping.setSourceSystem("CMD");
        mapping.setSourceCode(source.getOneId());
        mapping.setSourceName(source.getLegalName());
        mapping.setBuScope(source.getBuScope());
        mapping.setMappingType("MERGE");
        mapping.setStatus("0");
        mapping.setEffectiveFrom(LocalDateTime.now());
        mapping.setRemark("客户合并交叉引用：源 " + source.getOneId() + " 并入 " + target.getOneId());
        legacyMappingMapper.insert(mapping);

        // 3.1) 合并记录落库（总设计「审计与合并记录」：原因 / 操作者 / 审批流 / Before / After 可回溯）
        CmdMergeRecord record = new CmdMergeRecord();
        record.setMergeCode(generateMergeRecordCode());
        record.setSurvivorOneId(target.getOneId());
        record.setMergedOneId(source.getOneId());
        record.setMergeType("MANUAL");
        record.setMergeStrategy("TARGET_FIRST");
        record.setFieldJson(enrichedFields.isEmpty() ? null : JsonUtils.toJsonString(enrichedFields));
        record.setBeforeJson(JsonUtils.toJsonString(source));
        record.setAfterJson(JsonUtils.toJsonString(requireCustomerByOneId(target.getOneId(), "合并目标")));
        Object reasonObj = snapshot.get("reason");
        record.setReason(reasonObj == null || StringUtils.isBlank(String.valueOf(reasonObj))
            ? "疑似重复，经业务核实确认为同一客户" : String.valueOf(reasonObj));
        record.setCanRollback("Y");
        record.setStatus("EFFECTIVE");
        record.setFlowInstanceId(task.getFlowInstanceId());
        record.setRemark("审批单 " + task.getTaskNo() + " · 执行人 " + operator);
        mergeRecordMapper.insert(record);

        recordMergeAudit(task, source, target,
            "执行合并：" + source.getOneId() + " → " + target.getOneId()
                + (enriched ? "（Golden Record 已用源记录补全空字段）" : ""), operator);
    }

    /** 合并执行 / 发起审计留痕（Before / After 证据） */
    private void recordMergeAudit(CmdApprovalTask task, CmdCustomer source, CmdCustomer target,
                                  String eventName, String operator) {
        AuditEvent audit = new AuditEvent();
        audit.setEventType("MERGE");
        audit.setEventName(eventName + "：" + task.getBizTitle());
        audit.setBizType(CmdConstants.SCENE_MERGE);
        audit.setBizId(task.getTaskNo());
        audit.setOneId(task.getOneId());
        audit.setOperatorId(task.getApplicantId());
        audit.setOperatorName(operator);
        audit.setOperatorRole("DATA_STEWARD");
        audit.setEventTime(LocalDateTime.now());
        audit.setResult("SUCCESS");
        if (source != null) {
            audit.setBeforeJson(JsonUtils.toJsonString(source));
        }
        if (target != null) {
            audit.setAfterJson(JsonUtils.toJsonString(target));
        }
        auditService.record(audit);
    }

    /** 按客户类型 One ID 查主档（不存在即抛业务异常） */
    private CmdCustomer requireCustomerByOneId(String oneId, String label) {
        if (StringUtils.isBlank(oneId)) {
            throw new ServiceException(label + " One ID 不能为空");
        }
        List<CmdCustomer> list = customerMapper.selectList(new LambdaQueryWrapper<CmdCustomer>()
            .eq(CmdCustomer::getOneId, oneId)
            .orderByDesc(CmdCustomer::getCreateTime)
            .last("LIMIT 1"));
        if (list.isEmpty()) {
            throw new ServiceException(label + "主档不存在：{}", oneId);
        }
        return list.get(0);
    }

    /** 从治理任务证据 JSON 中提取候选 One ID（兼容「候选One ID」/「candidateOneId」两种键名） */
    private String candidateOneIdOf(String evidenceJson) {
        try {
            Map<String, Object> map = JsonUtils.parseMap(evidenceJson);
            Object v = map.get("候选One ID");
            if (v == null) {
                v = map.get("candidateOneId");
            }
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    /** 治理任务编号：GOV-yyyyMMdd-####（按表内当日最大流水递增） */
    private String generateGovTaskNo() {
        String prefix = "GOV-" + LocalDate.now().format(GOV_NO_DAY) + "-";
        for (int seq = 1; seq <= 9999; seq++) {
            String code = prefix + String.format("%04d", seq);
            Long exists = taskMapper.lambda().eq(CmdGovernanceTask::getTaskCode, code).count();
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new ServiceException("治理任务编号生成失败：当日流水号已用尽");
    }

    /** 合并审批任务编号：AP-yyyyMMdd-####（与统一待办共用号段） */
    private String generateMergeTaskNo() {
        String prefix = "AP-" + LocalDate.now().format(GOV_NO_DAY) + "-";
        for (int seq = 1; seq <= 9999; seq++) {
            String no = prefix + String.format("%04d", seq);
            Long exists = approvalTaskMapper.lambda().eq(CmdApprovalTask::getTaskNo, no).count();
            if (exists == null || exists == 0) {
                return no;
            }
        }
        throw new ServiceException("申请编号生成失败：当日流水号已用尽");
    }

    /** 合并记录编号：MG-yyyyMMdd-####（按 cmd_merge_record 当日最大流水递增） */
    private String generateMergeRecordCode() {
        String prefix = "MG-" + LocalDate.now().format(GOV_NO_DAY) + "-";
        for (int seq = 1; seq <= 9999; seq++) {
            String code = prefix + String.format("%04d", seq);
            Long exists = mergeRecordMapper.lambda()
                .eq(CmdMergeRecord::getMergeCode, code)
                .count();
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new ServiceException("合并记录编号生成失败：当日流水号已用尽");
    }

    /** 操作人姓名（POC 免登录兜底） */
    private String resolveOperatorName() {
        try {
            String name = LoginHelper.getUsername();
            if (StringUtils.isNotBlank(name)) {
                return name;
            }
        } catch (Exception ignored) {
            // 免登录场景取不到会话，走兜底
        }
        return "Business User";
    }
}
