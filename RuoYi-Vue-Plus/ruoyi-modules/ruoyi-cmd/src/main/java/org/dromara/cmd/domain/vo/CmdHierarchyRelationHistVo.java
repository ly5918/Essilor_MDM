package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdHierarchyRelationHist;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户层级关系历史视图对象 cmd_hierarchy_relation_hist
 * <p>
 * 对应页面：客户层级 hier 右侧「关系历史」时间线 —— 每个版本一行，回答
 * 「这条关系什么时候建的、改过几次、以前挂在谁下面」。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdHierarchyRelationHist.class)
public class CmdHierarchyRelationHistVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关系 ID */
    private Long relationId;

    /** 关系编号 */
    private String relationCode;

    /** 关系版本号 */
    private Integer versionNo;

    /** 操作类型（CREATE / UPDATE / EXPIRE / APPROVE / REJECT） */
    private String operation;

    /** 层级类型 */
    private String hierarchyType;

    /** 关系类型 */
    private String relationType;

    /** 父节点 One ID */
    private String parentOneId;

    /** 子节点 One ID */
    private String childOneId;

    /** Payer One ID */
    private String payerOneId;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 操作后状态 */
    private String status;

    /** 关系快照（JSON 字符串） */
    private String snapshotJson;

    /** 变更原因 */
    private String changeReason;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createBy;

    /** 创建时间（历史发生时间） */
    private LocalDateTime createTime;
}
