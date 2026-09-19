package org.dromara.cmd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.dromara.cmd.domain.CmdChangeRequest;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更与停用业务对象（新增 / 查询入参）cmd_change_request
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdChangeRequest.class, reverseConvertGenerate = false)
public class CmdChangeRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 申请编号 */
    private String requestCode;

    /** 客户 One ID */
    @NotBlank(message = "客户 One ID 不能为空")
    private String oneId;

    /** 客户名称 */
    private String legalName;

    /** 变更类型（Update / Deactivate） */
    @NotBlank(message = "变更类型不能为空")
    private String changeType;

    /** 目标状态 */
    private String targetStatus;

    /** 是否关键字段变更 */
    private String isKeyChange;

    /** 归属 BU */
    private String buScope;

    /** 变更原因 */
    private String changeReason;

    /** 计划生效日期 */
    private LocalDateTime effectiveDate;

    /** 审批状态 */
    private String status;

    /** 关键字：模糊匹配 申请编号 / One ID / 客户名称 */
    private String keyword;

    /** 备注 */
    private String remark;
}
