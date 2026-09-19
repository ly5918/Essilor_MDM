package org.dromara.cmd.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 场景-工作流映射 数据层 cmd_flow_scene / cmd_flow_node_rule
 * <p>
 * 只读映射配置（场景 → Warm-Flow 流程编码、节点审批人规则），
 * 与框架 Warm-Flow 的 flow_definition / flow_node 通过 flow_code 关联，不复制引擎数据。
 *
 * @author Essilor CMD POC
 */
public interface CmdFlowSceneMapper {

    /**
     * 按场景编码查询场景 → 流程映射
     *
     * @param sceneCode 场景编码
     * @return scene_code / scene_name / flow_code / flow_name / sla_hours
     */
    @Select("SELECT scene_code, scene_name, flow_code, flow_name, sla_hours, escalate_rule " +
        "FROM cmd_flow_scene WHERE scene_code = #{sceneCode} AND del_flag = '0' LIMIT 1")
    Map<String, Object> selectSceneByCode(@Param("sceneCode") String sceneCode);

    /**
     * 查询场景下的节点审批人规则（按优先级）
     *
     * @param sceneCode 场景编码
     * @return node_code / node_name / assignee_value / scope_type / multi_mode / sla_hours
     */
    @Select("SELECT node_code, node_name, assignee_value, scope_type, multi_mode, sla_hours " +
        "FROM cmd_flow_node_rule WHERE scene_code = #{sceneCode} AND status = '0' AND del_flag = '0' " +
        "ORDER BY priority")
    List<Map<String, Object>> selectNodeRules(@Param("sceneCode") String sceneCode);

    /**
     * 查询全部业务场景（用于「流程中心」列出所有 CMD 工作流）
     *
     * @return scene_code / scene_name / flow_code / flow_name / sla_hours
     */
    @Select("SELECT scene_code, scene_name, flow_code, flow_name, sla_hours " +
        "FROM cmd_flow_scene WHERE del_flag = '0' ORDER BY id")
    List<Map<String, Object>> selectAllScenes();
}
