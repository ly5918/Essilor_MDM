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
     * {@inheritDoc}
     */
    @Override
    public List<MdField> selectFieldList(String keyword, String modelCode) {
        LambdaQueryWrapper<MdField> lqw = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(modelCode)) {
            lqw.eq(MdField::getModelCode, modelCode);
        }
        if (StringUtils.isNotBlank(keyword)) {
            lqw.and(w -> w.like(MdField::getFieldCode, keyword)
                .or().like(MdField::getFieldName, keyword));
        }
        lqw.orderByAsc(MdField::getOrderNum);
        return fieldMapper.selectList(lqw);
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
     */
    @Override
    public List<PlatformVersionVo> selectVersionList() {
        List<MdField> fields = fieldMapper.selectList(Wrappers.lambdaQuery());
        Map<String, Long> counter = new LinkedHashMap<>();
        Map<String, Boolean> publishedFlag = new LinkedHashMap<>();
        Map<String, java.time.LocalDateTime> publishedTime = new LinkedHashMap<>();
        for (MdField field : fields) {
            String version = StringUtils.blankToDefault(field.getVersionNo(), "v1.0");
            counter.merge(version, 1L, Long::sum);
            boolean isPublished = "0".equals(field.getStatus());
            publishedFlag.merge(version, isPublished, Boolean::logicalOr);
            if (isPublished && field.getUpdateTime() != null) {
                publishedTime.merge(version, field.getUpdateTime(),
                    (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<PlatformVersionVo> list = new ArrayList<>();
        counter.forEach((version, count) -> {
            PlatformVersionVo vo = new PlatformVersionVo();
            vo.setVersion(version);
            vo.setRuleCount(count);
            // 状态由实际字段状态推导：本版本存在已发布字段即视为 Current，否则 Draft
            vo.setStatus(Boolean.TRUE.equals(publishedFlag.get(version)) ? "Current" : "Draft");
            if (publishedTime.get(version) != null) {
                vo.setPublishedAt(publishedTime.get(version)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
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
