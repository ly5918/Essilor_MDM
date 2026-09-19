package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审计导出入参
 * <p>
 * 对应页面：审计中心 audit 导出表单（时间范围 / 事件类型 / 格式 / 脱敏）。
 *
 * @author Essilor CMD POC
 */
@Data
public class AuditExportBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 时间范围 */
    private String range;

    /** 事件类型 */
    private String eventType;

    /** 导出格式（Excel / CSV） */
    private String format;

    /** 脱敏策略 */
    private String masking;
}
