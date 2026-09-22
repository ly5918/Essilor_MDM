package org.dromara.cmd.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.MatchRule;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.MatchRuleMapper;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 匹配规则控制层
 * <p>
 * 对应页面：平台管理 admin → 匹配规则（模拟测试）。
 * 对齐总设计 V6.1「匹配规则变更」场景：规则调整（字段组合 / 阈值 / 标准化）→ 样例模拟
 * （运行测试数据集）→ 输出候选与解释（字段贡献、相似度、候选 One ID、Exact / Suspected / New 分类）。
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/cmd/match")
public class CmdMatchController {

    /** 加权匹配的字段权重（对齐总设计：信用代码 / 经营地址 / 辅助名称 / BU 路由） */
    private static final Map<String, Integer> FIELD_WEIGHTS = Map.of(
        "credit_code", 40,
        "legal_name", 30,
        "address", 20,
        "bu_scope", 10
    );

    private final MatchRuleMapper matchRuleMapper;
    private final CmdCustomerMapper customerMapper;

    @GetMapping("/rule/list")
    public R<List<MatchRule>> ruleList() {
        return R.ok(matchRuleMapper.selectList(Wrappers.<MatchRule>lambdaQuery()
            .eq(MatchRule::getDelFlag, "0").orderByAsc(MatchRule::getOrderNum)));
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
        if (rule.getAutoMergeFlag() == null) rule.setAutoMergeFlag("N");
        if (rule.getCrossBuFlag() == null) rule.setCrossBuFlag("Y");
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

    /**
     * 匹配规则样例模拟：以一条样例记录（从主档选择或手工输入）对全量主档执行
     * 标准化 + 加权相似度计算，输出候选 One ID、字段贡献与 Exact / Suspected / New 分类分布。
     */
    @PostMapping("/simulate")
    public R<MatchSimulateResult> simulate(@RequestBody MatchSimulateForm form) {
        // 1. 取匹配规则：可指定，否则取第一条（按显示顺序）
        List<MatchRule> rules = matchRuleMapper.selectList(Wrappers.<MatchRule>lambdaQuery()
            .eq(MatchRule::getDelFlag, "0").orderByAsc(MatchRule::getOrderNum));
        if (form.getRuleId() != null) {
            rules = rules.stream().filter(r -> form.getRuleId().equals(r.getId())).toList();
        }
        if (rules.isEmpty()) {
            return R.fail("未找到可模拟的匹配规则，请先新增规则");
        }
        MatchRule rule = rules.get(0);

        // 2. 解析样例记录：优先 oneId 回读主档，其次手工输入字段
        String sampleOneId = form.getOneId();
        String sName = form.getLegalName();
        String sCredit = form.getCreditCode();
        String sAddr = form.getAddress();
        String sBu = form.getBu();
        if (sampleOneId != null && !sampleOneId.isBlank()) {
            CmdCustomer sample = customerMapper.selectOne(Wrappers.<CmdCustomer>lambdaQuery()
                .eq(CmdCustomer::getOneId, sampleOneId).last("limit 1"));
            if (sample == null) {
                return R.fail("样例客户不存在：" + sampleOneId);
            }
            sName = sample.getLegalName();
            sCredit = sample.getCreditCode();
            sAddr = sample.getAddress();
            sBu = sample.getBuScope();
        }
        if (isBlank(sName) && isBlank(sCredit) && isBlank(sAddr)) {
            return R.fail("请先选择样例客户，或填写名称 / 信用代码 / 地址中的至少一项");
        }

        // 3. 候选扫描范围：跨 BU 规则扫全量，否则限定样例同 BU；排除样例自身
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CmdCustomer> qw =
            Wrappers.<CmdCustomer>lambdaQuery();
        if (!"Y".equals(rule.getCrossBuFlag()) && !isBlank(sBu)) {
            qw.eq(CmdCustomer::getBuScope, sBu);
        }
        if (sampleOneId != null && !sampleOneId.isBlank()) {
            qw.ne(CmdCustomer::getOneId, sampleOneId);
        }
        qw.last("limit 2000");
        List<CmdCustomer> pool = customerMapper.selectList(qw);

        // 4. 逐候选计算加权相似度（标准化 → 字段相似 → 阈值分层）
        boolean exactOnly = "EXACT".equals(rule.getAlgorithm());
        BigDecimal exactThreshold = rule.getExactThreshold() == null ? new BigDecimal("95") : rule.getExactThreshold();
        BigDecimal suspectThreshold = rule.getSuspectThreshold() == null ? new BigDecimal("70") : rule.getSuspectThreshold();

        List<MatchCandidateVO> candidates = new ArrayList<>();
        MatchDistribution distribution = new MatchDistribution();
        Map<String, List<String>> sampleTokens = new HashMap<>();
        for (Map.Entry<String, Integer> e : FIELD_WEIGHTS.entrySet()) {
            sampleTokens.put(e.getKey(), bigrams(normalize(valueOf(sampleOneId, e.getKey(), sName, sCredit, sAddr, sBu))));
        }
        for (CmdCustomer c : pool) {
            List<MatchFieldScore> fields = new ArrayList<>();
            double weighted = 0;
            double weightSum = 0;
            for (Map.Entry<String, Integer> e : FIELD_WEIGHTS.entrySet()) {
                String field = e.getKey();
                int weight = e.getValue();
                String sampleVal = valueOf(sampleOneId, field, sName, sCredit, sAddr, sBu);
                String candVal = valueOf(c.getOneId(), field, c.getLegalName(), c.getCreditCode(), c.getAddress(), c.getBuScope());
                if (exactOnly && !"credit_code".equals(field)) {
                    continue; // EXACT 算法仅按信用代码全等匹配
                }
                if (isBlank(sampleVal) || isBlank(candVal)) {
                    continue; // 空值不参与加权（对齐总设计「空值策略」）
                }
                int score;
                String detail;
                if ("credit_code".equals(field)) {
                    score = sampleVal.equals(candVal) ? 100 : 0;
                    detail = score == 100 ? "全等匹配" : "不一致";
                } else if ("bu_scope".equals(field)) {
                    score = sampleVal.equals(candVal) ? 100 : 0;
                    detail = score == 100 ? "同 BU" : "跨 BU";
                } else {
                    score = (int) Math.round(dice(sampleTokens.get(field), bigrams(normalize(candVal))) * 100);
                    detail = score == 100 ? "标准化后一致" : "相似度 " + score + "%";
                }
                weighted += score * weight;
                weightSum += weight;
                if (fields.size() < 8) {
                    MatchFieldScore fs = new MatchFieldScore();
                    fs.setField(field);
                    fs.setLabel(fieldLabel(field));
                    fs.setWeight(weight);
                    fs.setScore(score);
                    fs.setDetail(detail);
                    fields.add(fs);
                }
            }
            if (weightSum == 0) {
                continue;
            }
            double score = weighted / weightSum;
            String verdict = score >= exactThreshold.doubleValue() ? "EXACT"
                : score >= suspectThreshold.doubleValue() ? "SUSPECTED" : "BELOW";
            if ("EXACT".equals(verdict)) distribution.setExact(distribution.getExact() + 1);
            else if ("SUSPECTED".equals(verdict)) distribution.setSuspected(distribution.getSuspected() + 1);
            else distribution.setBelow(distribution.getBelow() + 1);

            if (!"BELOW".equals(verdict) || candidates.size() < 10) {
                MatchCandidateVO vo = new MatchCandidateVO();
                vo.setOneId(c.getOneId());
                vo.setLegalName(c.getLegalName());
                vo.setCreditCode(c.getCreditCode());
                vo.setBuScope(c.getBuScope());
                vo.setStatus(c.getStatus());
                vo.setScore(BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP).doubleValue());
                vo.setResult(verdict);
                vo.setFields(fields);
                candidates.add(vo);
            }
        }
        // 得分降序，最多展示前 10 条候选
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (candidates.size() > 10) candidates = new ArrayList<>(candidates.subList(0, 10));

        MatchRuleBrief brief = new MatchRuleBrief();
        brief.setRuleCode(rule.getRuleCode());
        brief.setRuleName(rule.getRuleName());
        brief.setScene(rule.getScene());
        brief.setAlgorithm(rule.getAlgorithm());
        brief.setExactThreshold(exactThreshold.toPlainString());
        brief.setSuspectThreshold(suspectThreshold.toPlainString());
        brief.setCrossBuFlag(rule.getCrossBuFlag());
        brief.setAutoMergeFlag(rule.getAutoMergeFlag());

        MatchSampleVO sampleVo = new MatchSampleVO();
        sampleVo.setOneId(sampleOneId);
        sampleVo.setLegalName(sName);
        sampleVo.setCreditCode(sCredit);
        sampleVo.setAddress(sAddr);
        sampleVo.setBuScope(sBu);

        MatchSimulateResult result = new MatchSimulateResult();
        result.setRule(brief);
        result.setSample(sampleVo);
        result.setScanned(pool.size());
        result.setDistribution(distribution);
        result.setCandidates(candidates);
        return R.ok(result);
    }

