package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程跟踪视图对象（场景泳道图 → Warm-Flow 实例进度）
 * <p>
 * 对应页面：治理与审批 approval 右侧详情「流程跟踪」弹窗
 * （横向步骤条：已完成 / 进行中 / 待执行 三态高亮 + Data context state 变量表）。
 * <ul>
 *   <li>步骤模板来自总设计泳道图（场景一：单条客户创建，7 阶段 × 6 泳道）</li>
 *   <li>实时状态由 cmd_approval_task 当前节点 + cmd_approval_action 轨迹推导</li>
 *   <li>场景 → Warm-Flow 流程映射读 cmd_flow_scene，节点审批人规则读 cmd_flow_node_rule</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdFlowTraceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务编号 */
    private String taskNo;

    /** 客户名称 / 主题 */
    private String bizTitle;

    /** 业务类型 */
    private String bizType;

    /** 场景编码（cmd_flow_scene.scene_code） */
    private String sceneCode;

    /** 场景名称 */
    private String sceneName;

    /** 任务状态（PENDING / APPROVED / RETURNED / ESCALATED ...） */
    private String status;

    /** 当前节点名称 */
    private String currentNodeName;

    /** 当前处理人 */
    private String assigneeName;

    /** 当前处理角色 */
    private String assigneeRole;

    /** 归属 BU */
    private String buScope;

    /** 风险等级 */
    private String riskLevel;

    /** SLA 状态（NORMAL / DUE_SOON / OVERDUE） */
    private String slaState;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** SLA 应完成时间 */
    private LocalDateTime slaDue;

    /** Warm-Flow 流程编码（cmd_flow_scene.flow_code） */
    private String flowCode;

    /** Warm-Flow 流程名称 */
    private String flowName;

    /** 场景整体 SLA（小时，cmd_flow_scene.sla_hours） */
    private Integer slaHours;

    /** Warm-Flow 流程实例 ID（cmd_approval_task.flow_instance_id） */
    private Long flowInstanceId;

    /** Warm-Flow 待办任务 ID */
    private Long flowTaskId;

    /** Warm-Flow 流程定义 ID */
    private Long flowDefinitionId;

    /** Warm-Flow 流程状态镜像 */
    private String flowStatus;

    /** 步骤总数 */
    private Integer totalSteps;

    /** 已完成步骤数 */
    private Integer completedSteps;

    /** 进度百分比（0-100） */
    private Integer progressPercent;

    /** 泳道图旁路节点（规则与参数配置，不打断主流程） */
    private BypassNodeVo bypass;

    /** 主流程步骤（按泳道图阶段顺序） */
    private List<StepVo> steps = new ArrayList<>();

    /** 流程上下文变量（Data context state） */
    private List<VarVo> contextVars = new ArrayList<>();

    /** 审批动作轨迹（cmd_approval_action） */
    private List<ActionTraceVo> actions = new ArrayList<>();

    /** 是否已接入 Warm-Flow 引擎（flow_instance_id 非空） */
    private Boolean engineBound = Boolean.FALSE;

    /** BPMN 风格流程图（引擎节点/连线 + 实时状态） */
    private GraphVo graph;

    /**
     * BPMN 风格流程图（矩形任务节点 / 菱形网关 / 连线，按实例进度高亮）
     */
    @Data
    public static class GraphVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 流程定义 ID */
        private Long definitionId;

        /** 流程编码 */
        private String flowCode;

        /** 泳道顺序（自上而下，泳道图行标签） */
        private List<String> lanes = new ArrayList<>();

        private List<GraphNodeVo> nodes = new ArrayList<>();

        private List<GraphEdgeVo> edges = new ArrayList<>();
    }

    /** 流程图形节点 */
    @Data
    public static class GraphNodeVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 节点编码 */
        private String nodeCode;

        /** 节点名称 */
        private String nodeName;

        /** 图形类型（START 圆 / TASK 矩形 / GATEWAY 菱形 / END 圆） */
        private String shape;

        /** 节点类型（引擎 node_type） */
        private Integer nodeType;

        /** 泳道（角色，仅泳道图定义视图使用） */
        private String lane;

        /** 阶段序号（1-7，仅泳道图定义视图使用） */
        private Integer phase;

        /** 阶段名称（仅泳道图定义视图使用） */
        private String phaseName;

        /** 节点业务说明（仅泳道图定义视图使用） */
        private String note;

        /** 坐标（x,y） */
        private Integer x;

        private Integer y;

        /** 状态（COMPLETED / CURRENT / PENDING） */
        private String status;

        /** 处理人（当前/历史节点） */
        private String approver;

        /** 到达时间 */
        private LocalDateTime actionTime;
    }

    /** 流程图形连线 */
    @Data
    public static class GraphEdgeVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 起点节点编码 */
        private String from;

        /** 终点节点编码 */
        private String to;

        /** 连线名称（通过 / 退回） */
        private String label;

        /** 跳转类型（PASS / REJECT / NONE） */
        private String skipType;

        /** 条件表达式 */
        private String condition;

        /** 是否已走过（高亮） */
        private Boolean passed = Boolean.FALSE;
    }

    /**
     * 主流程步骤
     */
    @Data
    public static class StepVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 步骤序号（1 起） */
        private Integer order;

        /** 阶段序号（泳道图 1-7：发起/数据准备/自动校验/匹配分流/人工治理/审批发布/追踪审计） */
        private Integer phase;

        /** 阶段名称 */
        private String phaseName;

        /** 泳道（角色）：Business User / 系统自动处理 / Data Steward BU Scope / Data Steward GC Scope / Platform Admin / Auditor（只读） */
        private String lane;

        /** 节点编码 */
        private String nodeCode;

        /** 节点名称 */
        private String nodeName;

        /** 节点类型（AUTO 系统自动 / MANUAL 人工 / GATEWAY 分支网关） */
        private String nodeType;

        /** 执行状态（COMPLETED 已完成 / CURRENT 进行中 / PENDING 待执行 / TERMINATED 已终止） */
        private String status;

        /** 审批人（人工节点，来自 cmd_flow_node_rule.assignee_value） */
        private String assignee;

        /** 实际操作人（来自轨迹） */
        private String operator;

        /** 实际操作时间（来自轨迹） */
        private LocalDateTime actionTime;

        /** 审批意见（来自轨迹） */
        private String opinion;

        /** 路由说明（如 SUSPECT 进入 / Cross-BU 升级） */
        private String note;
    }

    /**
     * 泳道图旁路节点（规则与参数配置：配置变更不打断主流程）
     */
    @Data
    public static class BypassNodeVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 泳道 */
        private String lane;

        /** 节点名称 */
        private String nodeName;

        /** 说明 */
        private String note;
    }

    /**
     * 上下文变量（Data context state 表行）
     */
    @Data
    public static class VarVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 变量名 */
        private String name;

        /** 变量值（null 渲染为 not defined） */
        private String value;
    }

    /**
     * 审批动作轨迹行
     */
    @Data
    public static class ActionTraceVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 动作类型 */
        private String actionType;

        /** 动作名称 */
        private String actionName;

        /** 操作人 */
        private String operatorName;

        /** 操作角色 */
        private String operatorRole;

        /** 操作时间 */
        private LocalDateTime actionTime;

        /** 审批意见 */
        private String opinion;
    }
}
