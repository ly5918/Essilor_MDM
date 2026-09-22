package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CfgSequence;
import org.dromara.cmd.domain.CmdLegacyMapping;
import org.dromara.cmd.domain.CmdMergeRecord;
import org.dromara.cmd.domain.CmdRole;
import org.dromara.cmd.domain.MdField;
import org.dromara.cmd.domain.MdValueSet;
import org.dromara.cmd.domain.OneIdRule;
import org.dromara.cmd.domain.vo.PermissionMatrixVo;
import org.dromara.cmd.domain.vo.PlatformVersionVo;
import org.dromara.cmd.mapper.AuditEventMapper;
import org.dromara.cmd.mapper.CfgSequenceMapper;
import org.dromara.cmd.mapper.CmdLegacyMappingMapper;
import org.dromara.cmd.mapper.CmdMergeRecordMapper;
import org.dromara.cmd.mapper.CmdRoleMapper;
import org.dromara.cmd.mapper.MdFieldMapper;
import org.dromara.cmd.mapper.MdValueSetMapper;
import org.dromara.cmd.mapper.OneIdRuleMapper;
import org.dromara.cmd.service.ICmdPlatformService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台管理 / One ID 服务层实现
 * <p>
 * 关键设计：
 * <ol>
 *   <li>配置类查询全部走单表，避免复杂联表，便于运维直接改数据</li>
 *   <li>权限矩阵与 One ID 策略为平台展示规则，POC 阶段按常量生成，后续可改为读 cfg_config</li>
 * </ol>
 *
 * @author Essilor CMD POC
 */
@RequiredArgsConstructor
@Service
public class CmdPlatformServiceImpl implements ICmdPlatformService {

    private final MdFieldMapper fieldMapper;
    private final MdValueSetMapper valueSetMapper;
    private final CmdRoleMapper roleMapper;
    private final OneIdRuleMapper oneIdRuleMapper;
    private final CfgSequenceMapper cfgSequenceMapper;
    private final CmdLegacyMappingMapper legacyMappingMapper;
    private final CmdMergeRecordMapper mergeRecordMapper;
    private final AuditEventMapper auditEventMapper;

