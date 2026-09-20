package org.dromara.cmd.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
        return R.ok(dqRuleMapper.selectList(Wrappers.<DqRule>lambdaQuery().eq(DqRule::getDelFlag, "0")));
    }

    @PostMapping("/rule")
    public R<DqRule> saveRule(@RequestBody DqRule rule) {
        if (rule.getModelCode() == null) rule.setModelCode("CUSTOMER");
        if (rule.getCheckType() == null) rule.setCheckType("NOT_NULL");
        if (rule.getRuleType() == null) rule.setRuleType("TECHNICAL");
        if (rule.getSeverity() == null) rule.setSeverity("WARNING");
        if (rule.getStatus() == null) rule.setStatus("0");
        if (rule.getVersionNo() == null) rule.setVersionNo("v1");
        if (rule.getScoreWeight() == null) rule.setScoreWeight(new java.math.BigDecimal("10.00"));
        DqRule exist = dqRuleMapper.selectOne(Wrappers.<DqRule>lambdaQuery()
            .eq(DqRule::getRuleCode, rule.getRuleCode()).eq(DqRule::getVersionNo, rule.getVersionNo()));
        if (exist != null) {
            rule.setId(exist.getId());
            dqRuleMapper.updateById(rule);
        } else {
            dqRuleMapper.insert(rule);
        }
        return R.ok(rule);
    }

    @DeleteMapping("/rule/{id}")
    public R<String> deleteRule(@PathVariable Long id) {
        dqRuleMapper.deleteById(id);
        return R.ok("DQ规则已删除");
    }

    @PostMapping("/simulate")
    public R<String> simulate(@RequestBody DqRule rule) {
        return R.ok("模拟完成：" + rule.getRuleName() + " → " + rule.getSeverity());
    }

    @GetMapping("/scorecard")
    public R<DqScorecard> scorecard(@RequestParam(required = false) String oneId) {
        DqScorecard sc = new DqScorecard();
        sc.setOneId(oneId != null ? oneId : "GC-000128");
        sc.setOverall(86);
        return R.ok(sc);
    }

    @PostMapping("/reEvaluate")
    public R<String> reEvaluate(@RequestBody ReEvaluateForm form) {
        return R.ok("历史数据重评估任务已创建，旧规则版本与旧分数保留");
    }
}

class DqScorecard {
    private String oneId;
    private Integer overall;
    public String getOneId() { return oneId; }
    public void setOneId(String oneId) { this.oneId = oneId; }
    public Integer getOverall() { return overall; }
    public void setOverall(Integer overall) { this.overall = overall; }
}

class ReEvaluateForm {
    private String targetVersion;
    private String dataScope;
    private String execMode;
    private String exceptionHandling;
    public String getTargetVersion() { return targetVersion; }
    public void setTargetVersion(String targetVersion) { this.targetVersion = targetVersion; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public String getExecMode() { return execMode; }
    public void setExecMode(String execMode) { this.execMode = execMode; }
    public String getExceptionHandling() { return exceptionHandling; }
    public void setExceptionHandling(String exceptionHandling) { this.exceptionHandling = exceptionHandling; }
}