package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdHierarchyRelation;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户层级关系视图对象 cmd_hierarchy_relation
 * <p>
 * 对应页面：客户层级 hier 关系列表与详情。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdHierarchyRelation.class)
public class CmdHierarchyRelationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关系编号 */
    private String relationCode;

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

    /** 归属 BU */
    private String buScope;

    /** 是否跨 BU */
    private String crossBuFlag;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 状态（Pending / Effective / Expired / Rejected） */
    private String status;

    /** 变更原因 */
    private String changeReason;

    /** 来源 */
    private String sourceType;

    /** 关联待办 ID */
    private Long approvalId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
