package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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

    @TableField("rule_code")
    private String ruleCode;

    @TableField("rule_name")
    private String ruleName;

    @TableField("dimension")
    private String dimension;

    @TableField("role")
    private String role;

    @TableField("threshold")
    private String threshold;

    @TableField("result")
    private String result;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("version")
    private String version;

    @TableField("description")
    private String description;
}