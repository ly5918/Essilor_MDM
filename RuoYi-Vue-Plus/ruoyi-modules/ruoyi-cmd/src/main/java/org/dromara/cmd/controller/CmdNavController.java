package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.service.ICmdNavService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 侧边导航统计 控制层
 * <p>
 * 供全部角色的左侧菜单角标使用：一次请求拿到该角色所有菜单的「待处理条数」，
 * 避免前端逐个菜单各调一个接口、也避免前端用中文标签去匹配数值（脆弱且易错）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/nav")
public class CmdNavController extends BaseController {

    private final ICmdNavService navService;

    /**
     * 查询菜单统计角标（按角色 scope 实时聚合）
     *
     * @param role 角色标识（business / bu / gc / admin / audit），为空按 BU 处理
     * @return 菜单标识 → 统计数
     */
    @GetMapping("/badge")
    public R<Map<String, Long>> badge(@RequestParam(required = false) String role) {
        return R.ok(navService.selectMenuBadges(role));
    }
}
