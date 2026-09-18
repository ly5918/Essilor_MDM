package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户层级关系实体 cmd_hierarchy_relation
 * <p>
 * 对应页面：客户层级 hier —— 新增关系 / 编辑关系 / 增加子节点 / 发起申请。
 * 同一生效期内父节点唯一（uk_cmd_hier_rel）；跨 BU 关系（crossBuFlag=Y）需升级 GC Scope 审批。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_hierarchy_relation")
public class CmdHierarchyRelation extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 关系编号 */
    private String relationCode;

    /** 层级类型 */
    private String hierarchyType;

    /** 关系类型（A3_A2 / A2_A1 / MAIN_DOOR / PAYER_LINK） */
    private String relationType;

    /** 父节点 One ID */
    private String parentOneId;

    /** 子节点 One ID */
    private String childOneId;

    /** Payer One ID */
    private String payerOneId;

    /** 归属 BU */
    private String buScope;

    /** 是否跨 BU（Y：需 GC Scope 审批） */
    private String crossBuFlag;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 状态（Pending 待审批 / Effective 已生效 / Expired 已失效 / Rejected 已拒绝） */
    private String status;

    /** 变更原因 */
    private String changeReason;

    /** 来源（MANUAL 手工 / IMPORT 导入 / REQUEST 业务申请 / API） */
    private String sourceType;

    /** 申请人（业务申请场景） */
    private Long applicantId;

    /** 审批人 */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedTime;

    /** 关联流程实例 */
    private Long flowInstanceId;

    /** 关联待办 ID（cmd_approval_task.id） */
    private Long approvalId;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
