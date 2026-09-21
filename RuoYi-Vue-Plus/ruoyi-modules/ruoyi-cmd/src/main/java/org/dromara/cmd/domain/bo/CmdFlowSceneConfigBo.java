package org.dromara.cmd.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 业务场景工作流配置入参（平台管理 › Workflow › 工作流定义 › 配置 › 确认）
 * <p>
 * 只允许提交「场景级可配置项」：场景 SLA / 超时升级 / 开始条件 / 超时动作 / 通知 / 节点规则。
 * 场景编码、流程编码、主干审批节点由平台固定，不在入参内（避免前端越权改动引擎绑定）。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdFlowSceneConfigBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 场景整体 SLA（小时） */
    private Integer slaHours;

    /** 超时升级规则（TO_GC / NOTIFY） */
    private String escalateRule;

    /** 启动条件表达式 */
    private String startConditions;

    /** 超时动作（ext_json.timeoutAction） */
    private String timeoutAction;

    /** 通知方式（ext_json.notifyMode） */
    private String notifyMode;

    /** 通知对象（ext_json.notifyTargets） */
    private List<String> notifyTargets;

    /** 本次变更说明（ext_json.changeNote） */
    private String changeNote;

    /** 节点审批人规则（id 为空 = 新增；status=1 = 停用即移除） */
    private List<RuleBo> rules;

    /**
     * 节点审批人规则入参
     */
    @Data
    public static class RuleBo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 规则主键（为空 = 新增） */
        private Long id;

        /** 节点编码（新增时必填） */
        private String nodeCode;

        /** 节点名称（新增时选填，缺省按节点编码生成） */
        private String nodeName;

        /** 命中条件表达式 */
        private String conditionExpr;

        /** 审批人类型 */
        private String assigneeType;

        /** 审批人值 */
        private String assigneeValue;

        /** 适用范围 */
        private String scopeType;

        /** 多审批人模式（ALL / ANY / SEQUENCE） */
        private String multiMode;

        /** 节点 SLA（小时） */
        private Integer slaHours;

        /** 优先级（新增时选填，缺省按表格顺序 10/20/30 递增） */
        private Integer priority;

        /** 状态（0 正常 / 1 停用） */
        private String status;

        /** 备注 */
        private String remark;
    }
}
