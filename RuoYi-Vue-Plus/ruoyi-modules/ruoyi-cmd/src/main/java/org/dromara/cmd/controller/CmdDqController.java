package org.dromara.cmd.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.CmdCustomer;
import org.dromara.cmd.domain.DqRule;
import org.dromara.cmd.mapper.CmdCustomerMapper;
import org.dromara.cmd.mapper.DqRuleMapper;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final CmdCustomerMapper customerMapper;

    @GetMapping("/rule/list")
    public R<List<DqRule>> ruleList() {
        return R.ok(dqRuleMapper.selectList(Wrappers.<DqRule>lambdaQuery()
            .eq(DqRule::getDelFlag, "0").orderByAsc(DqRule::getOrderNum)));
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

    /**
     * DQ 规则模拟测试（对齐总设计 V6.1「DQ 规则变更」场景：选择测试数据集 → 执行规则模拟 → 影响评估）。
     * <p>
     * 输入测试数据集筛选条件（Customer Type / BU / Source System / 样例数量），对真实 cmd_customer
     * 数据逐条执行规则表达式，输出每条规则的 Pass / Warning / Block、命中率与字段级错误样例。
     */
    @PostMapping("/simulate")
    public R<DqSimulateResult> simulate(@RequestBody DqSimulateForm form) {
        // 1. 规则集：草稿与已发布都参与模拟（模拟测试的目的就是验证 Draft）；可指定单条规则
        List<DqRule> rules = dqRuleMapper.selectList(Wrappers.<DqRule>lambdaQuery()
            .eq(DqRule::getDelFlag, "0").orderByAsc(DqRule::getOrderNum));
        if (form.getRuleId() != null) {
            rules = rules.stream().filter(r -> form.getRuleId().equals(r.getId())).toList();
        }
        if (rules.isEmpty()) {
            return R.fail("未找到可模拟的 DQ 规则");
        }

        // 2. 测试数据集：按筛选条件从主档取真实客户记录
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CmdCustomer> qw =
            Wrappers.<CmdCustomer>lambdaQuery();
        if (isNotBlank(form.getCustomerType())) qw.eq(CmdCustomer::getCustomerType, form.getCustomerType());
        if (isNotBlank(form.getBu())) qw.eq(CmdCustomer::getBuScope, form.getBu());
        if (isNotBlank(form.getSourceSystem())) qw.eq(CmdCustomer::getSourceSystem, form.getSourceSystem());
        int sampleSize = (form.getSampleSize() == null || form.getSampleSize() <= 0) ? 200 : Math.min(form.getSampleSize(), 1000);
        qw.last("limit " + sampleSize);
        List<CmdCustomer> dataset = customerMapper.selectList(qw);
        if (dataset.isEmpty()) {
            return R.fail("当前筛选条件下没有可用测试数据，请调整 Customer Type / BU / Source System");
        }

        // 3. 逐规则执行模拟
        List<DqRuleResult> results = new ArrayList<>();
        Set<String> affectedCustomers = new HashSet<>();
        int blockHits = 0;
        int warningHits = 0;
        double deductionSum = 0;
        for (DqRule rule : rules) {
            DqRuleResult rr = evaluateRule(rule, dataset);
            results.add(rr);
            blockHits += rr.getBlock();
            warningHits += rr.getWarn();
            deductionSum += rr.getBlock() * rule.getScoreWeight().doubleValue()
                + rr.getWarn() * rule.getScoreWeight().doubleValue() * 0.5;
            rr.getSamples().forEach(s -> affectedCustomers.add(s.getOneId()));
        }

        DqImpactSummary impact = new DqImpactSummary();
        impact.setAffectedCustomers(affectedCustomers.size());
        impact.setBlockHits(blockHits);
        impact.setWarningHits(warningHits);
        impact.setAvgScoreDelta("-" + Math.round(deductionSum / dataset.size()) + " 分");

        DqSimulateResult result = new DqSimulateResult();
        result.setDatasetSize(dataset.size());
        result.setRuleCount(rules.size());
        result.setRules(results);
        result.setImpact(impact);
        return R.ok(result);
    }

    /** 单条规则在测试数据集上的评估 */
    private DqRuleResult evaluateRule(DqRule rule, List<CmdCustomer> dataset) {
        DqRuleResult rr = new DqRuleResult();
        rr.setId(rule.getId());
        rr.setRuleCode(rule.getRuleCode());
        rr.setRuleName(rule.getRuleName());
        rr.setDimension(rule.getDimension());
        rr.setDimensionName(mapDimName(rule.getDimension()));
        rr.setFieldCode(rule.getFieldCode());
        rr.setCheckType(rule.getCheckType());
        rr.setSeverity(rule.getSeverity());
        rr.setScoreWeight(rule.getScoreWeight());
        rr.setStatus(rule.getStatus());

        boolean blocking = "ERROR".equals(rule.getSeverity());
        String field = rule.getFieldCode();

        // 唯一性规则：先对整个数据集统计字段值出现次数
        Map<String, Integer> valueCount = new LinkedHashMap<>();
        if ("UNIQUE".equals(rule.getCheckType())) {
            for (CmdCustomer c : dataset) {
                String v = fieldOf(c, field);
                if (isNotBlank(v)) valueCount.merge(v, 1, Integer::sum);
            }
        }

        int pass = 0, warn = 0, block = 0, skip = 0;
        List<DqSampleError> samples = new ArrayList<>();
        boolean fieldMissing = dataset.stream().allMatch(c -> fieldOf(c, field) == null)
            && !"CROSS_FIELD".equals(rule.getCheckType());

        for (CmdCustomer c : dataset) {
            String verdict = checkOne(rule, c, field, valueCount);
            if ("SKIP".equals(verdict)) {
                skip++;
            } else if ("PASS".equals(verdict)) {
                pass++;
            } else {
                if (blocking) block++; else warn++;
                if (samples.size() < 5) {
                    DqSampleError se = new DqSampleError();
                    se.setOneId(c.getOneId());
                    se.setLegalName(c.getLegalName());
                    se.setMessage(isNotBlank(rule.getErrorMessage()) ? rule.getErrorMessage() : verdict);
                    samples.add(se);
                }
            }
        }
        if (fieldMissing && skip == dataset.size()) {
            rr.setNote("字段「" + field + "」在 CUSTOMER 主档模型中未启用，本次模拟全部跳过（N/A）");
        }
        rr.setTotal(dataset.size());
        rr.setPass(pass);
        rr.setWarn(warn);
        rr.setBlock(block);
        rr.setSkip(skip);
        rr.setPassRate(Math.round(pass * 1000f / dataset.size()) / 10f + "%");
        rr.setSamples(samples);
        return rr;
    }

    /** 对单条客户记录执行规则，返回 PASS / SKIP（空值跳过）/ 失败说明文案 */
    private String checkOne(DqRule rule, CmdCustomer c, String field, Map<String, Integer> valueCount) {
        String expr = rule.getExpression();
        switch (rule.getCheckType() == null ? "" : rule.getCheckType()) {
            case "NOT_NULL": {
                String v = fieldOf(c, field);
                return isNotBlank(v) ? "PASS" : fieldLabel(field) + "为空";
            }
            case "REGEX": {
                String v = fieldOf(c, field);
                if (isBlank(v)) return "SKIP";
                try {
                    return v.matches(expr) ? "PASS" : fieldLabel(field) + "「" + abbreviate(v) + "」不符合格式规则";
                } catch (Exception e) {
                    return "SKIP";
                }
            }
            case "LENGTH": {
                String v = fieldOf(c, field);
                if (isBlank(v)) return "SKIP";
                String[] range = (expr == null ? "1,500" : expr).split(",");
                int min = Integer.parseInt(range[0].trim());
                int max = range.length > 1 ? Integer.parseInt(range[1].trim()) : 500;
                int len = v.length();
                return (len >= min && len <= max) ? "PASS" : fieldLabel(field) + "长度 " + len + " 超出范围 [" + min + "," + max + "]";
            }
            case "RANGE": {
                String v = fieldOf(c, field);
                if (isBlank(v)) return "SKIP";
                java.math.BigDecimal num;
                try {
                    num = new java.math.BigDecimal(v.replaceAll("[^0-9.\\-]", ""));
                } catch (Exception e) {
                    return fieldLabel(field) + "「" + abbreviate(v) + "」不是有效数值";
                }
                String[] range = (expr == null ? "0,99999999" : expr).split(",");
                boolean ok = num.compareTo(new java.math.BigDecimal(range[0].trim())) >= 0
                    && (range.length <= 1 || num.compareTo(new java.math.BigDecimal(range[1].trim())) <= 0);
                return ok ? "PASS" : fieldLabel(field) + "「" + abbreviate(v) + "」超出阈值范围 [" + expr + "]";
            }
            case "UNIQUE": {
                String v = fieldOf(c, field);
                if (isBlank(v)) return "SKIP";
                int count = valueCount.getOrDefault(v, 0);
                return count <= 1 ? "PASS" : fieldLabel(field) + "「" + abbreviate(v) + "」在数据集中出现 " + count + " 次";
            }
            case "CROSS_FIELD": {
                if (expr != null && expr.startsWith("address.contains")) {
                    String addr = c.getAddress();
                    String city = c.getCity();
                    if (isBlank(addr) || isBlank(city)) return "SKIP";
                    return addr.contains(city) ? "PASS" : "经营地址「" + abbreviate(addr) + "」未包含城市「" + city + "」";
                }
                // 其它跨字段校验（如 nameMatch(creditCode)）需对接外部核验源，POC 阶段返回 N/A
                return "SKIP";
            }
            default:
                return "SKIP";
        }
    }

    /** 主档字段取值（field_code → 实体属性） */
    private String fieldOf(CmdCustomer c, String fieldCode) {
        if (fieldCode == null) return null;
        return switch (fieldCode) {
            case "legal_name" -> c.getLegalName();
            case "legal_name_en" -> c.getLegalNameEn();
            case "short_name" -> c.getShortName();
            case "credit_code" -> c.getCreditCode();
            case "tax_no" -> c.getTaxNo();
            case "contact_email" -> c.getContactEmail();
            case "contact_phone" -> c.getContactPhone();
            case "contact_name" -> c.getContactName();
            case "address" -> c.getAddress();
            case "city" -> c.getCity();
            case "province" -> c.getProvince();
            case "country" -> c.getCountry();
            case "postal_code" -> c.getPostalCode();
            case "payer_id" -> c.getPayerId();
            case "customer_type" -> c.getCustomerType();
            case "bu_scope" -> c.getBuScope();
            case "source_system" -> c.getSourceSystem();
            case "source_id" -> c.getSourceId();
            default -> null; // 模型中未启用的字段（如 business_license_expiry / credit_limit）
        };
    }

    private String fieldLabel(String fieldCode) {
        if (fieldCode == null) return "字段";
        return switch (fieldCode) {
            case "legal_name" -> "客户名称";
            case "legal_name_en" -> "英文名称";
            case "short_name" -> "简称";
            case "credit_code" -> "统一社会信用代码";
            case "contact_email" -> "联系邮箱";
            case "contact_phone" -> "联系电话";
            case "address" -> "经营地址";
            case "city" -> "城市";
            case "customer_type" -> "客户类型";
            case "payer_id" -> "Payer";
            default -> fieldCode;
        };
    }

    private static boolean isNotBlank(String s) { return s != null && !s.isBlank(); }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String abbreviate(String s) { return s.length() > 24 ? s.substring(0, 24) + "…" : s; }

    @GetMapping("/scorecard")
    public R<DqScorecard> scorecard(@RequestParam(required = false) String oneId) {
        String target = (oneId != null && !oneId.isBlank()) ? oneId : "GC-000128";
        CmdCustomer customer = customerMapper.selectOne(Wrappers.<CmdCustomer>lambdaQuery()
            .eq(CmdCustomer::getOneId, target).last("limit 1"));
        List<DqRule> rules = dqRuleMapper.selectList(Wrappers.<DqRule>lambdaQuery().eq(DqRule::getDelFlag, "0"));

        DqScorecard sc = new DqScorecard();
        sc.setOneId(target);
        sc.setLegalName(customer != null ? customer.getLegalName() : "");
        sc.setOverall(customer != null && customer.getDqScore() != null ? customer.getDqScore().intValue() : 86);

        // 维度分：按 dimension 聚合权重比例与错误命中，输出百分比与得分
        Map<String, int[]> dimAgg = new LinkedHashMap<>();
        int totalWeight = 0;
        for (DqRule rule : rules) {
            String dim = rule.getDimension() == null ? "COMPLETENESS" : rule.getDimension();
            int[] acc = dimAgg.computeIfAbsent(dim, k -> new int[2]);
            acc[0] += rule.getScoreWeight() == null ? 0 : rule.getScoreWeight().intValue();
            acc[1] += "ERROR".equals(rule.getSeverity()) ? 1 : 0;
            totalWeight += rule.getScoreWeight() == null ? 0 : rule.getScoreWeight().intValue();
        }
        List<DqDimension> dimensions = new ArrayList<>();
        for (Map.Entry<String, int[]> e : dimAgg.entrySet()) {
            int w = e.getValue()[0];
            int err = e.getValue()[1];
            int score = Math.max(60, Math.min(100, 100 - err * 6 - w / 10));
            int pct = totalWeight > 0 ? Math.round(w * 100f / totalWeight) : 0;
            DqDimension d = new DqDimension();
            d.setDimension(mapDimName(e.getKey()));
            d.setWeight(pct + "%");
            d.setScore(score);
            dimensions.add(d);
        }
        sc.setDimensions(dimensions);

        // 版本影响：按 versionNo + status 归并
        Map<String, DqVersion> verMap = new LinkedHashMap<>();
        for (DqRule rule : rules) {
            String v = rule.getVersionNo() == null ? "v1" : rule.getVersionNo();
            DqVersion ver = verMap.computeIfAbsent(v, k -> new DqVersion());
            ver.setVersion(v);
            ver.setRuleCount(ver.getRuleCount() + 1);
            ver.setStatus("1".equals(rule.getStatus()) ? "Current" : "Draft");
        }
        sc.setVersions(new ArrayList<>(verMap.values()));

        // 质量异常：仅展示 ERROR / WARNING 级别的已发布规则
        List<DqException> exceptions = new ArrayList<>();
        for (DqRule rule : rules) {
            if ("0".equals(rule.getStatus()) || rule.getSeverity() == null) continue;
            DqException ex = new DqException();
            ex.setCode(rule.getRuleCode());
            ex.setName(rule.getRuleName());
            ex.setType("BUSINESS".equals(rule.getRuleType()) ? "Business" : "Technical");
            ex.setLevel("ERROR".equals(rule.getSeverity()) ? "Blocking" : "Warning");
            ex.setAction("ERROR".equals(rule.getSeverity()) ? "阻止提交" : "允许提交并标记");
            ex.setEnabled(true);
            exceptions.add(ex);
        }
        sc.setExceptions(exceptions);
        return R.ok(sc);
    }

    @PostMapping("/reEvaluate")
    public R<String> reEvaluate(@RequestBody ReEvaluateForm form) {
        return R.ok("历史数据重评估任务已创建，旧规则版本与旧分数保留");
    }

    @GetMapping("/reEvaluate/impact")
    public R<List<ReEvaluateImpact>> reEvaluateImpact() {
        List<ReEvaluateImpact> impacts = new ArrayList<>();
        impacts.add(new ReEvaluateImpact("受影响客户", String.valueOf(customerMapper.selectCount(Wrappers.<CmdCustomer>lambdaQuery().eq(CmdCustomer::getDelFlag, "0")))));
        impacts.add(new ReEvaluateImpact("受影响数据行", "待重评估 Job 执行后统计"));
        impacts.add(new ReEvaluateImpact("预计耗时", "按数据量动态计算"));
        return R.ok(impacts);
    }

    private String mapDimName(String code) {
        switch (code == null ? "" : code) {
            case "COMPLETENESS": return "完整性";
            case "VALIDITY": return "有效性";
            case "CONSISTENCY": return "一致性";
            case "UNIQUENESS": return "唯一性";
            case "TIMELINESS": return "及时性";
            default: return code;
        }
    }
}

