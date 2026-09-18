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
 * 治理任务实体 cmd_governance_task
 * <p>
 * 对应页面：治理任务 gov —— 4 张指标卡（Suspect / Review / New / Cross-BU）与下钻明细；
 * 治理与审批 approval —— 治理复核 Tab。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_governance_task")
public class CmdGovernanceTask extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 任务编号 */
    private String taskCode;

    /** 任务类型（SUSPECT 疑似重复 / REVIEW 待复核 / NEW 新建确认 / CROSS_BU 跨BU决策） */
    private String taskType;

    /** 业务类型 */
    private String bizType;

    /** 业务主键 */
    private String bizId;

    /** 相关 One ID */
    private String oneId;

    /** 任务主题（客户名称） */
    private String subject;

    /** 归属 BU */
    private String buScope;

    /** 是否跨 BU（Y：需 GC Scope） */
    private String crossBuFlag;

    /** 风险等级（High / Medium / Low） */
    private String riskLevel;

    /** 关联匹配结果（cmd_match_result.id） */
    private Long matchResultId;

    /** 匹配结论快照 */
    private String matchState;

    /** 匹配分 */
    private BigDecimal matchScore;

    /** 质量分 */
    private BigDecimal dqScore;

    /** 优先级（数字越大越优先） */
    private Integer priority;

    /** 状态（OPEN / CLAIMED / RESOLVED / CLOSED / ESCALATED） */
    private String status;

    /** 处理结论（LINK_EXISTING / CREATE_NEW / EXCLUDE / MERGE / RETURN） */
    private String resolution;

    /** 当前处理人 */
    private Long assigneeId;

    /** 认领时间 */
    private LocalDateTime claimedTime;

    /** 解决时间 */
    private LocalDateTime resolvedTime;

    /** 应完成时间（SLA） */
    private LocalDateTime dueTime;

    /** 关联流程实例（flow_instance.id） */
    private Long flowInstanceId;

    /** 治理证据（候选对比、规则命中，JSON 字符串） */
    private String evidenceJson;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
