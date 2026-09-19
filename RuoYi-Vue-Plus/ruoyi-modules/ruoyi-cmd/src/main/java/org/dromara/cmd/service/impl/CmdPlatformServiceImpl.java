package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.cmd.domain.AuditEvent;
import org.dromara.cmd.domain.CmdLegacyMapping;
import org.dromara.cmd.domain.CmdRole;
import org.dromara.cmd.domain.MdField;
import org.dromara.cmd.domain.MdValueSet;
import org.dromara.cmd.domain.OneIdRule;
import org.dromara.cmd.domain.vo.PermissionMatrixVo;
import org.dromara.cmd.domain.vo.PlatformVersionVo;
import org.dromara.cmd.mapper.AuditEventMapper;
import org.dromara.cmd.mapper.CmdLegacyMappingMapper;
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
    private final CmdLegacyMappingMapper legacyMappingMapper;
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
        field.setStatus(StringUtils.blankToDefault(field.getStatus(), "0"));
        fieldMapper.insert(field);
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
    public List<PlatformVersionVo> selectVersionList() {
        List<MdField> fields = fieldMapper.selectList(Wrappers.lambdaQuery());
        Map<String, Long> counter = new LinkedHashMap<>();
        for (MdField field : fields) {
            String version = StringUtils.blankToDefault(field.getVersionNo(), "v1.0");
            counter.merge(version, 1L, Long::sum);
        }
        List<PlatformVersionVo> list = new ArrayList<>();
        counter.forEach((version, count) -> {
            PlatformVersionVo vo = new PlatformVersionVo();
            vo.setVersion(version);
            vo.setRuleCount(count);
            vo.setStatus(version.toLowerCase().contains("draft") ? "Draft" : "Current");
            list.add(vo);
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
     */
    @Override
    public OneIdRule selectOneIdRule() {
        OneIdRule rule = oneIdRuleMapper.selectOne(
            Wrappers.<OneIdRule>lambdaQuery().eq(OneIdRule::getIsDefault, "Y").last("limit 1"));
        if (rule == null) {
            rule = oneIdRuleMapper.selectOne(Wrappers.<OneIdRule>lambdaQuery().last("limit 1"));
        }
        if (rule == null) {
            throw new ServiceException("尚未配置 One ID 规则");
        }
        return rule;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String publishOneIdRule() {
        OneIdRule rule = selectOneIdRule();
        OneIdRule update = new OneIdRule();
        update.setId(rule.getId());
        update.setStatus("0");
        oneIdRuleMapper.updateById(update);
        return "One ID规则" + rule.getRuleName() + "已发布";
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
        copy.setSerialLength(rule.getSerialLength());
        copy.setSeqCode(rule.getSeqCode());
        copy.setGenStrategy(rule.getGenStrategy());
        copy.setStablePolicy(rule.getStablePolicy());
        copy.setReusePolicy(rule.getReusePolicy());
        copy.setScopeType(rule.getScopeType());
        copy.setStatus("1");
        copy.setIsDefault("N");
        oneIdRuleMapper.insert(copy);
        return "已复制规则为Draft " + copy.getRuleName();
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
        MdField update = new MdField();
        update.setStatus("0");
        fieldMapper.update(update, Wrappers.<MdField>lambdaQuery().eq(MdField::getVersionNo, target));
        return "模型版本 " + target + " 已发布";
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
