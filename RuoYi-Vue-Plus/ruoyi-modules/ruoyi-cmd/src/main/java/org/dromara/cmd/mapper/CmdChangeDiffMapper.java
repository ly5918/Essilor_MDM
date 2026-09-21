package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdChangeDiff;
import org.dromara.cmd.domain.vo.CmdChangeDiffVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 变更字段差异明细 数据层 cmd_change_diff
 * <p>
 * 对应页面：变更与停用 change 的 Before / After 差异清单。
 *
 * @author Essilor CMD POC
 */
public interface CmdChangeDiffMapper extends BaseMapperPlus<CmdChangeDiff, CmdChangeDiffVo> {

}
