package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 导入行明细 cmd_import_row
 * <p>
 * 对应页面：批量导入 batch 的结果分流下钻「查看 N 条」。
 * 数据来源：业务用户下载模板 → 线下填写 → 上传 Excel，
 * 后端按 cmd_import_template_mapping 解析后逐行写入本表
 *（raw_json 保存原始列值，parsed_json 保存字段编码值）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_import_row")
public class CmdImportRow extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 导入任务 ID */
    private Long jobId;

    /** 任务编号（冗余） */
    private String jobCode;

    /** 行号（Excel 实际行号） */
    private Integer rowNo;

    /** 行状态（PENDING / PROCESSING / SUCCESS / FAILED / SKIPPED / GOVERNANCE） */
    private String rowStatus;

    /** 结果分流（EXACT / SUSPECTED / NEW / REVIEW / INVALID） */
    private String resultType;

    /** 生成/关联的 One ID */
    private String oneId;

    /** 客户名称 */
    private String legalName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 归属 BU */
    private String buScope;

    /** 质量分 */
    private java.math.BigDecimal dqScore;

    /** 匹配结论 */
    private String matchState;

    /** 匹配分 */
    private java.math.BigDecimal matchScore;

    /** 原始行数据（列名 → 值，JSON 字符串） */
    private String rawJson;

    /** 解析后数据（字段编码 → 值，JSON 字符串） */
    private String parsedJson;

    /** 错误数 */
    private Integer errorCount;

    /** 错误摘要 */
    private String errorSummary;

    /** 处理策略（RETRY / FIX / GOVERNANCE / IGNORE） */
    private String handling;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
