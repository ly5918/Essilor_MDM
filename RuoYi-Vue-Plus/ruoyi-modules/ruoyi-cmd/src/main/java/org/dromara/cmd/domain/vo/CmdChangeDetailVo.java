package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 变更与停用 详情视图对象
 * <p>
 * 对应页面：变更与停用 change 详情弹窗，1:1 覆盖泳道图「关键客户属性变更」与
 * 「客户逻辑停用」两条链路在审批前需要看到的全部证据：
 * <ol>
 *   <li>申请基本信息（编号 / One ID / 客户 / 类型 / BU / 原因 / 状态 / 生效日期）</li>
 *   <li>Before / After 字段级差异清单（cmd_change_diff）</li>
 *   <li>关联关系影响检查结论（A3-A2-A1 / Payer / 跨 BU 依赖）</li>
 *   <li>影响面清单（人可读的影响说明，由 impact_json 与实时检查合并）</li>
 *   <li>审批轨迹（cmd_approval_action）</li>
 *   <li>主档版本上下文（当前版本号，用于证明「换版本不换 One ID」）</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdChangeDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 申请编号 */
    private String requestCode;

    /** 客户 One ID */
    private String oneId;

    /** 客户名称 */
    private String legalName;

    /** 变更类型（Update 属性变更 / Deactivate 逻辑停用） */
    private String changeType;

    /** 目标状态（active / inactive / archived） */
    private String targetStatus;

    /** 是否关键属性变更（Y / N） */
    private String isKeyChange;

    /** 归属 BU */
    private String buScope;

    /** 变更原因 */
    private String changeReason;

    /** 审批状态（DRAFT / PENDING / APPROVED / REJECTED / EFFECTIVE / CANCELLED） */
    private String status;

    /** 计划生效日期 */
    private LocalDateTime effectiveDate;

    /** 实际生效时间 */
    private LocalDateTime effectiveTime;

    /** 关联关系影响检查结果（PASS / WARN / FAIL） */
    private String relationCheck;

    /** 关联关系影响检查说明 */
    private String relationMsg;

    /** 关联审批待办编号（cmd_approval_task.task_no） */
    private String approvalTaskNo;

    /** 关联流程实例 ID */
    private Long flowInstanceId;

    /** 备注 */
    private String remark;

    /** 申请提交时间 */
    private LocalDateTime createTime;

    /** 审批人姓名 */
    private String approvedByName;

    /** 审批时间 */
    private LocalDateTime approvedTime;

    /** 主档当前版本号（生效前 / 生效后对照，One ID 不变、版本号递增） */
    private Integer currentVersionNo;

    /** 生效后的主档版本号（未生效时为 null） */
    private Integer effectiveVersionNo;

    /** Before / After 字段级差异清单 */
    private List<CmdChangeDiffVo> diffs = new ArrayList<>();

    /** 审批轨迹（谁 / 何时 / 做了什么 / 结论） */
    private List<CmdChangeTrailVo> trail = new ArrayList<>();

    /** 影响面清单（人可读，逐条说明本次变更波及的层级、关系与下游） */
    private List<String> impacts = new ArrayList<>();

    /** 版本历史（该 One ID 的版本快照，证明历史版本全部保留） */
    private List<CmdCustomerVersionVo> versions = new ArrayList<>();
}
