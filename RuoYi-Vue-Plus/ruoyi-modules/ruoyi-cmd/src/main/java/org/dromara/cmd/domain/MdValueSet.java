package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 值集 md_value_set
 * <p>
 * 对应页面：平台管理 → 字段与值集（值集清单）。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_value_set")
public class MdValueSet extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** 值集编码 */
    private String setCode;

    /** 值集名称 */
    private String setName;

    /** 值集类型 */
    private String setType;

    /** 来源 SQL */
    private String sourceSql;

    /** 作用范围 */
    private String scopeType;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
