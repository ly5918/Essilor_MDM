package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdHierarchyRelationHist;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationHistVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 客户层级关系历史 数据层 cmd_hierarchy_relation_hist
 * <p>
 * 对应页面：客户层级 hier「关系历史」、审计中心 audit。
 *
 * @author Essilor CMD POC
 */
public interface CmdHierarchyRelationHistMapper extends BaseMapperPlus<CmdHierarchyRelationHist, CmdHierarchyRelationHistVo> {

}
