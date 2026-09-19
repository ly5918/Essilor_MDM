package org.dromara.cmd.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.DqRule;
import org.dromara.cmd.mapper.DqRuleMapper;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DQ规则控制层
 * <p>
 * 对应页面：平台管理 admin → DQ规则（模拟测试）
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/dq")
public class CmdDqController {

    private final DqRuleMapper dqRuleMapper;

    @GetMapping("/rule/list")
    public R<List<DqRule>> ruleList() {
        return R.ok(dqRuleMapper.selectList(null));
    }

    @PostMapping("/rule")
    public R<String> saveRule(@RequestBody DqRule rule) {
        dqRuleMapper.insertOrUpdate(rule);
        return R.ok("DQ规则已保存");
    }

    @DeleteMapping("/rule/{id}")
    public R<String> deleteRule(@PathVariable Long id) {
        dqRuleMapper.deleteById(id);
        return R.ok("DQ规则已删除");
    }

    @PostMapping("/simulate")
    public R<String> simulate(@RequestBody DqRule rule) {
        return R.ok("模拟完成：" + rule.getRuleName() + " → " + rule.getResult());
    }
}