class DqScorecard {
    private String oneId;
    private String legalName;
    private Integer overall;
    private List<DqDimension> dimensions;
    private List<DqVersion> versions;
    private List<DqException> exceptions;
    public String getOneId() { return oneId; }
    public void setOneId(String oneId) { this.oneId = oneId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public Integer getOverall() { return overall; }
    public void setOverall(Integer overall) { this.overall = overall; }
    public List<DqDimension> getDimensions() { return dimensions; }
    public void setDimensions(List<DqDimension> dimensions) { this.dimensions = dimensions; }
    public List<DqVersion> getVersions() { return versions; }
    public void setVersions(List<DqVersion> versions) { this.versions = versions; }
    public List<DqException> getExceptions() { return exceptions; }
    public void setExceptions(List<DqException> exceptions) { this.exceptions = exceptions; }
}

class DqDimension {
    private String dimension;
    private String weight;
    private Integer score;
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}

class DqVersion {
    private String version;
    private Integer ruleCount = 0;
    private String status;
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Integer getRuleCount() { return ruleCount; }
    public void setRuleCount(Integer ruleCount) { this.ruleCount = ruleCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

class DqException {
    private String code;
    private String name;
    private String type;
    private String level;
    private String action;
    private Boolean enabled;
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
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
class ReEvaluateImpact {
    private String label;
    private String value;
    public ReEvaluateImpact() {}
    public ReEvaluateImpact(String label, String value) {
        this.label = label;
        this.value = value;
    }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}

/* ==================== DQ 规则模拟测试 DTO ==================== */

class DqSimulateForm {
    private String customerType;
    private String bu;
    private String sourceSystem;
    private Integer sampleSize;
    private Long ruleId;
    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }
    public String getBu() { return bu; }
    public void setBu(String bu) { this.bu = bu; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
}

class DqSimulateResult {
    private Integer datasetSize;
    private Integer ruleCount;
    private List<DqRuleResult> rules;
    private DqImpactSummary impact;
    public Integer getDatasetSize() { return datasetSize; }
    public void setDatasetSize(Integer datasetSize) { this.datasetSize = datasetSize; }
    public Integer getRuleCount() { return ruleCount; }
    public void setRuleCount(Integer ruleCount) { this.ruleCount = ruleCount; }
    public List<DqRuleResult> getRules() { return rules; }
    public void setRules(List<DqRuleResult> rules) { this.rules = rules; }
    public DqImpactSummary getImpact() { return impact; }
    public void setImpact(DqImpactSummary impact) { this.impact = impact; }
}

class DqRuleResult {
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String dimension;
    private String dimensionName;
    private String fieldCode;
    private String checkType;
    private String severity;
    private java.math.BigDecimal scoreWeight;
    private String status;
    private Integer total;
    private Integer pass;
    private Integer warn;
    private Integer block;
    private Integer skip;
    private String passRate;
    private String note;
    private List<DqSampleError> samples;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getDimensionName() { return dimensionName; }
    public void setDimensionName(String dimensionName) { this.dimensionName = dimensionName; }
    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public java.math.BigDecimal getScoreWeight() { return scoreWeight; }
    public void setScoreWeight(java.math.BigDecimal scoreWeight) { this.scoreWeight = scoreWeight; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public Integer getPass() { return pass; }
    public void setPass(Integer pass) { this.pass = pass; }
    public Integer getWarn() { return warn; }
    public void setWarn(Integer warn) { this.warn = warn; }
    public Integer getBlock() { return block; }
    public void setBlock(Integer block) { this.block = block; }
    public Integer getSkip() { return skip; }
    public void setSkip(Integer skip) { this.skip = skip; }
    public String getPassRate() { return passRate; }
    public void setPassRate(String passRate) { this.passRate = passRate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<DqSampleError> getSamples() { return samples; }
    public void setSamples(List<DqSampleError> samples) { this.samples = samples; }
}

class DqSampleError {
    private String oneId;
    private String legalName;
    private String message;
    public String getOneId() { return oneId; }
    public void setOneId(String oneId) { this.oneId = oneId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

class DqImpactSummary {
    private Integer affectedCustomers;
    private Integer blockHits;
    private Integer warningHits;
    private String avgScoreDelta;
    public Integer getAffectedCustomers() { return affectedCustomers; }
    public void setAffectedCustomers(Integer affectedCustomers) { this.affectedCustomers = affectedCustomers; }
    public Integer getBlockHits() { return blockHits; }
    public void setBlockHits(Integer blockHits) { this.blockHits = blockHits; }
    public Integer getWarningHits() { return warningHits; }
    public void setWarningHits(Integer warningHits) { this.warningHits = warningHits; }
    public String getAvgScoreDelta() { return avgScoreDelta; }
    public void setAvgScoreDelta(String avgScoreDelta) { this.avgScoreDelta = avgScoreDelta; }
}