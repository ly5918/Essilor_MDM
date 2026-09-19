package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户新建申请提交结果视图对象
 * <p>
 * 对应页面：「新建客户申请」弹窗点击「提交申请」后的回执，
 * 前端据此提示 One ID / 申请编号 / 当前节点，并可在「治理与审批 → 流程跟踪」查看引擎进度。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdCustomerSubmitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户主档主键 */
    private Long customerId;

    /** One ID（由服务端生成，后续不可变更） */
    private String oneId;

    /** 申请编号（cmd_approval_task.task_no） */
    private String taskNo;

    /** 业务场景编码（CUSTOMER_CREATE） */
    private String sceneCode;

    /** 业务场景名称（客户创建） */
    private String sceneName;

    /** 客户状态（draft / pending ...） */
    private String status;

    /** 待办状态（PENDING / ...） */
    private String taskStatus;

    /** 当前审批节点编码（引擎镜像） */
    private String currentNodeCode;

    /** 当前审批节点名称（页面「当前节点」） */
    private String currentNodeName;

    /** 当前处理角色（BU_STEWARD / GC_STEWARD） */
    private String assigneeRole;

    /** 质量分（提交时自动检查结果） */
    private BigDecimal dqScore;

    /** 风险等级（High / Medium / Low） */
    private String riskLevel;

    /** SLA 应完成时间 */
    private LocalDateTime slaDue;

    /** Warm-Flow 流程实例 ID */
    private Long flowInstanceId;

    /** 流程状态镜像（1审批中 2通过 8完成 ...） */
    private String flowStatus;
}
