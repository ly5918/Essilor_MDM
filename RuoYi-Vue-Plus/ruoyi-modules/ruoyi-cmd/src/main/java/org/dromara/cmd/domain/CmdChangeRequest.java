package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户变更与逻辑停用申请 cmd_change_request
 * <p>
 * 对应页面：变更与停用 change —— 申请列表、提交变更、提交停用。
 * 设计约束：停用为「逻辑停用」，只改 status 与 effectiveTo，记录不物理删除。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_change_request")
public class CmdChangeRequest extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 申请编号 */
    private String requestCode;

    /** 客户 One ID */
    private String oneId;

    /** 客户名称（冗余，列表直接展示） */
    private String legalName;

    /** 变更类型（Update / Deactivate） */
    private String changeType;

    /** 目标状态（active / inactive / archived） */
    private String targetStatus;

    /** 是否关键字段变更（Y是 N否） */
    private String isKeyChange;

    /** 归属 BU */
    private String buScope;

    /** 变更原因 */
    private String changeReason;

    /** 计划生效日期 */
    private LocalDateTime effectiveDate;

    /** 审批状态（DRAFT / PENDING / APPROVED / REJECTED / EFFECTED） */
    private String status;

    /** 影响面分析（JSON 字符串） */
    private String impactJson;

    /** 关联关系校验结果（PASS / WARN / BLOCK） */
    private String relationCheck;

    /** 关联关系校验说明 */
    private String relationMsg;

    /** 关联 Warm-Flow 流程实例 */
    private Long flowInstanceId;

    /** 关联审批任务 */
    private Long approvalId;

    /** 审批人 */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedTime;

    /** 实际生效时间 */
    private LocalDateTime effectiveTime;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
