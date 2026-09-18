package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdHierarchyNode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户层级节点视图对象 cmd_hierarchy_node
 * <p>
 * 对应页面：客户层级 hier —— 树节点、搜索结果项、右侧节点详情。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdHierarchyNode.class)
public class CmdHierarchyNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 节点编码 */
    private String nodeCode;

    /** 关联客户 One ID */
    private String oneId;

    /** 客户名称 */
    private String legalName;

    /** 层级类型 */
    private String hierarchyType;

    /** 层级级别（A1 / A2 / A3） */
    private String level;

    /** 父节点 One ID */
    private String parentOneId;

    /** 完整路径 */
    private String fullPath;

    /** 路径名称串 */
    private String pathNames;

    /** 层级深度 */
    private Integer depth;

    /** Payer 节点 One ID */
    private String payerOneId;

    /** 归属 BU */
    private String buScope;

    /** 直接子节点数 */
    private Integer childrenCount;

    /** 后代节点总数 */
    private Integer descendants;

    /** 状态 */
    private String status;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 备注 */
    private String remark;
}
