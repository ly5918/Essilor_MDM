package org.dromara.cmd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdChangeDiff;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 变更字段差异明细业务对象 cmd_change_diff
 * <p>
 * 作为 {@code CmdChangeRequestBo.diffs} 的元素使用：前端在「发起属性变更」弹窗中
 * 逐行维护「字段 / 新值」，服务端据此与主档当前值比对后补全 Before 值再落库。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdChangeDiff.class, reverseConvertGenerate = false)
public class CmdChangeDiffBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 变更申请 ID */
    private Long requestId;

    /** 申请编号 */
    private String requestCode;

    /** 字段编码（对应 cmd_customer 的字段名，如 address / creditCode / legalName） */
    private String fieldCode;

    /** 字段名称（页面展示用中文名） */
    private String fieldName;

    /** 变更前值（一般由服务端从主档回填，前端可不传） */
    private String beforeValue;

    /** 变更后值（前端必填） */
    private String afterValue;

    /** 是否关键字段 */
    private String isKeyField;

    /** 是否敏感字段 */
    private String isSensitive;

    /** 变化类型 */
    private String changeFlag;

    /** 对质量分的影响 */
    private BigDecimal dqImpact;

    /** 显示顺序 */
    private Integer orderNum;

    /** 备注 */
    private String remark;
}