    /**
     * 总设计 V6.1 点名的核心主数据字段（字段编码 → 不可删原因），删除会破坏匹配 / DQ / 主档完整性。
     * <ul>
     *   <li>credit_code / address：DESIGN BOUNDARIES「信用代码与经营地址为主要匹配依据」</li>
     *   <li>province / city / contact_name / contact_phone：DQ Scorecard 必评维度</li>
     *   <li>legal_name / customer_type / bu_scope / country：主档必填属性与数据权限维度</li>
     *   <li>status：生命周期状态（总设计「逻辑停用」依赖该字段）</li>
     * </ul>
     */
    private static final java.util.Map<String, String> PROTECTED_FIELDS = java.util.Map.ofEntries(
        java.util.Map.entry("legal_name", "客户法定名称，主档主键属性"),
        java.util.Map.entry("credit_code", "总设计 DESIGN BOUNDARIES：主要匹配依据"),
        java.util.Map.entry("address", "总设计 DESIGN BOUNDARIES：主要匹配依据 + DQ 必评项"),
        java.util.Map.entry("province", "DQ 必评维度（地址）"),
        java.util.Map.entry("city", "DQ 必评维度（地址）"),
        java.util.Map.entry("contact_name", "DQ 必评维度（联系人）"),
        java.util.Map.entry("contact_phone", "DQ 必评维度（联系电话）"),
        java.util.Map.entry("customer_type", "主档必填属性（客户分层维度）"),
        java.util.Map.entry("bu_scope", "主档必填属性（数据权限维度）"),
        java.util.Map.entry("country", "主档必填属性"),
        java.util.Map.entry("status", "生命周期状态，逻辑停用依赖")
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MdField> selectFieldList(String keyword, String modelCode) {
        LambdaQueryWrapper<MdField> lqw = Wrappers.lambdaQuery();
        // 已删除（del_flag='1'）的字段不进字段目录与业务表单
        lqw.eq(MdField::getDelFlag, "0");
        if (StringUtils.isNotBlank(modelCode)) {
            lqw.eq(MdField::getModelCode, modelCode);
        }
        if (StringUtils.isNotBlank(keyword)) {
            lqw.and(w -> w.like(MdField::getFieldCode, keyword)
                .or().like(MdField::getFieldName, keyword));
        }
        lqw.orderByAsc(MdField::getOrderNum);
        List<MdField> list = fieldMapper.selectList(lqw);
        // 回填「不可删原因」，供前端禁用删除按钮（规则集中在后端，避免前后端漂移）
        list.forEach(field -> field.setDeleteGuard(protectedReason(field)));
        return list;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 口径与前端 listMetadataFields 完全一致（已发布 → 当前版本 → 按 field_code 去重）：
     * 后端提交校验与前端动态表单必须读同一套字段，否则会出现「表单没这个字段、
     * 后端却判必填缺失」或「表单有这个字段、后端不校验」的漂移。
     */
    @Override
    public List<MdField> selectPublishedFields() {
        List<MdField> all = fieldMapper.selectList(
            Wrappers.<MdField>lambdaQuery().orderByAsc(MdField::getOrderNum).orderByAsc(MdField::getId));
        List<MdField> published = all.stream()
            .filter(f -> "0".equals(f.getStatus()) && StringUtils.isNotBlank(f.getFieldCode()))
            .toList();
        String current = published.stream()
            .map(f -> StringUtils.blankToDefault(f.getVersionNo(), ""))
            .filter(StringUtils::isNotBlank)
            .max(CmdPlatformServiceImpl::compareVersion)
            .orElse("");
        List<MdField> scoped = StringUtils.isBlank(current)
            ? published
            : published.stream().filter(f -> current.equals(StringUtils.blankToDefault(f.getVersionNo(), ""))).toList();
        // 同版本内同 field_code 仍可能有多行（历史测试数据）→ 保留排序后的首条
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<MdField> result = new ArrayList<>();
        for (MdField field : scoped) {
            if (seen.add(field.getFieldCode())) {
                result.add(field);
            }
        }
        return result;
    }

    /**
     * 版本号比较（v1.10 &gt; v1.9，字符串比较会判反）
     *
     * @param a 版本号
     * @param b 版本号
     * @return 比较结果
     */
    private static int compareVersion(String a, String b) {
        int[] pa = versionSegments(a);
        int[] pb = versionSegments(b);
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int d = (i < pa.length ? pa[i] : 0) - (i < pb.length ? pb[i] : 0);
            if (d != 0) {
                return d;
            }
        }
        return 0;
    }

    /**
     * 版本号拆分为数字段（去掉前导 v，按 . 切分）
     */
    private static int[] versionSegments(String version) {
        String[] parts = StringUtils.blankToDefault(version, "0").replaceFirst("^[vV]", "").split("\\.");
        int[] seg = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                seg[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                seg[i] = 0;
            }
        }
        return seg;
    }

