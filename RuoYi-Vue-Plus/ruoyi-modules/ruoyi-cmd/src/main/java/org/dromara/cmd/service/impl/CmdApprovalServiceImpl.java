package org.dromara.cmd.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.bo.ApprovalActionBo;
import org.dromara.cmd.domain.bo.CmdApprovalTaskBo;
import org.dromara.cmd.domain.vo.CmdApprovalActionVo;
import org.dromara.cmd.domain.vo.CmdApprovalDetailVo;
import org.dromara.cmd.domain.vo.CmdApprovalKpiVo;
import org.dromara.cmd.domain.vo.CmdApprovalTaskVo;
import org.dromara.cmd.mapper.CmdApprovalActionMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.service.ICmdApprovalService;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final ICmdFlowEngineService flowEngineService;

    /** 「通过类」动作：落到 Warm-Flow 引擎推进节点（升级 → GC 决策，批准 → 结束） */
    private static final Set<String> ADVANCE_ACTIONS = Set.of(
        CmdConstants.ACTION_APPROVE, CmdConstants.ACTION_ESCALATE,
        CmdConstants.ACTION_MERGE, CmdConstants.ACTION_CREATE_NEW, CmdConstants.ACTION_LINK);

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
        String actionType = bo.getActionType();
        String beforeState = task.getStatus();
        String afterState = resolveAfterState(actionType, beforeState);

        // 拒绝与退回必须填写意见（业务合规要求）
        if ((CmdConstants.ACTION_REJECT.equals(actionType) || CmdConstants.ACTION_RETURN.equals(actionType))
            && StringUtils.isBlank(bo.getOpinion())) {
            throw new ServiceException("拒绝或退回时必须填写审批意见");
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

        return rows;
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
        LambdaQueryWrapper<CmdApprovalTask> lqw = QueryBuilder.lambda(CmdApprovalTask.class)
            .eqIfText(CmdApprovalTask::getTaskCategory, bo.getTaskCategory())
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
                 CmdConstants.ACTION_MERGE, CmdConstants.ACTION_LINK -> CmdConstants.APPR_STATUS_APPROVED;
            case CmdConstants.ACTION_REJECT, CmdConstants.ACTION_EXCLUDE -> CmdConstants.APPR_STATUS_REJECTED;
            case CmdConstants.ACTION_RETURN -> CmdConstants.APPR_STATUS_RETURNED;
            case CmdConstants.ACTION_ESCALATE -> CmdConstants.APPR_STATUS_ESCALATED;
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
    public List<CmdApprovalKpiVo> selectKpi(String scope) {
        List<CmdApprovalKpiVo> list = new ArrayList<>();
        list.add(kpi("待我处理", countBy(scope, CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_PENDING), "状态为待处理的任务"));
        list.add(kpi("临近SLA", countBy(scope, CmdApprovalTask::getSlaState, CmdConstants.SLA_DUE_SOON), "SLA 即将到期"));
        list.add(kpi("已超时", countBy(scope, CmdApprovalTask::getSlaState, CmdConstants.SLA_OVERDUE), "已超过 SLA 应完成时间"));
        list.add(kpi("退回待补充", countBy(scope, CmdApprovalTask::getStatus, CmdConstants.APPR_STATUS_RETURNED), "已退回申请人，等待补充材料"));
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
            throw new ServiceException("待办任务不存在：%s", taskNo);
        }
        CmdApprovalDetailVo vo = new CmdApprovalDetailVo();
        vo.setId(task.getId());
        vo.setTaskId(task.getTaskNo());
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
        LambdaQueryWrapper<CmdApprovalTask> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(scope)) {
            lqw.eq(CmdApprovalTask::getScope, scope.toUpperCase());
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
        if (StringUtils.isNotBlank(task.getDuplicateState()) && !"NONE".equalsIgnoreCase(task.getDuplicateState())) {
            decisions.add("存在疑似重复：需人工比对后决定合并或新建");
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
     *
     * @param task 任务
     * @return 操作按钮
     */
    private List<CmdApprovalDetailVo.ActionVo> buildActions(CmdApprovalTask task) {
        List<CmdApprovalDetailVo.ActionVo> actions = new ArrayList<>();
        boolean gc = "GC".equalsIgnoreCase(task.getScope());
        actions.add(action(CmdConstants.ACTION_APPROVE, gc ? "确认合并 / 批准" : "批准", "primary"));
        actions.add(action(CmdConstants.ACTION_REJECT, "拒绝", "danger"));
        actions.add(action(CmdConstants.ACTION_RETURN, gc ? "退回BU" : "退回补充", "warning"));
        if (!gc) {
            actions.add(action(CmdConstants.ACTION_ESCALATE, "升级GC", "info"));
        }
        return actions;
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
