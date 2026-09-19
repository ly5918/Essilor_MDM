package org.dromara.cmd.mapper;

import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.vo.AuditEventVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 审计事件 数据层 audit_event
 * <p>
 * 对应页面：审计中心 audit 列表。
 *
 * @author Essilor CMD POC
 */
public interface AuditEventMapper extends BaseMapperPlus<AuditEvent, AuditEventVo> {

}