    /**
     * 字段删除（逻辑删除，不做物理删除）。
     * <p>
     * 总设计 V6.1「DESIGN BOUNDARIES」的「无物理删除」约束针对客户主档（应逻辑停用并保留历史），
     * 并未禁止元数据字段删除；但总设计点名的核心主数据字段（匹配依据 / DQ 必评项 / 生命周期状态）
     * 属于治理基线，不允许删除，否则匹配与 DQ 规则将失去依据。
     *
     * @param id 字段主键
     * @return 提示文案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteField(Long id) {
        if (id == null) {
            throw new ServiceException("字段 id 不能为空");
        }
        MdField field = fieldMapper.selectById(id);
        if (field == null) {
            throw new ServiceException("字段不存在或已被删除");
        }
        String guard = protectedReason(field);
        if (guard != null) {
            throw new ServiceException("「" + field.getFieldName() + "」是核心主数据字段（" + guard + "），不允许删除");
        }
        // @TableLogic：deleteById 实际执行 UPDATE md_field SET del_flag='1'，行与历史保留
        fieldMapper.deleteById(id);
        return "字段「" + field.getFieldName() + "」已删除（逻辑删除，历史保留）；发布后不再进入业务表单";
    }

    /**
     * 判断字段是否受保护（不可删除），返回不可删原因；可删除时返回 null。
     * <p>
     * 三条判定依据：
     * <ol>
     *   <li>总设计 DESIGN BOUNDARIES 点名的匹配依据与 DQ 维度字段（按 field_code 白名单）</li>
     *   <li>治理标记 is_key_field / is_match_field / is_dq_field = Y</li>
     *   <li>必填主数据属性（客户类型 / BU / 国家 / 状态）——删除会破坏主档完整性</li>
     * </ol>
     */
    private String protectedReason(MdField field) {
        String byCode = PROTECTED_FIELDS.get(field.getFieldCode());
        if (byCode != null) {
            return byCode;
        }
        if ("Y".equals(field.getIsKeyField())) {
            return "已标记为主键字段 is_key_field=Y";
        }
        if ("Y".equals(field.getIsMatchField())) {
            return "已标记为匹配字段 is_match_field=Y";
        }
        if ("Y".equals(field.getIsDqField())) {
            return "已标记为 DQ 评分字段 is_dq_field=Y";
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveField(MdField field) {
        if (StringUtils.isBlank(field.getFieldCode())) {
            throw new ServiceException("字段编码不能为空");
        }
        if (field.getId() != null) {
            fieldMapper.updateById(field);
            return field.getFieldCode();
        }
        // 查重：同 model_code + field_code + version_no 已存在则 update
        MdField exist = fieldMapper.selectOne(
            Wrappers.<MdField>lambdaQuery()
                .eq(MdField::getModelCode, field.getModelCode())
                .eq(MdField::getFieldCode, field.getFieldCode())
                .eq(MdField::getVersionNo, field.getVersionNo()));
        if (exist != null) {
            field.setId(exist.getId());
            fieldMapper.updateById(field);
        } else {
            field.setStatus(StringUtils.blankToDefault(field.getStatus(), "0"));
            fieldMapper.insert(field);
        }
        return field.getFieldCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MdValueSet> selectValueSetList() {
        return valueSetMapper.selectList(Wrappers.<MdValueSet>lambdaQuery().orderByAsc(MdValueSet::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveValueSet(MdValueSet valueSet) {
        if (StringUtils.isBlank(valueSet.getSetCode())) {
            throw new ServiceException("值集编码不能为空");
        }
        MdValueSet exist = valueSetMapper.selectOne(
            Wrappers.<MdValueSet>lambdaQuery().eq(MdValueSet::getSetCode, valueSet.getSetCode()));
        if (exist != null) {
            valueSet.setId(exist.getId());
            valueSetMapper.updateById(valueSet);
        } else {
            valueSet.setStatus(StringUtils.blankToDefault(valueSet.getStatus(), "0"));
            valueSet.setDelFlag("0");
            valueSetMapper.insert(valueSet);
        }
        return valueSet.getSetCode();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 版本列表按 md_field.version_no 聚合：
     * <ul>
     *   <li>状态：本版本存在已发布字段（status='0'）即 Current，否则 Draft</li>
     *   <li>差异：与版本号顺序上的上一版本对比字段集（编码新增 / 属性变更），呼应总设计「形成状态、版本、差异和审计证据」</li>
     *   <li>草稿创建时间：该版本字段最早的 create_time（克隆/新建版本线的时间）</li>
     *   <li>发布时间：该版本已发布字段最新的 update_time</li>
     * </ul>
     */
    @Override
    public List<PlatformVersionVo> selectVersionList() {
        List<MdField> fields = fieldMapper.selectList(Wrappers.lambdaQuery());
        Map<String, List<MdField>> byVersion = new LinkedHashMap<>();
        for (MdField field : fields) {
            String version = StringUtils.blankToDefault(field.getVersionNo(), "v1.0");
            byVersion.computeIfAbsent(version, k -> new ArrayList<>()).add(field);
        }
        // 按版本号升序计算差异（第一个版本为基线）
        List<String> ordered = new ArrayList<>(byVersion.keySet());
        ordered.sort(String::compareTo);
        Map<String, String> diffText = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            String version = ordered.get(i);
            if (i == 0) {
                diffText.put(version, "基线");
                continue;
            }
            Map<String, String> prevSig = fieldSignatures(byVersion.get(ordered.get(i - 1)));
            int added = 0;
            int changed = 0;
            for (Map.Entry<String, String> entry : fieldSignatures(byVersion.get(version)).entrySet()) {
                String prev = prevSig.get(entry.getKey());
                if (prev == null) {
                    added++;
                } else if (!prev.equals(entry.getValue())) {
                    changed++;
                }
            }
            diffText.put(version, added == 0 && changed == 0 ? "无变更"
                : (added > 0 ? "新增 " + added : "") + (added > 0 && changed > 0 ? " · " : "") + (changed > 0 ? "变更 " + changed : ""));
        }
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<PlatformVersionVo> list = new ArrayList<>();
        byVersion.forEach((version, versionFields) -> {
            PlatformVersionVo vo = new PlatformVersionVo();
            vo.setVersion(version);
            // 状态由实际字段状态推导：本版本存在已发布字段即视为 Current，否则 Draft
            boolean hasPublished = versionFields.stream().anyMatch(f -> "0".equals(f.getStatus()));
            vo.setStatus(hasPublished ? "Current" : "Draft");
            vo.setDiff(diffText.getOrDefault(version, "—"));
            // 草稿创建时间 = 本版本字段最早的 create_time
            versionFields.stream().map(MdField::getCreateTime)
                .filter(java.util.Objects::nonNull)
                .min(java.time.LocalDateTime::compareTo)
                .ifPresent(t -> vo.setDraftCreatedAt(t.format(fmt)));
            // 发布时间 = 本版本已发布字段最新的 update_time
            versionFields.stream().filter(f -> "0".equals(f.getStatus()))
                .map(MdField::getUpdateTime).filter(java.util.Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .ifPresent(t -> vo.setPublishedAt(t.format(fmt)));
            list.add(vo);
        });
        // 稳定排序：Current 优先、其次版本号倒序，便于演示阅读
        list.sort((a, b) -> {
            int sa = "Current".equals(a.getStatus()) ? 0 : 1;
            int sb = "Current".equals(b.getStatus()) ? 0 : 1;
            if (sa != sb) return sa - sb;
            return b.getVersion().compareTo(a.getVersion());
        });
        return list;
    }

    /**
     * 字段签名表：fieldCode → 属性摘要（名称/类型/必填/值集/层级/归属BU），用于版本差异对比
     *
     * @param fields 字段列表
     * @return 编码到签名的映射
     */
    private Map<String, String> fieldSignatures(List<MdField> fields) {
        Map<String, String> sig = new LinkedHashMap<>();
        for (MdField f : fields) {
            sig.put(f.getFieldCode(), StringUtils.blankToDefault(f.getFieldName(), "")
                + "|" + StringUtils.blankToDefault(f.getDataType(), "")
                + "|" + StringUtils.blankToDefault(f.getIsRequired(), "")
                + "|" + StringUtils.blankToDefault(f.getValueSetCode(), "")
                + "|" + StringUtils.blankToDefault(f.getScopeType(), "")
                + "|" + StringUtils.blankToDefault(f.getOwnerBu(), ""));
        }
        return sig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdRole> selectRoleList() {
        return roleMapper.selectList(Wrappers.<CmdRole>lambdaQuery().orderByAsc(CmdRole::getOrderNum));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PermissionMatrixVo> selectPermissionMatrix() {
        List<PermissionMatrixVo> list = new ArrayList<>();
        list.add(matrix("客户创建与变更申请", "发起", "复核", "配置", "只读"));
        list.add(matrix("客户主档查询", "本BU", "本BU / 跨BU", "全量", "全量只读"));
        list.add(matrix("治理与审批", "无", "审批 / 升级", "流程配置", "只读"));
        list.add(matrix("批量导入与治理", "导入", "治理裁决", "模板配置", "只读"));
        list.add(matrix("平台配置", "无", "无", "全部", "只读"));
        list.add(matrix("审计日志", "无", "无", "查看", "查看 / 导出"));
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveRoles(List<CmdRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ServiceException("角色列表不能为空");
        }
        for (CmdRole role : roles) {
            saveRole(role);
        }
        return "角色权限已保存，共 " + roles.size() + " 条";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveRole(CmdRole role) {
        if (StringUtils.isBlank(role.getRoleCode())) {
            throw new ServiceException("角色编码不能为空");
        }
        CmdRole exist = roleMapper.selectOne(
            Wrappers.<CmdRole>lambdaQuery().eq(CmdRole::getRoleCode, role.getRoleCode()));
        if (exist != null) {
            role.setId(exist.getId());
            roleMapper.updateById(role);
            return role.getRoleCode();
        }
        if (role.getId() != null) {
            roleMapper.updateById(role);
            return role.getRoleCode();
        }
        role.setStatus(StringUtils.blankToDefault(role.getStatus(), "0"));
        roleMapper.insert(role);
        return role.getRoleCode();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 编辑视角：存在待发布草稿（默认规则 rule_code + "-DRAFT"）时返回草稿，否则返回当前默认规则。
     */
    @Override
    public OneIdRule selectOneIdRule() {
        OneIdRule base = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getIsDefault, "Y").last("limit 1"));
        String baseCode = base == null ? "ONEID_GC_DEFAULT" : base.getRuleCode();
        OneIdRule draft = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getRuleCode, baseCode + "-DRAFT").last("limit 1"));
        if (draft != null) {
            return draft;
        }
        if (base != null) {
            return base;
        }
        OneIdRule any = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getStatus, "0").last("limit 1"));
        if (any == null) {
            throw new ServiceException("尚未配置 One ID 规则");
        }
        return any;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 草稿模型：编辑内容写入「默认规则 rule_code + -DRAFT」草稿行，当前已发布规则保持不变，
     * 保证保存 → 发布期间全系统 One ID 生成不受影响；点击「发布规则」后草稿才成为生效版本。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveOneIdRule(OneIdRule rule) {
        if (rule == null) {
            throw new ServiceException("规则不能为空");
        }
        if (StringUtils.isBlank(rule.getRuleName())) {
            throw new ServiceException("规则名称不能为空");
        }
        OneIdRule base = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getIsDefault, "Y").last("limit 1"));
        if (base == null) {
            throw new ServiceException("尚未发布任何 One ID 规则，无法保存编辑");
        }
        String draftCode = base.getRuleCode() + "-DRAFT";
        OneIdRule draft = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getRuleCode, draftCode).last("limit 1"));
        if (draft == null) {
            draft = new OneIdRule();
            draft.setRuleCode(draftCode);
            draft.setRuleName(rule.getRuleName());
            draft.setPattern(buildPattern(
                StringUtils.blankToDefault(rule.getPrefix(), base.getPrefix()),
                StringUtils.defaultString(rule.getSeparator(), base.getSeparator()),
                rule.getSerialLength() == null ? base.getSerialLength() : rule.getSerialLength()));
            draft.setStatus("1");
            draft.setIsDefault("N");
            oneIdRuleMapper.insert(draft);
        }
        OneIdRule update = new OneIdRule();
        update.setId(draft.getId());
        update.setRuleName(rule.getRuleName());
        update.setPrefix(StringUtils.blankToDefault(rule.getPrefix(), base.getPrefix()));
        update.setSeparator(StringUtils.defaultString(rule.getSeparator(), base.getSeparator()));
        Integer serialLength = rule.getSerialLength() == null ? base.getSerialLength() : rule.getSerialLength();
        update.setSerialLength(serialLength);
        update.setScopeType(StringUtils.defaultString(rule.getScopeType(), base.getScopeType()));
        update.setGenStrategy(StringUtils.defaultString(rule.getGenStrategy(), base.getGenStrategy()));
        update.setStablePolicy(StringUtils.defaultString(rule.getStablePolicy(), base.getStablePolicy()));
        update.setReusePolicy(StringUtils.defaultString(rule.getReusePolicy(), base.getReusePolicy()));
        update.setSeqCode(StringUtils.defaultString(rule.getSeqCode(), base.getSeqCode()));
        update.setRemark(rule.getRemark());
        update.setPattern(buildPattern(update.getPrefix(), update.getSeparator(), serialLength));
        update.setStatus("1");
        update.setIsDefault("N");
        oneIdRuleMapper.updateById(update);
        return "One ID规则已保存为 Draft（未发布不影响全局生成），请发布后生效";
    }

    /**
     * 根据前缀、分隔符、流水长度构建编码模式
     */
    private String buildPattern(String prefix, String separator, Integer serialLength) {
        int len = serialLength == null ? 6 : serialLength;
        return StringUtils.blankToDefault(prefix, "GC") + StringUtils.blankToDefault(separator, "-") + "{seq" + len + "}";
    }

    /**
     * {@inheritDoc}
     * <p>
     * 发布 = 将待发布草稿（rule_code + -DRAFT）提升为全局唯一生效规则：
     * 原默认规则归档为历史版本（rule_code + -ARCH-xxxx），草稿接管默认 rule_code 并置为 Published。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String publishOneIdRule() {
        OneIdRule base = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getIsDefault, "Y").last("limit 1"));
        if (base == null) {
            throw new ServiceException("尚未配置 One ID 规则");
        }
        OneIdRule draft = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getRuleCode, base.getRuleCode() + "-DRAFT").last("limit 1"));
        if (draft == null) {
            if ("0".equals(base.getStatus())) {
                return "One ID规则「" + base.getRuleName() + "」已发布生效，当前无待发布草稿";
            }
            // 兼容历史数据：默认规则本身处于 Draft 状态时直接置为生效
            OneIdRule fix = new OneIdRule();
            fix.setId(base.getId());
            fix.setStatus("0");
            oneIdRuleMapper.updateById(fix);
            return "One ID规则「" + base.getRuleName() + "」已发布并全局生效";
        }
        // 1. 原默认规则归档：失去默认标记，rule_code 追加历史后缀
        OneIdRule archive = new OneIdRule();
        archive.setId(base.getId());
        archive.setIsDefault("N");
        archive.setRuleCode(base.getRuleCode() + "-ARCH-" + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        oneIdRuleMapper.updateById(archive);
        // 2. 草稿提升为生效规则：接管默认 rule_code，Published + 默认
        OneIdRule promote = new OneIdRule();
        promote.setId(draft.getId());
        promote.setRuleCode(base.getRuleCode());
        promote.setStatus("0");
        promote.setIsDefault("Y");
        oneIdRuleMapper.updateById(promote);
        return "One ID规则「" + draft.getRuleName() + "」已发布并全局生效";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String copyOneIdRule() {
        OneIdRule rule = selectOneIdRule();
        OneIdRule copy = new OneIdRule();
        copy.setRuleCode(rule.getRuleCode() + "-COPY-" + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        copy.setRuleName(rule.getRuleName() + "（副本）");
        copy.setPattern(rule.getPattern());
        copy.setPrefix(rule.getPrefix());
        copy.setSeparator(rule.getSeparator());
        copy.setSerialLength(rule.getSerialLength());
        copy.setSeqCode(rule.getSeqCode());
        copy.setGenStrategy(rule.getGenStrategy());
        copy.setStablePolicy(rule.getStablePolicy());
        copy.setReusePolicy(rule.getReusePolicy());
        copy.setScopeType(rule.getScopeType());
        copy.setStatus("1");
        copy.setIsDefault("N");
        copy.setRemark(rule.getRemark());
        oneIdRuleMapper.insert(copy);
        return "已复制规则为 Draft「" + copy.getRuleName() + "」，请编辑后发布";
    }

    /**
     * {@inheritDoc}
     * <p>
     * 生成只读取「Published + 默认」的唯一生效规则；草稿在发布前不影响全局编码。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateOneId() {
        OneIdRule rule = oneIdRuleMapper.selectOne(Wrappers.<OneIdRule>lambdaQuery()
            .eq(OneIdRule::getIsDefault, "Y").eq(OneIdRule::getStatus, "0").last("limit 1"));
        if (rule == null) {
            throw new ServiceException("尚未发布 One ID 规则，请先在平台配置中发布规则");
        }
        String seqCode = StringUtils.blankToDefault(rule.getSeqCode(), "ONE_ID");
        CfgSequence seq = cfgSequenceMapper.selectOne(
            Wrappers.<CfgSequence>lambdaQuery().eq(CfgSequence::getSeqCode, seqCode).last("limit 1"));
        if (seq == null) {
            throw new ServiceException("未配置序列规则：" + seqCode);
        }
        long next = nextSequenceValue(seq);
        String prefix = StringUtils.blankToDefault(rule.getPrefix(), seq.getPrefix());
        String separator = StringUtils.blankToDefault(rule.getSeparator(), seq.getSeparator());
        int length = rule.getSerialLength() == null ? (seq.getSerialLength() == null ? 6 : seq.getSerialLength()) : rule.getSerialLength();
        String serial = String.format("%0" + length + "d", next);
        return prefix + separator + serial;
    }

    /**
     * 原子递增 cfg_sequence 当前值并返回下一个可用值
     *
     * @param seq 序列规则
     * @return 下一个序列值
     */
    @Transactional(rollbackFor = Exception.class)
    protected long nextSequenceValue(CfgSequence seq) {
        int step = seq.getStep() == null ? 1 : seq.getStep();
        long init = seq.getInitValue() == null ? 1L : seq.getInitValue();
        // 使用 select ... for update 保证同一事务内原子性
        CfgSequence locked = cfgSequenceMapper.selectOne(
            Wrappers.<CfgSequence>lambdaQuery().eq(CfgSequence::getSeqCode, seq.getSeqCode()).last("FOR UPDATE"));
        if (locked == null) {
            throw new ServiceException("序列规则不存在：" + seq.getSeqCode());
        }
        long current = locked.getCurrentValue() == null ? 0L : locked.getCurrentValue();
        long next = current == 0L ? init : current + step;
        CfgSequence update = new CfgSequence();
        update.setId(locked.getId());
        update.setCurrentValue(next);
        cfgSequenceMapper.updateById(update);
        return next;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdLegacyMapping> selectLegacyMapping(String oneId) {
        LambdaQueryWrapper<CmdLegacyMapping> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(oneId)) {
            lqw.eq(CmdLegacyMapping::getOneId, oneId);
        }
        lqw.orderByAsc(CmdLegacyMapping::getId);
        return legacyMappingMapper.selectList(lqw);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String publishVersion(String version) {
        String target = StringUtils.isNotBlank(version) ? version : currentVersion();
        if (StringUtils.isBlank(target)) {
            throw new ServiceException("版本号不能为空");
        }
        // 退役其余版本（仅保留目标版本为已发布，其余一律置 Draft），保证单一 Current
        MdField retire = new MdField();
        retire.setStatus("1");
        fieldMapper.update(retire, Wrappers.<MdField>lambdaQuery().ne(MdField::getVersionNo, target));
        // 发布目标版本全部字段
        MdField publish = new MdField();
        publish.setStatus("0");
        fieldMapper.update(publish, Wrappers.<MdField>lambdaQuery().eq(MdField::getVersionNo, target));
        return "模型版本 " + target + " 已发布，其余版本已退役为 Draft";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createVersion() {
        // 源版本 = 当前已发布版本（存在已发布字段者），否则取版本号最大者
        String source = currentPublishedVersion();
        if (StringUtils.isBlank(source)) {
            List<PlatformVersionVo> versions = selectVersionList();
            source = versions.isEmpty() ? "v1.0" : versions.get(versions.size() - 1).getVersion();
        }
        List<MdField> sourceFields = fieldMapper.selectList(
            Wrappers.<MdField>lambdaQuery().eq(MdField::getVersionNo, source));
        String next = nextVersion(source);
        for (MdField field : sourceFields) {
            MdField clone = new MdField();
            clone.setModelCode(field.getModelCode());
            clone.setFieldCode(field.getFieldCode());
            clone.setFieldName(field.getFieldName());
            clone.setDataType(field.getDataType());
            clone.setValueSetCode(field.getValueSetCode());
            clone.setIsRequired(field.getIsRequired());
            clone.setScopeType(field.getScopeType());
            clone.setOwnerBu(field.getOwnerBu());
            clone.setOrderNum(field.getOrderNum());
            clone.setRemark(field.getRemark());
            clone.setVersionNo(next);
            clone.setStatus("1"); // 新版本整体为 Draft
            fieldMapper.insert(clone);
        }
        return next;
    }

    /**
     * 取当前已发布版本号（存在 status='0' 字段的版本），无则取列表首个
     */
    private String currentPublishedVersion() {
        List<PlatformVersionVo> versions = selectVersionList();
        for (PlatformVersionVo vo : versions) {
            if ("Current".equals(vo.getStatus())) {
                return vo.getVersion();
            }
        }
        return versions.isEmpty() ? null : versions.get(0).getVersion();
    }

    /**
     * 生成下一个版本号：解析 v{major}.{minor}，minor+1；minor 越界则 major+1、minor 归零
     */
    private String nextVersion(String current) {
        int major = 1;
        int minor = 0;
        if (current != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("v(\\d+)\\.(\\d+)").matcher(current);
            if (m.find()) {
                major = Integer.parseInt(m.group(1));
                minor = Integer.parseInt(m.group(2));
            }
        }
        minor += 1;
        if (minor > 9) {
            major += 1;
            minor = 0;
        }
        return "v" + major + "." + minor;
    }

    /**
     * 取当前生效版本号（版本列表中的第一条）
     *
     * @return 版本号
     */
    private String currentVersion() {
        List<PlatformVersionVo> versions = selectVersionList();
        return versions.isEmpty() ? null : versions.get(0).getVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<org.dromara.cmd.domain.vo.OneIdPolicyVo> selectOneIdPolicy() {
        List<org.dromara.cmd.domain.vo.OneIdPolicyVo> list = new ArrayList<>();
        list.add(policy("新建客户", "按规则生成 One ID，写入主档"));
        list.add(policy("属性变更", "One ID 保持不变，版本递增"));
        list.add(policy("合并客户", "保留主 One ID，被合并编码写入 Legacy 映射"));
        list.add(policy("逻辑停用", "One ID 保留，状态置为 inactive，不回收编码"));
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditEvent> selectOneIdHistory(String oneId) {
        return auditEventMapper.selectList(
            Wrappers.<AuditEvent>lambdaQuery().eq(AuditEvent::getOneId, oneId)
                .orderByDesc(AuditEvent::getEventTime));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CmdMergeRecord> selectMergeRecords(String oneId) {
        return mergeRecordMapper.selectList(Wrappers.<CmdMergeRecord>lambdaQuery()
            .and(q -> q.eq(CmdMergeRecord::getSurvivorOneId, oneId)
                .or().eq(CmdMergeRecord::getMergedOneId, oneId))
            .orderByDesc(CmdMergeRecord::getCreateTime));
    }

    private PermissionMatrixVo matrix(String capability, String business, String steward, String admin, String auditor) {
        PermissionMatrixVo vo = new PermissionMatrixVo();
        vo.setCapability(capability);
        vo.setBusiness(business);
        vo.setSteward(steward);
        vo.setAdmin(admin);
        vo.setAuditor(auditor);
        return vo;
    }

    private org.dromara.cmd.domain.vo.OneIdPolicyVo policy(String event, String handling) {
        org.dromara.cmd.domain.vo.OneIdPolicyVo vo = new org.dromara.cmd.domain.vo.OneIdPolicyVo();
        vo.setEvent(event);
        vo.setHandling(handling);
        return vo;
    }
}
