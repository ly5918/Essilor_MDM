package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdChangeRequest;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更与停用视图对象 cmd_change_request
 * <p>
 * 对应页面：变更与停用 change 列表行（申请编号 / 客户 / 类型 / BU / 原因 / 状态 / 生效日期）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdChangeRequest.class)
public class CmdChangeRequestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 申请编号 */
    @ExcelProperty(value = "申请编号")
    private String requestCode;

    /** 客户 One ID */
    @ExcelProperty(value = "One ID")
    private String oneId;

    /** 客户名称 */
    @ExcelProperty(value = "客户名称")
    private String legalName;

    /** 变更类型 */
    @ExcelProperty(value = "变更类型")
    private String changeType;

    /** 目标状态 */
    private String targetStatus;

    /** 是否关键字段变更 */
    private String isKeyChange;

    /** 归属 BU */
    @ExcelProperty(value = "归属BU")
    private String buScope;

    /** 变更原因 */
    @ExcelProperty(value = "变更原因")
    private String changeReason;

    /** 计划生效日期 */
    @ExcelProperty(value = "计划生效日期")
    private LocalDateTime effectiveDate;

    /** 审批状态 */
    @ExcelProperty(value = "状态")
    private String status;

    /** 关联关系校验结果 */
    private String relationCheck;

    /** 关联关系校验说明 */
    private String relationMsg;

    /** 实际生效时间 */
    private LocalDateTime effectiveTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @ExcelProperty(value = "申请时间")
    private LocalDateTime createTime;
}
