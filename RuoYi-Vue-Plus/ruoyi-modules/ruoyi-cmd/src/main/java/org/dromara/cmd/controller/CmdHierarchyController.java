package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.common.CmdConstants;
import org.dromara.cmd.domain.bo.CmdHierarchyAssignBo;
import org.dromara.cmd.domain.bo.CmdHierarchyChildBo;
import org.dromara.cmd.domain.bo.CmdHierarchyRelationBo;
import org.dromara.cmd.domain.vo.CmdHierarchyNodeVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationHistVo;
import org.dromara.cmd.domain.vo.CmdHierarchyRelationVo;
import org.dromara.cmd.domain.vo.CmdHierarchyUnassignedVo;
import org.dromara.cmd.domain.vo.CmdHierarchyValidateVo;
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
 * <p>
 * 主数据与层级的关系：
 * 审批通过 → 客户成为 Golden Record → 自动登记「待归位」节点（/unassigned 可见）
 * → Steward 归位（/assign）→ 进入 A3-A2-A1 树（/nodes 可见）。
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
     * <p>
     * 页面左侧「搜索与导航」的四个下拉（层级类型 / 层级 / BU / 状态）与关键字一起作为查询条件，
     * 保证下拉切换后结果实时变化。
     *
     * @param keyword       关键字（客户名称 / One ID / 节点编码）
     * @param hierarchyType 层级类型（LEGAL / DOOR / PAYER）
     * @param level         层级级别（A1 / A2 / A3）
     * @param buScope       归属 BU
     * @param status        状态（active / future / expired）
     * @return 节点列表
     */
    @GetMapping("/nodes")
    public R<List<CmdHierarchyNodeVo>> nodes(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String hierarchyType,
                                             @RequestParam(required = false) String level,
                                             @RequestParam(required = false) String buScope,
                                             @RequestParam(required = false) String status) {
        return R.ok(hierarchyService.selectNodeList(keyword, hierarchyType, level, buScope, status));
    }

    /**
     * 查询待归位主数据列表
     * <p>
     * 口径：已审批通过成为主数据（active），但尚未挂到 A3-A2-A1 树上的客户。
     * 批准后主数据会自动出现在这里，Data Steward 归位后即进入层级树。
     *
     * @param keyword 关键字（客户名称 / One ID）
     * @param buScope 归属 BU
     * @return 待归位主数据列表
     */
    @GetMapping("/unassigned")
    public R<List<CmdHierarchyUnassignedVo>> unassigned(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String buScope) {
        return R.ok(hierarchyService.selectUnassignedList(keyword, buScope));
    }

    /**
     * 层级归位：把待归位主数据挂到目标父节点之下
     *
     * @param bo 归位入参
     * @return 操作结果
     */
    @Log(title = "客户层级", businessType = BusinessType.INSERT)
    @PostMapping("/assign")
    public R<Void> assign(@Validated @RequestBody CmdHierarchyAssignBo bo) {
        return toAjax(hierarchyService.assignNode(bo));
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
     * 查询直接子节点（Lazy Load，默认全量）
     *
     * @param parentOneId 父节点 One ID
     * @return 子节点列表
     */
    @GetMapping("/children/{parentOneId}")
    public R<List<CmdHierarchyNodeVo>> children(@PathVariable String parentOneId) {
        return R.ok(hierarchyService.selectChildren(parentOneId));
    }

    /**
     * 分页查询直接子节点（树上「加载更多子节点 · 已显示 X / Y」按需追加）
     *
     * @param parentOneId 父节点 One ID
     * @param offset      偏移量（从 0 开始）
     * @param limit       每批条数（不传取默认 10）
     * @return 子节点列表
     */
    @GetMapping("/childrenPage/{parentOneId}")
    public R<List<CmdHierarchyNodeVo>> childrenPage(@PathVariable String parentOneId,
                                                    @RequestParam(defaultValue = "0") int offset,
                                                    @RequestParam(defaultValue = "0") int limit) {
        return R.ok(hierarchyService.selectChildren(parentOneId, offset, limit <= 0 ? CmdConstants.HIER_CHILD_PAGE_SIZE : limit));
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

    /**
     * 查询根节点列表（层级树「返回根节点」）
     *
     * @param buScope 归属 BU（可为空）
     * @return 根节点列表
     */
    @GetMapping("/roots")
    public R<List<CmdHierarchyNodeVo>> roots(@RequestParam(required = false) String buScope) {
        return R.ok(hierarchyService.selectRoots(buScope));
    }

    /**
     * 查询某节点当前的生效挂载关系（「编辑层级关系」回显用）
     *
     * @param childOneId 子节点 One ID
     * @return 生效关系，不存在返回 null
     */
    @GetMapping("/relationByChild/{childOneId}")
    public R<CmdHierarchyRelationVo> relationByChild(@PathVariable String childOneId) {
        return R.ok(hierarchyService.selectEffectiveRelationByChild(childOneId));
    }

    /**
     * 按主键查询层级关系
     *
     * @param id 关系主键
     * @return 关系详情
     */
    @GetMapping("/relation/{id}")
    public R<CmdHierarchyRelationVo> relation(@PathVariable Long id) {
        return R.ok(hierarchyService.selectRelationById(id));
    }

    /**
     * 查询层级关系历史（版本留痕，历史归属追溯）
     *
     * @param relationId 关系主键（可为空）
     * @param oneId      节点 One ID（relationId 为空时按节点查询全部相关历史）
     * @return 历史版本列表
     */
    @GetMapping("/relationHistory")
    public R<List<CmdHierarchyRelationHistVo>> relationHistory(@RequestParam(required = false) Long relationId,
                                                               @RequestParam(required = false) String oneId) {
        return R.ok(hierarchyService.selectRelationHistory(relationId, oneId));
    }

    /**
     * 提交前实时校验（不修改业务数据，只写 Loop Check 证据日志）
     * <p>
     * 弹窗里的「提交前校验」区域直接调用本接口，保证页面结论与数据库判断完全一致。
     *
     * @param bo 关系入参（id 非空表示编辑语义）
     * @return 校验结果（推导级别 / 预览路径 / 逐条明细 / 阻塞原因）
     */
    @PostMapping("/validate")
    public R<CmdHierarchyValidateVo> validate(@RequestBody CmdHierarchyRelationBo bo) {
        return R.ok(hierarchyService.validateRelation(bo));
    }

    /**
     * 增加子节点（在当前节点下面挂一个下级，真实落库）
     *
     * @param bo 增加子节点入参
     * @return 操作结果
     */
    @Log(title = "客户层级-增加子节点", businessType = BusinessType.INSERT)
    @PostMapping("/child")
    public R<Void> addChild(@Validated @RequestBody CmdHierarchyChildBo bo) {
        return toAjax(hierarchyService.addChildNode(bo));
    }

    /**
     * 编辑层级关系（改挂父节点 / 改 Payer / 改有效期，历史不覆盖）
     *
     * @param id 关系主键
     * @param bo 关系入参
     * @return 操作结果
     */
    @Log(title = "客户层级-编辑关系", businessType = BusinessType.UPDATE)
    @PutMapping("/relation/{id}")
    public R<Void> updateRelation(@PathVariable Long id, @RequestBody CmdHierarchyRelationBo bo) {
        bo.setId(id);
        return toAjax(hierarchyService.updateRelation(bo));
    }
}
