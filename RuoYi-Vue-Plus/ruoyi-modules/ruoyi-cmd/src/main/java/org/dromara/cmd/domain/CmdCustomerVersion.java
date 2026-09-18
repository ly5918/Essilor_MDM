package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 客户版本快照实体 cmd_customer_version
 * <p>
 * 对应页面：
 * <ul>
 *   <li>客户变更与逻辑停用 change —— Before / After 对比</li>
 *   <li>审计中心 audit —— 变更留痕</li>
 * </ul>
 * 每次客户变更追加一条（不覆盖），首版本 beforeJson 为空。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_customer_version")
public class CmdCustomerVersion extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** One ID */
    private String oneId;

    /** 版本号（同一 one_id 内递增） */
    private Integer versionNo;

    /** 变更类型（CREATE / UPDATE / DEACTIVATE / MERGE / RESTORE） */
    private String changeType;

    /** 变更原因 */
    private String changeReason;

    /** 变更后完整快照（JSON 字符串） */
    private String snapshotJson;

    /** 变更前快照（首版本为空，JSON 字符串） */
    private String beforeJson;

    /** 本次变更的字段编码列表（逗号分隔） */
    private String changedFields;

    /** 当时生效的元数据版本 */
    private String ruleVersion;

    /** 该版本的质量总分 */
    private BigDecimal dqScore;

    /** 版本状态（effective 生效中 / history 历史） */
    private String status;

    /** 来源系统 */
    private String sourceSystem;

    /** 触发该版本的流程实例 */
    private Long flowInstanceId;

    /** 关联变更申请（cmd_change_request.id） */
    private Long changeRequestId;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
