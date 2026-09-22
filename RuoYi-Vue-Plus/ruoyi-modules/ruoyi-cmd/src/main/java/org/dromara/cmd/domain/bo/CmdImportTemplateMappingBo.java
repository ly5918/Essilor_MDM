package org.dromara.cmd.domain.bo;

import lombok.Data;
import org.dromara.cmd.domain.CmdImportTemplateMapping;

import java.io.Serial;
import java.io.Serializable;

/**
 * 导入模板字段映射保存对象 cmd_import_template_mapping
 * <p>
 * 对应页面：平台管理 › 导入Template › 字段映射管理（新增上传字段 / 编辑转换规则与必填）。
 * id 为空 = 新增列；id 非空 = 更新列（仅允许改展示/校验相关字段，模板编码与列序由后端控制）。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdImportTemplateMappingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（为空 = 新增） */
    private Long id;

    /** 模板编码（新增时必填） */
    private String templateCode;

    /** 源列（上传文件的表头名，新增时必填） */
    private String columnName;

    /** 目标字段编码（新增时必填，模板内唯一，建议小写下划线） */
    private String fieldCode;

    /** 目标字段名称 */
    private String fieldName;

    /** 数据类型（Text / Number / Date，新增时必填） */
    private String dataType;

    /** 是否必填（Y是 N否） */
    private String isRequired;

    /** 默认值（上传该列缺失时的兜底值） */
    private String defaultValue;

    /** 转换规则（如 Trim / Upper / DateFormat(yyyy-MM-dd)，页面展示用） */
    private String convertRule;

    /** 备注 */
    private String remark;

    /**
     * 复制可编辑字段到已有实体（更新用）：模板编码 / 列序 / 字段编码不属于可编辑范围，
     * 由后端在新增时生成并锁定。
     */
    public void applyEditableTo(CmdImportTemplateMapping entity) {
        entity.setColumnName(this.columnName);
        entity.setFieldName(this.fieldName);
        entity.setDataType(this.dataType);
        entity.setIsRequired(this.isRequired);
        entity.setDefaultValue(this.defaultValue);
        entity.setConvertRule(this.convertRule);
        entity.setRemark(this.remark);
    }
}
