package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdImportTemplate;

import java.io.Serial;
import java.io.Serializable;

/**
 * 导入模板视图对象 cmd_import_template
 * <p>
 * 对应页面：批量导入 batch 的模板列表（模板名 / 适用场景 / 版本 / 字段数 / 状态）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdImportTemplate.class)
public class CmdImportTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    @ExcelProperty(value = "模板名称")
    private String templateName;

    /** 场景（页面「适用场景」） */
    @ExcelProperty(value = "适用场景")
    private String scene;

    /** 归属 BU */
    private String buScope;

    /** 客户类型（下载模板筛选项） */
    @ExcelProperty(value = "客户类型")
    private String customerType;

    /** 产品线（下载模板筛选项） */
    @ExcelProperty(value = "产品线")
    private String productLine;

    /** 来源系统（下载模板筛选项） */
    @ExcelProperty(value = "来源系统")
    private String sourceSystem;

    /** 版本号 */
    @ExcelProperty(value = "版本")
    private String versionNo;

    /** 字段数（由映射表统计后回填） */
    @ExcelProperty(value = "字段数")
    private Integer fieldCount;

    /** 状态（Published / Draft） */
    @ExcelProperty(value = "状态")
    private String status;

    /** 文件路径 */
    private String filePath;

    /** 备注 */
    private String remark;
}
