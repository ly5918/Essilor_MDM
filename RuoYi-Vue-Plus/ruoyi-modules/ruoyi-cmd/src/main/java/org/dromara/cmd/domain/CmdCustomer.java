package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户主档（Golden Record）实体 cmd_customer
 * <p>
 * 对应页面：
 * <ul>
 *   <li>客户管理 customers —— 列表 / 新建 / 查看 / OCR 识别结果</li>
 *   <li>工作台 dash —— 客户总数、质量分布统计</li>
 *   <li>治理任务 gov —— 指标卡下钻后的客户明细</li>
 * </ul>
 * 设计约束：
 * <ol>
 *   <li>One ID 稳定：oneId 生成后永不变更，字段变更只追加 {@link CmdCustomerVersion}</li>
 *   <li>无物理删除：停用写 status=inactive + effectiveTo，删除走 delFlag 逻辑删除</li>
 *   <li>扩展字段不落主表，走 EAV 表 cmd_customer_field_value</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_customer")
public class CmdCustomer extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** One ID（全局唯一，生成后永不变更） */
    private String oneId;

    /** 客户法定名称 */
    private String legalName;

    /** 客户英文名称 */
    private String legalNameEn;

    /** 客户简称 */
    private String shortName;

    /** 统一社会信用代码（关键匹配字段） */
    private String creditCode;

    /** 税号 */
    private String taxNo;

    /** 客户类型（SoldTo / ShipTo / Payer / BillTo） */
    private String customerType;

    /** 客户层级（A1 / A2 / A3） */
    private String customerLevel;

    /** 归属 BU（数据权限隔离维度） */
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

    /** 数据质量总分（冗余自 dq_scorecard，避免列表 JOIN） */
    private BigDecimal dqScore;

    /** 质量等级（A / B / C / D） */
    private String dqGrade;

    /** 匹配状态（EXACT / SUSPECTED / NEW / REVIEW / INVALID） */
    private String matchState;

    /** 疑似重复标记（Y是 N否） */
    private String duplicateFlag;

    /** 合并到哪个 One ID（被合并后指向主记录） */
    private String mergedToOneId;

    /** 当前版本号（对应 cmd_customer_version） */
    private Integer versionNo;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间（逻辑停用写入） */
    private LocalDateTime effectiveTo;

    /** 审批人 */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedTime;

    /** 关联 Warm-Flow 流程实例（flow_instance.id） */
    private Long flowInstanceId;

    /** 备注 */
    private String remark;

    /** 扩展属性（未建模字段统一放这里，JSON 字符串） */
    private String extJson;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private String delFlag;
}
