package org.dromara.cmd.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批动作入参（批准 / 拒绝 / 退回 / 升级 / 认领 / 转办 ...）
 * <p>
 * 对应页面：治理与审批 approval —— 详情弹窗中的决策按钮。
 *
 * @author Essilor CMD POC
 */
@Data
public class ApprovalActionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待办任务 ID（cmd_approval_task.id） */
    @NotNull(message = "待办任务ID不能为空")
    private Long taskId;

    /** 动作类型（APPROVE / REJECT / RETURN / ESCALATE / CLAIM / TRANSFER / MERGE ...） */
    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    /** 审批意见（拒绝与退回时必填） */
    private String opinion;

    /** 转办目标人（actionType=TRANSFER 时必填） */
    private Long targetUserId;

    /** 附件 ID 列表（逗号分隔） */
    private String attachIds;

    /** 扩展参数（如治理决策的 mergeTargetOneId / linkOneId 等） */
    private String extJson;
}
