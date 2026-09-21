package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 变更字段差异明细 cmd_change_diff
 * <p>
 * 对应页面：变更与停用 change —— 「Before / After」字段级差异清单。
 * 设计约束：差异行在提交时一次性锁定（快照），审批期间不随主档变化，保证审计可回溯。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_change_diff")
public class CmdChangeDiff extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 变更申请 ID（cmd_change_request.id） */
    private Long requestId;

    /** 申请编号（冗余，便于按编号直接检索） */
    private String requestCode;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 变更前值 */
    private String beforeValue;

    /** 变更后值 */
    private String afterValue;

    /** 是否关键字段（Y：单独审批） */
    private String isKeyField;

    /** 是否敏感字段 */
    private String isSensitive;

    /** 变化类型（ADD 新增 / MODIFY 修改 / DELETE 删除 / SAME 未变） */
    private String changeFlag;

    /** 对质量分的影响 */
    private BigDecimal dqImpact;

    /** 显示顺序 */
    private Integer orderNum;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
