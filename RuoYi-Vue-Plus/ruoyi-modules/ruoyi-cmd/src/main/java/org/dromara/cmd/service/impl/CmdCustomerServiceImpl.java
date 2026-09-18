package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdCustomerVersion;
import org.dromara.cmd.domain.bo.CmdCustomerBo;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.cmd.domain.vo.CmdCustomerVo;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdCustomerVersionMapper;
import org.dromara.cmd.service.ICmdCustomerService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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
@RequiredArgsConstructor
@Service
public class CmdCustomerServiceImpl implements ICmdCustomerService {

    private final CmdCustomerMapper customerMapper;
    private final CmdCustomerVersionMapper versionMapper;

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
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String insertCustomer(CmdCustomerBo bo) {
        CmdCustomer customer = MapstructUtils.convert(bo, CmdCustomer.class);
        // One ID 由服务端按规则生成，前端不可指定（保证 One ID 稳定性）
        customer.setOneId(generateOneId());
        customer.setVersionNo(1);
        customer.setStatus(StringUtils.blankToDefault(bo.getStatus(), CmdConstants.CUST_STATUS_DRAFT));
        if (customer.getEffectiveFrom() == null) {
            customer.setEffectiveFrom(LocalDateTime.now());
        }
        customerMapper.insert(customer);
        // 首版本快照：beforeJson 为空
        appendVersion(customer, null, "CREATE", bo.getRemark());
        return customer.getOneId();
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
        return QueryBuilder.lambda(CmdCustomer.class)
            .likeIfText(CmdCustomer::getLegalName, bo.getLegalName())
            .eqIfText(CmdCustomer::getOneId, bo.getOneId())
            .eqIfText(CmdCustomer::getCreditCode, bo.getCreditCode())
            .eqIfText(CmdCustomer::getBuScope, bo.getBuScope())
            .eqIfText(CmdCustomer::getStatus, bo.getStatus())
            .eqIfText(CmdCustomer::getMatchState, bo.getMatchState())
            .eqIfText(CmdCustomer::getSourceSystem, bo.getSourceSystem())
            .betweenParams(CmdCustomer::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByDesc(CmdCustomer::getCreateTime)
            .build();
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
     * 生成 One ID（POC 实现）
     * <p>
     * 生产环境应改为读取 oneid_rule 表并按规则生成（编码模式 / 序列 / 校验位），
     * 此处使用 UUID 片段保证 POC 阶段全局唯一且不依赖外部组件。
     *
     * @return One ID
     */
    private String generateOneId() {
        return "GC-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }
}
