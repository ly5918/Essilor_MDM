package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户层级节点实体 cmd_hierarchy_node
 * <p>
 * 对应页面：客户层级 hier —— 树形结构、搜索结果列表、右侧节点详情。
 * fullPath 为冗余字段（/A3-001/A2-0188/A1-000128），用于加速祖先/后代查询。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_hierarchy_node")
public class CmdHierarchyNode extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 节点编码 */
    private String nodeCode;

    /** 关联客户 One ID */
    private String oneId;

    /** 客户名称 */
    private String legalName;

    /** 层级类型（LEGAL 法人 / SALES 销售 / PAYER 付款） */
    private String hierarchyType;

    /** 层级级别（A1 / A2 / A3） */
    private String level;

    /** 父节点 One ID（根节点为空） */
    private String parentOneId;

    /** 完整路径（/A3-001/A2-0188/A1-000128，冗余加速） */
    private String fullPath;

    /** 路径名称串（展示用） */
    private String pathNames;

    /** 层级深度（A3=1, A2=2, A1=3） */
    private Integer depth;

    /** Payer 节点 One ID */
    private String payerOneId;

    /** 归属 BU */
    private String buScope;

    /** 直接子节点数（Lazy Load 分页用） */
    private Integer childrenCount;

    /** 后代节点总数 */
    private Integer descendants;

    /** 状态（active / inactive / pending） */
    private String status;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 同级排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
