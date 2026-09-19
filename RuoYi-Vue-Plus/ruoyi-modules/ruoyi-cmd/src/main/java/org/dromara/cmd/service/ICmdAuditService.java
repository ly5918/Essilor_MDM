package org.dromara.cmd.service;

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
     * 登记一次审计导出（同时写入一条审计事件，保证导出行为可追溯）
     *
     * @param bo 导出条件
     * @return 导出编号
     */
    String exportLog(AuditExportBo bo);
}
