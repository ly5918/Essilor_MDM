package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.MdField;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可变更字段 视图对象
 * <p>
 * 对应页面：变更与停用 change「发起属性变更」弹窗的字段选择器。
 * 数据来源为 md_field（平台管理 → 字段与值集 维护），因此「哪些字段可变更、
 * 哪些字段属关键属性需更高级别审批、哪些字段敏感需要额外留痕」全部由配置决定，
 * 前端与服务端共用同一份口径，不在代码里硬编码字段清单。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = MdField.class)
public class CmdChangeFieldVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字段编码（提交变更时回传） */
    private String fieldCode;

    /** 字段名称（下拉展示） */
    private String fieldName;

    /** 数据类型（STRING / ENUM / DATE / NUMBER ...），决定输入控件形态 */
    private String dataType;

    /** 值集编码（ENUM 类型使用） */
    private String valueSetCode;

    /** 是否必填（Y / N） */
    private String isRequired;

    /** 是否关键字段（Y：变更需更高级别审批） */
    private String isKeyField;

    /** 是否敏感字段（Y：变更需额外留痕） */
    private String isSensitive;

    /** 长度上限（文本类型校验用） */
    private Integer maxLength;

    /** 正则校验表达式 */
    private String regexPattern;

    /** 物理列名（服务端据此写回主档） */
    private String physicalColumn;

    /** 显示顺序 */
    private Integer orderNum;
}
