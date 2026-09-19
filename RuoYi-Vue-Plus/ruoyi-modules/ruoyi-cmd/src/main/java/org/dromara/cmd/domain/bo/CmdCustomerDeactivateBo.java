package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户停用入参
 * <p>
 * 对应页面：变更与停用 change —— 客户逻辑停用申请。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdCustomerDeactivateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 停用原因（写入变更申请与审计记录） */
    private String reason;
}
