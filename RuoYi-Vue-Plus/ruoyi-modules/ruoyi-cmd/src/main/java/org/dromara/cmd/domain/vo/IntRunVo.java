package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.IntRun;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 集成运行视图对象 int_run
 * <p>
 * 对应页面：集成监控 integration 列表行（Run ID / 方向 / 系统 / 状态 / 明细 / 重试次数 / 记录数）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = IntRun.class)
public class IntRunVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 运行编号（页面 Run ID） */
    @ExcelProperty(value = "Run ID")
    private String runCode;

    /** 端点编码 */
    private String endpointCode;

    /** 端点名称 */
    private String endpointName;

    /** 方向（页面 Inbound / Outbound） */
    @ExcelProperty(value = "方向")
    private String direction;

    /** 目标系统 */
    @ExcelProperty(value = "系统")
    private String targetSystem;

    /** 运行状态 */
    @ExcelProperty(value = "状态")
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

    /** 错误信息（页面错误明细） */
    @ExcelProperty(value = "明细")
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
