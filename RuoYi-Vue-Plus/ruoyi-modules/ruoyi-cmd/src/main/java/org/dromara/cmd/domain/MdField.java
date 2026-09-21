package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 元数据字段 md_field
 * <p>
 * 对应页面：平台管理 → 字段与值集（字段清单、必填/关键/匹配/DQ 标记、版本）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_field")
public class MdField extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** 模型编码 */
    private String modelCode;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 英文名称 */
    private String fieldNameEn;

    /** 数据类型 */
    private String dataType;

    /** 值集编码 */
    private String valueSetCode;

    /** 默认值 */
    private String defaultValue;

    /** 是否必填（Y是 N否） */
    private String isRequired;

    /** 是否唯一 */
    private String isUnique;

    /** 是否关键字段 */
    private String isKeyField;

    /** 是否参与匹配 */
    private String isMatchField;

    /** 是否参与 DQ */
    private String isDqField;

    /** 是否敏感字段 */
    private String isSensitive;

    /** 是否物理列（Y 表示该字段落在客户主档的实体列上） */
    private String isPhysical;

    /** 物理列名（is_physical=Y 时有效，如 customer_name） */
    private String physicalColumn;

    /** 最小长度 */
    private Integer minLength;

    /** 最大长度 */
    private Integer maxLength;

    /** 正则校验表达式 */
    private String regexPattern;

    /** 扩展属性（JSON 字符串） */
    private String extJson;

    /** 作用范围（GLOBAL / BU / CUSTOMER_TYPE） */
    private String scopeType;

    /** 归属 BU */
    private String ownerBu;

    /** 版本号 */
    private String versionNo;

    /** 状态（0正常 1停用） */
    private String status;

    /** 排序号 */
    private Integer orderNum;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
