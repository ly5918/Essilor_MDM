package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdApprovalTask;
import org.dromara.cmd.domain.vo.CmdApprovalTaskVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 统一待办 / 审批任务 数据层 cmd_approval_task
 * <p>
 * 对应页面：治理与审批 approval 的 5 个 Tab 全部基于本表查询。
 *
 * @author Essilor CMD POC
 */
public interface CmdApprovalTaskMapper extends BaseMapperPlus<CmdApprovalTask, CmdApprovalTaskVo> {

}