    private String valueOf(String oneId, String field, String name, String credit, String addr, String bu) {
        return switch (field) {
            case "legal_name" -> name;
            case "credit_code" -> credit;
            case "address" -> addr;
            case "bu_scope" -> bu;
            default -> oneId;
        };
    }

    /** 标准化：小写、去除非字母数字汉字（对齐 normalize_rule 的 POC 实现） */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** 字符 bigram 集合 */
    private static List<String> bigrams(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.length() < 2) {
            if (s != null && !s.isEmpty()) out.add(s);
            return out;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            out.add(s.substring(i, i + 2));
        }
        return out;
    }

    /** Dice 系数（0~1）：2×|A∩B| / (|A|+|B|) */
    private static double dice(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Map<String, Integer> count = new HashMap<>();
        a.forEach(t -> count.merge(t, 1, Integer::sum));
        int overlap = 0;
        for (String t : b) {
            int left = count.getOrDefault(t, 0);
            if (left > 0) {
                overlap++;
                count.put(t, left - 1);
            }
        }
        return 2.0 * overlap / (a.size() + b.size());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String fieldLabel(String field) {
        return switch (field) {
            case "credit_code" -> "统一社会信用代码";
            case "legal_name" -> "客户名称";
            case "address" -> "经营地址";
            case "bu_scope" -> "BU 归属";
            default -> field;
        };
    }
}

