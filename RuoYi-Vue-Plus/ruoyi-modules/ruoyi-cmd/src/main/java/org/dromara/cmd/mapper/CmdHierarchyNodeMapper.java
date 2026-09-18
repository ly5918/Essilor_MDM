package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdHierarchyNode;
import org.dromara.cmd.domain.vo.CmdHierarchyNodeVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 客户层级节点 数据层 cmd_hierarchy_node
 * <p>
 * 对应页面：客户层级 hier 树与搜索结果。
 *
 * @author Essilor CMD POC
 */
public interface CmdHierarchyNodeMapper extends BaseMapperPlus<CmdHierarchyNode, CmdHierarchyNodeVo> {

}
