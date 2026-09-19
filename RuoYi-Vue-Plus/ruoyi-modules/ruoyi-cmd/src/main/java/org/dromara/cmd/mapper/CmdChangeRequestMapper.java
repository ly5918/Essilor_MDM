package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdChangeRequest;
import org.dromara.cmd.domain.vo.CmdChangeRequestVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 变更与停用 数据层 cmd_change_request
 * <p>
 * 对应页面：变更与停用 change 列表与详情。
 *
 * @author Essilor CMD POC
 */
public interface CmdChangeRequestMapper extends BaseMapperPlus<CmdChangeRequest, CmdChangeRequestVo> {

}
