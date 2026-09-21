package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 业务场景工作流配置视图对象（平台管理 › Workflow › 工作流定义 › 某一行「配置」）
 * <p>
 * 对应 V6.1 总设计第 16 页「Workflow配置」：流程节点、路由条件、SLA、超时升级和邮件通知。
 * <p>
 * 数据来源（全部为已存在的配置表，不复制 Warm-Flow 引擎数据）：
 * <ul>
 *   <li>{@code cmd_flow_scene}：整体 SLA、超时升级规则、启动条件、表单标识、扩展属性；</li>
 *   <li>{@code cmd_flow_node_rule}：节点审批人规则（命中条件 / 审批人 / 会签或签 / 节点 SLA / 启停）；</li>
 *   <li>泳道节点蓝图由 {@code ICmdFlowEngineService#buildSwimlane} 统一产出，保证与泳道图口径一致。</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdFlowSceneConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 场景编码（平台固定，与 flow_code 一起绑定引擎流程定义，不可修改） */
    private String sceneCode;

    /** 场景名称（平台固定） */
    private String sceneName;

    /** Warm-Flow 流程编码（平台固定） */
    private String flowCode;

    /** Warm-Flow 流程名称 */
    private String flowName;

    /** 场景整体 SLA（小时） */
    private Integer slaHours;

    /** 超时升级规则（TO_GC 升级 GC Scope / NOTIFY 仅提醒） */
    private String escalateRule;

    /** 启动条件表达式：满足条件才走流程，否则直接生效 */
    private String startConditions;

    /** 审批表单标识（前端动态表单路由） */
    private String formKey;

    /** 是否已部署并发布到 Warm-Flow 引擎 */
    private Boolean deployed;

    /** 流程版本号 */
    private Integer version;

    /** 超时动作（ext_json.timeoutAction：Notify+Escalate / Auto Approve / Auto Reject） */
    private String timeoutAction;

    /** 通知方式（ext_json.notifyMode：Email Only / Email + System Message） */
    private String notifyMode;

    /** 通知对象（ext_json.notifyTargets） */
    private List<String> notifyTargets;

    /** 本次变更说明（ext_json.changeNote） */
    private String changeNote;

    /** 泳道节点蓝图（只读展示，标注平台固定 / 自动执行 / 可配置） */
    private List<NodeVo> nodes;

    /** 可配置的节点审批人规则（可增删、可改命中条件 / 审批人 / 会签方式 / 节点 SLA） */
    private List<RuleVo> rules;

    /**
     * 泳道节点（业务蓝图，来自 buildSwimlane，与「泳道图」弹窗同一份口径）
     */
    @Data
    public static class NodeVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 阶段序号（1-7） */
        private Integer phase;

        /** 阶段名称 */
        private String phaseName;

        /** 泳道（角色） */
        private String lane;

        /** 节点编码 */
        private String nodeCode;

        /** 节点名称 */
        private String nodeName;

        /** 节点类型（AUTO 系统自动 / MANUAL 人工 / GATEWAY 分支网关） */
        private String nodeType;

        /** 节点业务说明 */
        private String note;

        /** 是否平台固定（不可删除 / 不可停用） */
        private Boolean locked;

        /** 是否可配置（人工节点由路由规则配置） */
        private Boolean configurable;

        /** 约束说明（为什么不可修改 / 可以配置什么） */
        private String constraint;
    }

    /**
     * 节点审批人规则（cmd_flow_node_rule）—— 场景级「可增删、可调整」的工作流项目
     */
    @Data
    public static class RuleVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 规则主键（为空表示新增） */
        private Long id;

        /** Warm-Flow 节点编码（bu_review / gc_review ...） */
        private String nodeCode;

        /** 节点名称（冗余展示） */
        private String nodeName;

        /** 命中条件表达式（如 risk_level == "High" || cross_bu） */
        private String conditionExpr;

        /** 审批人类型（ROLE / USER / DEPT_LEADER / SPEL） */
        private String assigneeType;

        /** 审批人值（角色编码 / 用户 ID / 表达式） */
        private String assigneeValue;

        /** 适用范围（GC / BU / CROSS_BU） */
        private String scopeType;

        /** 多审批人模式（ALL 会签 / ANY 或签 / SEQUENCE 依次） */
        private String multiMode;

        /** 节点 SLA（小时） */
        private Integer slaHours;

        /** 优先级（同节点多规则按优先级命中） */
        private Integer priority;

        /** 状态（0 正常 / 1 停用）—— 停用即从该场景的工作流中移除该节点规则 */
        private String status;

        /** 备注 */
        private String remark;

        /** 是否平台固定（不可停用，如 BU 初审主干） */
        private Boolean locked;

        /** 约束说明 */
        private String constraint;
    }
}
