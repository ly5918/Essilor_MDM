package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 导入模板字段映射 cmd_import_template_mapping
 * <p>
 * 对应页面：批量导入 batch 的模板字段映射展示（源列 / 目标字段 / 转换规则 / 错误策略）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_import_template_mapping")
public class CmdImportTemplateMapping extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** 模板编码 */
    private String templateCode;

    /** 列序号 */
    private Integer columnIndex;

    /** 列名（源列） */
    private String columnName;

    /** 目标字段编码 */
    private String fieldCode;

    /** 目标字段名称 */
    private String fieldName;

    /** 数据类型 */
    private String dataType;

    /** 是否必填（Y是 N否） */
    private String isRequired;

    /** 默认值 */
    private String defaultValue;

    /** 转换规则（页面 transform） */
    private String convertRule;

    /** 值集编码 */
    private String valueSetCode;

    /** 排序号 */
    private Integer orderNum;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
