package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdHierarchyAssignBo;
import org.dromara.cmd.domain.bo.CmdHierarchyChildBo;
import org.dromara.cmd.domain.bo.CmdHierarchyRelationBo;
import org.dromara.cmd.domain.vo.CmdHierarchyNodeVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationHistVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationVo;
import org.dromara.cmd.domain.vo.CmdHierarchyUnassignedVo;
import org.dromara.cmd.domain.vo.CmdHierarchyValidateVo;

import java.util.List;

/**
 * 客户层级 服务层接口
 * <p>
 * 对应页面：客户层级 hier —— 左侧搜索与结果列表、中间层级树、右侧节点详情。
 * <p>
 * 主数据与层级的关系（重要）：
 * 审批通过 → 客户成为 Golden Record → 系统自动登记「待归位」节点 → Steward 归位后进入 A3-A2-A1 树。
 * 因此「层级树」+「待归位列表」合起来才是主数据的完整视图。
 *
 * @author Essilor CMD POC
 */
public interface ICmdHierarchyService {

    /**
     * 查询待归位主数据列表
     * <p>
     * 口径：已审批通过（active）但尚未归位的主数据 ——
     * 层级节点表中没有节点，或节点仍为 UNASSIGNED（未挂父节点）。
     *
     * @param keyword 关键字（客户名称 / One ID，可为空）
     * @param buScope 归属 BU（可为空）
     * @return 待归位主数据列表
     */
    List<CmdHierarchyUnassignedVo> selectUnassignedList(String keyword, String buScope);

    /**
     * 登记层级节点（幂等）
     * <p>
     * 客户审批通过成为主数据时调用：为该客户在 cmd_hierarchy_node 中登记一个
     * 「待归位」节点（hierarchyType=UNASSIGNED、无父节点），使其进入客户层级的待归位区。
     * 已存在节点时不做任何处理。
     *
     * @param oneId 客户 One ID
     * @return 影响行数（0 表示已存在，未重复登记）
     */
    int registerNode(String oneId);

    /**
     * 层级归位：把待归位主数据挂到目标父节点之下
     * <p>
     * 含环路检测、级别推导（父 depth + 1）、路径重建、祖先计数刷新与关系留痕。
     *
     * @param bo 归位入参（客户 One ID + 目标父节点 One ID）
     * @return 影响行数
     */
    int assignNode(CmdHierarchyAssignBo bo);

    /**
     * 查询层级节点列表（页面左侧「搜索与导航」的下拉筛选全部走这里）
     *
     * @param keyword       关键字（客户名称 / One ID / 节点编码，可为空）
     * @param hierarchyType 层级类型（LEGAL / DOOR / PAYER，可为空）
     * @param level         层级级别（A1 / A2 / A3，可为空；传「全部层级」按空处理）
     * @param buScope       归属 BU（可为空；传「All Authorized BU」按空处理）
     * @param status        状态（active / future / expired，可为空）
     * @return 节点列表
     */
    List<CmdHierarchyNodeVo> selectNodeList(String keyword, String hierarchyType, String level, String buScope, String status);

    /**
     * 按 One ID 查询节点详情
     *
     * @param oneId 客户 One ID
     * @return 节点详情
     */
    CmdHierarchyNodeVo selectNodeByOneId(String oneId);

    /**
     * 查询某节点的直接子节点（Lazy Load，全量）
     *
     * @param parentOneId 父节点 One ID
     * @return 子节点列表
     */
    List<CmdHierarchyNodeVo> selectChildren(String parentOneId);

    /**
     * 查询某节点的直接子节点（分页，供树上「加载更多子节点」按需追加）
     *
     * @param parentOneId 父节点 One ID
     * @param offset      偏移量（从 0 开始）
     * @param limit       每页条数（0 或负数表示取默认值）
     * @return 子节点列表（按 sort_order、one_id 排序，保证多次翻页不重不漏）
     */
    List<CmdHierarchyNodeVo> selectChildren(String parentOneId, int offset, int limit);

    /**
     * 新增层级关系（含环路检测与父子校验）
     *
     * @param bo 关系入参
     * @return 影响行数
     */
    int addRelation(CmdHierarchyRelationBo bo);

    /**
     * 查询层级关系列表
     *
     * @param oneId 客户 One ID（父或子）
     * @return 关系列表
     */
    List<CmdHierarchyRelationVo> selectRelationList(String oneId);

    /**
     * 查询根节点列表（A3 集团 / 无父节点）
     * <p>
     * 供层级树「返回根节点」使用；按 BU 过滤时只返回该 BU 的根。
     *
     * @param buScope 归属 BU（可为空）
     * @return 根节点列表
     */
    List<CmdHierarchyNodeVo> selectRoots(String buScope);

    /**
     * 查询某节点的生效挂载关系（childOneId 维度）
     * <p>
     * 「编辑层级关系」需要先知道这个节点当前挂在谁下面、Payer 是谁、生效期多长。
     *
     * @param childOneId 子节点 One ID
     * @return 生效关系，不存在返回 null
     */
    CmdHierarchyRelationVo selectEffectiveRelationByChild(String childOneId);

    /**
     * 按主键查询层级关系
     *
     * @param id 关系主键
     * @return 关系，不存在返回 null
     */
    CmdHierarchyRelationVo selectRelationById(Long id);

    /**
     * 查询关系历史（版本留痕，倒序）
     *
     * @param relationId 关系主键（可为空）
     * @param oneId      节点 One ID（父子任一，relationId 为空时按节点查全部相关历史）
     * @return 历史版本列表
     */
    List<CmdHierarchyRelationHistVo> selectRelationHistory(Long relationId, String oneId);

    /**
     * 提交前实时校验（不修改业务数据）
     * <p>
     * 逐条检查：父子节点不同 → 父节点自身不可挂到子节点下 → 层级级别上限 →
     * 多父冲突 → 完整路径循环 → 跨 BU 升级 GC。
     * 每次校验都会向 cmd_loop_check_log 写一条证据（PASS / FAIL / WARN），
     * 失败时携带冲突路径，供 Auditor 追溯。
     *
     * @param bo 关系入参（id 非空时按「编辑」语义排除自身）
     * @return 校验结果（含推导级别、预览路径、逐条明细）
     */
    CmdHierarchyValidateVo validateRelation(CmdHierarchyRelationBo bo);

    /**
     * 增加子节点（真实落库）
     * <p>
     * 在当前节点下面挂一个下级：自动登记缺失节点 → 校验 → 级别推导 → 路径重建 →
     * 祖先计数刷新 → 关系落地（含跨 BU 判定）→ 关系历史留痕。
     *
     * @param bo 增加子节点入参
     * @return 影响行数
     */
    int addChildNode(CmdHierarchyChildBo bo);

    /**
     * 编辑层级关系（真实落库，历史不覆盖）
     * <p>
     * 支持改挂父节点、改 Payer、改关系类型、改有效期。父节点变化时：
     * 旧祖先链计数 -1、新祖先链计数 +1、子节点及其整棵子树重建 fullPath / pathNames / depth / level，
     * 移动后若超过三层则整体拒绝（不产生半成品数据）。
     * 每次编辑追加一条 cmd_hierarchy_relation_hist（versionNo + 1），保留改动前后快照。
     *
     * @param bo 关系入参（id 必传）
     * @return 影响行数
     */
    int updateRelation(CmdHierarchyRelationBo bo);
}
