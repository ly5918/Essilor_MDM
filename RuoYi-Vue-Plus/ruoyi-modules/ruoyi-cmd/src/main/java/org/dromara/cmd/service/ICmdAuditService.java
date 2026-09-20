package org.dromara.cmd.service;

import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.bo.AuditEventBo;
import org.dromara.cmd.domain.bo.AuditExportBo;
import org.dromara.cmd.domain.vo.AuditEventVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 审计中心 服务层
 * <p>
 * 对应页面：审计中心 audit（全量操作留痕查询 / 导出登记）。
 *
 * @author Essilor CMD POC
 */
public interface ICmdAuditService {

    /**
     * 分页查询审计事件
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 审计事件分页结果
     */
    PageResult<AuditEventVo> selectPage(AuditEventBo bo, PageQuery pageQuery);

    /**
     * 写入一条审计事件（业务侧主动留痕）
     * <p>
     * 关键业务动作（提交申请 / 审批决策等）调用本方法，保证
     * 审计中心可按 One ID 或关联单号反查完整证据链。
     *
     * @param event 审计事件实体（eventId 为空时自动生成 AE-yyyyMMdd-####）
     * @return 事件编号
     */
    String record(AuditEvent event);

    /**
     * 登记一次审计导出（同时写入一条审计事件，保证导出行为可追溯）
     *
     * @param bo 导出条件
     * @return 导出编号
     */
    String exportLog(AuditExportBo bo);
}
