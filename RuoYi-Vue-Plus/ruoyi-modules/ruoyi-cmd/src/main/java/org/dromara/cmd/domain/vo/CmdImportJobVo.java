package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdImportJob;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 批量导入任务视图对象 cmd_import_job
 * <p>
 * 对应页面：批量导入 batch 列表行（Job ID / 文件 / 总行数 / 状态 / 提交时间 / 提交人）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdImportJob.class)
public class CmdImportJobVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 任务编号（页面 Job ID） */
    @ExcelProperty(value = "Job ID")
    private String jobCode;

    /** 任务名称 */
    @ExcelProperty(value = "任务名称")
    private String jobName;

    /** 场景 */
    private String scene;

    /** 归属 BU */
    @ExcelProperty(value = "归属BU")
    private String buScope;

    /** 文件名 */
    @ExcelProperty(value = "文件")
    private String fileName;

    /** 总行数 */
    @ExcelProperty(value = "总行数")
    private Integer totalCount;

    /** 精确匹配数 */
    private Integer exactCount;

    /** 疑似重复数 */
    private Integer suspectedCount;

    /** 新建数 */
    private Integer newCount;

    /** 待复核数 */
    private Integer reviewCount;

    /** 无效数 */
    private Integer invalidCount;

    /** 任务状态 */
    @ExcelProperty(value = "状态")
    private String jobStatus;

    /** 进度 */
    private Integer progress;

    /** 提交人 */
    @ExcelProperty(value = "提交人")
    private String submitBy;

    /** 提交时间 */
    @ExcelProperty(value = "提交时间")
    private LocalDateTime submitTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
