package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdApprovalAction;
import org.dromara.cmd.domain.vo.CmdApprovalActionVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 审批动作轨迹 数据层 cmd_approval_action
 * <p>
 * 对应页面：治理与审批 approval 详情轨迹、审计中心 audit。
 *
 * @author Essilor CMD POC
 */
public interface CmdApprovalActionMapper extends BaseMapperPlus<CmdApprovalAction, CmdApprovalActionVo> {

}
