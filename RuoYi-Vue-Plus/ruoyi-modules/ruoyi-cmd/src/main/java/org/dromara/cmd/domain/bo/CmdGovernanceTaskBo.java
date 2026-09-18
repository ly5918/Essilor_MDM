package org.dromara.cmd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdGovernanceTask;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 治理任务业务对象（查询入参）cmd_governance_task
 * <p>
 * 对应页面：治理任务 gov —— 4 张指标卡下钻与任务列表筛选。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdGovernanceTask.class, reverseConvertGenerate = false)
public class CmdGovernanceTaskBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 任务编号 */
    private String taskCode;

    /** 任务类型（SUSPECT / REVIEW / NEW / CROSS_BU） */
    private String taskType;

    /** 业务主键 */
    private String bizId;

    /** 相关 One ID */
    private String oneId;

    /** 任务主题（模糊匹配） */
    private String subject;

    /** 归属 BU */
    private String buScope;

    /** 是否跨 BU */
    private String crossBuFlag;

    /** 风险等级 */
    private String riskLevel;

    /** 匹配结论 */
    private String matchState;

    /** 状态（OPEN / CLAIMED / RESOLVED / CLOSED / ESCALATED） */
    private String status;

    /** 当前处理人 */
    private Long assigneeId;

    /** 请求参数（支持 beginTime / endTime 等动态条件） */
    private Map<String, Object> params = new HashMap<>();
}
