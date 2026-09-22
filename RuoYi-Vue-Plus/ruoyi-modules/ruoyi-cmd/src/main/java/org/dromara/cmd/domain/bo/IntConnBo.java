package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 集成连接配置入参
 * <p>
 * 对应页面：集成配置 端点配置表单（目标系统 / 协议 / 方向 / 地址 / 认证 / 报文格式 / 重试策略 / 同步周期）。
 *
 * @author Essilor CMD POC
 */
@Data
public class IntConnBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（编辑时传，新增为空） */
    private Long id;

    /** 目标系统 */
    private String system;

    /** 端点名称（为空时按系统名生成） */
    private String name;

    /** 协议（HTTP / HTTPS / API / FILE） */
    private String protocol;

    /** 方向（OUTBOUND 出站 / INBOUND 入站） */
    private String direction;

    /** 同步周期 */
    private String period;

    /** 端点地址 */
    private String url;

    /** 认证方式（NONE / BASIC / TOKEN / OAUTH2） */
    private String authType;

    /** 业务类型 */
    private String bizType;

    /** 报文格式（JSON / XML / CSV） */
    private String messageFormat;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 状态（0正常 1停用） */
    private String status;
}