package org.dromara.cmd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.IntEndpoint;
import org.dromara.cmd.domain.IntRun;
import org.dromara.cmd.domain.bo.IntConnBo;
import org.dromara.cmd.domain.bo.IntRunBo;
import org.dromara.cmd.domain.vo.IntRunVo;
import org.dromara.cmd.mapper.IntEndpointMapper;
import org.dromara.cmd.mapper.IntRunMapper;
import org.dromara.cmd.service.ICmdIntegrationService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成监控 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>重试只累加尝试次数并置为 RETRYING，不伪造成功结果</li>
 *   <li>连接配置按「目标系统」做新增或更新，避免重复端点</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdIntegrationServiceImpl implements ICmdIntegrationService {

    private final IntRunMapper runMapper;
    private final IntEndpointMapper endpointMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<IntRunVo> selectPage(IntRunBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<IntRun> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(bo.getRunCode())) {
            lqw.eq(IntRun::getRunCode, bo.getRunCode());
        }
        if (StringUtils.isNotBlank(bo.getDirection())) {
            lqw.eq(IntRun::getDirection, bo.getDirection());
        }
        if (StringUtils.isNotBlank(bo.getTargetSystem())) {
            lqw.eq(IntRun::getTargetSystem, bo.getTargetSystem());
        }
        if (StringUtils.isNotBlank(bo.getRunStatus())) {
            lqw.eq(IntRun::getRunStatus, bo.getRunStatus());
        }
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            lqw.and(w -> w.like(IntRun::getRunCode, bo.getKeyword())
                .or().like(IntRun::getTargetSystem, bo.getKeyword()));
        }
        lqw.orderByDesc(IntRun::getStartTime);
        Page<IntRunVo> page = runMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String retry(String runCode) {
        IntRun run = runMapper.selectOne(Wrappers.<IntRun>lambdaQuery().eq(IntRun::getRunCode, runCode));
        if (run == null) {
            throw new ServiceException("集成运行记录不存在：{}", runCode);
        }
        IntRun update = new IntRun();
        update.setId(run.getId());
        update.setRunStatus("RETRYING");
        update.setAttemptCount((run.getAttemptCount() == null ? 0 : run.getAttemptCount()) + 1);
        runMapper.updateById(update);
        return "已触发重试：" + runCode + "（第 " + update.getAttemptCount() + " 次）";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveConn(IntConnBo bo) {
        if (StringUtils.isBlank(bo.getSystem())) {
            throw new ServiceException("目标系统不能为空");
        }
        String endpointCode = "EP-" + bo.getSystem().toUpperCase().replaceAll("[^A-Z0-9]", "");
        IntEndpoint exist = endpointMapper.selectOne(
            Wrappers.<IntEndpoint>lambdaQuery().eq(IntEndpoint::getEndpointCode, endpointCode));
        if (exist != null) {
            exist.setProtocol(StringUtils.blankToDefault(bo.getProtocol(), exist.getProtocol()));
            exist.setEndpointUrl(bo.getUrl());
            exist.setRemark("同步周期：" + StringUtils.blankToDefault(bo.getPeriod(), "未提供"));
            endpointMapper.updateById(exist);
            return endpointCode;
        }
        IntEndpoint entity = new IntEndpoint();
        entity.setEndpointCode(endpointCode);
        entity.setEndpointName(bo.getSystem());
        entity.setDirection("OUTBOUND");
        entity.setProtocol(StringUtils.blankToDefault(bo.getProtocol(), "HTTP"));
        entity.setTargetSystem(bo.getSystem());
        entity.setEndpointUrl(bo.getUrl());
        entity.setMessageFormat("JSON");
        entity.setStatus("0");
        entity.setRemark("同步周期：" + StringUtils.blankToDefault(bo.getPeriod(), "未提供"));
        endpointMapper.insert(entity);
        return endpointCode;
    }
}
