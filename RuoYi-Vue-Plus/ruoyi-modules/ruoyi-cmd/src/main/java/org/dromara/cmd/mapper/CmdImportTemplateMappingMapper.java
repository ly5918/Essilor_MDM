package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.CmdImportTemplateMapping;
import org.dromara.cmd.domain.vo.CmdImportTemplateMappingVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 导入模板字段映射 数据层 cmd_import_template_mapping
 * <p>
 * 对应页面：批量导入 batch 的字段映射展示。
 *
 * @author Essilor CMD POC
 */
public interface CmdImportTemplateMappingMapper extends BaseMapperPlus<CmdImportTemplateMapping, CmdImportTemplateMappingVo> {

}
