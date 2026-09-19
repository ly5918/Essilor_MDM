package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审计事件查询对象 audit_event
 * <p>
 * 对应页面：审计中心 audit 列表查询（按事件类型 / 操作角色 / 关键字过滤）。
 *
 * @author Essilor CMD POC
 */
@Data
public class AuditEventBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型 */
    private String eventType;

    /** 业务类型 */
    private String bizType;

    /** 操作角色 */
    private String operatorRole;

    /** 关联 One ID */
    private String oneId;

    /** 关键字（事件编号 / 事件名称 / 操作人 模糊匹配） */
    private String keyword;

    /** 结果（SUCCESS / FAILED / TEST） */
    private String result;
}
