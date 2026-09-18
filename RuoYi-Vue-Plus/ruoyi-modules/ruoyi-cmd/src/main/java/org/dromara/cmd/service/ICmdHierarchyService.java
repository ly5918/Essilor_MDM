package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdHierarchyRelationBo;
import org.dromara.cmd.domain.vo.CmdHierarchyNodeVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationVo;

import java.util.List;

/**
 * 客户层级 服务层接口
 * <p>
 * 对应页面：客户层级 hier —— 左侧搜索与结果列表、中间层级树、右侧节点详情。
 *
 * @author Essilor CMD POC
 */
public interface ICmdHierarchyService {

    /**
     * 查询层级节点列表（可按层级级别 / BU / 关键字过滤）
     *
     * @param keyword  关键字（客户名称 / One ID / 节点编码）
     * @param level    层级级别（A1 / A2 / A3，可为空）
     * @param buScope  归属 BU（可为空）
     * @return 节点列表
     */
    List<CmdHierarchyNodeVo> selectNodeList(String keyword, String level, String buScope);

    /**
     * 按 One ID 查询节点详情
     *
     * @param oneId 客户 One ID
     * @return 节点详情
     */
    CmdHierarchyNodeVo selectNodeByOneId(String oneId);

    /**
     * 查询某节点的直接子节点（Lazy Load）
     *
     * @param parentOneId 父节点 One ID
     * @return 子节点列表
     */
    List<CmdHierarchyNodeVo> selectChildren(String parentOneId);

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
}
