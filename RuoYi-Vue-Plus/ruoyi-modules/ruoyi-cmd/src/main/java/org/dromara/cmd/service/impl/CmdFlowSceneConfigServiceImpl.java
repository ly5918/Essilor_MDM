package org.dromara.cmd.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.cmd.domain.bo.CmdFlowSceneConfigBo;
import org.dromara.cmd.domain.vo.CmdFlowSceneConfigVo;
import org.dromara.cmd.mapper.CmdFlowSceneMapper;
import org.dromara.cmd.service.ICmdFlowEngineService;
import org.dromara.cmd.service.ICmdFlowSceneConfigService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务场景工作流配置服务实现
 * <p>
 * 配置口径（与总设计第 16 页一致）：
 * <ul>
 *   <li><b>平台固定（不可修改）</b>：场景编码、流程编码（绑定 Warm-Flow 流程定义）、
 *       业务入口与系统自动节点（DQ / Duplicate Check / One ID 生成）、发布与审计节点、
 *       主干 BU 初审节点——不可删除或停用；</li>
 *   <li><b>场景级可配置（可增删 / 可调整）</b>：路由条件（启动条件 + 节点命中条件）、
 *       节点办理角色与适用范围、会签 / 或签、节点 SLA、场景 SLA、超时升级、邮件通知、
 *       GC 决策节点是否启用（V6.1 待确认项：High End 必经 / Mainstream 可只走 BU 初审）。</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CmdFlowSceneConfigServiceImpl implements ICmdFlowSceneConfigService {

    /** 可配置（可停用 / 可增删）的节点：GC 决策按场景决定是否启用 */
    private static final Set<String> CONFIGURABLE_NODES = Set.of("GC_REVIEW");

    /** 平台固定节点：业务入口 + 系统自动 + 发布与审计（不可删除） */
    private static final Map<String, String> LOCKED_NODE_CONSTRAINT = Map.of(
        "APPLY", "业务入口：Business User 发起，平台固定不可删除",
        "INPUT", "申请数据录入与附件，平台固定不可删除",
        "OCR", "系统自动：OCR 与地址标准化，规则驱动不可删除",
        "DQ", "系统自动：DQ 打分，结果写入实例变量驱动路由，不可删除",
        "DUP", "系统自动：Duplicate Check（信用代码 + 经营地址为主依据），不可删除",
        "BU_REVIEW", "主干审批节点：BU Scope 初审，不可停用；命中条件与办理角色可配置",
        "RESULT", "系统自动：One ID 生成 / 关联，One ID 稳定不重新生成，不可删除",
        "PUBLISH", "发布下游与 Retry / Resubmit 由平台统一执行，不可删除",
        "TRACE", "运行追踪：任务状态与失败原因，平台固定",
        "AUDIT", "审计证据链：Who / When / What 与 Before / After，只读保留不可删除"
    );

    private final CmdFlowSceneMapper flowSceneMapper;
    private final ICmdFlowEngineService flowEngineService;

    @Override
    public CmdFlowSceneConfigVo selectConfig(String sceneCode) {
        Map<String, Object> scene = flowSceneMapper.selectSceneConfig(sceneCode);
        if (scene == null) {
            throw new ServiceException("场景不存在：" + sceneCode);
        }

        CmdFlowSceneConfigVo vo = new CmdFlowSceneConfigVo();
        vo.setSceneCode(String.valueOf(scene.get("scene_code")));
        vo.setSceneName(asString(scene.get("scene_name")));
        vo.setFlowCode(asString(scene.get("flow_code")));
        vo.setFlowName(asString(scene.get("flow_name")));
        vo.setSlaHours(asInteger(scene.get("sla_hours")));
        vo.setEscalateRule(asString(scene.get("escalate_rule")));
        vo.setStartConditions(asString(scene.get("start_conditions")));
        vo.setFormKey(asString(scene.get("form_key")));

        // 部署状态与版本取引擎真实状态（与「工作流定义」列表同一口径）
        flowEngineService.listScenes().stream()
            .filter(s -> sceneCode.equals(s.getSceneCode()))
            .findFirst()
            .ifPresent(s -> {
                vo.setDeployed(s.getDeployed());
                vo.setVersion(s.getVersion());
            });

        // 扩展属性：超时动作 / 通知方式 / 通知对象 / 变更说明（复用既有 ext_json，无需改表）
        Map<String, Object> ext = readExt(scene.get("ext_json"));
        vo.setTimeoutAction(asString(ext.get("timeoutAction")));
        vo.setNotifyMode(asString(ext.get("notifyMode")));
        Object targets = ext.get("notifyTargets");
        if (targets instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            list.forEach(item -> values.add(String.valueOf(item)));
            vo.setNotifyTargets(values);
        } else {
            vo.setNotifyTargets(new ArrayList<>());
        }
        vo.setChangeNote(asString(ext.get("changeNote")));

        vo.setNodes(buildNodes(sceneCode));
        vo.setRules(buildRules(sceneCode));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateConfig(String sceneCode, CmdFlowSceneConfigBo bo) {
        if (flowSceneMapper.selectSceneConfig(sceneCode) == null) {
            throw new ServiceException("场景不存在：" + sceneCode);
        }
        Long operator = currentUserId();

        // 场景级配置：SLA / 超时升级 / 启动条件 + 扩展属性（超时动作 / 通知 / 变更说明）
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("timeoutAction", bo.getTimeoutAction());
        ext.put("notifyMode", bo.getNotifyMode());
        ext.put("notifyTargets", bo.getNotifyTargets() == null ? List.of() : bo.getNotifyTargets());
        ext.put("changeNote", bo.getChangeNote());
        flowSceneMapper.updateSceneConfig(
            sceneCode,
            bo.getSlaHours(),
            bo.getEscalateRule(),
            bo.getStartConditions(),
            JsonUtils.toJsonString(ext),
            operator
        );

        // 节点规则：id 为空 = 新增（场景级「增加工作流节点」）；有 id = 更新（含 status=1 停用即移除）
        if (bo.getRules() != null) {
            int index = 0;
            for (CmdFlowSceneConfigBo.RuleBo rule : bo.getRules()) {
                index++;
                if (rule.getId() != null) {
                    flowSceneMapper.updateNodeRule(
                        rule.getId(),
                        rule.getConditionExpr(),
                        StringUtils.blankToDefault(rule.getAssigneeType(), "ROLE"),
                        rule.getAssigneeValue(),
                        rule.getScopeType(),
                        StringUtils.blankToDefault(rule.getMultiMode(), "ANY"),
                        rule.getSlaHours(),
                        StringUtils.blankToDefault(rule.getStatus(), "0"),
                        rule.getRemark(),
                        operator
                    );
                    continue;
                }
                if (StringUtils.isBlank(rule.getNodeCode())) {
                    continue;
                }
                flowSceneMapper.insertNodeRule(
                    IdUtil.getSnowflakeNextId(),
                    sceneCode,
                    rule.getNodeCode().toLowerCase(),
                    StringUtils.blankToDefault(rule.getNodeName(), rule.getNodeCode()),
                    rule.getConditionExpr(),
                    StringUtils.blankToDefault(rule.getAssigneeType(), "ROLE"),
                    rule.getAssigneeValue(),
                    rule.getScopeType(),
                    StringUtils.blankToDefault(rule.getMultiMode(), "ANY"),
                    rule.getSlaHours(),
                    rule.getPriority() == null ? index * 10 : rule.getPriority(),
                    rule.getRemark(),
                    operator
                );
            }
        }
        log.info("CMD workflow config updated: scene={}, slaHours={}, escalateRule={}, rules={}",
            sceneCode, bo.getSlaHours(), bo.getEscalateRule(), bo.getRules() == null ? 0 : bo.getRules().size());
        return true;
    }

    /** 泳道节点蓝图：与「泳道图」弹窗同一份口径，并标注平台固定 / 可配置 */
    private List<CmdFlowSceneConfigVo.NodeVo> buildNodes(String sceneCode) {
        List<CmdFlowSceneConfigVo.NodeVo> nodes = new ArrayList<>();
        for (var step : flowEngineService.buildSwimlane(sceneCode)) {
            CmdFlowSceneConfigVo.NodeVo node = new CmdFlowSceneConfigVo.NodeVo();
            node.setPhase(step.getPhase());
            node.setPhaseName(step.getPhaseName());
            node.setLane(step.getLane());
            node.setNodeCode(step.getNodeCode());
            node.setNodeName(step.getNodeName());
            node.setNodeType(step.getNodeType());
            node.setNote(step.getNote());
            boolean configurable = CONFIGURABLE_NODES.contains(step.getNodeCode());
            node.setConfigurable(configurable);
            node.setLocked(!configurable);
            node.setConstraint(configurable
                ? "可配置：可停用（Mainstream 可只走 BU 初审）或调整升级命中条件（V6.1 待确认项）"
                : LOCKED_NODE_CONSTRAINT.getOrDefault(step.getNodeCode(), "平台固定节点，不可删除"));
            nodes.add(node);
        }
        return nodes;
    }

    /** 节点审批人规则：可增删、可改命中条件 / 办理角色 / 会签方式 / 节点 SLA */
    private List<CmdFlowSceneConfigVo.RuleVo> buildRules(String sceneCode) {
        List<CmdFlowSceneConfigVo.RuleVo> rules = new ArrayList<>();
        for (Map<String, Object> row : flowSceneMapper.selectSceneRules(sceneCode)) {
            CmdFlowSceneConfigVo.RuleVo rule = new CmdFlowSceneConfigVo.RuleVo();
            Object id = row.get("id");
            rule.setId(id == null ? null : ((Number) id).longValue());
            String nodeCode = asString(row.get("node_code"));
            rule.setNodeCode(nodeCode);
            rule.setNodeName(asString(row.get("node_name")));
            rule.setConditionExpr(asString(row.get("condition_expr")));
            rule.setAssigneeType(asString(row.get("assignee_type")));
            rule.setAssigneeValue(asString(row.get("assignee_value")));
            rule.setScopeType(asString(row.get("scope_type")));
            rule.setMultiMode(StringUtils.blankToDefault(asString(row.get("multi_mode")), "ANY"));
            rule.setSlaHours(asInteger(row.get("sla_hours")));
            rule.setPriority(asInteger(row.get("priority")));
            rule.setStatus(StringUtils.blankToDefault(asString(row.get("status")), "0"));
            rule.setRemark(asString(row.get("remark")));
            boolean locked = "bu_review".equalsIgnoreCase(nodeCode);
            rule.setLocked(locked);
            rule.setConstraint(locked
                ? "主干必经：BU Scope 初审不可停用；命中条件 / 办理角色 / 会签方式 / 节点 SLA 可调整"
                : "可增删：停用后该场景不再走此节点（条件与节点 SLA 可调整）");
            rules.add(rule);
        }
        return rules;
    }

    /** 读取 ext_json（JSON 列在 MyBatis 中以字符串返回；非法内容按空处理，不打断页面） */
    private Map<String, Object> readExt(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        String text = String.valueOf(raw);
        if (StringUtils.isBlank(text) || !JsonUtils.isJsonObject(text)) {
            return Map.of();
        }
        try {
            return JsonUtils.parseMap(text);
        } catch (Exception e) {
            log.warn("parse cmd_flow_scene.ext_json failed: {}", text);
            return Map.of();
        }
    }

    /** 当前登录人（POC 免登录场景下 /cmd/** 为白名单，取不到时回落管理员） */
    private Long currentUserId() {
        try {
            Long uid = LoginHelper.getUserId();
            return uid == null ? 1L : uid;
        } catch (Exception ignored) {
            return 1L;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
