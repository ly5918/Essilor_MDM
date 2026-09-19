package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.CmdRole;
import org.dromara.cmd.domain.vo.PermissionMatrixVo;
import org.dromara.cmd.service.ICmdPlatformService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色与权限 控制层
 * <p>
 * 对应页面：平台管理 admin → 角色与权限。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/permission")
public class CmdPermissionController extends BaseController {

    private final ICmdPlatformService platformService;

    /**
     * 查询权限矩阵
     *
     * @return 权限矩阵
     */
    @GetMapping("/matrix")
    public R<List<PermissionMatrixVo>> matrix() {
        return R.ok(platformService.selectPermissionMatrix());
    }

    /**
     * 查询角色列表
     *
     * @return 角色列表
     */
    @GetMapping("/role/list")
    public R<List<CmdRole>> roleList() {
        return R.ok(platformService.selectRoleList());
    }

    /**
     * 新增或修改角色权限
     *
     * @param role 角色信息
     * @return 角色编码
     */
    @Log(title = "平台管理", businessType = BusinessType.UPDATE)
    @PutMapping("/role")
    public R<String> saveRole(@Validated @RequestBody List<CmdRole> roles) {
        return R.ok(platformService.saveRoles(roles));
    }
}
