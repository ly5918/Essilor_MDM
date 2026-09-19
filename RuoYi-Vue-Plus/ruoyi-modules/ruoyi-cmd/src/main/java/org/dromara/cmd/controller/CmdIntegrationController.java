package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.IntConnBo;
import org.dromara.cmd.domain.bo.IntRunBo;
import org.dromara.cmd.domain.vo.IntRunVo;
import org.dromara.cmd.service.ICmdIntegrationService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集成监控 控制层
 * <p>
 * 对应页面：集成监控 integration（运行记录 / Retry / 集成配置）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/integration")
public class CmdIntegrationController extends BaseController {

    private final ICmdIntegrationService integrationService;

    /**
     * 分页查询集成运行记录
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 运行记录分页结果
     */
    @GetMapping("/run/list")
    public R<PageResult<IntRunVo>> list(IntRunBo bo, PageQuery pageQuery) {
        return R.ok(integrationService.selectPage(bo, pageQuery));
    }

    /**
     * 重试指定运行记录
     *
     * @param runCode 运行编号
     * @return 提示文案
     */
    @Log(title = "集成监控", businessType = BusinessType.UPDATE)
    @PutMapping("/run/{runCode}/retry")
    public R<String> retry(@PathVariable String runCode) {
        return R.ok(integrationService.retry(runCode));
    }

    /**
     * 保存集成连接配置
     *
     * @param bo 连接配置
     * @return 端点编码
     */
    @Log(title = "集成监控", businessType = BusinessType.INSERT)
    @PostMapping("/conn")
    public R<String> saveConn(@Validated @RequestBody IntConnBo bo) {
        return R.ok("集成连接已保存，等待连通性测试", integrationService.saveConn(bo));
    }
}
