package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 序列号生成规则 cfg_sequence
 * <p>
 * 用于 One ID、申请编号、批次号等原子递增编码。
 *
 * @author Essilor CMD POC
 */
@Data
@TableName("cfg_sequence")
public class CfgSequence {

    @TableId(value = "id")
    private Long id;

    /** 序列编码 */
    private String seqCode;

    /** 序列名称 */
    private String seqName;

    /** 前缀 */
    private String prefix;

    /** 日期模式（yyyyMMdd / yyyyMM 等，NULL 表示不拼接日期） */
    private String datePattern;

    /** 流水长度 */
    private Integer serialLength;

    /** 步长 */
    private Integer step;

    /** 初始值 */
    private Long initValue;

    /** 当前值 */
    private Long currentValue;

    /** 循环类型（NONE / DAY / MONTH 等） */
    private String cycleType;

    /** 分隔符（separator 为 MySQL 保留字，需反引号转义） */
    @TableField("`separator`")
    private String separator;

    /** 后缀 */
    private String suffix;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注 */
    private String remark;

    /** 扩展属性 */
    private String extJson;
}
