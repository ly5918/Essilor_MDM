package org.dromara.cmd.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdLegacyMapping;
import org.dromara.cmd.domain.CmdMergeRecord;
import org.dromara.cmd.domain.OneIdRule;
import org.dromara.cmd.domain.vo.OneIdPolicyVo;
import org.dromara.cmd.service.ICmdPlatformService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * One ID 规则 控制层
 * <p>
 * 对应页面：平台管理 admin → One ID 规则管理（命名规则 / 策略 / Legacy 映射 / 历史）。
 *
 * @author Essilor CMD POC
 */
@SaCheckLogin
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/oneid")
public class CmdOneIdController extends BaseController {

    private final ICmdPlatformService platformService;

    /**
     * 查询默认 One ID 规则
     *
     * @return 规则
     */
    @GetMapping("/rule")
    public R<OneIdRule> rule() {
        return R.ok(platformService.selectOneIdRule());
    }

    /**
     * 保存（更新）当前默认 One ID 规则
     *
     * @param rule 规则修改内容
     * @return 提示文案
     */
    @Log(title = "One ID 规则", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public R<String> save(@RequestBody OneIdRule rule) {
        return R.ok(platformService.saveOneIdRule(rule));
    }

    /**
     * 发布 One ID 规则
     *
     * @return 提示文案
     */
    @Log(title = "One ID 规则", businessType = BusinessType.UPDATE)
    @PutMapping("/rule/publish")
    public R<String> publish() {
        return R.ok(platformService.publishOneIdRule());
    }

    /**
     * 复制规则为草稿
     *
     * @return 提示文案
     */
    @Log(title = "One ID 规则", businessType = BusinessType.INSERT)
    @PutMapping("/rule/copy")
    public R<String> copy() {
        return R.ok(platformService.copyOneIdRule());
    }

    /**
     * 查询 One ID 生成与状态策略
     *
     * @return 策略列表
     */
    @GetMapping("/policy/list")
    public R<List<OneIdPolicyVo>> policy() {
        return R.ok(platformService.selectOneIdPolicy());
    }

    /**
     * 查询 Legacy Code ↔ One ID 映射
     *
     * @param oneId One ID，为空时查全部
     * @return 映射列表
     */
    @GetMapping("/legacy/list")
    public R<List<CmdLegacyMapping>> legacy(@RequestParam(required = false) String oneId) {
        return R.ok(platformService.selectLegacyMapping(oneId));
    }

    /**
     * 查询某个 One ID 的变更历史
     *
     * @param oneId One ID
     * @return 历史事件
     */
    @GetMapping("/{oneId}/history")
    public R<List<AuditEvent>> history(@PathVariable String oneId) {
        return R.ok(platformService.selectOneIdHistory(oneId));
    }

    /**
     * 查询某个 One ID 的合并记录（总设计「审计与合并记录」：双向——保留方或被合并方）
     *
     * @param oneId One ID
     * @return 合并记录列表
     */
    @GetMapping("/{oneId}/mergeRecords")
    public R<List<CmdMergeRecord>> mergeRecords(@PathVariable String oneId) {
        return R.ok(platformService.selectMergeRecords(oneId));
    }
}
