package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdCustomerVersion;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 客户版本快照 数据层 cmd_customer_version
 * <p>
 * 对应页面：客户变更与逻辑停用 change 的 Before / After 对比、审计中心 audit。
 *
 * @author Essilor CMD POC
 */
public interface CmdCustomerVersionMapper extends BaseMapperPlus<CmdCustomerVersion, CmdCustomerVersionVo> {

}
