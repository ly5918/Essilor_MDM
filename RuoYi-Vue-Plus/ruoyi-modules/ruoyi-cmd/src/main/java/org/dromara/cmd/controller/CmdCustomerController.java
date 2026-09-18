package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.CmdCustomerBo;
import org.dromara.cmd.domain.vo.CmdCustomerVersionVo;
import org.dromara.cmd.domain.vo.CmdCustomerVo;
import org.dromara.cmd.service.ICmdCustomerService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 客户主档 控制层
 * <p>
 * 对应页面：客户管理 customers（列表 / 新建 / 查看 / 停用）。
 * 本层只做参数接收与结果封装，业务逻辑全部在 ICmdCustomerService 中实现。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/customer")
public class CmdCustomerController extends BaseController {

    private final ICmdCustomerService customerService;

    /**
     * 分页查询客户主档列表
     *
     * @param bo        查询条件（名称 / One ID / 信用代码 / BU / 状态 / 匹配结论）
     * @param pageQuery 分页参数
     * @return 客户分页结果
     */
    @GetMapping("/list")
    public R<PageResult<CmdCustomerVo>> list(CmdCustomerBo bo, PageQuery pageQuery) {
        return R.ok(customerService.selectPageCustomerList(bo, pageQuery));
    }

    /**
     * 按主键查询客户详情
     *
     * @param id 主键
     * @return 客户详情
     */
    @GetMapping("/{id}")
    public R<CmdCustomerVo> getInfo(@PathVariable Long id) {
        return R.ok(customerService.selectCustomerById(id));
    }

    /**
     * 按 One ID 查询客户详情
     *
     * @param oneId One ID
     * @return 客户详情
     */
    @GetMapping("/oneId/{oneId}")
    public R<CmdCustomerVo> getInfoByOneId(@PathVariable String oneId) {
        return R.ok(customerService.selectCustomerByOneId(oneId));
    }

    /**
     * 查询客户版本历史（Before / After 对比）
     *
     * @param oneId One ID
     * @return 版本列表
     */
    @GetMapping("/version/{oneId}")
    public R<List<CmdCustomerVersionVo>> versionList(@PathVariable String oneId) {
        return R.ok(customerService.selectVersionList(oneId));
    }

    /**
     * 新增客户
     *
     * @param bo 客户信息
     * @return 操作结果
     */
    @Log(title = "客户主档", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody CmdCustomerBo bo) {
        customerService.insertCustomer(bo);
        return R.ok();
    }

    /**
     * 修改客户（自动追加版本快照）
     *
     * @param bo 客户信息
     * @return 操作结果
     */
    @Log(title = "客户主档", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody CmdCustomerBo bo) {
        return toAjax(customerService.updateCustomer(bo));
    }

    /**
     * 客户逻辑停用（无物理删除，写 status=inactive + effectiveTo）
     *
     * @param oneId  客户 One ID
     * @param reason 停用原因
     * @return 操作结果
     */
    @Log(title = "客户主档", businessType = BusinessType.UPDATE)
    @PutMapping("/deactivate/{oneId}")
    public R<Void> deactivate(@PathVariable String oneId, @RequestParam(required = false) String reason) {
        return toAjax(customerService.deactivateCustomer(oneId, reason));
    }

    /**
     * 批量删除客户（生效中的客户不允许直接删除）
     *
     * @param ids 主键数组
     * @return 操作结果
     */
    @Log(title = "客户主档", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(customerService.deleteCustomerByIds(Arrays.asList(ids), true));
    }
}
