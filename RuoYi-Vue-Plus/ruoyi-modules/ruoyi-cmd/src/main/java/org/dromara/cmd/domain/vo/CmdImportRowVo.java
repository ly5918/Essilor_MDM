package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.cmd.domain.CmdImportRow;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 导入行明细视图对象 cmd_import_row
 * <p>
 * 对应页面：批量导入 batch 结果分流下钻「查看 N 条」的明细列表。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdImportRow.class)
public class CmdImportRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 导入任务 ID */
    private Long jobId;

    /** 任务编号 */
    private String jobCode;

    /** 行号 */
    private Integer rowNo;

    /** 行状态 */
    private String rowStatus;

    /** 结果分流 */
    private String resultType;

    /** 客户名称 */
    private String legalName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 归属 BU */
    private String buScope;

    /** 质量分 */
    private BigDecimal dqScore;

    /** 匹配结论 */
    private String matchState;

    /** 匹配分 */
    private BigDecimal matchScore;

    /** 原始行数据 */
    private String rawJson;

    /** 解析后数据 */
    private String parsedJson;

    /** 错误数 */
    private Integer errorCount;

    /** 错误摘要 */
    private String errorSummary;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private String createTime;
}
