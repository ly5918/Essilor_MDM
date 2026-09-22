package org.dromara.cmd.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.CmdChangeRequest;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdLegacyMapping;
import org.dromara.cmd.domain.CmdWorkflowStepLog;
import org.dromara.cmd.domain.bo.ApprovalActionBo;
import org.dromara.cmd.domain.bo.CmdApprovalTaskBo;
import org.dromara.cmd.domain.vo.CmdApprovalActionVo;
import org.dromara.cmd.domain.vo.CmdApprovalDetailVo;
import org.dromara.cmd.domain.vo.CmdApprovalKpiVo;
import org.dromara.cmd.domain.vo.CmdApprovalTaskVo;
import org.dromara.cmd.mapper.CmdApprovalActionMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.mapper.CmdChangeRequestMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdLegacyMappingMapper;
import org.dromara.cmd.service.ICmdApprovalService;
import org.dromara.cmd.service.ICmdAuditService;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.cmd.service.ICmdGovernanceService;
import org.dromara.cmd.service.ICmdHierarchyService;
import org.dromara.cmd.service.ICmdImportService;
import org.dromara.cmd.service.ICmdWorkflowStepLogService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 统一待办 / 审批 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>审批与治理复核合并为单表，通过 taskCategory + status 组合承载页面 3 个 Tab</li>
 *   <li>每个决策追加一条 cmd_approval_action，形成完整业务审计链</li>
 *   <li>与 Warm-Flow 解耦：业务表只保存 flowInstanceId / flowTaskId，
 *       接入引擎时在 doAction 中调用 InsService 即可，表结构无需调整</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdApprovalServiceImpl implements ICmdApprovalService {

    private final CmdApprovalTaskMapper taskMapper;
    private final CmdApprovalActionMapper actionMapper;
    private final CmdChangeRequestMapper changeRequestMapper;
    private final ICmdFlowEngineService flowEngineService;
    private final CmdCustomerMapper customerMapper;
    private final ICmdWorkflowStepLogService stepLogService;
    private final ICmdAuditService auditService;
    private final ICmdHierarchyService hierarchyService;
    private final ICmdImportService importService;
    private final ICmdGovernanceService governanceService;
    private final CmdLegacyMappingMapper legacyMappingMapper;
    /** 「通过类」动作：落到 Warm-Flow 引擎推进节点（升级 → GC 决策，批准 → 结束） */
    private static final Set<String> ADVANCE_ACTIONS = Set.of(
        CmdConstants.ACTION_APPROVE, CmdConstants.ACTION_ESCALATE,
        CmdConstants.ACTION_MERGE, CmdConstants.ACTION_CREATE_NEW, CmdConstants.ACTION_LINK,
        CmdConstants.ACTION_EXCLUDE);

    /** 允许的决策动作白名单：不在其中的动作直接拒绝，避免写入无效步骤 / 误推进流程 */
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
        CmdConstants.ACTION_APPROVE, CmdConstants.ACTION_REJECT, CmdConstants.ACTION_RETURN,
        CmdConstants.ACTION_ESCALATE, CmdConstants.ACTION_CLAIM, CmdConstants.ACTION_TRANSFER,
        CmdConstants.ACTION_MERGE, CmdConstants.ACTION_LINK, CmdConstants.ACTION_EXCLUDE,
        CmdConstants.ACTION_CREATE_NEW, CmdConstants.ACTION_WITHDRAW);

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CmdApprovalTaskVo> selectPageTaskList(CmdApprovalTaskBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CmdApprovalTask> lqw = buildQueryWrapper(bo);
        Page<CmdApprovalTaskVo> page = taskMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdApprovalTaskVo selectTaskById(Long id) {
        return taskMapper.selectVoById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdApprovalActionVo> selectActionList(Long taskId) {
        LambdaQueryWrapper<CmdApprovalAction> lqw = new LambdaQueryWrapper<CmdApprovalAction>()
            .eq(CmdApprovalAction::getTaskId, taskId)
            .orderByAsc(CmdApprovalAction::getActionTime);
        return actionMapper.selectVoList(lqw);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int doAction(ApprovalActionBo bo) {
        CmdApprovalTask task = taskMapper.selectById(bo.getTaskId());
        if (ObjectUtil.isNull(task)) {
            throw new ServiceException("待办任务不存在或已删除");
        }
        // 终态任务（已批准/已拒绝/已退回/已取消/已完成）不允许再执行任何审批动作
        if (isFinished(task.getStatus())) {
            throw new ServiceException("该任务已处理完成（状态：" + task.getStatus() + "），不能再执行审批操作");
        }
        String actionType = bo.getActionType();
        String beforeState = task.getStatus();
        String afterState = resolveAfterState(actionType, beforeState);

        // 拒绝与退回必须填写意见（业务合规要求）
        if ((CmdConstants.ACTION_REJECT.equals(actionType) || CmdConstants.ACTION_RETURN.equals(actionType))
            && StringUtils.isBlank(bo.getOpinion())) {
            throw new ServiceException("拒绝或退回时必须填写审批意见");
        }
        // 动作类型白名单：未知动作直接拒绝，避免写入无效步骤日志或误推进流程
        if (!ALLOWED_ACTIONS.contains(actionType)) {
            throw new ServiceException("不支持的审批动作：" + actionType + "（仅支持 批准 / 拒绝 / 退回补充 / 升级GC / 认领 / 转办）");
        }

        // 1) 更新待办主表状态
        CmdApprovalTask update = new CmdApprovalTask();
        update.setId(task.getId());
        update.setStatus(afterState);
        update.setOpinion(bo.getOpinion());
        if (CmdConstants.ACTION_TRANSFER.equals(actionType)) {
            if (bo.getTargetUserId() == null) {
                throw new ServiceException("转办时必须指定目标处理人");
            }
            update.setAssigneeId(bo.getTargetUserId());
        }
        if (isFinished(afterState)) {
            update.setFinishTime(LocalDateTime.now());
            if (task.getSubmitTime() != null) {
                long hours = java.time.Duration.between(task.getSubmitTime(), LocalDateTime.now()).toHours();
                update.setDurationHours(new java.math.BigDecimal(hours));
            }
        }
        int rows = taskMapper.updateById(update);

        // 2) 追加动作轨迹（业务视角审计，与 Warm-Flow 的 flow_his_task 互补）
        CmdApprovalAction action = new CmdApprovalAction();
        action.setTaskId(task.getId());
        action.setTaskNo(task.getTaskNo());
        action.setActionType(actionType);
        action.setActionName(resolveActionName(actionType));
        action.setOneId(task.getOneId());
        action.setFromNodeCode(task.getCurrentNodeCode());
        action.setOperatorId(LoginHelper.getUserId());
        action.setOperatorRole(task.getAssigneeRole());
        action.setActionTime(LocalDateTime.now());
        action.setOpinion(bo.getOpinion());
        action.setAttachIds(bo.getAttachIds());
        action.setBeforeState(beforeState);
        action.setAfterState(afterState);
        action.setEvidenceJson(bo.getExtJson());
        actionMapper.insert(action);

        // 3) 联动 Warm-Flow 引擎：首次决策启动流程实例，通过类动作推进节点
        linkFlowEngine(task, actionType, bo.getOpinion());

        // 4) 重新读取任务镜像（引擎已推进节点 / 状态），用于步骤日志与客户主档回写
        CmdApprovalTask after = taskMapper.selectById(task.getId());
        String toNode = after.getCurrentNodeCode();
        String toStatus = after.getStatus();
        String flowStatus = after.getFlowStatus();
        Long operatorId = action.getOperatorId();
        String operatorName = resolveOperatorName(task, operatorId);
        // 拒绝视为流程终止（结束节点）
        String effectiveToNode = CmdConstants.ACTION_REJECT.equals(actionType) ? "END" : toNode;
        // 拒绝 / 退回不推进引擎节点，但工作流镜像状态必须同步为「已拒绝 / 已退回」，
        // 否则实例会一直显示「运行中」，泳道图与列表的状态口径不一致。
        String effectiveFlowStatus = flowStatus;
        if (CmdConstants.ACTION_REJECT.equals(actionType)) {
            effectiveFlowStatus = FlowStatus.REJECT.getKey();
        } else if (CmdConstants.ACTION_RETURN.equals(actionType)) {
            effectiveFlowStatus = FlowStatus.TASK_BACK.getKey();
        }
        if (!Objects.equals(effectiveFlowStatus, flowStatus)) {
            CmdApprovalTask fsPatch = new CmdApprovalTask();
            fsPatch.setId(task.getId());
            fsPatch.setFlowStatus(effectiveFlowStatus);
            taskMapper.updateById(fsPatch);
        }

        // 4.1) 业务轨迹补写目标节点 + one_id（与步骤日志互补，均按客户追溯）
        CmdApprovalAction actPatch = new CmdApprovalAction();
        actPatch.setId(action.getId());
        actPatch.setOneId(task.getOneId());
        actPatch.setToNodeCode(effectiveToNode);
        actPatch.setOperatorName(operatorName);
        actionMapper.updateById(actPatch);

        // 5) 工作流步骤总账：记录本次人工决策（带客户 one_id，可跨页面 / 跨工作流关联）
        recordDecisionStep(task, actionType, bo.getOpinion(), effectiveToNode, beforeState, toStatus, operatorId, operatorName);

        // 6) 结果回写：
        //    变更 / 停用类申请（bizType=CHANGE）只回写「变更单状态」—— 主档的字段改写与状态切换
        //    由变更服务在「生效」动作中显式执行，避免审批推进过程中提前改写 Golden Record；
        //    批量导入确认（bizType=IMPORT）回写导入任务：批准为 New 行生成 One ID、拒绝置 Failed、退回回待复核；
        //    其余场景（客户新建 / 治理复核 / 层级关系）保持「审批结果直接决定客户主档状态」的原有行为。
        if (CmdConstants.BIZ_TYPE_CHANGE.equalsIgnoreCase(task.getBizType())) {
            syncChangeRequestStatus(task.getBizId(), task.getOneId(), actionType, effectiveToNode);
        } else if (CmdConstants.BIZ_TYPE_IMPORT.equalsIgnoreCase(task.getBizType())) {
            importService.onApproval(task.getBizId(), actionType, operatorName);
        } else if (CmdConstants.BIZ_TYPE_MERGE.equalsIgnoreCase(task.getBizType())) {
            // 跨BU客户合并（总设计 MERGE 场景）：批准走到 END 才执行合并（Golden Record / 行级关联 / 交叉引用）；
            // 拒绝 / 退回不执行业务动作，仅记录审计结论。
            if (CmdConstants.ACTION_APPROVE.equals(actionType) && "END".equals(effectiveToNode)) {
                governanceService.execMergeTask(after);
            }
            auditMergeDecision(task, actionType, operatorName);
        } else if (isDuplicateLinkApproval(task)) {
            // 单条创建命中存量主档（EXACT / SUSPECTED，总设计场景一 + MERGE 场景）：
            // 「确认合并 / 确认关联已有」→ 新申请合并指向存量 One ID（One ID 保持稳定）；
            // 「排除重复 / 创建新主档」→ 判定非同一客户，按普通新建生效（生成新 One ID）。
            boolean reachedEnd = "END".equals(effectiveToNode);
            boolean linkDecision = CmdConstants.ACTION_APPROVE.equals(actionType)
                || CmdConstants.ACTION_MERGE.equals(actionType);
            boolean createDecision = CmdConstants.ACTION_EXCLUDE.equals(actionType)
                || CmdConstants.ACTION_CREATE_NEW.equals(actionType);
            if (reachedEnd && linkDecision) {
                linkNewCustomerToExisting(task, operatorId, operatorName);
            } else if (reachedEnd && createDecision) {
                syncCustomerStatus(task.getOneId(), CmdConstants.ACTION_APPROVE, "END",
                    effectiveFlowStatus, operatorId, operatorName);
            } else if (!linkDecision && !createDecision) {
                syncCustomerStatus(task.getOneId(), actionType, effectiveToNode, effectiveFlowStatus, operatorId, operatorName);
            }
        } else {
            syncCustomerStatus(task.getOneId(), actionType, effectiveToNode, effectiveFlowStatus, operatorId, operatorName);
        }

        // 7) 审计留痕：审批决策（审计中心可按 One ID / 申请编号检索到本次决策）
        AuditEvent audit = new AuditEvent();
        audit.setEventType("APPROVAL");
        audit.setEventName(resolveActionName(actionType) + "：" + task.getBizTitle());
        audit.setBizType(StringUtils.defaultString(task.getBizType(), task.getSceneCode()));
        audit.setBizId(task.getTaskNo());
        audit.setOneId(task.getOneId());
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        audit.setOperatorRole(StringUtils.defaultString(task.getAssigneeRole(), ""));
        audit.setEventTime(LocalDateTime.now());
        audit.setResult("SUCCESS");
        audit.setRiskLevel(StringUtils.defaultString(task.getRiskLevel(), "Low"));
        audit.setRemark(bo.getOpinion());
        // before_json / after_json 是 JSON 列：裸状态串（如 PENDING）不是合法 JSON，需包成对象
        audit.setBeforeJson("{\"flow_status\":\"" + StringUtils.defaultString(beforeState, "") + "\"}");
        audit.setAfterJson("{\"flow_status\":\"" + StringUtils.defaultString(toStatus, "") + "\"}");
        auditService.record(audit);

        return rows;
    }

    /**
     * 记录一条人工决策步骤（带客户 one_id）
     */
    private void recordDecisionStep(CmdApprovalTask task, String actionType, String opinion,
                                    String toNode, String fromStatus, String toStatus,
                                    Long operatorId, String operatorName) {
        CmdWorkflowStepLog step = new CmdWorkflowStepLog();
        // 批量导入确认（IMPORT）等批次级任务没有单一客户 one_id，置空串满足步骤日志非空约束
        step.setOneId(StringUtils.defaultString(task.getOneId()));
        step.setTaskNo(task.getTaskNo());
        step.setFlowInstanceId(task.getFlowInstanceId());
        step.setStepType(CmdConstants.STEP_BUSINESS);
        step.setNodeCode(toNode);
        step.setNodeName(CmdConstants.nodeName(toNode));
        step.setActionType(actionType);
        step.setActionName(resolveActionName(actionType));
        step.setOperatorId(operatorId);
        step.setOperatorName(operatorName);
        step.setOperatorRole(task.getAssigneeRole());
        step.setFromStatus(fromStatus);
        step.setToStatus(toStatus);
        step.setOpinion(opinion);
        step.setCreateTime(LocalDateTime.now());
        stepLogService.recordStep(step);
    }

    /**
     * 审批流转回写客户主档状态：审批结果直接决定 Golden Record 的生效 / 驳回 / 退回
     *
     * @param oneId       客户主数据标识
     * @param actionType  动作类型
     * @param toNode      流转后的节点（END 表示流程终止）
     * @param flowStatus  流程实例状态
     */
    /**
     * 是否「单条创建命中存量主档」的审批任务（EXACT / SUSPECTED，批准即关联已有 One ID）
     *
     * @param task 审批任务
     * @return true = 走关联已有 One ID 的合并回写
     */
    private boolean isDuplicateLinkApproval(CmdApprovalTask task) {
        return (CmdConstants.MATCH_EXACT.equals(task.getDuplicateState())
            || CmdConstants.MATCH_SUSPECTED.equals(task.getDuplicateState()))
            && task.getEvidenceJson() != null && task.getEvidenceJson().contains("候选One ID");
    }

    /**
     * 单条创建批准后关联已有 One ID（总设计场景一：发现跨BU疑似重复并关联已有 One ID）：
     * 新申请主档 status=merged、merged_to_one_id=存量 One ID（One ID 保持稳定），
     * 并建立 Legacy 交叉引用（新申请的 One ID → 存量 One ID），保留 Source Snapshot 于审计。
     *
     * @param task         创建审批任务
     * @param operatorId   操作人
     * @param operatorName 操作人姓名
     */
    private void linkNewCustomerToExisting(CmdApprovalTask task, Long operatorId, String operatorName) {
        String candidateOneId = null;
        try {
            Object v = JsonUtils.parseMap(task.getEvidenceJson()).get("候选One ID");
            candidateOneId = v == null ? null : String.valueOf(v);
        } catch (Exception ignored) {
            // 证据快照解析失败按普通创建处理
        }
        if (StringUtils.isBlank(candidateOneId) || customerMapper.selectCount(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, candidateOneId)) == 0) {
            // 候选缺失（如候选已被合并 / 删除）→ 回退为普通创建生效
            syncCustomerStatus(task.getOneId(), CmdConstants.ACTION_APPROVE, "END", task.getFlowStatus(), operatorId, operatorName);
            return;
        }
        // 1) 新申请主档合并指向存量 One ID
        CmdCustomer patch = new CmdCustomer();
        patch.setStatus(CmdConstants.CUST_STATUS_MERGED);
        patch.setMergedToOneId(candidateOneId);
        patch.setDuplicateFlag(CmdConstants.NO);
        patch.setApprovedBy(operatorId);
        patch.setApprovedTime(LocalDateTime.now());
        customerMapper.update(patch, new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, task.getOneId()));

        // 2) Legacy 交叉引用：新申请 One ID → 存量 One ID（下游按旧编码仍可路由）
        CmdLegacyMapping mapping = new CmdLegacyMapping();
        mapping.setOneId(candidateOneId);
        mapping.setSourceSystem("CMD");
        mapping.setSourceCode(task.getOneId());
        mapping.setSourceName(task.getBizTitle());
        mapping.setBuScope(task.getBuScope());
        mapping.setMappingType("MERGE");
        mapping.setStatus("0");
        mapping.setEffectiveFrom(LocalDateTime.now());
        mapping.setRemark("单条创建命中存量主档，合并指向 " + candidateOneId + "（申请 " + task.getTaskNo() + "）");
        legacyMappingMapper.insert(mapping);

        // 3) 审计留痕（Before / After）
        AuditEvent audit = new AuditEvent();
        audit.setEventType("MERGE");
        audit.setEventName("创建申请命中存量主档，已关联 One ID " + candidateOneId + "：" + task.getBizTitle());
        audit.setBizType(task.getSceneCode());
        audit.setBizId(task.getTaskNo());
        audit.setOneId(task.getOneId());
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        audit.setOperatorRole(StringUtils.defaultString(task.getAssigneeRole(), ""));
        audit.setEventTime(LocalDateTime.now());
        audit.setResult("SUCCESS");
        audit.setAfterJson(JsonUtils.toJsonString(java.util.Map.of("mergedToOneId", candidateOneId, "status", "merged")));
        auditService.record(audit);
        log.info("[CMD][MERGE] 创建申请已关联存量 One ID：taskNo={} new={} target={}",
            task.getTaskNo(), task.getOneId(), candidateOneId);
    }

    private void syncCustomerStatus(String oneId, String actionType, String toNode,
                                    String flowStatus, Long operatorId, String operatorName) {
        String custStatus;
        if (CmdConstants.ACTION_REJECT.equals(actionType)) {
            custStatus = CmdConstants.CUST_STATUS_REJECTED;
        } else if (CmdConstants.ACTION_RETURN.equals(actionType)) {
            custStatus = CmdConstants.CUST_STATUS_RETURNED;
        } else {
            // APPROVE / ESCALATE：走到 END 即生效，否则仍待处理（如升级后进入 GC 决策）
            custStatus = "END".equals(toNode) ? CmdConstants.CUST_STATUS_ACTIVE : CmdConstants.CUST_STATUS_PENDING;
        }
        CmdCustomer patch = new CmdCustomer();
        patch.setStatus(custStatus);
        patch.setFlowStatus(flowStatus);
        if (CmdConstants.CUST_STATUS_ACTIVE.equals(custStatus)) {
            patch.setApprovedBy(operatorId);
            patch.setApprovedTime(LocalDateTime.now());
        }
        customerMapper.update(patch, new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, oneId));
        log.info("[CMD][CUSTOMER] 主档状态已回写：oneId={} action={} -> status={}", oneId, actionType, custStatus);
        // 6.1) 主数据 ↔ 客户层级联动：客户正式生效（成为 Golden Record）即自动登记「待归位」层级节点，
        //      使其立刻出现在「客户层级 → 待归位主数据」中，等待 Data Steward 归位到 A3-A2-A1 树。
        //      登记失败不能影响审批结果，因此单独兜底（registerNode 未开启独立事务，不会污染外层事务）。
        if (CmdConstants.CUST_STATUS_ACTIVE.equals(custStatus)) {
            try {
                int registered = hierarchyService.registerNode(oneId);
                if (registered > 0) {
                    log.info("[CMD][HIER] 审批通过自动登记待归位层级节点：oneId={}", oneId);
                }
            } catch (Exception e) {
                log.warn("[CMD][HIER] 自动登记层级节点失败（不影响审批结果）：oneId={} err={}", oneId, e.getMessage());
            }
        }
    }

    /**
     * 合并审批决策审计留痕（批准由 execMergeTask 记录执行审计，此处记录拒绝 / 退回等结论）
     *
     * @param task        MERGE 审批任务
     * @param actionType  审批动作
     * @param operatorName 操作人
     */
    private void auditMergeDecision(CmdApprovalTask task, String actionType, String operatorName) {
        if (CmdConstants.ACTION_APPROVE.equals(actionType)) {
            return; // 批准的执行审计在 execMergeTask / 行级治理审计中落库
        }
        AuditEvent audit = new AuditEvent();
        audit.setEventType("MERGE");
        audit.setEventName(resolveActionName(actionType) + "（合并审批）：" + task.getBizTitle());
        audit.setBizType(CmdConstants.SCENE_MERGE);
        audit.setBizId(task.getTaskNo());
        audit.setOneId(task.getOneId());
        audit.setOperatorId(task.getApplicantId());
        audit.setOperatorName(operatorName);
        audit.setOperatorRole(StringUtils.defaultString(task.getAssigneeRole(), ""));
        audit.setEventTime(LocalDateTime.now());
        audit.setResult("SUCCESS");
        auditService.record(audit);
    }

    /**
     * 审批流转回写变更 / 停用申请状态
     * <p>
     * 变更与停用链路的主档改写（字段更新、状态置 Inactive）由变更服务在「生效」时执行，
     * 此处只负责把审批结论同步到 cmd_change_request.status，保证变更页与审批中心的
     * 状态口径一致，也避免审批到一半就改写 Golden Record。
     *
     * @param requestCode 变更申请编号（cmd_approval_task.biz_id）
     * @param oneId       客户主数据标识（仅用于按 One ID 兜底匹配）
     * @param actionType  审批动作
     * @param toNode      流转后的节点（END 表示流程终止）
     */
    private void syncChangeRequestStatus(String requestCode, String oneId, String actionType, String toNode) {
        String status = switch (actionType) {
            case CmdConstants.ACTION_REJECT, CmdConstants.ACTION_EXCLUDE -> CmdConstants.CHG_STATUS_REJECTED;
            case CmdConstants.ACTION_RETURN -> CmdConstants.CHG_STATUS_RETURNED;
            case CmdConstants.ACTION_WITHDRAW -> CmdConstants.CHG_STATUS_CANCELLED;
            // 通过类动作：走到 END 才算审批通过（可生效），否则仍在流程中（如升级后进入 GC 决策）
            case CmdConstants.ACTION_APPROVE, CmdConstants.ACTION_MERGE,
                 CmdConstants.ACTION_CREATE_NEW, CmdConstants.ACTION_LINK ->
                "END".equals(toNode) ? CmdConstants.CHG_STATUS_APPROVED : CmdConstants.CHG_STATUS_PENDING;
            default -> null;
        };
        if (status == null) {
            return;
        }
        LambdaQueryWrapper<CmdChangeRequest> lqw = new LambdaQueryWrapper<CmdChangeRequest>()
            .eq(CmdChangeRequest::getRequestCode, requestCode);
        if (StringUtils.isBlank(requestCode)) {
            lqw = new LambdaQueryWrapper<CmdChangeRequest>()
                .eq(CmdChangeRequest::getOneId, oneId)
                .ne(CmdChangeRequest::getStatus, CmdConstants.CHG_STATUS_EFFECTIVE)
                .orderByDesc(CmdChangeRequest::getCreateTime)
                .last("LIMIT 1");
        }
        CmdChangeRequest patch = new CmdChangeRequest();
        patch.setStatus(status);
        int rows = changeRequestMapper.update(patch, lqw);
        log.info("[CMD][CHANGE] 审批结论已回写变更申请：code={} action={} -> status={} rows={}",
            requestCode, actionType, status, rows);
    }

    /**
     * 解析操作人姓名（POC 免登录兜底到任务办理人）
     */
    private String resolveOperatorName(CmdApprovalTask task, Long operatorId) {
        try {
            String name = LoginHelper.getUsername();
            if (StringUtils.isNotBlank(name)) {
                return name;
            }
        } catch (Exception ignored) {
            // 免登录场景下取不到会话，走兜底
        }
        return StringUtils.blankToDefault(task.getAssigneeName(), "Data Steward");
    }

    /**
     * 联动 Warm-Flow 引擎
     * <p>
     * 业务表与引擎解耦，只交换 flow_instance_id / flow_task_id / 当前节点名三个镜像字段。
     * 首次动作时启动流程实例，随后「通过类」动作推进节点；
     * 退回 / 拒绝 / 转办 / 认领 不改动引擎节点，泳道图由业务状态驱动。
     *
     * @param task       处理前的任务快照
     * @param actionType 动作类型
     * @param opinion    审批意见
     */
    private void linkFlowEngine(CmdApprovalTask task, String actionType, String opinion) {
        boolean started = task.getFlowInstanceId() != null;
        if (!started && !CmdConstants.APPR_STATUS_PENDING.equals(task.getStatus())) {
            // 已处于终态的历史任务不补启实例
            return;
        }
        if (!started) {
            flowEngineService.startInstance(task.getTaskNo());
        }
        if (ADVANCE_ACTIONS.contains(actionType)) {
            flowEngineService.advance(task.getTaskNo(), actionType, opinion);
        }
        log.info("[CMD][FLOW] 审批动作已联动引擎：taskNo={} action={}", task.getTaskNo(), actionType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Long> selectTaskStats(Long userId) {
        Map<String, Long> stats = new HashMap<>(8);
        if (userId == null) {
            return stats;
        }
        // 我的待办：处理人=当前用户 且 状态为待审批
        stats.put("myTodo", taskMapper.lambda()
            .eq(CmdApprovalTask::getAssigneeId, userId)
            .eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING)
            .count());
        // 我已处理：处理人=当前用户 且 已完成
        stats.put("myDone", taskMapper.lambda()
            .eq(CmdApprovalTask::getAssigneeId, userId)
            .eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_COMPLETED)
            .count());
        // 升级与退回
        stats.put("returned", taskMapper.lambda()
            .eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_RETURNED)
            .count());
        // SLA 超时
        stats.put("slaOverdue", taskMapper.lambda()
            .eq(CmdApprovalTask::getSlaState, CmdConstants.SLA_OVERDUE)
            .count());
        return stats;
    }

    /**
     * 构造待办查询条件（对应页面筛选行：任务类型 / BU / SLA / 风险 / 关键字）
     *
     * @param bo 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CmdApprovalTask> buildQueryWrapper(CmdApprovalTaskBo bo) {
        // 队列表口径：task_category 只做"待办分类"用；
        // 「我已处理(DONE)」不是建表时打的分类，而是按终态推导，所以要跳过列匹配改用 status IN，
        // 否则任务处理完仍挂在待办队列、而已处理队列又查不到（演示时最容易困惑的点）
        String category = bo.getTaskCategory();
        boolean doneCategory = CmdConstants.APPR_CAT_DONE.equalsIgnoreCase(category);
        // 「全部待办(ALL)」/「我已处理(DONE)」/「升级与退回(RETURNED)」都不是建表分类，
        // 而是按 status 推导的聚合口径，因此跳过 task_category 列匹配。
        // 其中 RETURNED 尤需注意：退回是**状态**而非分类（退回的任务 task_category 仍为 APPROVAL），
        // 若按分类匹配会导致退回任务既不在待办队列、也不在退回队列，页面看到空列表。
        boolean returnedCategory = CmdConstants.APPR_CAT_RETURNED.equalsIgnoreCase(category);
        boolean statusOnlyCategory = doneCategory || returnedCategory
            || CmdConstants.APPR_CAT_ALL.equalsIgnoreCase(category);
        LambdaQueryWrapper<CmdApprovalTask> lqw = QueryBuilder.lambda(CmdApprovalTask.class)
            .eqIfText(CmdApprovalTask::getTaskCategory, statusOnlyCategory ? null : category)
            .eqIfText(CmdApprovalTask::getBizType, bo.getBizType())
            .eqIfText(CmdApprovalTask::getBuScope, bo.getBuScope())
            .eqIfText(CmdApprovalTask::getScope, bo.getScope())
            .eqIfText(CmdApprovalTask::getStatus, bo.getStatus())
            .eqIfText(CmdApprovalTask::getRiskLevel, bo.getRiskLevel())
            .eqIfText(CmdApprovalTask::getDuplicateState, bo.getDuplicateState())
            .eqIfText(CmdApprovalTask::getSlaState, bo.getSlaState())
            .eqIfText(CmdApprovalTask::getSceneCode, bo.getSceneCode())
            .eqIfPresent(CmdApprovalTask::getAssigneeId, bo.getAssigneeId())
            .eqIfText(CmdApprovalTask::getOneId, bo.getOneId())
            .betweenParams(CmdApprovalTask::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByDesc(CmdApprovalTask::getCreateTime)
            .build();
        // 关键字：同时匹配申请编号 / 业务标题 / One ID
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String kw = bo.getKeyword();
            lqw.and(w -> w.like(CmdApprovalTask::getTaskNo, kw)
                .or().like(CmdApprovalTask::getBizTitle, kw)
                .or().like(CmdApprovalTask::getOneId, kw));
        }
        // 「全部待办」不是建表分类，而是「所有还需要我处理」的聚合口径：
        // 待处理（PENDING）+ 退回待补充（RETURNED）—— 与治理与审批页「全部待办」页签一致，
        // 避免 Steward 打开页面看到空列表、任务却藏在「审批任务」页签里（测试报告 BUG-6）。
        if (CmdConstants.APPR_CAT_ALL.equalsIgnoreCase(category)) {
            // 用「PENDING OR RETURNED」而非 in(...)：varargs in 在本项目 MP 封装下会拼出空条件集，
            // 结果就是「全部待办」永远空列表（实测 GC ALL=0 / APPROVAL=1）。
            lqw.and(w -> w.eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING)
                .or().eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_RETURNED));
            return lqw;
        }
        // 队列状态口径：待办队列只列未处理任务；"我已处理"列终态；"升级与退回"按退回状态取
        if (StringUtils.isNotBlank(category)) {
            if (doneCategory) {
                // 我已处理 = 已闭环终态。刻意不含 RETURNED：退回的申请仍需 Steward 跟进，
                // 归属「升级与退回」页签，避免同一条任务在两个队列里重复出现。
                lqw.in(CmdApprovalTask::getStatus,
                    CmdConstants.APPR_STATUS_APPROVED,
                    CmdConstants.APPR_STATUS_REJECTED,
                    CmdConstants.APPR_STATUS_CANCELLED,
                    CmdConstants.APPR_STATUS_COMPLETED);
            } else if (returnedCategory) {
                // 「升级与退回」= 状态为退回待补充的任务（task_category 仍为 APPROVAL，不能按分类匹配）
                lqw.eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_RETURNED);
            } else {
                lqw.eq(CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING);
            }
        }
        return lqw;
    }

    /**
     * 根据动作类型推导处理后状态
     *
     * @param actionType  动作类型
     * @param beforeState 处理前状态
     * @return 处理后状态
     */
    private String resolveAfterState(String actionType, String beforeState) {
        return switch (actionType) {
            case CmdConstants.ACTION_APPROVE, CmdConstants.ACTION_CREATE_NEW,
                 CmdConstants.ACTION_MERGE, CmdConstants.ACTION_LINK,
                 // 排除重复（总设计「排除」）：判定非同一客户，继续按新建主档生效
                 CmdConstants.ACTION_EXCLUDE -> CmdConstants.APPR_STATUS_APPROVED;
            case CmdConstants.ACTION_REJECT -> CmdConstants.APPR_STATUS_REJECTED;
            case CmdConstants.ACTION_RETURN -> CmdConstants.APPR_STATUS_RETURNED;
            // ESCALATE 只是把任务从 BU 初审推进到 GC 决策，仍处于待处理状态
            case CmdConstants.ACTION_ESCALATE -> CmdConstants.APPR_STATUS_PENDING;
            case CmdConstants.ACTION_WITHDRAW -> CmdConstants.APPR_STATUS_CANCELLED;
            case CmdConstants.ACTION_CLAIM -> CmdConstants.APPR_STATUS_PENDING;
            default -> beforeState;
        };
    }

    /**
     * 动作类型转中文名称（写入轨迹表，便于页面直接展示）
     *
     * @param actionType 动作类型
     * @return 动作名称
     */
    private String resolveActionName(String actionType) {
        return switch (actionType) {
            case CmdConstants.ACTION_SUBMIT -> "提交申请";
            case CmdConstants.ACTION_APPROVE -> "审批通过";
            case CmdConstants.ACTION_REJECT -> "审批拒绝";
            case CmdConstants.ACTION_RETURN -> "退回申请人";
            case CmdConstants.ACTION_ESCALATE -> "升级 GC Scope";
            case CmdConstants.ACTION_TRANSFER -> "转办";
            case CmdConstants.ACTION_CLAIM -> "认领任务";
            case CmdConstants.ACTION_MERGE -> "确认合并";
            case CmdConstants.ACTION_CREATE_NEW -> "确认新建";
            case CmdConstants.ACTION_EXCLUDE -> "排除记录";
            case CmdConstants.ACTION_WITHDRAW -> "撤回申请";
            default -> actionType;
        };
    }

    /**
     * 判断状态是否为终态
     *
     * @param status 状态
     * @return true 终态
     */
    private boolean isFinished(String status) {
        return CmdConstants.APPR_STATUS_APPROVED.equals(status)
            || CmdConstants.APPR_STATUS_REJECTED.equals(status)
            || CmdConstants.APPR_STATUS_COMPLETED.equals(status)
            || CmdConstants.APPR_STATUS_CANCELLED.equals(status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdApprovalKpiVo> selectKpi(String scope, Long userId) {
        List<CmdApprovalKpiVo> list = new ArrayList<>();
        // "待我处理"按当前用户过滤（assigneeId = userId），而非全局所有待处理任务
        long myPending = userId != null
            ? countBy(scope, userId, CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING)
            : countBy(scope, null, CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING);
        list.add(kpi("待我处理", myPending, "状态为待处理且分配给当前用户的任务"));
        list.add(kpi("临近SLA", countBy(scope, null, CmdApprovalTask::getSlaState, CmdConstants.SLA_DUE_SOON), "SLA 即将到期"));
        list.add(kpi("已超时", countBy(scope, null, CmdApprovalTask::getSlaState, CmdConstants.SLA_OVERDUE), "已超过 SLA 应完成时间"));
        list.add(kpi("退回待补充", countBy(scope, null, CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_RETURNED), "已退回申请人，等待补充材料"));
        list.add(kpi("本周已处理", countFinishedThisWeek(scope), "本周一以来完成的任务"));
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdApprovalDetailVo selectDetailByTaskNo(String taskNo) {
        CmdApprovalTask task = taskMapper.selectOne(
            Wrappers.<CmdApprovalTask>lambdaQuery().eq(CmdApprovalTask::getTaskNo, taskNo));
        if (ObjectUtil.isNull(task)) {
            throw new ServiceException("待办任务不存在：" + taskNo);
        }
        CmdApprovalDetailVo vo = new CmdApprovalDetailVo();
        vo.setId(task.getId());
        vo.setTaskId(task.getTaskNo());
        vo.setOneId(task.getOneId());
        vo.setBizId(task.getBizId());
        vo.setName(task.getBizTitle());
        vo.setScene(task.getBizType());
        vo.setSubmitter(StringUtils.blankToDefault(task.getApplicantName(), "-"));
        vo.setCurrentNode(StringUtils.blankToDefault(task.getCurrentNodeName(), "-"));
        vo.setSla(resolveSlaText(task.getSlaState()));
        vo.setDq(task.getDqScore() == null ? "-" : "DQ " + task.getDqScore());
        vo.setDuplicate(StringUtils.blankToDefault(task.getDuplicateState(), "-"));
        vo.setEvidence(StringUtils.blankToDefault(task.getEvidenceJson(), "暂无治理证据"));
        vo.setDecisions(buildDecisions(task));
        vo.setActions(buildActions(task));
        return vo;
    }

    /**
     * 按单个字段统计任务数（带 Scope 过滤）
     *
     * @param scope  审批范围
     * @param column 统计字段
     * @param value  字段值
     * @return 任务数
     */
    private Long countBy(String scope, SFunction<CmdApprovalTask, ?> column, Object value) {
        return countBy(scope, null, column, value);
    }

    /**
     * 按单个字段统计任务数（带 Scope + userId 过滤）
     *
     * @param scope  审批范围
     * @param userId 用户 ID（为空时不过滤）
     * @param column 统计字段
     * @param value  字段值
     * @return 任务数
     */
    private Long countBy(String scope, Long userId, SFunction<CmdApprovalTask, ?> column, Object value) {
        LambdaQueryWrapper<CmdApprovalTask> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(scope)) {
            lqw.eq(CmdApprovalTask::getScope, scope.toUpperCase());
        }
        if (userId != null) {
            lqw.eq(CmdApprovalTask::getAssigneeId, userId);
        }
        lqw.eq(column, value);
        return taskMapper.selectCount(lqw);
    }

    /**
     * 统计本周一以来已完成的任务数
     *
     * @param scope 审批范围
     * @return 任务数
     */
    private Long countFinishedThisWeek(String scope) {
        LocalDateTime weekStart = LocalDateTime.now()
            .with(DayOfWeek.MONDAY)
            .toLocalDate()
            .atStartOfDay();
        LambdaQueryWrapper<CmdApprovalTask> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(scope)) {
            lqw.eq(CmdApprovalTask::getScope, scope.toUpperCase());
        }
        lqw.ge(CmdApprovalTask::getFinishTime, weekStart);
        return taskMapper.selectCount(lqw);
    }

    /**
     * 生成决策 / 判断标签（页面「BU初审判断 / GC治理决策」区）
     *
     * @param task 任务
     * @return 决策标签
     */
    private List<String> buildDecisions(CmdApprovalTask task) {
        List<String> decisions = new ArrayList<>();
        if ("High".equalsIgnoreCase(task.getRiskLevel())) {
            decisions.add("高风险：建议升级 GC Scope 复核");
        }
        if (task.getDqScore() != null && task.getDqScore().doubleValue() < 60) {
            decisions.add("DQ 低于 60：建议退回补充材料");
        }
        // 匹配结论决定批准后的落库动作（总设计四类分流：Exact 关联已有 One ID；New 审批后生成 One ID）
        String match = StringUtils.blankToDefault(task.getDuplicateState(), "").toUpperCase();
        if (match.contains(CmdConstants.MATCH_SUSPECTED)) {
            decisions.add("疑似重复：需人工比对后决定关联已有或新建");
        } else if (match.contains(CmdConstants.MATCH_EXACT)) {
            decisions.add("已命中存量：批准后关联已有 One ID");
        } else if (match.contains(CmdConstants.MATCH_NEW)) {
            decisions.add("未命中存量：批准后生成新 One ID");
        }
        if ("Y".equals(task.getCrossBuFlag())) {
            decisions.add("跨 BU 申请：需 GC Scope 参与决策");
        }
        if (decisions.isEmpty()) {
            decisions.add("自动检查通过：可直接审批");
        }
        return decisions;
    }

    /**
     * 生成可执行操作按钮（按审批 Scope 区分）
     * <p>
     * 主按钮文案按匹配结论精确区分（测试报告 BUG-9）：
     * <ul>
     *   <li>NEW（未命中存量）→「批准新建」，不再对全新客户显示「确认合并」</li>
     *   <li>EXACT / SUSPECTED（命中候选）→「确认合并」</li>
     *   <li>其余（变更 / 停用 / 层级 / 批量导入确认等无合并语义）→「批准」</li>
     * </ul>
     *
     * @param task 任务
     * @return 操作按钮
     */
    private List<CmdApprovalDetailVo.ActionVo> buildActions(CmdApprovalTask task) {
        List<CmdApprovalDetailVo.ActionVo> actions = new ArrayList<>();
        // 终态任务（已批准/已拒绝/已退回/已取消/已完成）不再显示操作按钮
        if (isFinished(task.getStatus())) {
            return actions;
        }
        boolean gc = "GC".equalsIgnoreCase(task.getScope());
        // 跨BU客户合并（MERGE 场景）：批准即「确认合并」——执行 Golden Record 合并 / 行级关联
        boolean merge = CmdConstants.BIZ_TYPE_MERGE.equalsIgnoreCase(task.getBizType());
        // 单条创建命中存量主档（EXACT / SUSPECTED，总设计场景一 + MERGE 场景）：
        // 不提供普通「批准 / 拒绝」——重复结论必须做出合并决策（关联已有）或排除 / 新建 / 升级
        boolean dupCreate = isDuplicateLinkApproval(task);
        if (merge) {
            actions.add(action(CmdConstants.ACTION_APPROVE, "确认合并", "primary"));
            actions.add(action(CmdConstants.ACTION_REJECT, "拒绝合并", "danger"));
            actions.add(action(CmdConstants.ACTION_RETURN, "退回BU", "warning"));
            return actions;
        }
        if (dupCreate) {
            if (gc) {
                // GC Scope 决策（总设计 MERGE 场景节点：跨BU确认关联已有、创建新主档或退回修复）
                actions.add(action(CmdConstants.ACTION_MERGE, "确认关联已有", "primary"));
                actions.add(action(CmdConstants.ACTION_CREATE_NEW, "创建新主档", "success"));
                actions.add(action(CmdConstants.ACTION_RETURN, "退回BU修复", "warning"));
            } else if (CmdConstants.YES.equals(task.getCrossBuFlag())) {
                // BU Scope 初审（总设计：核验本BU来源记录；确认升级、排除或退回）——跨BU无权直接合并
                actions.add(action(CmdConstants.ACTION_ESCALATE, "升级GC决策", "primary"));
                actions.add(action(CmdConstants.ACTION_EXCLUDE, "排除重复", "warning"));
                actions.add(action(CmdConstants.ACTION_RETURN, "退回补充", "info"));
            } else {
                // Same-BU：BU 直接决策（泳道标签 Same-BU → BU 层处理）
                actions.add(action(CmdConstants.ACTION_MERGE, "确认合并", "primary"));
                actions.add(action(CmdConstants.ACTION_EXCLUDE, "排除重复·继续新建", "warning"));
                actions.add(action(CmdConstants.ACTION_RETURN, "退回补充", "info"));
            }
            return actions;
        }
        // 默认分支 = 非合并、非重复关联类审批（如客户新建 NEW / 变更 / 停用 / 层级 / 批量导入确认）。
        // 无合并语义时不得使用「确认合并」字样（BUG-12）：NEW 场景明确为「批准新建」，其余为「批准」。
        actions.add(action(CmdConstants.ACTION_APPROVE, approveLabel(task), "primary"));
        actions.add(action(CmdConstants.ACTION_REJECT, "拒绝", "danger"));
        actions.add(action(CmdConstants.ACTION_RETURN, gc ? "退回BU" : "退回补充", "warning"));
        if (!gc) {
            actions.add(action(CmdConstants.ACTION_ESCALATE, "升级GC", "info"));
        }
        return actions;
    }

    /**
     * 主审批按钮文案：严格按匹配结论区分，避免「新建」被误读为「合并」（测试报告 BUG-9）
     *
     * @param task 审批任务
     * @return 按钮文案
     */
    private String approveLabel(CmdApprovalTask task) {
        String match = StringUtils.blankToDefault(task.getDuplicateState(), "").toUpperCase();
        if (CmdConstants.MATCH_NEW.equals(match)) {
            return "批准新建";
        }
        if (match.contains(CmdConstants.MATCH_EXACT) || match.contains(CmdConstants.MATCH_SUSPECTED)) {
            return "确认合并";
        }
        return "批准";
    }

    private CmdApprovalDetailVo.ActionVo action(String key, String label, String type) {
        CmdApprovalDetailVo.ActionVo vo = new CmdApprovalDetailVo.ActionVo();
        vo.setKey(key);
        vo.setLabel(label);
        vo.setType(type);
        return vo;
    }

    private CmdApprovalKpiVo kpi(String label, Long value, String hint) {
        CmdApprovalKpiVo vo = new CmdApprovalKpiVo();
        vo.setLabel(label);
        vo.setValue(value == null ? 0L : value);
        vo.setHint(hint);
        return vo;
    }

    /**
     * SLA 状态转页面展示文案
     *
     * @param slaState SLA 状态
     * @return 展示文案
     */
    private String resolveSlaText(String slaState) {
        if (CmdConstants.SLA_OVERDUE.equals(slaState)) {
            return "已超时";
        }
        if (CmdConstants.SLA_DUE_SOON.equals(slaState)) {
            return "临近SLA";
        }
        return "正常";
    }
}
