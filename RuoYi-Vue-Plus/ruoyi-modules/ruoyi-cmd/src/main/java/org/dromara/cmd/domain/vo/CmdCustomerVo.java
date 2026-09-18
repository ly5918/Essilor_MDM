package org.dromara.cmd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.cmd.domain.CmdCustomer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户主档视图对象 cmd_customer
 * <p>
 * 对应页面：客户管理 customers 列表行（One ID / 名称 / 信用代码 / BU / 状态 / 质量分 / 来源 / 匹配结论）。
 *
 * @author Essilor CMD POC
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CmdCustomer.class)
public class CmdCustomerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** One ID */
    @ExcelProperty(value = "One ID")
    private String oneId;

    /** 客户法定名称 */
    @ExcelProperty(value = "客户名称")
    private String legalName;

    /** 客户英文名称 */
    private String legalNameEn;

    /** 客户简称 */
    private String shortName;

    /** 统一社会信用代码 */
    @ExcelProperty(value = "统一社会信用代码")
    private String creditCode;

    /** 客户类型 */
    private String customerType;

    /** 客户层级 */
    private String customerLevel;

    /** 归属 BU */
    @ExcelProperty(value = "归属BU")
    private String buScope;

    /** 城市 */
    private String city;

    /** 地址 */
    private String address;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;

    /** 来源系统（页面"来源"列） */
    @ExcelProperty(value = "来源")
    private String sourceSystem;

    /** 数据质量总分（页面 DQ 列） */
    @ExcelProperty(value = "质量分")
    private BigDecimal dqScore;

    /** 质量等级 */
    private String dqGrade;

    /** 匹配状态 */
    private String matchState;

    /** 疑似重复标记 */
    private String duplicateFlag;

    /** 当前版本号 */
    private Integer versionNo;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 审批人 */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedTime;

    /** 关联流程实例 */
    private Long flowInstanceId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;
}
