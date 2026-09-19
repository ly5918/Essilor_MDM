package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.CmdChangeRequestBo;
import org.dromara.cmd.domain.vo.CmdChangeRequestVo;
import org.dromara.cmd.service.ICmdChangeService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 变更与停用 控制层
 * <p>
 * 对应页面：变更与停用 change（申请列表 / 提交变更 / 提交停用）。
 * 本层只做参数接收与结果封装，业务逻辑全部在 ICmdChangeService 中实现。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/change")
public class CmdChangeController extends BaseController {

    private final ICmdChangeService changeService;

    /**
     * 分页查询变更 / 停用申请
     *
     * @param bo        查询条件（One ID / 变更类型 / 状态 / BU / 关键字）
     * @param pageQuery 分页参数
     * @return 申请分页结果
     */
    @GetMapping("/list")
    public R<PageResult<CmdChangeRequestVo>> list(CmdChangeRequestBo bo, PageQuery pageQuery) {
        return R.ok(changeService.selectPage(bo, pageQuery));
    }

    /**
     * 按申请编号查询详情
     *
     * @param requestCode 申请编号
     * @return 申请详情
     */
    @GetMapping("/{requestCode}")
    public R<CmdChangeRequestVo> getInfo(@PathVariable String requestCode) {
        return R.ok(changeService.selectByRequestCode(requestCode));
    }

    /**
     * 提交变更 / 停用申请
     *
     * @param bo 申请信息
     * @return 操作结果
     */
    @Log(title = "变更与停用", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> submit(@Validated @RequestBody CmdChangeRequestBo bo) {
        changeService.submit(bo);
        return R.ok();
    }
}
