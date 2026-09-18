package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdGovernanceTask;
import org.dromara.cmd.domain.vo.CmdGovernanceTaskVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 治理任务 数据层 cmd_governance_task
 * <p>
 * 对应页面：治理任务 gov 的 4 张指标卡与下钻列表。
 *
 * @author Essilor CMD POC
 */
public interface CmdGovernanceTaskMapper extends BaseMapperPlus<CmdGovernanceTask, CmdGovernanceTaskVo> {

}
