package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdChangeDiff;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 变更字段差异明细视图对象 cmd_change_diff
 * <p>
 * 对应页面：变更与停用 change 详情中的「Before / After」表格行。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdChangeDiff.class)
public class CmdChangeDiffVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 变更申请 ID */
    private Long requestId;

    /** 申请编号 */
    private String requestCode;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    @ExcelProperty(value = "字段")
    private String fieldName;

    /** 变更前值 */
    @ExcelProperty(value = "Before")
    private String beforeValue;

    /** 变更后值 */
    @ExcelProperty(value = "After")
    private String afterValue;

    /** 是否关键字段 */
    private String isKeyField;

    /** 是否敏感字段 */
    private String isSensitive;

    /** 变化类型（ADD / MODIFY / DELETE / SAME） */
    @ExcelProperty(value = "变化类型")
    private String changeFlag;

    /** 对质量分的影响 */
    private BigDecimal dqImpact;

    /** 显示顺序 */
    private Integer orderNum;

    /** 备注 */
    private String remark;
}
