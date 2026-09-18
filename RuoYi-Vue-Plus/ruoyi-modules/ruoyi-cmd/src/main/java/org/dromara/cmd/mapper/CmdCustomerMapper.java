package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.vo.CmdCustomerVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 客户主档 数据层 cmd_customer
 * <p>
 * 说明：本模块所有 Mapper 均不感知数据源配置，统一走框架主库 ruoyi_plus（master 数据源），
 * 连接参数统一维护在 ruoyi-admin 的 application-*.yml，业务代码不出现数据源相关注解。
 *
 * @author Essilor CMD POC
 */
public interface CmdCustomerMapper extends BaseMapperPlus<CmdCustomer, CmdCustomerVo> {

}
