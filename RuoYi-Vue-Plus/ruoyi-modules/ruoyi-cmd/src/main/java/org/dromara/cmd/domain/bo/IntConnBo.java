package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 集成连接配置入参
 * <p>
 * 对应页面：集成监控 integration 集成配置表单（系统 / 协议 / 同步周期 / 地址）。
 *
 * @author Essilor CMD POC
 */
@Data
public class IntConnBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标系统 */
    private String system;

    /** 协议 */
    private String protocol;

    /** 同步周期 */
    private String period;

    /** 端点地址 */
    private String url;
}
