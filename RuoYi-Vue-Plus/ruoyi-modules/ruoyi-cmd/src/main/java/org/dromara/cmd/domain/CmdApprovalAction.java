package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 审批动作轨迹实体 cmd_approval_action
 * <p>
 * 对应页面：治理与审批 approval 详情轨迹、审计中心 audit、客户变更 change 审批轨迹。
 * 与 Warm-Flow 的 flow_his_task 互补：flow_his_task 是引擎视角，本表是业务视角。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_approval_action")
public class CmdApprovalAction extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 待办任务 ID（cmd_approval_task.id） */
    private Long taskId;

    /** 任务编号（冗余） */
    private String taskNo;

    /** 动作类型（SUBMIT / APPROVE / REJECT / RETURN / ESCALATE / TRANSFER / CLAIM / MERGE ...） */
    private String actionType;

    /** 动作名称（展示用） */
    private String actionName;

    /** 源节点编码 */
    private String fromNodeCode;

    /** 目标节点编码 */
    private String toNodeCode;

    /** 操作人 */
    private Long operatorId;

    /** 操作人姓名（冗余） */
    private String operatorName;

    /** 操作角色（BU_STEWARD / GC_STEWARD） */
    private String operatorRole;

    /** 操作时间 */
    private LocalDateTime actionTime;

    /** 审批意见 */
    private String opinion;

    /** 附件 ID 列表（逗号分隔） */
    private String attachIds;

    /** 操作前状态 */
    private String beforeState;

    /** 操作后状态 */
    private String afterState;

    /** 操作证据（决策依据快照，JSON 字符串） */
    private String evidenceJson;

    /** 关联 Warm-Flow 任务 ID */
    private Long flowTaskId;

    /** 关联 Warm-Flow 历史任务 ID */
    private Long flowHisId;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
