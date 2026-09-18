package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.bo.CmdHierarchyRelationBo;
import org.dromara.cmd.domain.vo.CmdHierarchyNodeVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationVo;
import org.dromara.cmd.service.ICmdHierarchyService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户层级 控制层
 * <p>
 * 对应页面：客户层级 hier（左搜索 / 中树 / 右详情）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/hierarchy")
public class CmdHierarchyController extends BaseController {

    private final ICmdHierarchyService hierarchyService;

    /**
     * 查询层级节点列表（搜索与筛选）
     *
     * @param keyword 关键字
     * @param level   层级级别（A1 / A2 / A3）
     * @param buScope 归属 BU
     * @return 节点列表
     */
    @GetMapping("/nodes")
    public R<List<CmdHierarchyNodeVo>> nodes(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String level,
                                             @RequestParam(required = false) String buScope) {
        return R.ok(hierarchyService.selectNodeList(keyword, level, buScope));
    }

    /**
     * 查询节点详情
     *
     * @param oneId 客户 One ID
     * @return 节点详情
     */
    @GetMapping("/node/{oneId}")
    public R<CmdHierarchyNodeVo> node(@PathVariable String oneId) {
        return R.ok(hierarchyService.selectNodeByOneId(oneId));
    }

    /**
     * 查询直接子节点（Lazy Load）
     *
     * @param parentOneId 父节点 One ID
     * @return 子节点列表
     */
    @GetMapping("/children/{parentOneId}")
    public R<List<CmdHierarchyNodeVo>> children(@PathVariable String parentOneId) {
        return R.ok(hierarchyService.selectChildren(parentOneId));
    }

    /**
     * 新增层级关系（含环路检测，跨 BU 自动判定）
     *
     * @param bo 关系入参
     * @return 操作结果
     */
    @Log(title = "客户层级", businessType = BusinessType.INSERT)
    @PostMapping("/relation")
    public R<Void> addRelation(@Validated @RequestBody CmdHierarchyRelationBo bo) {
        return toAjax(hierarchyService.addRelation(bo));
    }

    /**
     * 查询层级关系列表
     *
     * @param oneId 客户 One ID
     * @return 关系列表
     */
    @GetMapping("/relations/{oneId}")
    public R<List<CmdHierarchyRelationVo>> relations(@PathVariable String oneId) {
        return R.ok(hierarchyService.selectRelationList(oneId));
    }
}
