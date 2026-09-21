package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待归位主数据视图对象
 * <p>
 * 对应页面：客户层级 hier —— 左侧「待归位主数据」列表。
 * <p>
 * 口径（与 A3-A2-A1 树互补，两者一起才能覆盖全部主数据）：
 * <ol>
 *   <li>客户已审批通过成为 Golden Record（cmd_customer.status = active）</li>
 *   <li>但在 cmd_hierarchy_node 中「没有节点」或「节点还是 UNASSIGNED（未挂父节点）」</li>
 * </ol>
 * 即：已经是主数据，只是还没有被 Data Steward 归位到层级树上。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdHierarchyUnassignedVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户 One ID */
    private String oneId;

    /** 客户名称 */
    private String legalName;

    /** 归属 BU */
    private String buScope;

    /** 客户状态（active） */
    private String customerStatus;

    /** 来源系统 */
    private String sourceSystem;

    /** 数据质量分 */
    private BigDecimal dqScore;

    /** 审批通过时间 */
    private LocalDateTime approvedTime;

    /** 是否已在层级节点表登记（true=已登记待归位，false=尚未登记） */
    private Boolean registered;

    /** 已登记的待归位节点编码（未登记为空） */
    private String nodeCode;

    /** 建议层级级别（POC 默认按 A1 门店登记，归位时按父节点自动推导） */
    private String suggestedLevel;

    /** 提示文案 */
    private String remark;
}
