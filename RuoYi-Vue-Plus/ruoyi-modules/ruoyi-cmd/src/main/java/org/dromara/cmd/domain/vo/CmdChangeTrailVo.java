package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更申请 审批轨迹行视图对象
 * <p>
 * 对应页面：变更与停用 change 详情弹窗的「审批轨迹」表
 * （谁 / 何时 / 做了什么 / 结论 / 意见）。
 * <p>
 * 数据来自 cmd_approval_action（业务视角审计轨迹），与 Warm-Flow 的
 * flow_his_task（引擎视角）互补，前者面向业务人员可读。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdChangeTrailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 动作发生时间 */
    private LocalDateTime time;

    /** 操作角色（Business User / BU Steward / GC Steward ...） */
    private String role;

    /** 操作人姓名 */
    private String operator;

    /** 动作名称（提交申请 / 审批通过 / 退回申请人 ...） */
    private String action;

    /** 节点（提交 → BU初审 → GC复核 → 结束） */
    private String node;

    /** 结论（Submitted / Approved / Rejected / Returned） */
    private String result;

    /** 审批意见 */
    private String opinion;
}
