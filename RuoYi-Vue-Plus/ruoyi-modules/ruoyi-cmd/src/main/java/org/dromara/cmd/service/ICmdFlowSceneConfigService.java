package org.dromara.cmd.service;

import org.dromara.cmd.domain.bo.CmdFlowSceneConfigBo;
import org.dromara.cmd.domain.vo.CmdFlowSceneConfigVo;

/**
 * 业务场景工作流配置服务（平台管理 › Workflow › 工作流定义 › 配置）
 * <p>
 * 对应 V6.1 总设计第 16 页「Workflow配置」：流程节点、路由条件、SLA、超时升级和邮件通知。
 * 配置一律落到 {@code cmd_flow_scene} / {@code cmd_flow_node_rule} 两张既有配置表，
 * 不新建引擎表、不复制 Warm-Flow 数据（引擎只认 flow_code 与节点编码）。
 *
 * @author Essilor CMD POC
 */
public interface ICmdFlowSceneConfigService {

    /**
     * 查询场景的工作流配置（含泳道节点蓝图 + 可配置的节点审批人规则）
     *
     * @param sceneCode 场景编码（CUSTOMER_CREATE / CUSTOMER_CHANGE / DEACTIVATE / HIER_RELATION / IMPORT_BATCH / MERGE）
     * @return 场景配置视图
     */
    CmdFlowSceneConfigVo selectConfig(String sceneCode);

    /**
     * 保存场景的工作流配置
     * <p>
     * 只更新场景级可配置项与节点规则；场景编码、流程编码等平台固定项不在更新范围内。
     *
     * @param sceneCode 场景编码
     * @param bo        配置入参（节点规则 id 为空表示新增）
     * @return 是否成功
     */
    boolean updateConfig(String sceneCode, CmdFlowSceneConfigBo bo);
}
