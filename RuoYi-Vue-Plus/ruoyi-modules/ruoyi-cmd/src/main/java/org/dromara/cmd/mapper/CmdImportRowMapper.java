package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdImportRow;
import org.dromara.cmd.domain.vo.CmdImportRowVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 导入行明细 数据层 cmd_import_row
 * <p>
 * 对应页面：批量导入 batch 的结果分流下钻「查看 N 条」。
 * 写入时机：上传 Excel 后由 CmdImportServiceImpl 逐行落库。
 *
 * @author Essilor CMD POC
 */
public interface CmdImportRowMapper extends BaseMapperPlus<CmdImportRow, CmdImportRowVo> {

}
