package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdHierarchyNode;
import org.dromara.cmd.domain.CmdHierarchyRelation;
import org.dromara.cmd.domain.bo.CmdHierarchyRelationBo;
import org.dromara.cmd.domain.vo.CmdHierarchyNodeVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationVo;
import org.dromara.cmd.mapper.CmdHierarchyNodeMapper;
import org.dromara.cmd.mapper.CmdHierarchyRelationMapper;
import org.dromara.cmd.service.ICmdHierarchyService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户层级 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>环路检测（Loop Check）：新增关系前校验子节点路径是否已包含父节点，阻止成环</li>
 *   <li>跨 BU 关系自动判定：父子节点 BU 不同则置 crossBuFlag=Y，需升级 GC Scope 审批</li>
 *   <li>历史不覆盖：关系变更写入 cmd_hierarchy_relation_hist（由历史表服务补充）</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdHierarchyServiceImpl implements ICmdHierarchyService {

    private final CmdHierarchyNodeMapper nodeMapper;
    private final CmdHierarchyRelationMapper relationMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyNodeVo> selectNodeList(String keyword, String level, String buScope) {
        LambdaQueryWrapper<CmdHierarchyNode> lqw = new LambdaQueryWrapper<CmdHierarchyNode>()
            .eq(StringUtils.isNotBlank(level), CmdHierarchyNode::getLevel, level)
            .eq(StringUtils.isNotBlank(buScope), CmdHierarchyNode::getBuScope, buScope)
            .orderByAsc(CmdHierarchyNode::getDepth)
            .orderByAsc(CmdHierarchyNode::getSortOrder);
        if (StringUtils.isNotBlank(keyword)) {
            lqw.and(w -> w.like(CmdHierarchyNode::getLegalName, keyword)
                .or().like(CmdHierarchyNode::getOneId, keyword)
                .or().like(CmdHierarchyNode::getNodeCode, keyword));
        }
        return nodeMapper.selectVoList(lqw);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdHierarchyNodeVo selectNodeByOneId(String oneId) {
        List<CmdHierarchyNodeVo> list = nodeMapper.selectVoList(
            new LambdaQueryWrapper<CmdHierarchyNode>().eq(CmdHierarchyNode::getOneId, oneId));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyNodeVo> selectChildren(String parentOneId) {
        return nodeMapper.selectVoList(new LambdaQueryWrapper<CmdHierarchyNode>()
            .eq(CmdHierarchyNode::getParentOneId, parentOneId)
            .orderByAsc(CmdHierarchyNode::getSortOrder));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addRelation(CmdHierarchyRelationBo bo) {
        // 1) 父子不能相同
        if (bo.getParentOneId().equals(bo.getChildOneId())) {
            throw new ServiceException("父节点与子节点不能相同");
        }
        CmdHierarchyNode parent = getNode(bo.getParentOneId());
        CmdHierarchyNode child = getNode(bo.getChildOneId());

        // 2) 环路检测：若父节点已在子节点的路径上，则新增后必然成环
        String childPath = StringUtils.blankToDefault(child.getFullPath(), "/" + child.getOneId());
        if (childPath.contains(parent.getOneId())) {
            throw new ServiceException("检测到循环路径：%s → %s → %s，系统已阻止提交",
                child.getOneId(), parent.getOneId(), child.getOneId());
        }

        // 3) 跨 BU 自动判定：父子 BU 不同需升级 GC Scope
        String crossBu = CmdConstants.NO;
        if (StringUtils.isNotBlank(parent.getBuScope()) && StringUtils.isNotBlank(child.getBuScope())
            && !parent.getBuScope().equals(child.getBuScope())) {
            crossBu = CmdConstants.YES;
        }

        CmdHierarchyRelation relation = new CmdHierarchyRelation();
        relation.setRelationCode("REL-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        relation.setHierarchyType(StringUtils.blankToDefault(bo.getHierarchyType(), "LEGAL"));
        relation.setRelationType(bo.getRelationType());
        relation.setParentOneId(bo.getParentOneId());
        relation.setChildOneId(bo.getChildOneId());
        relation.setPayerOneId(bo.getPayerOneId());
        relation.setBuScope(StringUtils.blankToDefault(bo.getBuScope(), parent.getBuScope()));
        relation.setCrossBuFlag(crossBu);
        relation.setEffectiveFrom(ObjectUtil.defaultIfNull(bo.getEffectiveFrom(), LocalDateTime.now()));
        relation.setEffectiveTo(bo.getEffectiveTo());
        relation.setStatus("Pending");
        relation.setChangeReason(bo.getChangeReason());
        relation.setSourceType(StringUtils.blankToDefault(bo.getSourceType(), "MANUAL"));
        relation.setApplicantId(LoginHelper.getUserId());
        relation.setRemark(bo.getRemark());
        return relationMapper.insert(relation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyRelationVo> selectRelationList(String oneId) {
        return relationMapper.selectVoList(new LambdaQueryWrapper<CmdHierarchyRelation>()
            .eq(CmdHierarchyRelation::getParentOneId, oneId)
            .or().eq(CmdHierarchyRelation::getChildOneId, oneId));
    }

    /**
     * 查询节点，不存在时抛业务异常
     *
     * @param oneId 客户 One ID
     * @return 层级节点
     */
    private CmdHierarchyNode getNode(String oneId) {
        CmdHierarchyNode node = nodeMapper.selectOne(
            new LambdaQueryWrapper<CmdHierarchyNode>().eq(CmdHierarchyNode::getOneId, oneId));
        if (ObjectUtil.isNull(node)) {
            throw new ServiceException("层级节点不存在：%s", oneId);
        }
        return node;
    }
}
