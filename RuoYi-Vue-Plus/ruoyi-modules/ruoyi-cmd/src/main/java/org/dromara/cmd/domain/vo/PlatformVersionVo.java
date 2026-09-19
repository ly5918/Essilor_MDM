package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型版本视图对象（由 md_field 按版本号聚合得出）
 * <p>
 * 对应页面：平台管理 → 字段与值集的模型版本列表。
 *
 * @author Essilor CMD POC
 */
@Data
public class PlatformVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 版本号 */
    private String version;

    /** 规则 / 字段数量 */
    private Long ruleCount;

    /** 状态（Current / Draft） */
    private String status;

    /** 发布时间 */
    private String publishedAt;
}
