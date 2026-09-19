package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 导入模板 cmd_import_template
 * <p>
 * 对应页面：批量导入 batch 的模板下载与字段映射展示。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_import_template")
public class CmdImportTemplate extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 场景 */
    private String scene;

    /** 归属 BU */
    private String buScope;

    /** 客户类型（Door / Payer / A1 / A2 / A3），下载模板筛选项 */
    private String customerType;

    /** 产品线（Lens / Frame / Sunglasses），下载模板筛选项 */
    private String productLine;

    /** 来源系统（DMS+ / Cloud / SAP），下载模板筛选项 */
    private String sourceSystem;

    /** 版本号 */
    private String versionNo;

    /** 文件类型 */
    private String fileType;

    /** 表头行号 */
    private Integer headerRow;

    /** 数据起始行号 */
    private Integer dataStartRow;

    /** 工作表名称 */
    private String sheetName;

    /** 模板文件路径 */
    private String filePath;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
