package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户层级关系历史实体 cmd_hierarchy_relation_hist
 * <p>
 * 对应页面：客户层级 hier —— 右侧「关系历史」（历史归属追溯）；审计中心 audit。
 * <p>
 * 设计原则（对齐 V6.1「关系变更追加历史，不覆盖」与「无物理删除，逻辑状态和历史必须保留」）：
 * 每次新增 / 编辑 / 失效层级关系都追加一条历史，携带 versionNo（关系版本号）与 snapshotJson（操作后快照），
 * 旧版本永不删除，因此任何时候都能回答「这个门店以前挂在哪个 A2 下」。
 * <p>
 * 本表没有 create_dept / update_* 列，因此不继承 BaseEntity，避免插入不存在的列。
 *
 * @author Essilor CMD POC
 */
@Data
@TableName("cmd_hierarchy_relation_hist")
public class CmdHierarchyRelationHist implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 关系 ID（cmd_hierarchy_relation.id） */
    private Long relationId;

    /** 关系编号（冗余） */
    private String relationCode;

    /** 关系版本号（同一关系每次变更 +1） */
    private Integer versionNo;

    /** 操作类型（CREATE / UPDATE / EXPIRE / APPROVE / REJECT / DELETE） */
    private String operation;

    /** 层级类型（COMMERCIAL / LEGAL / DOOR） */
    private String hierarchyType;

    /** 关系类型（A3_A2 / A2_A1 / MAIN_DOOR / PAYER_LINK） */
    private String relationType;

    /** 父节点 One ID（历史值） */
    private String parentOneId;

    /** 子节点 One ID（历史值） */
    private String childOneId;

    /** Payer One ID（历史值） */
    private String payerOneId;

    /** 生效时间（历史值） */
    private LocalDateTime effectiveFrom;

    /** 失效时间（历史值） */
    private LocalDateTime effectiveTo;

    /** 操作后状态（Pending / Effective / Expired / Rejected） */
    private String status;

    /** 关系快照（JSON 字符串，含操作前 / 操作后的完整字段） */
    private String snapshotJson;

    /** 变更原因 */
    private String changeReason;

    /** 关联流程实例 */
    private Long flowInstanceId;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;

    /** 创建者 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新者 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
