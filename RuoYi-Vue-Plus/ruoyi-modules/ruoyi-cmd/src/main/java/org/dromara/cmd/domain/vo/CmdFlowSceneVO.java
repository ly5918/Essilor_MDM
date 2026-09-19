package org.dromara.cmd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程场景视图对象（CMD 业务场景 → Warm-Flow 流程定义）
 * <p>
 * 对应「流程中心」页面：单独列出所有 CMD 业务工作流（来自 V6.1 总设计的场景），
 * 每个场景给出部署状态与节点数，点击「查看流程图」可进入详细流程图（Graph）。
 *
 * @author Essilor CMD POC
 */
@Data
public class CmdFlowSceneVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 场景编码（cmd_flow_scene.scene_code） */
    private String sceneCode;

    /** 场景名称 */
    private String sceneName;

    /** Warm-Flow 流程编码 */
    private String flowCode;

    /** Warm-Flow 流程名称 */
    private String flowName;

    /** 场景整体 SLA（小时） */
    private Integer slaHours;

    /** 是否已部署并发布到 Warm-Flow 引擎 */
    private Boolean deployed;

    /** 已发布的流程定义 ID（未部署为 null） */
    private Long definitionId;

    /** 流程版本号（未部署为 null） */
    private Integer version;

    /** 流程节点数（未部署为 0） */
    private Integer nodeCount;
}
