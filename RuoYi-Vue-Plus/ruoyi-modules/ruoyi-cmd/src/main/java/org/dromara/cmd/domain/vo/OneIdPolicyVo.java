package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * One ID 生成与状态策略视图对象
 * <p>
 * 对应页面：One ID 规则管理 → One ID 生成与状态策略。
 *
 * @author Essilor CMD POC
 */
@Data
public class OneIdPolicyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件 */
    private String event;

    /** 处理方式 */
    private String handling;
}
