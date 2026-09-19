package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdChangeRequest;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.bo.CmdChangeRequestBo;
import org.dromara.cmd.domain.vo.CmdChangeRequestVo;
import org.dromara.cmd.mapper.CmdChangeRequestMapper;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.service.ICmdChangeService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 变更与停用 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>停用为逻辑停用：只置 status 与 effectiveTo，客户记录与历史版本全部保留</li>
 *   <li>申请编号由服务端生成：CH-yyyyMMdd-四位流水，避免前端伪造</li>
 *   <li>客户名称冗余写入：列表直接展示，无需每次 JOIN 客户表</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdChangeServiceImpl implements ICmdChangeService {

    private final CmdChangeRequestMapper changeMapper;
    private final CmdCustomerMapper customerMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<CmdChangeRequestVo> selectPage(CmdChangeRequestBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<CmdChangeRequest> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getOneId()), CmdChangeRequest::getOneId, bo.getOneId())
            .eq(StringUtils.isNotBlank(bo.getChangeType()), CmdChangeRequest::getChangeType, bo.getChangeType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), CmdChangeRequest::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getBuScope()), CmdChangeRequest::getBuScope, bo.getBuScope())
            .orderByDesc(CmdChangeRequest::getCreateTime);
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
            Wrappers.<CmdChangeRequest>lambdaQuery().eq(CmdChangeRequest::getRequestCode, requestCode));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submit(CmdChangeRequestBo bo) {
        CmdCustomer customer = customerMapper.selectOne(
            Wrappers.<CmdCustomer>lambdaQuery().eq(CmdCustomer::getOneId, bo.getOneId()));
        if (customer == null) {
            throw new ServiceException("客户不存在：%s", bo.getOneId());
        }

        CmdChangeRequest entity = new CmdChangeRequest();
        entity.setRequestCode("CH-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        entity.setOneId(bo.getOneId());
        entity.setLegalName(customer.getLegalName());
        entity.setChangeType(bo.getChangeType());
        entity.setTargetStatus(bo.getTargetStatus());
        entity.setIsKeyChange(StringUtils.blankToDefault(bo.getIsKeyChange(), CmdConstants.NO));
        entity.setBuScope(StringUtils.blankToDefault(bo.getBuScope(), customer.getBuScope()));
        entity.setChangeReason(bo.getChangeReason());
        entity.setEffectiveDate(bo.getEffectiveDate());
        // 提交后进入审批，正式环境此处由 Warm-Flow 发起流程并回填 flowInstanceId
        entity.setStatus("PENDING");
        entity.setRemark(bo.getRemark());
        return changeMapper.insert(entity);
    }
}
