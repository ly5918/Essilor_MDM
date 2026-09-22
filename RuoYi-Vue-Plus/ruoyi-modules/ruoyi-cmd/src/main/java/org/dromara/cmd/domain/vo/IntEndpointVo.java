package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.IntEndpoint;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 集成端点视图对象 int_endpoint
 * <p>
 * 对应页面：集成配置 端点列表行（系统 / 方向 / 协议 / 地址 / 状态 / 周期）。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = IntEndpoint.class)
public class IntEndpointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 端点编码 */
    private String endpointCode;

    /** 端点名称 */
    private String endpointName;

    /** 方向（INBOUND 入站 / OUTBOUND 出站） */
    private String direction;

    /** 协议（HTTP / HTTPS / SFTP / KAFKA / JDBC / FILE） */
    private String protocol;

    /** 目标系统（SAP / CRM / EC / DW / Mock 下游） */
    private String targetSystem;

    /** 端点地址 */
    private String endpointUrl;

    /** 认证方式（NONE / BASIC / TOKEN / OAUTH2 / CERT） */
    private String authType;

    /** 业务类型 */
    private String bizType;

    /** 报文格式 */
    private String messageFormat;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注（同步周期等） */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}