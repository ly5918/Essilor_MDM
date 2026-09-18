package org.dromara.cmd.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdGovernanceTask;
import org.dromara.cmd.domain.bo.CmdGovernanceTaskBo;
import org.dromara.cmd.domain.vo.CmdGovernanceTaskVo;
import org.dromara.cmd.mapper.CmdGovernanceTaskMapper;
import org.dromara.cmd.service.ICmdGovernanceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 治理任务 服务层实现
 * <p>
 * 对应页面：治理任务 gov。指标卡统计直接由本表按 taskType 聚合得到，
 * 不维护冗余统计表，避免统计口径不一致。
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdGovernanceServiceImpl implements ICmdGovernanceService {

    private final CmdGovernanceTaskMapper taskMapper;

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
        return taskMapper.updateById(update);
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
}
