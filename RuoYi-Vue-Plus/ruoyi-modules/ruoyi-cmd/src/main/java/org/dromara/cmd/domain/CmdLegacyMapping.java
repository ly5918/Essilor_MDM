package org.dromara.cmd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * Legacy Code 映射 cmd_legacy_mapping
 * <p>
 * 对应页面：One ID 规则管理 → Legacy Code ↔ One ID 交叉引用。
 *
 * @author Essilor CMD POC
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cmd_legacy_mapping")
public class CmdLegacyMapping extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** One ID */
    private String oneId;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源编码（页面 Legacy Code） */
    private String sourceCode;

    /** 来源名称 */
    private String sourceName;

    /** 归属 BU */
    private String buScope;

    /** 映射类型 */
    private String mappingType;

    /** 状态（0有效 1失效） */
    private String status;

    /** 生效开始 */
    private LocalDateTime effectiveFrom;

    /** 生效结束 */
    private LocalDateTime effectiveTo;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
