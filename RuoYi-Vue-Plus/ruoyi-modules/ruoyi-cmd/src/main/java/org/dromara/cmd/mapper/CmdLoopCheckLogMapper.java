package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdLoopCheckLog;
import org.dromara.cmd.domain.vo.CmdLoopCheckLogVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 层级环路检测日志 数据层 cmd_loop_check_log
 * <p>
 * 对应页面：客户层级 hier 提交前校验证据。
 *
 * @author Essilor CMD POC
 */
public interface CmdLoopCheckLogMapper extends BaseMapperPlus<CmdLoopCheckLog, CmdLoopCheckLogVo> {

}
