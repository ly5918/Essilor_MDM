package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdImportJob;
import org.dromara.cmd.domain.vo.CmdImportJobVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 批量导入任务 数据层 cmd_import_job
 * <p>
 * 对应页面：批量导入 batch 的任务列表与结果分流。
 *
 * @author Essilor CMD POC
 */
public interface CmdImportJobMapper extends BaseMapperPlus<CmdImportJob, CmdImportJobVo> {

}
