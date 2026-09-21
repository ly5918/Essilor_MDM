package org.dromara.cmd.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 层级归位业务对象
 * <p>
 * 对应页面：客户层级 hier —— 「待归位主数据」列表里的「归位」按钮。
 * <p>
 * 语义：把一条已批准的主数据（待归位节点）挂到某个 A3 / A2 节点之下，
 * 使其成为层级树上的 A2 / A1 节点。归位时服务端会做：
 * 环路检测 → 级别推导（父 depth + 1）→ 路径重建 → 祖先计数刷新 → 关系留痕。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdHierarchyAssignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待归位客户 One ID（子节点） */
    @NotBlank(message = "待归位客户 One ID 不能为空")
    private String oneId;

    /** 目标父节点 One ID（A3 或 A2） */
    @NotBlank(message = "目标父节点不能为空")
    private String parentOneId;

    /** 变更原因（写入关系与审计） */
    private String changeReason;

    /** 备注 */
    private String remark;
}
