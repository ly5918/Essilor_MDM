package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdApprovalTask;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一待办 / 审批任务视图对象 cmd_approval_task
 * <p>
 * 对应页面：治理与审批 approval 队列表列
 * （申请编号 / 客户主题 / 类型 / BU / DQ / Duplicate / SLA / 风险）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdApprovalTask.class)
public class CmdApprovalTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 任务编号（页面"申请编号"列） */
    @ExcelProperty(value = "申请编号")
    private String taskNo;

    /** 任务分类（APPROVAL / GOVERNANCE / RETURNED / DONE） */
    private String taskCategory;

    /** 业务类型 */
    @ExcelProperty(value = "类型")
    private String bizType;

    /** 业务主键 */
    private String bizId;

    /** 业务标题（页面"客户主题"列） */
    @ExcelProperty(value = "客户主题")
    private String bizTitle;

    /** 关联 One ID */
    private String oneId;

    /** 业务场景编码 */
    private String sceneCode;

    /** 申请人 */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 归属 BU */
    @ExcelProperty(value = "BU")
    private String buScope;

    /** 审批 Scope */
    private String scope;

    /** 当前节点编码 */
    private String currentNodeCode;

    /** 当前节点名称 */
    private String currentNodeName;

    /** 当前处理人 */
    private Long assigneeId;

    /** 当前处理人姓名 */
    private String assigneeName;

    /** 当前处理角色 */
    private String assigneeRole;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;

    /** 风险等级 */
    @ExcelProperty(value = "风险")
    private String riskLevel;

    /** 质量分（页面 DQ 列） */
    @ExcelProperty(value = "DQ")
    private BigDecimal dqScore;

    /** 重复状态（页面 Duplicate 列） */
    @ExcelProperty(value = "Duplicate")
    private String duplicateState;

    /** 是否跨 BU */
    private String crossBuFlag;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** SLA 应完成时间 */
    private LocalDateTime slaDue;

    /** SLA 状态（页面 SLA 列） */
    @ExcelProperty(value = "SLA")
    private String slaState;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 实际处理时长（小时） */
    private BigDecimal durationHours;

    /** 最新审批意见 */
    private String opinion;

    /** 证据快照（JSON 字符串） */
    private String evidenceJson;

    /** 业务数据快照（JSON 字符串） */
    private String bizSnapshotJson;

    /** Warm-Flow 流程实例 ID */
    private Long flowInstanceId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
