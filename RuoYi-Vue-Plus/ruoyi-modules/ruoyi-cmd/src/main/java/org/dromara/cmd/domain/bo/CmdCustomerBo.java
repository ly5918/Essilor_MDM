package org.dromara.cmd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.cmd.domain.CmdCustomer;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户主档业务对象（新增/修改/查询入参）cmd_customer
 * <p>
 * 同时作为列表查询条件使用（配合 params 支持时间区间等扩展条件）。
 *
 * @author Essilor CMD POC
 */
@Data
@AutoMapper(target = CmdCustomer.class, reverseConvertGenerate = false)
public class CmdCustomerBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（修改时必传） */
    private Long id;

    /** One ID（新增时由服务端按 oneid_rule 生成，修改时不可变） */
    private String oneId;

    /** 客户法定名称 */
    @NotBlank(message = "客户法定名称不能为空")
    @Size(max = 500, message = "客户法定名称不能超过{max}个字符")
    private String legalName;

    /** 客户英文名称 */
    private String legalNameEn;

    /** 客户简称 */
    private String shortName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 税号 */
    private String taxNo;

    /** 客户类型（SoldTo / ShipTo / Payer / BillTo） */
    private String customerType;

    /** 客户层级（A1 / A2 / A3） */
    private String customerLevel;

    /** 产品线（High End / Mainstream） */
    private String productLine;

    /** 归属 BU */
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

    /** Payer 编码 */
    private String payerId;

    /** 邮编 */
    private String postalCode;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 邮箱 */
    private String contactEmail;

    /** 状态（draft / pending / active / inactive / archived / rejected） */
    private String status;

    /** 来源系统（OCR / EXCEL / SAP / CRM / MANUAL） */
    private String sourceSystem;

    /** 来源系统主键 */
    private String sourceId;

    /** 匹配状态（EXACT / SUSPECTED / NEW / REVIEW / INVALID） */
    private String matchState;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 备注 */
    private String remark;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /**
     * 关键字（贯通查询）：客户名称 / One ID / 统一社会信用代码，三列模糊匹配。
     * <p>
     * 用于「客户管理」「客户主档」等页面的搜索框按贯穿 ID 检索。
     */
    private String keyword;

    /** 请求参数（支持 beginTime / endTime 等动态条件） */
    private Map<String, Object> params = new HashMap<>();
}
