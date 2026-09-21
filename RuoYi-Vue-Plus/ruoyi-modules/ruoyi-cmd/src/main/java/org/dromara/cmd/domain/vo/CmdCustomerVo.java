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
 * <p>
 * 字段口径：本 VO 与 {@link CmdCustomer} 的**全部业务列**保持一一对应（除 delFlag 逻辑删除标志），
 * 客户详情弹窗按「基本信息 / 联络与地址 / 治理与质量 / 生效与流程」分组完整展示，不再二次查询。
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

    /** 税号 */
    private String taxNo;

    /** 客户类型 */
    private String customerType;

    /** 客户层级 */
    private String customerLevel;

    /** 产品线（页面 Product Line 列） */
    @ExcelProperty(value = "产品线")
    private String productLine;

    /** 归属 BU */
    @ExcelProperty(value = "归属BU")
    private String buScope;

    /** 是否跨 BU 全局可见（Y是 N否） */
    private String gcScopeFlag;

    /** 国家/地区 */
    private String country;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 注册地址 */
    private String address;

    /** 邮编 */
    private String postalCode;

    /** Payer 编码 */
    private String payerId;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 邮箱 */
    private String contactEmail;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;

    /** 来源系统（页面"来源"列） */
    @ExcelProperty(value = "来源")
    private String sourceSystem;

    /** 来源系统主键（外部系统原始 ID，追溯用） */
    private String sourceId;

    /** 数据质量总分（页面 DQ 列） */
    @ExcelProperty(value = "质量分")
    private BigDecimal dqScore;

    /** 质量等级 */
    private String dqGrade;

    /** 匹配状态 */
    private String matchState;

    /** 疑似重复标记 */
    private String duplicateFlag;

    /** 合并到哪个 One ID（被合并后指向主记录） */
    private String mergedToOneId;

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

    /** 工作流实例状态（Warm-Flow flow_instance.flow_status 镜像） */
    private String flowStatus;

    /** 备注 */
    private String remark;

    /** 扩展属性（未建模字段统一放这里，JSON 字符串） */
    private String extJson;

    /** 创建时间 */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
