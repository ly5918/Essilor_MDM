package org.dromara.cmd.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.bo.ApprovalActionBo;
import org.dromara.cmd.domain.bo.CmdApprovalTaskBo;
import org.dromara.cmd.domain.vo.CmdApprovalActionVo;
import org.dromara.cmd.domain.vo.CmdApprovalTaskVo;
import org.dromara.cmd.mapper.CmdApprovalActionMapper;
import org.dromara.cmd.mapper.CmdApprovalTaskMapper;
import org.dromara.cmd.service.ICmdApprovalService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
@RequiredArgsConstructor
@Service
public class CmdApprovalServiceImpl implements ICmdApprovalService {

    private final CmdApprovalTaskMapper taskMapper;
    private final CmdApprovalActionMapper actionMapper;

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

        return rows;
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
}
