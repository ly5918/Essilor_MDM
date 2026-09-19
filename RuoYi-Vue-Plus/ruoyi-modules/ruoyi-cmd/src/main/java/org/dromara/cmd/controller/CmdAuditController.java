package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.AuditEventBo;
import org.dromara.cmd.domain.bo.AuditExportBo;
import org.dromara.cmd.domain.vo.AuditEventVo;
import org.dromara.cmd.service.ICmdAuditService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计中心 控制层
 * <p>
 * 对应页面：审计中心 audit（全量操作留痕查询、导出登记）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/audit")
public class CmdAuditController extends BaseController {

    private final ICmdAuditService auditService;

    /**
     * 分页查询审计事件
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 审计事件分页结果
     */
    @GetMapping("/list")
    public R<PageResult<AuditEventVo>> list(AuditEventBo bo, PageQuery pageQuery) {
        return R.ok(auditService.selectPage(bo, pageQuery));
    }

    /**
     * 登记审计导出（写入审计事件，返回导出编号）
     *
     * @param bo 导出条件
     * @return 导出编号
     */
    @Log(title = "审计中心", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public R<String> export(@Validated @RequestBody AuditExportBo bo) {
        return R.ok("审计报告已生成并置于下载中心", auditService.exportLog(bo));
    }
}
