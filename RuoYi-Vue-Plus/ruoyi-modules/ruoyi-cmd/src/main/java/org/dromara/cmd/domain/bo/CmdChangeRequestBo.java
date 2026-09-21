package org.dromara.cmd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.dromara.cmd.domain.CmdChangeRequest;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 字段级变更明细（变更场景必填）
     * <p>
     * 前端「发起属性变更」弹窗逐行维护「字段 / 新值」；服务端据此比对主档当前值
     * 补全 Before 值并落入 cmd_change_diff，形成 Before / After 证据链。
     * 停用场景可不传（停用是状态切换，不是字段改写）。
     */
    private List<CmdChangeDiffBo> diffs;
}
