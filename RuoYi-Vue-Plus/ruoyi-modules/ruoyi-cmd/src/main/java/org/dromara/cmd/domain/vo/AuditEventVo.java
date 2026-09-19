package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.AuditEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计事件视图对象 audit_event
 * <p>
 * 对应页面：审计中心 audit 列表行（时间 / 事件 / 角色 / 结果）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AuditEvent.class)
public class AuditEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 事件编号 */
    @ExcelProperty(value = "事件编号")
    private String eventId;

    /** 事件类型 */
    private String eventType;

    /** 事件名称（页面"事件"列） */
    @ExcelProperty(value = "事件")
    private String eventName;

    /** 业务类型 */
    private String bizType;

    /** 业务主键 */
    private String bizId;

    /** 关联 One ID */
    @ExcelProperty(value = "One ID")
    private String oneId;

    /** 操作人 */
    @ExcelProperty(value = "操作人")
    private String operatorName;

    /** 操作角色（页面"角色"列） */
    @ExcelProperty(value = "角色")
    private String operatorRole;

    /** 事件发生时间（页面"时间"列） */
    @ExcelProperty(value = "时间")
    private LocalDateTime eventTime;

    /** 结果 */
    @ExcelProperty(value = "结果")
    private String result;

    /** 风险等级 */
    private String riskLevel;

    /** 备注 */
    private String remark;
}
