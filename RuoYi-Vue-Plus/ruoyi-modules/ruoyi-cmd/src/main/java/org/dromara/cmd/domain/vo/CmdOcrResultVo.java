package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * OCR 字段识别结果视图对象
 * <p>
 * 对应「OCR识别结果」弹窗底部字段表格：字段 / 识别值 / 置信度。
 * <p>
 * 注意：<code>field</code> 必须与客户模型元数据 <code>md_field.field_name</code> 完全一致，
 * 前端（NewCustomerDialog.onApplyOcr）按字段名匹配动态字段并回填表单；
 * <code>code</code> 为元数据字段编码 <code>md_field.field_code</code>，供表单提交时回写主档。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdOcrResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字段编码（元数据 field_code） */
    private String code;

    /** 字段名称（元数据 field_name，前端按此名称匹配动态字段） */
    private String field;

    /** 识别值 */
    private String value;

    /** 置信度（百分数，如 98%） */
    private String confidence;
}
