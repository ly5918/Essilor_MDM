package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 流程实例记录视图对象（流程中心「流程实例记录」列表）
 * <p>
 * 对应参考页 Active / Completed Workflows 列表：每一次执行过的工作流都留一条记录，
 * 可查看进度并打开泳道图（Graph）回看当时的流转情况。
 * <p>
 * 数据来源：cmd_approval_task（业务侧镜像），Warm-Flow 实例进度以
 * flow_instance_id / flow_status 字段透出，不直接查 flow_* 表（引擎解耦约定）。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdFlowInstanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待办任务主键 */
    private Long id;

    /** 申请编号（对应参考页 Label 列） */
    private String taskNo;

    /**
     * 客户主数据标识（One ID）：贯穿全部页面与工作流的贯通 ID，
     * 与 cmd_customer.one_id 同源，可按此 ID 反查客户主档与全部工作流记录。
     */
    private String oneId;

    /** 业务标题（对应参考页 Description 列） */
    private String bizTitle;

    /** 业务类型（中文，如「客户创建」） */
    private String bizType;

    /** 场景编码（归一后，如 CUSTOMER_CREATE） */
    private String sceneCode;

    /** 场景名称（对应参考页 Parent workflow 列） */
    private String sceneName;

    /** Warm-Flow 流程名称 */
    private String flowName;

    /** Warm-Flow 流程编码 */
    private String flowCode;

    /** Warm-Flow 流程实例 ID（为空表示尚未启动引擎实例） */
    private Long flowInstanceId;

    /** 是否已启动 Warm-Flow 实例 */
    private Boolean engineBound = Boolean.FALSE;

    /** 发起人（对应参考页 Creator 列） */
    private String applicantName;

    /** 当前处理人 */
    private String assigneeName;

    /** 当前处理角色 */
    private String assigneeRole;

    /** 优先级（取风险等级 High / Medium / Low，对应参考页 Priority 列） */
    private String priority;

    /** 任务状态（PENDING / APPROVED / REJECTED / RETURNED / ESCALATED / CANCELLED） */
    private String status;

    /** 当前节点名称 */
    private String currentNodeName;

    /** 已完成步骤数（泳道图口径） */
    private Integer completedSteps;

    /** 总步骤数（泳道图口径） */
    private Integer totalSteps;

    /** 进度百分比 */
    private Integer progressPercent;

    /** SLA 状态（NORMAL / DUE_SOON / OVERDUE） */
    private String slaState;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 处理时长（小时） */
    private BigDecimal durationHours;

    /** 创建时间（对应参考页 Creation date 列） */
    private LocalDateTime createTime;
}