/* ==================== 匹配样例模拟 DTO ==================== */

class MatchSimulateForm {
    private String oneId;
    private String legalName;
    private String creditCode;
    private String address;
    private String bu;
    private Long ruleId;
    public String getOneId() { return oneId; }
    public void setOneId(String oneId) { this.oneId = oneId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getCreditCode() { return creditCode; }
    public void setCreditCode(String creditCode) { this.creditCode = creditCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getBu() { return bu; }
    public void setBu(String bu) { this.bu = bu; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
}

class MatchRuleBrief {
    private String ruleCode;
    private String ruleName;
    private String scene;
    private String algorithm;
    private String exactThreshold;
    private String suspectThreshold;
    private String crossBuFlag;
    private String autoMergeFlag;
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public String getExactThreshold() { return exactThreshold; }
    public void setExactThreshold(String exactThreshold) { this.exactThreshold = exactThreshold; }
    public String getSuspectThreshold() { return suspectThreshold; }
    public void setSuspectThreshold(String suspectThreshold) { this.suspectThreshold = suspectThreshold; }
    public String getCrossBuFlag() { return crossBuFlag; }
    public void setCrossBuFlag(String crossBuFlag) { this.crossBuFlag = crossBuFlag; }
    public String getAutoMergeFlag() { return autoMergeFlag; }
    public void setAutoMergeFlag(String autoMergeFlag) { this.autoMergeFlag = autoMergeFlag; }
}

class MatchSampleVO {
    private String oneId;
    private String legalName;
    private String creditCode;
    private String address;
    private String buScope;
    public String getOneId() { return oneId; }
    public void setOneId(String oneId) { this.oneId = oneId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getCreditCode() { return creditCode; }
    public void setCreditCode(String creditCode) { this.creditCode = creditCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getBuScope() { return buScope; }
    public void setBuScope(String buScope) { this.buScope = buScope; }
}

class MatchSimulateResult {
    private MatchRuleBrief rule;
    private MatchSampleVO sample;
    private Integer scanned;
    private MatchDistribution distribution;
    private List<MatchCandidateVO> candidates;
    public MatchRuleBrief getRule() { return rule; }
    public void setRule(MatchRuleBrief rule) { this.rule = rule; }
    public MatchSampleVO getSample() { return sample; }
    public void setSample(MatchSampleVO sample) { this.sample = sample; }
    public Integer getScanned() { return scanned; }
    public void setScanned(Integer scanned) { this.scanned = scanned; }
    public MatchDistribution getDistribution() { return distribution; }
    public void setDistribution(MatchDistribution distribution) { this.distribution = distribution; }
    public List<MatchCandidateVO> getCandidates() { return candidates; }
    public void setCandidates(List<MatchCandidateVO> candidates) { this.candidates = candidates; }
}

class MatchDistribution {
    private int exact;
    private int suspected;
    private int below;
    public int getExact() { return exact; }
    public void setExact(int exact) { this.exact = exact; }
    public int getSuspected() { return suspected; }
    public void setSuspected(int suspected) { this.suspected = suspected; }
    public int getBelow() { return below; }
    public void setBelow(int below) { this.below = below; }
}

class MatchCandidateVO {
    private String oneId;
    private String legalName;
    private String creditCode;
    private String buScope;
    private String status;
    private Double score;
    private String result;
    private List<MatchFieldScore> fields;
    public String getOneId() { return oneId; }
    public void setOneId(String oneId) { this.oneId = oneId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getCreditCode() { return creditCode; }
    public void setCreditCode(String creditCode) { this.creditCode = creditCode; }
    public String getBuScope() { return buScope; }
    public void setBuScope(String buScope) { this.buScope = buScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public List<MatchFieldScore> getFields() { return fields; }
    public void setFields(List<MatchFieldScore> fields) { this.fields = fields; }
}

class MatchFieldScore {
    private String field;
    private String label;
    private Integer weight;
    private Integer score;
    private String detail;
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
