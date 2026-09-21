package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 层级关系提交前校验结果视图对象
 * <p>
 * 对应页面：客户层级 hier —— 「增加子节点 / 编辑层级关系 / 发起申请」弹窗里的实时校验区。
 * <p>
 * 由服务端按父子节点、层级深度、多父冲突、完整路径循环、有效期重叠逐条检查后返回，
 * 前端只负责展示，不再自行推算 —— 保证「弹窗里看到的结论」与「提交时数据库的判断」完全一致。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdHierarchyValidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 校验编号（同时写入 cmd_loop_check_log.check_code） */
    private String checkCode;

    /** 是否全部通过（false 表示存在阻塞项，提交会被拒绝） */
    private Boolean passed;

    /** 阻塞原因（passed=false 时的第一条 FAIL 信息） */
    private String blockedReason;

    /** 父子节点显示串（A3-001 远见集团 → A2-0188 远见华东法人） */
    private String relationLabel;

    /** 父节点 One ID（校验入参回显） */
    private String parentOneId;

    /** 子节点 One ID（校验入参回显） */
    private String childOneId;

    /** 子节点名称 */
    private String childName;

    /** 父节点名称 */
    private String parentName;

    /** 父节点当前级别（A3 / A2） */
    private String parentLevel;

    /** 父节点当前深度 */
    private Integer parentDepth;

    /** 挂载后子节点级别（A3 / A2 / A1） */
    private String childLevel;

    /** 挂载后子节点深度 */
    private Integer childDepth;

    /** 挂载后的完整路径（/oneId/oneId/） */
    private String previewPath;

    /** 挂载后的路径名称串（展示用） */
    private String previewPathNames;

    /** 是否跨 BU */
    private Boolean crossBu;

    /** 是否需升级 GC Scope 审批（跨 BU 或重大 A2/A3 调整） */
    private Boolean requiresGcApproval;

    /** 关系类型（服务端推导或入参） */
    private String relationType;

    /** 层级类型 */
    private String hierarchyType;

    /** 层级上限（A3 → A2 → A1 最多三级） */
    private Integer maxDepth;

    /** 子节点是否已在树上（false 表示尚未归位 / 未登记节点） */
    private Boolean childMounted;

    /** 子节点当前父节点（已挂载时返回，用于「改挂」提示） */
    private String childCurrentParentOneId;

    /** 子节点当前级别 */
    private String childCurrentLevel;

    /** 逐条校验明细 */
    private List<CheckItem> checks = new ArrayList<>();

    /** 执行时间 */
    private LocalDateTime executeTime;

    /** 耗时（毫秒） */
    private Long durationMs;

    /**
     * 单条校验明细
     */
    @Data
    public static class CheckItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 检测类型（SELF_REF / SELF_MOUNT / LEVEL_RULE / MULTI_PARENT / CYCLE / VALIDITY / CROSS_BU） */
        private String checkType;

        /** 检测项名称（父子节点不同 / 层级级别有效 / 多父冲突 / 完整路径循环 ...） */
        private String label;

        /** 检测结果（PASS / FAIL / WARN） */
        private String checkResult;

        /** 结论说明 */
        private String message;

        /** 冲突路径（FAIL 时给出，作为审计证据） */
        private String conflictPath;

        /** 修正建议 */
        private String suggestion;
    }
}
