package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdCustomerVersion;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户版本快照视图对象 cmd_customer_version
 * <p>
 * 对应页面：客户变更与逻辑停用 change 的 Before / After 对比列表。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdCustomerVersion.class)
public class CmdCustomerVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** One ID */
    @ExcelProperty(value = "One ID")
    private String oneId;

    /** 版本号 */
    @ExcelProperty(value = "版本")
    private Integer versionNo;

    /** 变更类型（CREATE / UPDATE / DEACTIVATE / MERGE / RESTORE） */
    @ExcelProperty(value = "变更类型")
    private String changeType;

    /** 变更原因 */
    private String changeReason;

    /** 变更后完整快照（JSON 字符串） */
    private String snapshotJson;

    /** 变更前快照（JSON 字符串） */
    private String beforeJson;

    /** 本次变更字段列表 */
    private String changedFields;

    /** 元数据版本 */
    private String ruleVersion;

    /** 质量总分 */
    private BigDecimal dqScore;

    /** 版本状态（effective / history） */
    private String status;

    /** 来源系统 */
    private String sourceSystem;

    /** 触发该版本的流程实例 */
    private Long flowInstanceId;

    /** 关联变更申请（cmd_change_request.id） */
    private Long changeRequestId;

    /** 创建时间 */
    @ExcelProperty(value = "变更时间")
    private LocalDateTime createTime;
}
