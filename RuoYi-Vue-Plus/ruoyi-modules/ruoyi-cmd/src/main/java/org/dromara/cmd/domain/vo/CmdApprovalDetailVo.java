package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 治理与审批 详情视图对象
 * <p>
 * 对应页面：治理与审批 approval 右侧详情列
 * （申请信息 / 自动检查结果 / 决策标签 / 治理证据 / 审批意见 / 操作按钮）。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdApprovalDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（提交审批动作时回传） */
    private Long id;

    /** 任务编号 */
    private String taskId;

    /** 客户名称 / 主题 */
    private String name;

    /** 场景（业务类型） */
    private String scene;

    /** 提交人 */
    private String submitter;

    /** 当前节点 */
    private String currentNode;

    /** SLA 状态 */
    private String sla;

    /** 数据质量结论 */
    private String dq;

    /** 重复检查 / 匹配结论 */
    private String duplicate;

    /** 治理证据 */
    private String evidence;

    /** 决策 / 判断标签 */
    private List<String> decisions = new ArrayList<>();

    /** 可执行操作按钮 */
    private List<ActionVo> actions = new ArrayList<>();

    /**
     * 操作按钮
     */
    @Data
    public static class ActionVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 动作编码（APPROVE / REJECT / RETURN / ESCALATE ...） */
        private String key;

        /** 按钮文案 */
        private String label;

        /** 按钮样式（primary / success / warning / danger / info） */
        private String type;
    }
}
