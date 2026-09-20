package org.dromara.cmd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.domain.CmdWorkflowStepLog;
import org.dromara.cmd.domain.vo.CmdWorkflowStepLogVo;
import org.dromara.cmd.mapper.CmdWorkflowStepLogMapper;
import org.dromara.cmd.service.ICmdWorkflowStepLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作流步骤执行日志 服务层实现
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdWorkflowStepLogServiceImpl implements ICmdWorkflowStepLogService {

    private final CmdWorkflowStepLogMapper stepMapper;

    @Override
    public void recordStep(CmdWorkflowStepLog step) {
        if (step.getStepSeq() == null || step.getStepSeq() <= 0) {
            long existing = stepMapper.selectCount(new LambdaQueryWrapper<CmdWorkflowStepLog>()
                .eq(CmdWorkflowStepLog::getOneId, step.getOneId()));
            step.setStepSeq((int) existing + 1);
        }
        if (step.getCreateTime() == null) {
            step.setCreateTime(java.time.LocalDateTime.now());
        }
        stepMapper.insert(step);
    }

    @Override
    public List<CmdWorkflowStepLogVo> listByOneId(String oneId) {
        return stepMapper.selectVoList(
            new LambdaQueryWrapper<CmdWorkflowStepLog>()
                .eq(CmdWorkflowStepLog::getOneId, oneId)
                .orderByAsc(CmdWorkflowStepLog::getStepSeq, CmdWorkflowStepLog::getCreateTime));
    }

    @Override
    public List<CmdWorkflowStepLogVo> listByTaskNo(String taskNo) {
        return stepMapper.selectVoList(
            new LambdaQueryWrapper<CmdWorkflowStepLog>()
                .eq(CmdWorkflowStepLog::getTaskNo, taskNo)
                .orderByAsc(CmdWorkflowStepLog::getStepSeq, CmdWorkflowStepLog::getCreateTime));
    }
}
