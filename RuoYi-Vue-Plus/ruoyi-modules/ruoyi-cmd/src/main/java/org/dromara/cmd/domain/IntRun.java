package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 集成运行记录 int_run
 * <p>
 * 对应页面：集成监控 integration 运行记录列表（Run ID / 方向 / 系统 / 状态 / Retry）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("int_run")
public class IntRun extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 运行编号（页面 Run ID） */
    private String runCode;

    /** 端点编码 */
    private String endpointCode;

    /** 端点名称 */
    private String endpointName;

    /** 方向（INBOUND / OUTBOUND） */
    private String direction;

    /** 目标系统 */
    private String targetSystem;

    /** 业务类型 */
    private String bizType;

    /** 触发方式 */
    private String triggerType;

    /** 运行状态（SUCCESS / FAILED / RUNNING / RETRYING） */
    private String runStatus;

    /** 总条数 */
    private Integer totalCount;

    /** 成功条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failedCount;

    /** 已尝试次数 */
    private Integer attemptCount;

    /** 最大重试次数 */
    private Integer maxAttempt;

    /** 错误信息 */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
