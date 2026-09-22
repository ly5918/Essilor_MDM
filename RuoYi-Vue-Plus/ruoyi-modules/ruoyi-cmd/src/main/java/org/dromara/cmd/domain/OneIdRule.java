package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * One ID 规则 oneid_rule
 * <p>
 * 对应页面：One ID 规则管理（命名规则、自动生成、稳定性策略）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oneid_rule")
public class OneIdRule extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 编码模式（页面 pattern） */
    private String pattern;

    /** 前缀 */
    private String prefix;

    /** 分隔符 */
    @TableField("`separator`")
    private String separator;

    /** 流水号长度 */
    private Integer serialLength;

    /** 序列编码 */
    private String seqCode;

    /** 生成策略 */
    private String genStrategy;

    /** 稳定性策略 */
    private String stablePolicy;

    /** 复用策略 */
    private String reusePolicy;

    /** 作用范围 */
    private String scopeType;

    /** 状态（0发布 1草稿） */
    private String status;

    /** 是否默认规则 */
    private String isDefault;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
