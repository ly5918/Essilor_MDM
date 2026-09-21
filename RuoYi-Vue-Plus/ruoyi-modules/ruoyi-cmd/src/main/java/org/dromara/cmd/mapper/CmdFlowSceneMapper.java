package org.dromara.cmd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    // ==================== 场景工作流配置（平台管理 › Workflow › 配置） ====================

    /**
     * 查询场景的全部可配置字段（含 start_conditions / escalate_rule / form_key / ext_json）
     *
     * @param sceneCode 场景编码
     * @return 单行配置
     */
    @Select("SELECT scene_code, scene_name, flow_code, flow_name, sla_hours, escalate_rule, " +
        "start_conditions, form_key, ext_json " +
        "FROM cmd_flow_scene WHERE scene_code = #{sceneCode} AND del_flag = '0' LIMIT 1")
    Map<String, Object> selectSceneConfig(@Param("sceneCode") String sceneCode);

    /**
     * 更新场景级配置（场景编码与流程编码为平台固定项，不在更新范围内）
     */
    @Update("UPDATE cmd_flow_scene SET sla_hours = #{slaHours}, escalate_rule = #{escalateRule}, " +
        "start_conditions = #{startConditions}, ext_json = #{extJson}, " +
        "update_by = #{updateBy}, update_time = NOW() " +
        "WHERE scene_code = #{sceneCode} AND del_flag = '0'")
    int updateSceneConfig(@Param("sceneCode") String sceneCode,
                          @Param("slaHours") Integer slaHours,
                          @Param("escalateRule") String escalateRule,
                          @Param("startConditions") String startConditions,
                          @Param("extJson") String extJson,
                          @Param("updateBy") Long updateBy);

    /**
     * 查询场景的节点审批人规则（含停用记录，便于前端展示与再启用）
     *
     * @param sceneCode 场景编码
     * @return 规则列表（按节点优先级）
     */
    @Select("SELECT id, node_code, node_name, condition_expr, assignee_type, assignee_value, scope_type, " +
        "multi_mode, sla_hours, priority, status, remark " +
        "FROM cmd_flow_node_rule WHERE scene_code = #{sceneCode} AND del_flag = '0' " +
        "ORDER BY priority, id")
    List<Map<String, Object>> selectSceneRules(@Param("sceneCode") String sceneCode);

    /**
     * 更新节点审批人规则（命中条件 / 审批人 / 会签方式 / 节点 SLA / 启停）
     */
    @Update("UPDATE cmd_flow_node_rule SET condition_expr = #{conditionExpr}, assignee_type = #{assigneeType}, " +
        "assignee_value = #{assigneeValue}, scope_type = #{scopeType}, multi_mode = #{multiMode}, " +
        "sla_hours = #{slaHours}, status = #{status}, remark = #{remark}, " +
        "update_by = #{updateBy}, update_time = NOW() WHERE id = #{id} AND del_flag = '0'")
    int updateNodeRule(@Param("id") Long id,
                       @Param("conditionExpr") String conditionExpr,
                       @Param("assigneeType") String assigneeType,
                       @Param("assigneeValue") String assigneeValue,
                       @Param("scopeType") String scopeType,
                       @Param("multiMode") String multiMode,
                       @Param("slaHours") Integer slaHours,
                       @Param("status") String status,
                       @Param("remark") String remark,
                       @Param("updateBy") Long updateBy);

    /**
     * 新增节点审批人规则（场景级「增加工作流节点」）
     */
    @Insert("INSERT INTO cmd_flow_node_rule (id, scene_code, node_code, node_name, condition_expr, assignee_type, " +
        "assignee_value, scope_type, bu_scope, multi_mode, allow_transfer, allow_add_sign, sla_hours, priority, " +
        "status, remark, del_flag, create_by, create_time) " +
        "VALUES (#{id}, #{sceneCode}, #{nodeCode}, #{nodeName}, #{conditionExpr}, #{assigneeType}, " +
        "#{assigneeValue}, #{scopeType}, NULL, #{multiMode}, 'Y', 'Y', #{slaHours}, #{priority}, " +
        "'0', #{remark}, '0', #{createBy}, NOW())")
    int insertNodeRule(@Param("id") Long id,
                       @Param("sceneCode") String sceneCode,
                       @Param("nodeCode") String nodeCode,
                       @Param("nodeName") String nodeName,
                       @Param("conditionExpr") String conditionExpr,
                       @Param("assigneeType") String assigneeType,
                       @Param("assigneeValue") String assigneeValue,
                       @Param("scopeType") String scopeType,
                       @Param("multiMode") String multiMode,
                       @Param("slaHours") Integer slaHours,
                       @Param("priority") Integer priority,
                       @Param("remark") String remark,
                       @Param("createBy") Long createBy);

    /**
     * 查询节点的默认办理角色与适用范围（cmd_flow_node_rule 中该节点已有规则的取值）
     *
     * @param nodeCode 节点编码（bu_review / gc_review）
     * @return assignee_value / scope_type
     */
    @Select("SELECT assignee_value, scope_type, multi_mode FROM cmd_flow_node_rule " +
        "WHERE node_code = #{nodeCode} AND del_flag = '0' ORDER BY priority LIMIT 1")
    Map<String, Object> selectNodeDefault(@Param("nodeCode") String nodeCode);
}
