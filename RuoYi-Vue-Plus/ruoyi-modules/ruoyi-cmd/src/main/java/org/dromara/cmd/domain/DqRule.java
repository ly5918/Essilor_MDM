package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DQ规则对象 dq_rule
 *
 * @author Essilor CMD POC
 */
@Data
@NoArgsConstructor
@TableName("dq_rule")
public class DqRule implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 规则编码 */
    @TableField("rule_code")
    private String ruleCode;

    /** 规则名称 */
    @TableField("rule_name")
    private String ruleName;

    /** 规则类型 TECHNIICAL / BUSINESS */
    @TableField("rule_type")
    private String ruleType;

    /** 质量维度 COMPLETENESS / VALIDITY / CONSISTENCY / UNIQUENESS / TIMELINESS */
    @TableField("dimension")
    private String dimension;

    /** 作用模型 */
    @TableField("model_code")
    private String modelCode;

    /** 主校验字段 */
    @TableField("field_code")
    private String fieldCode;

    /** 校验类型 NOT_NULL / REGEX / LENGTH / RANGE / ENUM / UNIQUE / CROSS_FIELD / CUSTOM */
    @TableField("check_type")
    private String checkType;

    /** 校验表达式 */
    @TableField("expression")
    private String expression;

    /** 严重级别 ERROR / WARNING / INFO */
    @TableField("severity")
    private String severity;

    /** 扣分权重 */
    @TableField("score_weight")
    private BigDecimal scoreWeight;

    /** 失败提示文案 */
    @TableField("error_message")
    private String errorMessage;

    /** 规则版本号 */
    @TableField("version_no")
    private String versionNo;

    /** 状态 0草稿 1已发布 2已停用 */
    @TableField("status")
    private String status;

    /** 是否系统预置 */
    @TableField("is_preset")
    private String isPreset;

    /** 显示顺序 */
    @TableField("order_num")
    private Integer orderNum;

    /** 备注 */
    @TableField("remark")
    private String remark;

    @TableLogic
    @TableField("del_flag")
    private String delFlag;
}