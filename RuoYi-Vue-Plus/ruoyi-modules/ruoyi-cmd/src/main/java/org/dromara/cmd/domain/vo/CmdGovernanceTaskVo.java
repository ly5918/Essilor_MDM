package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdGovernanceTask;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 治理任务视图对象 cmd_governance_task
 * <p>
 * 对应页面：治理任务 gov 指标卡下钻明细列表。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdGovernanceTask.class)
public class CmdGovernanceTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 任务编号 */
    @ExcelProperty(value = "任务编号")
    private String taskCode;

    /** 任务类型（SUSPECT / REVIEW / NEW / CROSS_BU） */
    @ExcelProperty(value = "任务类型")
    private String taskType;

    /** 业务主键 */
    private String bizId;

    /** 相关 One ID */
    private String oneId;

    /** 任务主题（客户名称） */
    @ExcelProperty(value = "任务主题")
    private String subject;

    /** 归属 BU */
    @ExcelProperty(value = "BU")
    private String buScope;

    /** 是否跨 BU */
    private String crossBuFlag;

    /** 风险等级 */
    @ExcelProperty(value = "风险")
    private String riskLevel;

    /** 匹配结论快照 */
    private String matchState;

    /** 匹配分 */
    private BigDecimal matchScore;

    /** 质量分 */
    private BigDecimal dqScore;

    /** 状态（OPEN / CLAIMED / RESOLVED / CLOSED / ESCALATED） */
    @ExcelProperty(value = "状态")
    private String status;

    /** 处理结论 */
    private String resolution;

    /** 当前处理人 */
    private Long assigneeId;

    /** 应完成时间 */
    private LocalDateTime dueTime;

    /** 治理证据（JSON 字符串） */
    private String evidenceJson;

    /** 创建时间 */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;
}
