package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.CmdHierarchyNode;
import org.dromara.cmd.domain.CmdHierarchyRelation;
import org.dromara.cmd.domain.CmdHierarchyRelationHist;
import org.dromara.cmd.domain.CmdLoopCheckLog;
import org.dromara.cmd.domain.bo.CmdHierarchyAssignBo;
import org.dromara.cmd.domain.bo.CmdHierarchyChildBo;
import org.dromara.cmd.domain.bo.CmdHierarchyRelationBo;
import org.dromara.cmd.domain.vo.CmdHierarchyNodeVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationHistVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationVo;
import org.dromara.cmd.domain.vo.CmdHierarchyUnassignedVo;
import org.dromara.cmd.domain.vo.CmdHierarchyValidateVo;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.CmdHierarchyNodeMapper;
import org.dromara.cmd.mapper.CmdHierarchyRelationHistMapper;
import org.dromara.cmd.mapper.CmdHierarchyRelationMapper;
import org.dromara.cmd.mapper.CmdLoopCheckLogMapper;
import org.dromara.cmd.service.ICmdHierarchyService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户层级 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>环路检测（Loop Check）：新增 / 编辑关系前校验子节点路径是否已包含父节点，阻止成环，
 *       并把冲突路径写入 cmd_loop_check_log 作为审计证据</li>
 *   <li>跨 BU 关系自动判定：父子节点 BU 不同则置 crossBuFlag=Y，需升级 GC Scope 审批</li>
 *   <li>历史不覆盖：关系变更写入 cmd_hierarchy_relation_hist（versionNo 递增 + 前后快照），不做物理删除</li>
 *   <li>主数据与层级联动：审批通过的客户自动登记为「待归位」节点，归位 / 增加子节点后进入 A3-A2-A1 树</li>
 *   <li>单一校验口径：提交前校验（validateRelation）与提交时校验（mount / update）走同一份逻辑，
 *       保证「弹窗里看到的结论」= 「数据库的实际判断」</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdHierarchyServiceImpl implements ICmdHierarchyService {

    private final CmdHierarchyNodeMapper nodeMapper;
    private final CmdHierarchyRelationMapper relationMapper;
    private final CmdHierarchyRelationHistMapper relationHistMapper;
    private final CmdLoopCheckLogMapper loopCheckLogMapper;
    private final CmdCustomerMapper customerMapper;

    /** 祖先链向上回溯的最大层数（防御脏数据造成的死循环） */
    private static final int MAX_ANCESTOR_WALK = 16;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyNodeVo> selectNodeList(String keyword, String hierarchyType, String level,
                                                   String buScope, String status) {
        // 页面下拉里的「全部 / All Authorized BU」这类占位项一律按「不过滤」处理
        String hierTypeParam = blankIfPlaceholder(hierarchyType, CmdConstants.HIER_FILTER_ALL_TYPE);
        String levelParam = blankIfPlaceholder(level, CmdConstants.HIER_FILTER_ALL_LEVEL);
        String buParam = blankIfPlaceholder(buScope, CmdConstants.HIER_FILTER_ALL_BU);
        String statusParam = normalizeStatus(status);

        LambdaQueryWrapper<CmdHierarchyNode> lqw = new LambdaQueryWrapper<CmdHierarchyNode>()
            // 待归位节点不进 A3-A2-A1 树（它们没有父节点，进树会被当成根节点）
            .ne(CmdHierarchyNode::getHierarchyType, CmdConstants.HIER_TYPE_UNASSIGNED)
            .eq(StringUtils.isNotBlank(hierTypeParam), CmdHierarchyNode::getHierarchyType, hierTypeParam)
            .eq(StringUtils.isNotBlank(levelParam), CmdHierarchyNode::getLevel, levelParam)
            .eq(StringUtils.isNotBlank(buParam), CmdHierarchyNode::getBuScope, buParam)
            .eq(StringUtils.isNotBlank(statusParam), CmdHierarchyNode::getStatus, statusParam)
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
     * 下拉占位项（「全部层级」/「All Authorized BU」等）归一为「不过滤」
     */
    private String blankIfPlaceholder(String value, String placeholder) {
        if (StringUtils.isBlank(value) || placeholder.equalsIgnoreCase(value.trim())) {
            return StringUtils.EMPTY;
        }
        return value.trim();
    }

    /**
     * 状态文案归一为库里的枚举值（Active / Future / Expired → active / future / expired）
     */
    private String normalizeStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return StringUtils.EMPTY;
        }
        String lower = status.trim().toLowerCase();
        if ("active".equals(lower)) {
            return CmdConstants.HIER_NODE_ACTIVE;
        }
        if ("future".equals(lower)) {
            return CmdConstants.HIER_NODE_FUTURE;
        }
        if ("expired".equals(lower)) {
            return CmdConstants.HIER_NODE_EXPIRED;
        }
        return lower;
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
        return selectChildren(parentOneId, 0, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyNodeVo> selectChildren(String parentOneId, int offset, int limit) {
        LambdaQueryWrapper<CmdHierarchyNode> lqw = new LambdaQueryWrapper<CmdHierarchyNode>()
            .eq(CmdHierarchyNode::getParentOneId, parentOneId)
            .orderByAsc(CmdHierarchyNode::getSortOrder)
            .orderByAsc(CmdHierarchyNode::getOneId);
        // offset / limit 均为 0 表示不分页（兼容原有全量用法）
        if (offset > 0 || limit > 0) {
            lqw.last("LIMIT " + Math.max(offset, 0) + ", " + (limit > 0 ? limit : CmdConstants.HIER_CHILD_PAGE_SIZE));
        }
        return nodeMapper.selectVoList(lqw);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyNodeVo> selectRoots(String buScope) {
        return nodeMapper.selectVoList(new LambdaQueryWrapper<CmdHierarchyNode>()
            .ne(CmdHierarchyNode::getHierarchyType, CmdConstants.HIER_TYPE_UNASSIGNED)
            .eq(StringUtils.isNotBlank(buScope), CmdHierarchyNode::getBuScope, buScope)
            // 根节点 = 没有父节点（A3 集团 / 顶层 Commercial Entity）
            .and(w -> w.isNull(CmdHierarchyNode::getParentOneId)
                .or().eq(CmdHierarchyNode::getParentOneId, ""))
            .orderByAsc(CmdHierarchyNode::getSortOrder)
            .orderByAsc(CmdHierarchyNode::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyUnassignedVo> selectUnassignedList(String keyword, String buScope) {
        // 1) 候选：已审批通过、成为 Golden Record 的主数据
        LambdaQueryWrapper<CmdCustomer> cq = new LambdaQueryWrapper<CmdCustomer>()
            .eq(CmdCustomer::getStatus, CmdConstants.CUST_STATUS_ACTIVE)
            .eq(StringUtils.isNotBlank(buScope), CmdCustomer::getBuScope, buScope)
            .orderByDesc(CmdCustomer::getApprovedTime)
            .orderByDesc(CmdCustomer::getCreateTime);
        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            cq.and(w -> w.like(CmdCustomer::getLegalName, kw).or().like(CmdCustomer::getOneId, kw));
        }
        List<CmdCustomer> customers = customerMapper.selectList(cq);
        if (customers.isEmpty()) {
            return List.of();
        }

        // 2) 已归位（有父节点）的 One ID 集合：这些已经进入 A3-A2-A1 树，不再属于「待归位」
        List<String> oneIds = customers.stream().map(CmdCustomer::getOneId).toList();
        List<CmdHierarchyNode> nodes = nodeMapper.selectList(
            new LambdaQueryWrapper<CmdHierarchyNode>().in(CmdHierarchyNode::getOneId, oneIds));
        Map<String, CmdHierarchyNode> nodeByOneId = new HashMap<>(nodes.size());
        for (CmdHierarchyNode node : nodes) {
            nodeByOneId.put(node.getOneId(), node);
        }

        List<CmdHierarchyUnassignedVo> result = new ArrayList<>();
        for (CmdCustomer customer : customers) {
            CmdHierarchyNode node = nodeByOneId.get(customer.getOneId());
            boolean mounted = node != null
                && StringUtils.isNotBlank(node.getParentOneId())
                && !CmdConstants.HIER_TYPE_UNASSIGNED.equals(node.getHierarchyType());
            if (mounted) {
                continue;
            }
            CmdHierarchyUnassignedVo vo = new CmdHierarchyUnassignedVo();
            vo.setOneId(customer.getOneId());
            vo.setLegalName(customer.getLegalName());
            vo.setBuScope(customer.getBuScope());
            vo.setCustomerStatus(customer.getStatus());
            vo.setSourceSystem(customer.getSourceSystem());
            vo.setDqScore(customer.getDqScore());
            vo.setApprovedTime(customer.getApprovedTime());
            vo.setRegistered(node != null);
            vo.setNodeCode(node == null ? null : node.getNodeCode());
            vo.setSuggestedLevel(CmdConstants.HIER_LEVEL_A1);
            vo.setRemark(node == null
                ? "批准为主数据，尚未登记层级节点；归位后进入 A3-A2-A1 树"
                : "已登记为待归位节点，等待 Data Steward 归位");
            result.add(vo);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 刻意不加 {@code @Transactional}：本方法在「审批通过」的同事务中被调用，
     * 若单独开启事务边界后抛异常会把外层事务标记为 rollback-only，
     * 导致调用方即使 catch 也无法提交审批结果。此处跟随调用方事务即可。
     */
    @Override
    public int registerNode(String oneId) {
        if (StringUtils.isBlank(oneId)) {
            return 0;
        }
        // 幂等：该客户已有任何节点（含已归位）则不再登记
        Long exists = nodeMapper.selectCount(
            new LambdaQueryWrapper<CmdHierarchyNode>().eq(CmdHierarchyNode::getOneId, oneId));
        if (exists != null && exists > 0) {
            return 0;
        }
        CmdCustomer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, oneId).last("LIMIT 1"));
        if (ObjectUtil.isNull(customer)) {
            log.warn("[CMD][HIER] 登记层级节点失败，客户不存在：oneId={}", oneId);
            return 0;
        }
        CmdHierarchyNode node = new CmdHierarchyNode();
        node.setNodeCode(CmdConstants.HIER_NODE_CODE_UNASSIGNED_PREFIX + shortCode(oneId));
        node.setOneId(oneId);
        node.setLegalName(customer.getLegalName());
        node.setHierarchyType(CmdConstants.HIER_TYPE_UNASSIGNED);
        node.setLevel(CmdConstants.HIER_LEVEL_NONE);
        node.setDepth(0);
        node.setBuScope(customer.getBuScope());
        node.setPayerOneId(customer.getPayerId());
        node.setChildrenCount(0);
        node.setDescendants(0);
        node.setStatus(CmdConstants.HIER_NODE_PENDING);
        node.setSortOrder(0);
        node.setRemark("主数据审批通过，系统自动登记为待归位节点");
        int rows = nodeMapper.insert(node);
        log.info("[CMD][HIER] 已登记待归位层级节点：oneId={} nodeCode={} rows={}",
            oneId, node.getNodeCode(), rows);
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignNode(CmdHierarchyAssignBo bo) {
        // 归位 = 增加子节点的特例：不指定关系类型 / Payer / 有效期，全部按父节点推导
        CmdHierarchyChildBo childBo = new CmdHierarchyChildBo();
        childBo.setParentOneId(bo.getParentOneId());
        childBo.setChildOneId(bo.getOneId());
        childBo.setChangeReason(StringUtils.blankToDefault(bo.getChangeReason(), "主数据归位"));
        childBo.setRemark(bo.getRemark());
        childBo.setSourceType("MANUAL");
        return mountChild(childBo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addChildNode(CmdHierarchyChildBo bo) {
        if (StringUtils.isBlank(bo.getParentOneId()) || StringUtils.isBlank(bo.getChildOneId())) {
            throw new ServiceException("增加子节点失败：父节点与子节点都不能为空");
        }
        return mountChild(bo);
    }

    /**
     * 挂载子节点（归位 / 增加子节点共用同一条落地链路）
     * <p>
     * 流程：登记缺失节点 → 统一校验 → 级别推导 → 路径重建 → 祖先计数刷新 → 关系落地 → 关系历史留痕。
     *
     * @param bo 增加子节点入参
     * @return 影响行数
     */
    private int mountChild(CmdHierarchyChildBo bo) {
        String childOneId = bo.getChildOneId();
        String parentOneId = bo.getParentOneId();
        if (childOneId.equals(parentOneId)) {
            throw new ServiceException("不能把节点挂到自己下面：{}", childOneId);
        }

        // 1) 子节点：未登记时先登记（保证「批准即在层级体系内」），再参与校验
        CmdHierarchyNode child = findNode(childOneId);
        if (ObjectUtil.isNull(child)) {
            registerNode(childOneId);
            child = findNode(childOneId);
        }
        if (ObjectUtil.isNull(child)) {
            throw new ServiceException("层级节点不存在：{}", childOneId);
        }

        // 2) 统一校验（与弹窗里的「提交前校验」完全同一份逻辑，并写 Loop Check 证据）
        CmdHierarchyRelationBo validateBo = new CmdHierarchyRelationBo();
        validateBo.setParentOneId(parentOneId);
        validateBo.setChildOneId(childOneId);
        validateBo.setRelationType(bo.getRelationType());
        validateBo.setPayerOneId(bo.getPayerOneId());
        validateBo.setChangeReason(bo.getChangeReason());
        CmdHierarchyValidateVo check = doValidate(validateBo, true);
        if (!Boolean.TRUE.equals(check.getPassed())) {
            throw new ServiceException(check.getBlockedReason());
        }

        // 3) 回写子节点：父指针 + 级别 + 深度 + 路径 + 类型 + 状态
        CmdHierarchyNode parent = getNode(parentOneId);
        String childLevel = check.getChildLevel();
        CmdHierarchyNode patch = new CmdHierarchyNode();
        patch.setId(child.getId());
        patch.setParentOneId(parentOneId);
        patch.setLevel(childLevel);
        patch.setDepth(check.getChildDepth());
        patch.setHierarchyType(CmdConstants.hierTypeOfLevel(childLevel));
        // 待归位占位编码（UN-xxxx）在挂载后换成正式编码（A1-xxxx），与树上既有节点编码风格一致
        if (StringUtils.isBlank(child.getNodeCode())
            || child.getNodeCode().startsWith(CmdConstants.HIER_NODE_CODE_UNASSIGNED_PREFIX)) {
            patch.setNodeCode(childLevel + "-" + shortCode(childOneId));
        }
        patch.setFullPath(check.getPreviewPath());
        patch.setPathNames(check.getPreviewPathNames());
        patch.setBuScope(StringUtils.blankToDefault(child.getBuScope(), parent.getBuScope()));
        String payer = StringUtils.blankToDefault(bo.getPayerOneId(), parent.getPayerOneId());
        if (StringUtils.isNotBlank(payer)) {
            patch.setPayerOneId(payer);
        }
        patch.setEffectiveFrom(ObjectUtil.defaultIfNull(bo.getEffectiveFrom(), LocalDateTime.now()));
        patch.setEffectiveTo(bo.getEffectiveTo());
        patch.setStatus(CmdConstants.HIER_NODE_ACTIVE);
        patch.setRemark(bo.getRemark());
        int rows = nodeMapper.updateById(patch);

        // 4) 祖先计数刷新（父节点 +1 直接子节点，整条祖先链 +1 后代）
        refreshAncestors(parent, 1);

        // 5) 关系落地（历史不覆盖：已存在则更新并追加历史，否则新建并追加历史）
        CmdHierarchyRelation relation = new CmdHierarchyRelation();
        relation.setHierarchyType(StringUtils.blankToDefault(bo.getHierarchyType(),
            CmdConstants.hierTypeOfLevel(StringUtils.isBlank(childLevel) ? CmdConstants.HIER_LEVEL_A1 : childLevel)));
        relation.setRelationType(CmdConstants.hierRelationTypeOrDefault(parent.getLevel(), bo.getRelationType()));
        relation.setParentOneId(parentOneId);
        relation.setChildOneId(childOneId);
        relation.setPayerOneId(patch.getPayerOneId());
        relation.setBuScope(patch.getBuScope());
        relation.setCrossBuFlag(Boolean.TRUE.equals(check.getCrossBu()) ? CmdConstants.YES : CmdConstants.NO);
        relation.setEffectiveFrom(ObjectUtil.defaultIfNull(bo.getEffectiveFrom(), LocalDateTime.now()));
        relation.setEffectiveTo(bo.getEffectiveTo());
        relation.setStatus(CmdConstants.HIER_REL_STATUS_EFFECTIVE);
        relation.setChangeReason(StringUtils.blankToDefault(bo.getChangeReason(), "层级关系新增"));
        relation.setSourceType(StringUtils.blankToDefault(bo.getSourceType(), "MANUAL"));
        relation.setRemark(bo.getRemark());
        relation.setApprovedTime(LocalDateTime.now());
        try {
            relation.setApplicantId(LoginHelper.getUserId());
        } catch (Exception ignored) {
            // 免登录 POC 场景取不到会话，关系仍照常落库
        }
        upsertEffectiveRelation(relation);

        log.info("[CMD][HIER] 挂载子节点完成：child={} → parent={} level={} depth={} crossBu={}",
            childOneId, parentOneId, childLevel, check.getChildDepth(), relation.getCrossBuFlag());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addRelation(CmdHierarchyRelationBo bo) {
        // 1) 统一校验（父子相同 / 环路 / 级别 / 多父 / 跨 BU）
        CmdHierarchyValidateVo check = doValidate(bo, true);
        if (!Boolean.TRUE.equals(check.getPassed())) {
            throw new ServiceException(check.getBlockedReason());
        }
        CmdHierarchyNode parent = getNode(bo.getParentOneId());
        CmdHierarchyNode child = getNode(bo.getChildOneId());

        CmdHierarchyRelation relation = new CmdHierarchyRelation();
        relation.setRelationCode(newRelationCode());
        relation.setHierarchyType(StringUtils.blankToDefault(bo.getHierarchyType(),
            StringUtils.blankToDefault(parent.getHierarchyType(), "LEGAL")));
        relation.setRelationType(CmdConstants.hierRelationTypeOrDefault(parent.getLevel(), bo.getRelationType()));
        relation.setParentOneId(bo.getParentOneId());
        relation.setChildOneId(bo.getChildOneId());
        relation.setPayerOneId(StringUtils.blankToDefault(bo.getPayerOneId(), parent.getPayerOneId()));
        relation.setBuScope(StringUtils.blankToDefault(bo.getBuScope(), parent.getBuScope()));
        relation.setCrossBuFlag(Boolean.TRUE.equals(check.getCrossBu()) ? CmdConstants.YES : CmdConstants.NO);
        relation.setEffectiveFrom(ObjectUtil.defaultIfNull(bo.getEffectiveFrom(), LocalDateTime.now()));
        relation.setEffectiveTo(bo.getEffectiveTo());
        relation.setStatus(CmdConstants.HIER_REL_STATUS_PENDING);
        relation.setChangeReason(bo.getChangeReason());
        relation.setSourceType(StringUtils.blankToDefault(bo.getSourceType(), "MANUAL"));
        relation.setRemark(bo.getRemark());
        try {
            relation.setApplicantId(LoginHelper.getUserId());
        } catch (Exception ignored) {
            // 免登录 POC 场景
        }
        int rows = relationMapper.insert(relation);
        writeRelationHist(relation, CmdConstants.HIER_HIST_OP_CREATE, null);
        log.info("[CMD][HIER] 新增层级关系：{} {} → {} status=Pending",
            relation.getRelationCode(), child.getOneId(), parent.getOneId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRelation(CmdHierarchyRelationBo bo) {
        if (ObjectUtil.isNull(bo.getId())) {
            throw new ServiceException("编辑层级关系失败：缺少关系主键");
        }
        CmdHierarchyRelation relation = relationMapper.selectById(bo.getId());
        if (ObjectUtil.isNull(relation)) {
            throw new ServiceException("层级关系不存在：{}", bo.getId());
        }
        // 子节点是这条关系的身份，不允许在「编辑关系」里替换（要换子节点请新增关系）
        if (StringUtils.isNotBlank(bo.getChildOneId())
            && !bo.getChildOneId().equals(relation.getChildOneId())) {
            throw new ServiceException("编辑关系不支持替换子节点，请改为新增层级关系");
        }
        String childOneId = relation.getChildOneId();
        String oldParentOneId = relation.getParentOneId();
        String newParentOneId = StringUtils.blankToDefault(bo.getParentOneId(), oldParentOneId);
        boolean parentChanged = !newParentOneId.equals(oldParentOneId);

        // 1) 统一校验（编辑语义：排除自身，允许把节点改挂到别的父节点）
        CmdHierarchyRelationBo validateBo = new CmdHierarchyRelationBo();
        validateBo.setId(relation.getId());
        validateBo.setParentOneId(newParentOneId);
        validateBo.setChildOneId(childOneId);
        validateBo.setRelationType(StringUtils.blankToDefault(bo.getRelationType(), relation.getRelationType()));
        validateBo.setPayerOneId(StringUtils.blankToDefault(bo.getPayerOneId(), relation.getPayerOneId()));
        validateBo.setChangeReason(bo.getChangeReason());
        CmdHierarchyValidateVo check = doValidate(validateBo, true);
        if (!Boolean.TRUE.equals(check.getPassed())) {
            throw new ServiceException(check.getBlockedReason());
        }

        CmdHierarchyNode child = getNode(childOneId);
        Map<String, Object> before = relationSnapshot(relation);

        // 2) 父节点变化：旧祖先链计数 -1 → 重建子树路径 → 新祖先链计数 +1
        if (parentChanged) {
            CmdHierarchyNode oldParent = findNode(oldParentOneId);
            if (ObjectUtil.isNull(oldParent)) {
                throw new ServiceException("原父节点不存在，无法完成改挂：{}", oldParentOneId);
            }
            refreshAncestors(oldParent, -1);

            CmdHierarchyNode newParent = getNode(newParentOneId);
            CmdHierarchyNode patch = new CmdHierarchyNode();
            patch.setId(child.getId());
            patch.setParentOneId(newParentOneId);
            patch.setLevel(check.getChildLevel());
            patch.setDepth(check.getChildDepth());
            patch.setHierarchyType(CmdConstants.hierTypeOfLevel(check.getChildLevel()));
            patch.setFullPath(check.getPreviewPath());
            patch.setPathNames(check.getPreviewPathNames());
            // 级别可能变了（A2 改挂 A3 会变成 A2；A2 改挂 A2 会变成 A1），编码前缀随之同步
            patch.setNodeCode(check.getChildLevel() + "-" + shortCode(childOneId));
            nodeMapper.updateById(patch);

            // 子树整体平移：后代 depth / level / fullPath / pathNames 必须一起重建，
            // 否则后代的 fullPath 还指向旧祖先，环路检测会失效。
            CmdHierarchyNode moved = findNode(childOneId);
            rebuildSubtree(moved);
            refreshAncestors(newParent, 1);
            log.info("[CMD][HIER] 层级关系改挂：child={} {} → {}", childOneId, oldParentOneId, newParentOneId);
        }

        // 3) 属性更新（Payer 可直接改；有效期 / 关系类型 / 原因 / 备注）
        CmdHierarchyRelation patch = new CmdHierarchyRelation();
        patch.setId(relation.getId());
        patch.setHierarchyType(StringUtils.blankToDefault(bo.getHierarchyType(), relation.getHierarchyType()));
        patch.setRelationType(StringUtils.blankToDefault(bo.getRelationType(), relation.getRelationType()));
        patch.setParentOneId(newParentOneId);
        patch.setPayerOneId(StringUtils.blankToDefault(bo.getPayerOneId(), relation.getPayerOneId()));
        patch.setBuScope(StringUtils.blankToDefault(bo.getBuScope(), relation.getBuScope()));
        patch.setCrossBuFlag(Boolean.TRUE.equals(check.getCrossBu()) ? CmdConstants.YES : CmdConstants.NO);
        patch.setEffectiveFrom(ObjectUtil.defaultIfNull(bo.getEffectiveFrom(), relation.getEffectiveFrom()));
        patch.setEffectiveTo(bo.getEffectiveTo());
        patch.setChangeReason(StringUtils.blankToDefault(bo.getChangeReason(), relation.getChangeReason()));
        patch.setRemark(StringUtils.blankToDefault(bo.getRemark(), relation.getRemark()));
        patch.setSourceType(relation.getSourceType());
        patch.setStatus(CmdConstants.HIER_REL_STATUS_EFFECTIVE.equals(relation.getStatus())
            ? CmdConstants.HIER_REL_STATUS_EFFECTIVE : CmdConstants.HIER_REL_STATUS_PENDING);
        patch.setApprovedTime(LocalDateTime.now());
        int rows = relationMapper.updateById(patch);

        // 4) Payer 变更同步到被挂载节点（Payer 是 A1 门店的付款方归属）
        if (StringUtils.isNotBlank(patch.getPayerOneId())
            && !patch.getPayerOneId().equals(relation.getPayerOneId())) {
            CmdHierarchyNode payerPatch = new CmdHierarchyNode();
            payerPatch.setId(child.getId());
            payerPatch.setPayerOneId(patch.getPayerOneId());
            nodeMapper.updateById(payerPatch);
        }

        // 5) 历史留痕（追加版本，不覆盖）：before / after 快照都会保留
        CmdHierarchyRelation after = relationMapper.selectById(relation.getId());
        writeRelationHist(after, CmdConstants.HIER_HIST_OP_UPDATE, before);
        log.info("[CMD][HIER] 层级关系已编辑：{} child={} parent={}→{}",
            relation.getRelationCode(), childOneId, oldParentOneId, newParentOneId);
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyRelationVo> selectRelationList(String oneId) {
        return relationMapper.selectVoList(new LambdaQueryWrapper<CmdHierarchyRelation>()
            .and(w -> w.eq(CmdHierarchyRelation::getParentOneId, oneId)
                .or().eq(CmdHierarchyRelation::getChildOneId, oneId))
            .orderByDesc(CmdHierarchyRelation::getEffectiveFrom));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdHierarchyRelationVo selectEffectiveRelationByChild(String childOneId) {
        List<CmdHierarchyRelationVo> list = relationMapper.selectVoList(
            new LambdaQueryWrapper<CmdHierarchyRelation>()
                .eq(CmdHierarchyRelation::getChildOneId, childOneId)
                .in(CmdHierarchyRelation::getStatus,
                    List.of(CmdConstants.HIER_REL_STATUS_EFFECTIVE, CmdConstants.HIER_REL_STATUS_PENDING))
                .orderByDesc(CmdHierarchyRelation::getEffectiveFrom)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdHierarchyRelationVo selectRelationById(Long id) {
        return relationMapper.selectVoById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdHierarchyRelationHistVo> selectRelationHistory(Long relationId, String oneId) {
        LambdaQueryWrapper<CmdHierarchyRelationHist> lqw = new LambdaQueryWrapper<>();
        if (ObjectUtil.isNotNull(relationId)) {
            lqw.eq(CmdHierarchyRelationHist::getRelationId, relationId);
        } else if (StringUtils.isNotBlank(oneId)) {
            List<CmdHierarchyRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<CmdHierarchyRelation>()
                    .and(w -> w.eq(CmdHierarchyRelation::getParentOneId, oneId)
                        .or().eq(CmdHierarchyRelation::getChildOneId, oneId)));
            if (relations.isEmpty()) {
                return List.of();
            }
            lqw.in(CmdHierarchyRelationHist::getRelationId,
                relations.stream().map(CmdHierarchyRelation::getId).toList());
        } else {
            return List.of();
        }
        lqw.orderByDesc(CmdHierarchyRelationHist::getCreateTime)
            .orderByDesc(CmdHierarchyRelationHist::getVersionNo);
        return relationHistMapper.selectVoList(lqw);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CmdHierarchyValidateVo validateRelation(CmdHierarchyRelationBo bo) {
        // 只读校验：不登记节点、不落业务数据，但会写一条 Loop Check 证据日志
        return doValidate(bo, true);
    }

    /* ==================================================================================
     *  以下是内部实现
     * ================================================================================== */

    /**
     * 统一校验（提交前校验与提交时校验共用同一份逻辑）
     *
     * @param bo       关系入参（id 非空表示编辑语义，排除自身关系）
     * @param writeLog 是否写 cmd_loop_check_log 证据日志
     * @return 校验结果（含推导级别、预览路径、逐条明细）
     */
    private CmdHierarchyValidateVo doValidate(CmdHierarchyRelationBo bo, boolean writeLog) {
        LocalDateTime start = LocalDateTime.now();
        CmdHierarchyValidateVo vo = new CmdHierarchyValidateVo();
        vo.setCheckCode("LC-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        vo.setMaxDepth(CmdConstants.HIER_MAX_DEPTH);
        vo.setHierarchyType(StringUtils.blankToDefault(bo.getHierarchyType(), "LEGAL"));
        List<CmdHierarchyValidateVo.CheckItem> checks = new ArrayList<>();

        String parentOneId = bo.getParentOneId();
        String childOneId = bo.getChildOneId();

        // ---------- 0. 入参完整性 ----------
        if (StringUtils.isBlank(parentOneId) || StringUtils.isBlank(childOneId)) {
            checks.add(item(CmdConstants.LOOP_CHECK_SELF_REF, "父子节点完整性", CmdConstants.LOOP_CHECK_FAIL,
                "父节点与子节点都不能为空", null, "请先选择父节点与子节点"));
            return finish(vo, checks, start, writeLog, null);
        }

        CmdHierarchyNode parent = findNode(parentOneId);
        CmdHierarchyNode child = findNode(childOneId);
        if (ObjectUtil.isNull(child)) {
            // 子节点尚未登记（待归位主数据）：用客户主数据构造虚拟节点，
            // 保证名称回显、跨 BU 判定等检查基于真实数据执行，而不是直接跳过
            child = virtualNode(childOneId);
        }

        vo.setChildOneId(childOneId);
        vo.setParentOneId(parentOneId);
        vo.setChildName(ObjectUtil.isNull(child) ? null : child.getLegalName());
        vo.setParentName(ObjectUtil.isNull(parent) ? null : parent.getLegalName());
        vo.setParentLevel(ObjectUtil.isNull(parent) ? null : parent.getLevel());
        vo.setParentDepth(ObjectUtil.isNull(parent) ? null : parent.getDepth());
        vo.setChildMounted(ObjectUtil.isNotNull(child)
            && StringUtils.isNotBlank(child.getParentOneId())
            && !CmdConstants.HIER_TYPE_UNASSIGNED.equals(child.getHierarchyType()));
        if (ObjectUtil.isNotNull(child)) {
            vo.setChildCurrentParentOneId(child.getParentOneId());
            vo.setChildCurrentLevel(child.getLevel());
        }
        vo.setRelationLabel(String.format("%s %s → %s %s",
            ObjectUtil.isNull(parent) ? parentOneId : StringUtils.blankToDefault(parent.getNodeCode(), parentOneId),
            ObjectUtil.isNull(parent) ? "" : StringUtils.blankToDefault(parent.getLegalName(), ""),
            ObjectUtil.isNull(child) ? childOneId : StringUtils.blankToDefault(child.getNodeCode(), childOneId),
            ObjectUtil.isNull(child) ? "" : StringUtils.blankToDefault(child.getLegalName(), "")));

        // ---------- 1. 父子节点相同（自引用） ----------
        if (parentOneId.equals(childOneId)) {
            checks.add(item(CmdConstants.LOOP_CHECK_SELF_REF, "父子节点不能相同", CmdConstants.LOOP_CHECK_FAIL,
                "父节点与子节点是同一个客户：" + childOneId,
                parentOneId + " → " + childOneId, "请选择不同的父节点"));
        } else {
            checks.add(item(CmdConstants.LOOP_CHECK_SELF_REF, "父子节点不能相同", CmdConstants.LOOP_CHECK_PASS,
                "父节点 " + parentOneId + " ≠ 子节点 " + childOneId, null, null));
        }

        // ---------- 2. 节点存在性 ----------
        if (ObjectUtil.isNull(parent) || CmdConstants.HIER_TYPE_UNASSIGNED.equals(parent.getHierarchyType())) {
            checks.add(item(CmdConstants.LOOP_CHECK_SELF_MOUNT, "父节点必须已在层级树上", CmdConstants.LOOP_CHECK_FAIL,
                "父节点不存在或尚未归位：" + parentOneId, null, "请先把父节点归位到 A3-A2-A1 树"));
        } else {
            String parentPath = StringUtils.isBlank(parent.getFullPath())
                ? "/" + parent.getOneId() + "/" : parent.getFullPath();
            // 父节点自身不可挂到子节点下（直接环路）
            if (parentPath.contains("/" + childOneId + "/")) {
                checks.add(item(CmdConstants.LOOP_CHECK_SELF_MOUNT, "父节点不得位于子节点之下", CmdConstants.LOOP_CHECK_FAIL,
                    "父节点 " + parentOneId + " 已经在子节点 " + childOneId + " 的路径上",
                    buildConflictPath(parentPath, childOneId), "请改为把父节点挂到子节点下"));
            } else {
                checks.add(item(CmdConstants.LOOP_CHECK_SELF_MOUNT, "父节点不得位于子节点之下", CmdConstants.LOOP_CHECK_PASS,
                    "父节点 " + parentOneId + " 不在子节点路径上", null, null));
            }
        }

        // ---------- 3. 层级级别约束（含子树高度） ----------
        boolean levelOk = true;
        if (ObjectUtil.isNotNull(parent)) {
            int parentDepth = parent.getDepth() == null ? 1 : parent.getDepth();
            int childDepth = parentDepth + 1;
            int subtreeHeight = ObjectUtil.isNull(child) ? 0 : subtreeHeight(childOneId);
            vo.setChildDepth(childDepth);
            vo.setChildLevel(CmdConstants.hierLevelOfDepth(childDepth));
            vo.setRelationType(CmdConstants.hierRelationTypeOrDefault(parent.getLevel(), bo.getRelationType()));
            String parentPath = StringUtils.isBlank(parent.getFullPath())
                ? "/" + parent.getOneId() + "/" : parent.getFullPath();
            vo.setPreviewPath(parentPath + childOneId + "/");
            fillPreviewPathNames(vo, parent, child, childOneId);
            if (childDepth > CmdConstants.HIER_MAX_DEPTH) {
                levelOk = false;
                checks.add(item(CmdConstants.LOOP_CHECK_LEVEL_RULE, "层级级别有效（A3 → A2 → A1）",
                    CmdConstants.LOOP_CHECK_FAIL,
                    String.format("父节点 %s 已是末端节点（%s / depth=%d），不能再挂下级",
                        parentOneId, parent.getLevel(), parentDepth),
                    null, "请选择 A3 或 A2 作为父节点"));
            } else if (childDepth + subtreeHeight > CmdConstants.HIER_MAX_DEPTH) {
                levelOk = false;
                checks.add(item(CmdConstants.LOOP_CHECK_LEVEL_RULE, "层级级别有效（A3 → A2 → A1）",
                    CmdConstants.LOOP_CHECK_FAIL,
                    String.format("子节点 %s 下已有 %d 层下级，挂载后将达到 %d 层，超过 %d 级上限",
                        childOneId, subtreeHeight, childDepth + subtreeHeight, CmdConstants.HIER_MAX_DEPTH),
                    null, "请改挂到更上层节点，或先调整子节点下的层级"));
            } else {
                checks.add(item(CmdConstants.LOOP_CHECK_LEVEL_RULE, "层级级别有效（A3 → A2 → A1）",
                    CmdConstants.LOOP_CHECK_PASS,
                    String.format("挂载后级别 %s（depth=%d），子树高度 %d，未超过 %d 级",
                        vo.getChildLevel(), childDepth, subtreeHeight, CmdConstants.HIER_MAX_DEPTH), null, null));
            }
        }

        // ---------- 4. 多父冲突 ----------
        if (ObjectUtil.isNotNull(child)) {
            boolean mounted = StringUtils.isNotBlank(child.getParentOneId())
                && !CmdConstants.HIER_TYPE_UNASSIGNED.equals(child.getHierarchyType());
            if (mounted && !child.getParentOneId().equals(parentOneId)) {
                checks.add(item(CmdConstants.LOOP_CHECK_MULTI_PARENT, "多父冲突", CmdConstants.LOOP_CHECK_WARN,
                    String.format("子节点 %s 当前已挂在 %s 下，本次提交将把它改挂到 %s",
                        childOneId, child.getParentOneId(), parentOneId),
                    null, "确认改挂后原关系将被新关系取代，历史版本保留"));
            } else {
                checks.add(item(CmdConstants.LOOP_CHECK_MULTI_PARENT, "多父冲突", CmdConstants.LOOP_CHECK_PASS,
                    "同一生效期内父节点唯一，未发现多父冲突", null, null));
            }
        }

        // ---------- 5. 完整路径循环 ----------
        String conflictPath = null;
        if (ObjectUtil.isNotNull(child)) {
            // 把子节点整棵子树的所有后代收集起来：父节点若落在其中，挂上去必然成环
            List<String> descendantIds = new ArrayList<>();
            collectDescendants(childOneId, descendantIds, 0);
            if (descendantIds.contains(parentOneId)) {
                conflictPath = buildCyclePath(child, parentOneId);
                checks.add(item(CmdConstants.LOOP_CHECK_CYCLE, "完整路径循环", CmdConstants.LOOP_CHECK_FAIL,
                    "父节点 " + parentOneId + " 位于子节点 " + childOneId + " 的后代链上，新增后形成环路",
                    conflictPath, "系统已阻止提交，请先解除冲突关系"));
            } else if (ObjectUtil.isNotNull(parent)) {
                String childPath = StringUtils.blankToDefault(child.getFullPath(), "/" + childOneId);
                if (childPath.contains(parentOneId)) {
                    conflictPath = buildCyclePath(child, parentOneId);
                    checks.add(item(CmdConstants.LOOP_CHECK_CYCLE, "完整路径循环", CmdConstants.LOOP_CHECK_FAIL,
                        "检测到完整路径循环：" + conflictPath, conflictPath, "系统已阻止提交"));
                } else {
                    checks.add(item(CmdConstants.LOOP_CHECK_CYCLE, "完整路径循环", CmdConstants.LOOP_CHECK_PASS,
                        "子节点路径与父节点无交集，未发现循环", null, null));
                }
            }
        }

        // ---------- 6. 跨 BU 判定 ----------
        if (ObjectUtil.isNotNull(parent) && ObjectUtil.isNotNull(child)) {
            String childBu = StringUtils.blankToDefault(child.getBuScope(), parent.getBuScope());
            boolean crossBu = StringUtils.isNotBlank(parent.getBuScope())
                && StringUtils.isNotBlank(childBu) && !parent.getBuScope().equals(childBu);
            vo.setCrossBu(crossBu);
            boolean majorLevel = CmdConstants.HIER_LEVEL_A2.equals(parent.getLevel())
                || CmdConstants.HIER_LEVEL_A3.equals(parent.getLevel());
            vo.setRequiresGcApproval(crossBu);
            checks.add(item(CmdConstants.LOOP_CHECK_CROSS_BU, "跨 BU 判定", crossBu ? CmdConstants.LOOP_CHECK_WARN : CmdConstants.LOOP_CHECK_PASS,
                crossBu
                    ? String.format("父节点 BU=%s 与子节点 BU=%s 不一致，需升级 GC Scope 审批", parent.getBuScope(), childBu)
                    : String.format("父子节点同属 BU（%s），本 BU Data Steward 即可处理", parent.getBuScope()),
                null, crossBu ? "提交后进入 GC Scope 审批队列" : null));
            if (majorLevel && crossBu) {
                log.info("[CMD][HIER] 重大层级调整（跨 BU + {} 级）：{} → {}", parent.getLevel(), childOneId, parentOneId);
            }
        }

        return finish(vo, checks, start, writeLog, conflictPath);
    }

    /**
     * 待归位主数据（尚未登记为层级节点）→ 用客户主数据构造虚拟节点，仅供校验使用
     *
     * @param oneId 客户 One ID
     * @return 虚拟节点，客户不存在时返回 null
     */
    private CmdHierarchyNode virtualNode(String oneId) {
        CmdCustomer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<CmdCustomer>().eq(CmdCustomer::getOneId, oneId).last("LIMIT 1"));
        if (ObjectUtil.isNull(customer)) {
            return null;
        }
        CmdHierarchyNode node = new CmdHierarchyNode();
        node.setOneId(customer.getOneId());
        node.setLegalName(customer.getLegalName());
        node.setBuScope(customer.getBuScope());
        node.setHierarchyType(CmdConstants.HIER_TYPE_UNASSIGNED);
        node.setParentOneId(null);
        return node;
    }

    /**
     * 收尾：汇总逐条明细、推导结论、写 Loop Check 证据日志
     */
    private CmdHierarchyValidateVo finish(CmdHierarchyValidateVo vo, List<CmdHierarchyValidateVo.CheckItem> checks,
                                          LocalDateTime start, boolean writeLog, String conflictPath) {
        vo.setChecks(checks);
        String blockedReason = null;
        String logType = CmdConstants.LOOP_CHECK_SELF_REF;
        String logResult = CmdConstants.LOOP_CHECK_PASS;
        String logMessage = "提交前校验通过：父子节点不同、层级级别有效、未发现多父冲突与循环路径";
        for (CmdHierarchyValidateVo.CheckItem c : checks) {
            if (CmdConstants.LOOP_CHECK_FAIL.equals(c.getCheckResult())) {
                blockedReason = blockedReason == null ? c.getMessage() : blockedReason;
                logType = c.getCheckType();
                logResult = CmdConstants.LOOP_CHECK_FAIL;
                logMessage = c.getMessage();
            } else if (CmdConstants.LOOP_CHECK_WARN.equals(c.getCheckResult())
                && CmdConstants.LOOP_CHECK_PASS.equals(logResult)) {
                logType = c.getCheckType();
                logResult = CmdConstants.LOOP_CHECK_WARN;
                logMessage = c.getMessage();
            }
        }
        vo.setPassed(blockedReason == null);
        vo.setBlockedReason(blockedReason);
        LocalDateTime end = LocalDateTime.now();
        vo.setExecuteTime(end);
        vo.setDurationMs(Duration.between(start, end).toMillis());
        if (writeLog) {
            writeLoopCheckLog(vo, logType, logResult, logMessage, conflictPath);
        }
        return vo;
    }

    /**
     * 写环路检测证据日志（cmd_loop_check_log）
     */
    private void writeLoopCheckLog(CmdHierarchyValidateVo vo, String checkType, String result,
                                   String message, String conflictPath) {
        try {
            CmdLoopCheckLog logRow = new CmdLoopCheckLog();
            logRow.setCheckCode(vo.getCheckCode());
            logRow.setHierarchyType(StringUtils.blankToDefault(vo.getHierarchyType(), "LEGAL"));
            logRow.setParentOneId(vo.getParentOneId());
            logRow.setChildOneId(vo.getChildOneId());
            logRow.setCheckType(checkType);
            logRow.setCheckResult(result);
            logRow.setConflictPath(conflictPath);
            logRow.setConflictNodes(conflictPath == null ? null : conflictPath.replace("→", ",").replace(" ", ""));
            logRow.setMessage(message);
            logRow.setSuggestion(suggestionOf(vo));
            logRow.setExecuteTime(vo.getExecuteTime());
            logRow.setDurationMs(vo.getDurationMs());
            loopCheckLogMapper.insert(logRow);
        } catch (Exception e) {
            // 证据日志失败不能影响主流程（校验结论已返回给前端）
            log.warn("[CMD][HIER] 写入 Loop Check 日志失败：{}", e.getMessage());
        }
    }

    private String suggestionOf(CmdHierarchyValidateVo vo) {
        for (CmdHierarchyValidateVo.CheckItem c : vo.getChecks()) {
            if (CmdConstants.LOOP_CHECK_FAIL.equals(c.getCheckResult()) && StringUtils.isNotBlank(c.getSuggestion())) {
                return c.getSuggestion();
            }
        }
        return "校验通过，可提交";
    }

    /**
     * 关系落地：同一子节点若已有生效 / 待审关系则更新它，否则新建
     * <p>
     * 这样「先解绑再挂载」不会产生重复的有效关系，同时每次变更都有历史记录。
     */
    private void upsertEffectiveRelation(CmdHierarchyRelation relation) {
        List<CmdHierarchyRelation> exists = relationMapper.selectList(
            new LambdaQueryWrapper<CmdHierarchyRelation>()
                .eq(CmdHierarchyRelation::getChildOneId, relation.getChildOneId())
                .in(CmdHierarchyRelation::getStatus,
                    List.of(CmdConstants.HIER_REL_STATUS_EFFECTIVE, CmdConstants.HIER_REL_STATUS_PENDING))
                .orderByDesc(CmdHierarchyRelation::getEffectiveFrom));
        if (exists.isEmpty()) {
            relation.setRelationCode(newRelationCode());
            relationMapper.insert(relation);
            writeRelationHist(relation, CmdConstants.HIER_HIST_OP_CREATE, null);
            return;
        }
        CmdHierarchyRelation current = exists.get(0);
        Map<String, Object> before = relationSnapshot(current);
        CmdHierarchyRelation patch = new CmdHierarchyRelation();
        patch.setId(current.getId());
        patch.setHierarchyType(relation.getHierarchyType());
        patch.setRelationType(relation.getRelationType());
        patch.setParentOneId(relation.getParentOneId());
        patch.setPayerOneId(relation.getPayerOneId());
        patch.setBuScope(relation.getBuScope());
        patch.setCrossBuFlag(relation.getCrossBuFlag());
        patch.setEffectiveFrom(relation.getEffectiveFrom());
        patch.setEffectiveTo(relation.getEffectiveTo());
        patch.setStatus(relation.getStatus());
        patch.setChangeReason(relation.getChangeReason());
        patch.setSourceType(relation.getSourceType());
        patch.setRemark(relation.getRemark());
        patch.setApprovedTime(relation.getApprovedTime());
        relationMapper.updateById(patch);
        CmdHierarchyRelation after = relationMapper.selectById(current.getId());
        writeRelationHist(after, CmdConstants.HIER_HIST_OP_UPDATE, before);
    }

    /**
     * 写关系历史（versionNo 递增，追加不覆盖）
     *
     * @param relation 关系（操作后的最新值）
     * @param operation CREATE / UPDATE / EXPIRE
     * @param before    操作前快照（CREATE 传 null）
     */
    private void writeRelationHist(CmdHierarchyRelation relation, String operation, Map<String, Object> before) {
        if (ObjectUtil.isNull(relation) || ObjectUtil.isNull(relation.getId())) {
            return;
        }
        List<CmdHierarchyRelationHist> last = relationHistMapper.selectList(
            new LambdaQueryWrapper<CmdHierarchyRelationHist>()
                .eq(CmdHierarchyRelationHist::getRelationId, relation.getId())
                .orderByDesc(CmdHierarchyRelationHist::getVersionNo)
                .last("LIMIT 1"));
        int version = last.isEmpty() || last.get(0).getVersionNo() == null ? 1 : last.get(0).getVersionNo() + 1;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (before != null) {
            snapshot.put("before", before);
        }
        snapshot.put("after", relationSnapshot(relation));

        CmdHierarchyRelationHist hist = new CmdHierarchyRelationHist();
        hist.setRelationId(relation.getId());
        hist.setRelationCode(relation.getRelationCode());
        hist.setVersionNo(version);
        hist.setOperation(operation);
        hist.setHierarchyType(relation.getHierarchyType());
        hist.setRelationType(relation.getRelationType());
        hist.setParentOneId(relation.getParentOneId());
        hist.setChildOneId(relation.getChildOneId());
        hist.setPayerOneId(relation.getPayerOneId());
        hist.setEffectiveFrom(relation.getEffectiveFrom());
        hist.setEffectiveTo(relation.getEffectiveTo());
        hist.setStatus(relation.getStatus());
        hist.setSnapshotJson(JsonUtils.toJsonString(snapshot));
        hist.setChangeReason(relation.getChangeReason());
        hist.setFlowInstanceId(relation.getFlowInstanceId());
        hist.setRemark(relation.getRemark());
        relationHistMapper.insert(hist);
    }

    private Map<String, Object> relationSnapshot(CmdHierarchyRelation relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("relationCode", relation.getRelationCode());
        map.put("hierarchyType", relation.getHierarchyType());
        map.put("relationType", relation.getRelationType());
        map.put("parentOneId", relation.getParentOneId());
        map.put("childOneId", relation.getChildOneId());
        map.put("payerOneId", relation.getPayerOneId());
        map.put("buScope", relation.getBuScope());
        map.put("crossBuFlag", relation.getCrossBuFlag());
        map.put("effectiveFrom", relation.getEffectiveFrom() == null ? null : relation.getEffectiveFrom().toString());
        map.put("effectiveTo", relation.getEffectiveTo() == null ? null : relation.getEffectiveTo().toString());
        map.put("status", relation.getStatus());
        map.put("changeReason", relation.getChangeReason());
        return map;
    }

    /**
     * 刷新祖先计数（delta 可正可负）
     * <p>
     * 挂载时 +1、改挂 / 解除时 -1；直接父节点记 childrenCount，整条祖先链记 descendants。
     *
     * @param start 起始节点（直接父节点 / 原父节点）
     * @param delta +1 或 -1
     */
    private void refreshAncestors(CmdHierarchyNode start, int delta) {
        CmdHierarchyNode current = start;
        boolean direct = true;
        int guard = 0;
        while (ObjectUtil.isNotNull(current) && guard++ < MAX_ANCESTOR_WALK) {
            CmdHierarchyNode patch = new CmdHierarchyNode();
            patch.setId(current.getId());
            int children = current.getChildrenCount() == null ? 0 : current.getChildrenCount();
            int descendants = current.getDescendants() == null ? 0 : current.getDescendants();
            patch.setChildrenCount(Math.max(0, children + (direct ? delta : 0)));
            patch.setDescendants(Math.max(0, descendants + delta));
            nodeMapper.updateById(patch);
            direct = false;
            current = StringUtils.isBlank(current.getParentOneId()) ? null : findNode(current.getParentOneId());
        }
    }

    /**
     * 子树重建：父节点变化后，整棵子树必须一起平移
     * <p>
     * depth / level / hierarchyType / fullPath / pathNames 全部按新祖先链重算；
     * 任一层超过三级上限即抛异常，由外层 {@code @Transactional} 整体回滚（不留半成品数据）。
     *
     * @param node 已更新为新深度的节点
     */
    private void rebuildSubtree(CmdHierarchyNode node) {
        if (ObjectUtil.isNull(node) || ObjectUtil.isNull(node.getDepth())) {
            return;
        }
        List<CmdHierarchyNode> children = findChildren(node.getOneId());
        if (children.isEmpty()) {
            return;
        }
        int childDepth = node.getDepth() + 1;
        if (childDepth > CmdConstants.HIER_MAX_DEPTH) {
            throw new ServiceException("改挂后子树将超过 {} 级（{}-{} 已是末端），系统已阻止提交",
                CmdConstants.HIER_MAX_DEPTH, node.getNodeCode(), node.getOneId());
        }
        String childLevel = CmdConstants.hierLevelOfDepth(childDepth);
        String basePath = StringUtils.isBlank(node.getFullPath())
            ? "/" + node.getOneId() + "/" : node.getFullPath();
        String baseNames = StringUtils.blankToDefault(node.getPathNames(),
            StringUtils.blankToDefault(node.getLegalName(), node.getOneId()));
        for (CmdHierarchyNode child : children) {
            CmdHierarchyNode patch = new CmdHierarchyNode();
            patch.setId(child.getId());
            patch.setParentOneId(node.getOneId());
            patch.setDepth(childDepth);
            patch.setLevel(childLevel);
            patch.setHierarchyType(CmdConstants.hierTypeOfLevel(childLevel));
            patch.setFullPath(basePath + child.getOneId() + "/");
            patch.setPathNames(baseNames + "/" + StringUtils.blankToDefault(child.getLegalName(), child.getOneId()));
            nodeMapper.updateById(patch);

            // 递归下钻：下一层用「已打补丁」的对象继续
            CmdHierarchyNode patched = findNode(child.getOneId());
            rebuildSubtree(patched);
        }
    }

    /**
     * 计算子树高度（不含自身）
     */
    private int subtreeHeight(String oneId) {
        List<CmdHierarchyNode> children = findChildren(oneId);
        int max = 0;
        for (CmdHierarchyNode child : children) {
            max = Math.max(max, 1 + subtreeHeight(child.getOneId()));
        }
        return max;
    }

    /**
     * 收集后代 One ID（含自身，不含父节点），用于环路检测
     */
    private void collectDescendants(String oneId, List<String> target, int guard) {
        if (guard > MAX_ANCESTOR_WALK) {
            return;
        }
        List<CmdHierarchyNode> children = findChildren(oneId);
        for (CmdHierarchyNode child : children) {
            if (!target.contains(child.getOneId())) {
                target.add(child.getOneId());
                collectDescendants(child.getOneId(), target, guard + 1);
            }
        }
    }

    /**
     * 构造冲突路径证据串（A1-000128 → A2-0188 → A1-000128）
     */
    private String buildCyclePath(CmdHierarchyNode child, String parentOneId) {
        String base = pathToArrows(StringUtils.blankToDefault(child.getFullPath(), "/" + child.getOneId() + "/"));
        return base + " → " + parentOneId + " → " + child.getOneId();
    }

    /**
     * 构造父节点路径冲突证据串
     */
    private String buildConflictPath(String parentPath, String childOneId) {
        return pathToArrows(parentPath) + "（含子节点 " + childOneId + "）";
    }

    /**
     * /A/B/C/ → A → B → C（写进冲突证据，便于人读）
     */
    private String pathToArrows(String path) {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (StringUtils.isNotBlank(part)) {
                parts.add(part.trim());
            }
        }
        return String.join(" → ", parts);
    }

    /**
     * 预览路径名称串（A3 远见集团 / A2 远见华东法人 / A1 某门店）
     */
    private void fillPreviewPathNames(CmdHierarchyValidateVo vo, CmdHierarchyNode parent, CmdHierarchyNode child, String childOneId) {
        String parentNames = StringUtils.isBlank(parent.getPathNames())
            ? StringUtils.blankToDefault(parent.getLegalName(), parent.getOneId()) : parent.getPathNames();
        String childName = ObjectUtil.isNull(child)
            ? childOneId : StringUtils.blankToDefault(child.getLegalName(), childOneId);
        vo.setPreviewPathNames(parentNames + "/" + childName);
    }

    private CmdHierarchyValidateVo.CheckItem item(String type, String label, String result,
                                                  String message, String conflictPath, String suggestion) {
        CmdHierarchyValidateVo.CheckItem c = new CmdHierarchyValidateVo.CheckItem();
        c.setCheckType(type);
        c.setLabel(label);
        c.setCheckResult(result);
        c.setMessage(message);
        c.setConflictPath(conflictPath);
        c.setSuggestion(suggestion);
        return c;
    }

    private String newRelationCode() {
        return "REL-" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    /**
     * 按 One ID 查询节点（可能同时存在 UNASSIGNED 与已归位节点，优先取待归位节点）
     *
     * @param oneId 客户 One ID
     * @return 节点，不存在返回 null
     */
    private CmdHierarchyNode findNode(String oneId) {
        List<CmdHierarchyNode> list = nodeMapper.selectList(
            new LambdaQueryWrapper<CmdHierarchyNode>()
                .eq(CmdHierarchyNode::getOneId, oneId)
                .orderByDesc(CmdHierarchyNode::getDepth));
        if (list.isEmpty()) {
            return null;
        }
        // 深度最大的是最"新"的一层（A1 > A2 > A3 > 0），待归位 depth=0 会被排在最后
        return list.stream()
            .filter(n -> CmdConstants.HIER_TYPE_UNASSIGNED.equals(n.getHierarchyType()))
            .findFirst()
            .orElse(list.get(0));
    }

    /**
     * 查询直接子节点实体
     */
    private List<CmdHierarchyNode> findChildren(String parentOneId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<CmdHierarchyNode>()
            .eq(CmdHierarchyNode::getParentOneId, parentOneId)
            .orderByAsc(CmdHierarchyNode::getSortOrder));
    }

    /**
     * 查询节点，不存在时抛业务异常
     *
     * @param oneId 客户 One ID
     * @return 层级节点
     */
    private CmdHierarchyNode getNode(String oneId) {
        CmdHierarchyNode node = findNode(oneId);
        if (ObjectUtil.isNull(node)) {
            throw new ServiceException("层级节点不存在：{}", oneId);
        }
        return node;
    }

    /**
     * 由 One ID 生成短编码（用于节点编码）
     *
     * @param oneId 客户 One ID
     * @return 短编码
     */
    private String shortCode(String oneId) {
        String raw = oneId == null ? "" : oneId.replaceAll("[^0-9A-Za-z]", "");
        if (raw.length() <= 12) {
            return raw.toUpperCase();
        }
        return raw.substring(raw.length() - 12).toUpperCase();
    }
}
