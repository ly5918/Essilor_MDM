package org.dromara.cmd.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 增加子节点业务对象
 * <p>
 * 对应页面：客户层级 hier 右侧「增加子节点」按钮。
 * <p>
 * 与「归位（assign）」的区别：
 * <ul>
 *   <li>归位作用于「待归位主数据」列表，语义是把一条已批准的主数据挂进 A3-A2-A1 树；</li>
 *   <li>增加子节点作用于树上的某个节点，语义是「在当前节点下面挂一个下级」，
 *       子节点可以选待归位主数据，也可以选尚未登记节点的客户（服务端自动登记后挂载），
 *       并允许同时指定关系类型、Payer、生效日期（归位固定用系统默认值）。</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdHierarchyChildBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父节点 One ID（当前选中节点） */
    @NotBlank(message = "父节点不能为空")
    private String parentOneId;

    /** 子节点 One ID（被挂载的客户） */
    @NotBlank(message = "子节点不能为空")
    private String childOneId;

    /** 层级类型（留空按父节点推导：A3→COMMERCIAL、A2→LEGAL） */
    private String hierarchyType;

    /** 关系类型（留空按父节点级别推导：A3→A3_A2、A2→A2_A1） */
    private String relationType;

    /** Payer One ID（留空继承父节点） */
    private String payerOneId;

    /** 生效时间（留空取当前时间） */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 变更原因 */
    private String changeReason;

    /** 备注 */
    private String remark;

    /** 来源（MANUAL / REQUEST / IMPORT / API） */
    private String sourceType;
}
