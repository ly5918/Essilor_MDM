package org.dromara.cmd.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.MatchRule;
import org.dromara.cmd.mapper.MatchRuleMapper;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 匹配规则控制层
 * <p>
 * 对应页面：平台管理 admin → 匹配规则（模拟测试）
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/match")
public class CmdMatchController {

    private final MatchRuleMapper matchRuleMapper;

    @GetMapping("/rule/list")
    public R<List<MatchRule>> ruleList() {
        return R.ok(matchRuleMapper.selectList(null));
    }

    @PostMapping("/rule")
    public R<String> saveRule(@RequestBody MatchRule rule) {
        matchRuleMapper.insertOrUpdate(rule);
        return R.ok("匹配规则已保存");
    }

    @DeleteMapping("/rule/{id}")
    public R<String> deleteRule(@PathVariable Long id) {
        matchRuleMapper.deleteById(id);
        return R.ok("匹配规则已删除");
    }

    @PostMapping("/simulate")
    public R<String> simulate(@RequestBody MatchRule rule) {
        return R.ok("模拟完成：" + rule.getRuleName() + " → " + rule.getResult());
    }
}