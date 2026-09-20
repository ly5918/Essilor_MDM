package org.dromara.cmd.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.MatchRule;
import org.dromara.cmd.mapper.MatchRuleMapper;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
        return R.ok(matchRuleMapper.selectList(Wrappers.<MatchRule>lambdaQuery().eq(MatchRule::getDelFlag, "0")));
    }

    @PostMapping("/rule")
    public R<MatchRule> saveRule(@RequestBody MatchRule rule) {
        if (rule.getModelCode() == null) rule.setModelCode("CUSTOMER");
        if (rule.getScene() == null) rule.setScene("CREATE");
        if (rule.getAlgorithm() == null) rule.setAlgorithm("WEIGHTED");
        if (rule.getStatus() == null) rule.setStatus("0");
        if (rule.getVersionNo() == null) rule.setVersionNo("v1");
        if (rule.getExactThreshold() == null) rule.setExactThreshold(new BigDecimal("95.00"));
        if (rule.getSuspectThreshold() == null) rule.setSuspectThreshold(new BigDecimal("70.00"));
        MatchRule exist = matchRuleMapper.selectOne(Wrappers.<MatchRule>lambdaQuery()
            .eq(MatchRule::getRuleCode, rule.getRuleCode()).eq(MatchRule::getVersionNo, rule.getVersionNo()));
        if (exist != null) {
            rule.setId(exist.getId());
            matchRuleMapper.updateById(rule);
        } else {
            matchRuleMapper.insert(rule);
        }
        return R.ok(rule);
    }

    @DeleteMapping("/rule/{id}")
    public R<String> deleteRule(@PathVariable Long id) {
        matchRuleMapper.deleteById(id);
        return R.ok("匹配规则已删除");
    }

    @PostMapping("/simulate")
    public R<String> simulate(@RequestBody MatchRule rule) {
        return R.ok("模拟完成：" + rule.getRuleName() + " → " + rule.getAlgorithm());
    }
}