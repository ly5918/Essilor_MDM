package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一待办 / 审批任务实体 cmd_approval_task
 * <p>
 * 对应页面：治理与审批 approval（原型 2.2「治理与审批合并版」的 5 个 Tab 全部由本表支撑）
 * <ul>
 *   <li>我的队列 / 我已处理 / 升级与退回 —— 通过 taskCategory + status 组合筛选</li>
 *   <li>工作台 dash —— 待办数量、SLA 超时统计</li>
 * </ul>
 * 设计说明：审批与治理复核合并为单表，用 taskCategory 区分，
 * 通过 flowInstanceId / flowTaskId 与 Warm-Flow 流程实例关联（业务表与流程引擎解耦）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_approval_task")
public class CmdApprovalTask extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 任务编号（页面"申请编号"列） */
    private String taskNo;

    /** 任务分类（APPROVAL 审批 / GOVERNANCE 治理复核 / RETURNED 升级退回 / DONE 我已处理） */
    private String taskCategory;

    /** 业务类型（CUSTOMER_CREATE / CUSTOMER_CHANGE / DEACTIVATE / HIER_RELATION / IMPORT_BATCH / MERGE） */
    private String bizType;

    /** 业务主键 */
    private String bizId;

    /** 业务标题（客户主题） */
    private String bizTitle;

    /** 关联 One ID */
    private String oneId;

    /** 业务场景（cmd_flow_scene.scene_code） */
    private String sceneCode;

    /** 申请人 */
    private Long applicantId;

    /** 申请人姓名（冗余） */
    private String applicantName;

    /** 归属 BU（数据权限维度） */
    private String buScope;

    /** 审批 Scope（BU / GC / CROSS_BU） */
    private String scope;

    /** 当前流程节点编码 */
    private String currentNodeCode;

    /** 当前节点名称（页面"当前节点"） */
    private String currentNodeName;

    /** 当前处理人 */
    private Long assigneeId;

    /** 当前处理人姓名（冗余） */
    private String assigneeName;

    /** 当前处理角色（BU_STEWARD / GC_STEWARD / ...） */
    private String assigneeRole;

    /** 状态（DRAFT / PENDING / APPROVED / REJECTED / RETURNED / ESCALATED / CANCELLED / COMPLETED） */
    private String status;

    /** 风险等级（High / Medium / Low） */
    private String riskLevel;

    /** 质量分（页面 DQ 列） */
    private BigDecimal dqScore;

    /** 重复状态（页面 Duplicate 列：EXACT / SUSPECTED / NEW / NONE） */
    private String duplicateState;

    /** 是否跨 BU */
    private String crossBuFlag;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** SLA 应完成时间 */
    private LocalDateTime slaDue;

    /** SLA 状态（NORMAL 正常 / DUE_SOON 临近 / OVERDUE 已超时） */
    private String slaState;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 实际处理时长（小时） */
    private BigDecimal durationHours;

    /** 最新审批意见 */
    private String opinion;

    /** 证据快照（自动检查结果 / 治理证据 / Before-After，JSON 字符串） */
    private String evidenceJson;

    /** 业务数据快照（避免二次查询，JSON 字符串） */
    private String bizSnapshotJson;

    /** Warm-Flow 流程实例 ID（flow_instance.id） */
    private Long flowInstanceId;

    /** Warm-Flow 待办任务 ID（flow_task.id） */
    private Long flowTaskId;

    /** 流程定义 ID（flow_definition.id） */
    private Long flowDefinitionId;

    /** 流程状态镜像（0待提交 1审批中 2通过 4终止 5作废 6撤销 8完成 9退回） */
    private String flowStatus;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
