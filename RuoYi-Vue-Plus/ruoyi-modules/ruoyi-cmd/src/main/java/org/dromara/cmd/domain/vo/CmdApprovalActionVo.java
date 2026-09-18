package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdApprovalAction;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批动作轨迹视图对象 cmd_approval_action
 * <p>
 * 对应页面：治理与审批 approval 详情弹窗的审批轨迹时间线。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdApprovalAction.class)
public class CmdApprovalActionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 待办任务 ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 动作类型 */
    @ExcelProperty(value = "动作类型")
    private String actionType;

    /** 动作名称 */
    @ExcelProperty(value = "动作")
    private String actionName;

    /** 源节点编码 */
    private String fromNodeCode;

    /** 目标节点编码 */
    private String toNodeCode;

    /** 操作人 */
    private Long operatorId;

    /** 操作人姓名 */
    @ExcelProperty(value = "操作人")
    private String operatorName;

    /** 操作角色 */
    private String operatorRole;

    /** 操作时间 */
    @ExcelProperty(value = "操作时间")
    private LocalDateTime actionTime;

    /** 审批意见 */
    @ExcelProperty(value = "审批意见")
    private String opinion;

    /** 操作前状态 */
    private String beforeState;

    /** 操作后状态 */
    private String afterState;

    /** 创建时间 */
    private LocalDateTime createTime;
}
