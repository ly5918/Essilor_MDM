package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.CmdChangeRequestBo;
import org.dromara.cmd.domain.vo.CmdChangeDetailVo;
import org.dromara.cmd.domain.vo.CmdChangeFieldVo;
import org.dromara.cmd.domain.vo.CmdChangeKpiVo;
import org.dromara.cmd.domain.vo.CmdChangeRequestVo;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.cmd.domain.vo.CmdDeactivateResultVo;
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

import java.util.List;

/**
 * 变更与停用 控制层
 * <p>
 * 对应页面：变更与停用 change
 * （工作台 4 张指标卡 / 变更申请 / 停用申请 / 版本历史，以及详情、生效、撤回）。
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
     * 可变更字段目录（发起属性变更弹窗的字段选择器，配置驱动）
     *
     * @return 字段目录
     */
    @GetMapping("/fields")
    public R<List<CmdChangeFieldVo>> fields() {
        return R.ok(changeService.selectChangeableFields());
    }

    /**
     * 变更与停用指标卡（工作台 4 张卡）
     *
     * @return 指标列表
     */
    @GetMapping("/kpi")
    public R<List<CmdChangeKpiVo>> kpi() {
        return R.ok(changeService.selectKpi());
    }

    /**
     * 按申请编号查询详情（申请单原始字段）
     *
     * @param requestCode 申请编号
     * @return 申请详情
     */
    @GetMapping("/{requestCode}")
    public R<CmdChangeRequestVo> getInfo(@PathVariable String requestCode) {
        return R.ok(changeService.selectByRequestCode(requestCode));
    }

    /**
     * 申请详情：Before / After 差异 + 影响面 + 审批轨迹 + 版本上下文
     *
     * @param requestCode 申请编号
     * @return 详情
     */
    @GetMapping("/{requestCode}/detail")
    public R<CmdChangeDetailVo> detail(@PathVariable String requestCode) {
        return R.ok(changeService.selectDetail(requestCode));
    }

    /**
     * 查询客户主档版本历史（证明「换版本不换 One ID」）
     *
     * @param oneId 客户主数据标识
     * @return 版本快照列表
     */
    @GetMapping("/versions/{oneId}")
    public R<List<CmdCustomerVersionVo>> versions(@PathVariable String oneId) {
        return R.ok(changeService.selectVersions(oneId));
    }

    /**
     * 逻辑停用结果（业务视图 + 落库记录，证明无物理删除）
     *
     * @param oneId 客户主数据标识
     * @return 停用结果
     */
    @GetMapping("/{oneId}/deactivateResult")
    public R<CmdDeactivateResultVo> deactivateResult(@PathVariable String oneId) {
        return R.ok(changeService.selectDeactivateResult(oneId));
    }

    /**
     * 提交变更 / 停用申请
     *
     * @param bo 申请信息（变更场景需带 diffs 字段级明细）
     * @return 操作结果
     */
    @Log(title = "变更与停用", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> submit(@Validated @RequestBody CmdChangeRequestBo bo) {
        changeService.submit(bo);
        return R.ok();
    }

    /**
     * 生效申请（写主档新版本，One ID 保持不变）
     *
     * @param requestCode 申请编号
     * @return 操作结果
     */
    @Log(title = "变更生效", businessType = BusinessType.UPDATE)
    @PostMapping("/{requestCode}/effect")
    public R<Void> effect(@PathVariable String requestCode) {
        changeService.effect(requestCode);
        return R.ok();
    }

    /**
     * 撤回申请
     *
     * @param requestCode 申请编号
     * @return 操作结果
     */
    @Log(title = "撤回变更申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{requestCode}/cancel")
    public R<Void> cancel(@PathVariable String requestCode) {
        changeService.cancel(requestCode);
        return R.ok();
    }
}
