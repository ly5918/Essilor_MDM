package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 集成端点 int_endpoint
 * <p>
 * 对应页面：集成监控 integration 集成配置（系统 / 协议 / 周期 / 地址）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("int_endpoint")
public class IntEndpoint extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 端点编码 */
    private String endpointCode;

    /** 端点名称 */
    private String endpointName;

    /** 方向 */
    private String direction;

    /** 协议（HTTP / SFTP / API） */
    private String protocol;

    /** 目标系统 */
    private String targetSystem;

    /** 端点地址 */
    private String endpointUrl;

    /** 认证方式 */
    private String authType;

    /** 认证配置 */
    private String authConfig;

    /** 业务类型 */
    private String bizType;

    /** 报文格式 */
    private String messageFormat;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 重试间隔（秒） */
    private Integer retryInterval;

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注（页面同步周期写入此处） */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
