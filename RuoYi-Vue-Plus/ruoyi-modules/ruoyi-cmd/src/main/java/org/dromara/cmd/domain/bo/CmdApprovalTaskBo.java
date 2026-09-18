package org.dromara.cmd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdApprovalTask;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一待办 / 审批任务业务对象（查询入参）cmd_approval_task
 * <p>
 * 对应治理与审批页的筛选行：任务类型 / BU / SLA / 风险 / 关键字。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdApprovalTask.class, reverseConvertGenerate = false)
public class CmdApprovalTaskBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 任务编号 */
    private String taskNo;

    /** 任务分类（APPROVAL / GOVERNANCE / RETURNED / DONE） */
    private String taskCategory;

    /** 业务类型（CUSTOMER_CREATE / CUSTOMER_CHANGE / ...） */
    private String bizType;

    /** 业务主键 */
    private String bizId;

    /** 业务标题（关键字模糊匹配） */
    private String bizTitle;

    /** 关联 One ID */
    private String oneId;

    /** 业务场景编码 */
    private String sceneCode;

    /** 归属 BU */
    private String buScope;

    /** 审批 Scope（BU / GC / CROSS_BU） */
    private String scope;

    /** 当前处理人（我的队列时传当前登录人） */
    private Long assigneeId;

    /** 状态（PENDING / APPROVED / REJECTED / ...） */
    private String status;

    /** 风险等级（High / Medium / Low） */
    private String riskLevel;

    /** 重复状态（EXACT / SUSPECTED / NEW / NONE） */
    private String duplicateState;

    /** 是否跨 BU */
    private String crossBuFlag;

    /** SLA 状态（NORMAL / DUE_SOON / OVERDUE） */
    private String slaState;

    /** 关键字（同时匹配 taskNo / bizTitle / oneId） */
    private String keyword;

    /** 请求参数（支持 beginTime / endTime 等动态条件） */
    private Map<String, Object> params = new HashMap<>();
}
