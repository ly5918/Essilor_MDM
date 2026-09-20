package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 工作流步骤执行日志实体 cmd_workflow_step_log
 * <p>
 * 定位：客户视角的「步骤总账」。把提交申请、系统自动检查（OCR/DQ/查重）、
 * 人工决策（批准/拒绝/退回/升级）以及引擎节点推进的每一步都打上同一个
 * 客户主数据标识 {@code one_id}，从而让新建客户的 ID 在「所有页面 + 所有工作流步骤」
 * 中都能被关联与回溯。
 * <p>
 * 与 cmd_approval_action（业务审计链）、flow_his_task（引擎视角）互补：
 * 本表是三者中唯一以 one_id 为追溯主键、且覆盖「自动步骤 + 人工步骤 + 引擎步骤」的单一视图。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_workflow_step_log")
public class CmdWorkflowStepLog extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 客户主数据标识（One ID），全链路追溯主键 */
    private String oneId;

    /** 审批任务编号（AP-yyyyMMdd-####） */
    private String taskNo;

    /** Warm-Flow 流程实例 ID */
    private Long flowInstanceId;

    /** 步骤序号（同一 one_id 内从 1 递增） */
    private Integer stepSeq;

    /** 步骤类型：SUBMIT=提交 / SYSTEM=系统自动 / BUSINESS=人工决策 / ENGINE=引擎节点推进 */
    private String stepType;

    /** 节点编码（APPLY/INPUT/OCR/DQ/DUP/BU_REVIEW/GC_REVIEW/END） */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 动作类型（SUBMIT/APPROVE/REJECT/RETURN/ESCALATE） */
    private String actionType;

    /** 动作名称（提交申请/审批通过/审批拒绝/退回补充/升级GC） */
    private String actionName;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作角色（BU_STEWARD/GC_STEWARD/Business User） */
    private String operatorRole;

    /** 业务状态（操作前） */
    private String fromStatus;

    /** 业务状态（操作后） */
    private String toStatus;

    /** 审批意见 / 升级原因 */
    private String opinion;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
