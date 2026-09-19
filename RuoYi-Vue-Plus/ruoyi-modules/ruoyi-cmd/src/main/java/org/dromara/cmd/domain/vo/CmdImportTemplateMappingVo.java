package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdImportTemplateMapping;

import java.io.Serial;
import java.io.Serializable;

/**
 * 导入模板字段映射视图对象 cmd_import_template_mapping
 * <p>
 * 对应页面：批量导入 batch 的字段映射表（源列 / 目标字段 / 转换规则 / 错误策略）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdImportTemplateMapping.class)
public class CmdImportTemplateMappingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 源列（列名） */
    @ExcelProperty(value = "源列")
    private String columnName;

    /** 目标字段编码 */
    @ExcelProperty(value = "目标字段")
    private String fieldCode;

    /** 目标字段名称 */
    private String fieldName;

    /** 转换规则 */
    @ExcelProperty(value = "转换规则")
    private String convertRule;

    /** 错误策略（必填→Reject Row，非必填→Warning Row） */
    @ExcelProperty(value = "错误策略")
    private String errorStrategy;

    /** 是否必填 */
    private String isRequired;

    /** 排序号 */
    private Integer orderNum;
}
