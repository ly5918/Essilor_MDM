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
 * 匹配规则对象 match_rule
 *
 * @author Essilor CMD POC
 */
@Data
@NoArgsConstructor
@TableName("match_rule")
public class MatchRule implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 规则编码 */
    @TableField("rule_code")
    private String ruleCode;

    /** 规则名称 */
    @TableField("rule_name")
    private String ruleName;

    /** 作用模型 */
    @TableField("model_code")
    private String modelCode;

    /** 应用场景 CREATE / IMPORT / BATCH / MERGE */
    @TableField("scene")
    private String scene;

    /** 算法 WEIGHTED / EXACT / FUZZY / ML */
    @TableField("algorithm")
    private String algorithm;

    /** 标准化规则 */
    @TableField("normalize_rule")
    private String normalizeRule;

    /** Exact Match 阈值 */
    @TableField("exact_threshold")
    private BigDecimal exactThreshold;

    /** Suspected 阈值 */
    @TableField("suspect_threshold")
    private BigDecimal suspectThreshold;

    /** 超阈值自动合并 Y/N */
    @TableField("auto_merge_flag")
    private String autoMergeFlag;

    /** 参与跨 BU 匹配 Y/N */
    @TableField("cross_bu_flag")
    private String crossBuFlag;

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