package org.dromara.cmd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.dromara.cmd.domain.CmdHierarchyRelation;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户层级关系业务对象（新增 / 编辑 / 发起申请入参）cmd_hierarchy_relation
 * <p>
 * 对应页面：客户层级 hier —— 「新增层级关系」「发起层级关系申请」「增加子节点」「编辑关系」。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdHierarchyRelation.class, reverseConvertGenerate = false)
public class CmdHierarchyRelationBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（编辑时必传） */
    private Long id;

    /** 层级类型（LEGAL / SALES / PAYER） */
    private String hierarchyType;

    /** 关系类型（A3_A2 / A2_A1 / MAIN_DOOR / PAYER_LINK） */
    @NotBlank(message = "关系类型不能为空")
    private String relationType;

    /** 父节点 One ID */
    @NotBlank(message = "父节点不能为空")
    private String parentOneId;

    /** 子节点 One ID */
    @NotBlank(message = "子节点不能为空")
    private String childOneId;

    /** Payer One ID */
    private String payerOneId;

    /** 归属 BU */
    private String buScope;

    /** 是否跨 BU（由服务端按父子节点 BU 自动判定，前端可不传） */
    private String crossBuFlag;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 变更原因 */
    private String changeReason;

    /** 来源（MANUAL / IMPORT / REQUEST / API） */
    private String sourceType;

    /** 备注 */
    private String remark;
}
