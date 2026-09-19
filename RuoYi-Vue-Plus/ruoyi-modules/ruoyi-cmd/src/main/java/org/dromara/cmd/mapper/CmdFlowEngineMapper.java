package org.dromara.cmd.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Warm-Flow 引擎只读查询（flow_* 表）
 * <p>
 * 约定：业务模块不写引擎表，也不复制引擎数据；此处仅做「读取」用于流程跟踪展示
 * （流程定义图形、实例当前待办、历史轨迹），写入一律走 Warm-Flow 原生 Service。
 *
 * @author Essilor CMD POC
 */
public interface CmdFlowEngineMapper {

    /**
     * 查询已发布的流程定义 ID（Warm-Flow 发布状态：PUBLISHED=1）
     *
     * @param flowCode 流程编码
     * @return 定义 ID，不存在返回 null
     */
    @Select("SELECT id FROM flow_definition WHERE flow_code = #{flowCode} AND is_publish = 1 AND del_flag = '0' ORDER BY id DESC LIMIT 1")
    Long selectPublishedDefinitionId(@Param("flowCode") String flowCode);

    /**
     * 查询流程定义 ID（忽略发布状态，仅排除失效 EXPIRED=9）
     * <p>
     * 用于部署后回查主键：save 之后定义处于 UNPUBLISHED(0)，不能以“已发布”条件反查。
     *
     * @param flowCode 流程编码
     * @return 定义 ID，不存在返回 null
     */
    @Select("SELECT id FROM flow_definition WHERE flow_code = #{flowCode} AND is_publish <> 9 AND del_flag = '0' ORDER BY id DESC LIMIT 1")
    Long selectDefinitionId(@Param("flowCode") String flowCode);

    /**
     * 查询已发布流程定义的版本号（如 v1.0）
     *
     * @param definitionId 定义 ID
     * @return 版本字符串，不存在返回 null
     */
    @Select("SELECT version FROM flow_definition WHERE id = #{definitionId} AND del_flag = '0'")
    String selectVersion(@Param("definitionId") Long definitionId);

    /**
     * 查询流程定义节点（用于 BPMN 风格图形渲染）
     *
     * @param definitionId 定义 ID
     * @return node_code / node_name / node_type / coordinate
     */
    // 注意：flow_node.node_type 为 tinyint(1)，MySQL 驱动默认（tinyInt1isBit=true）会映射为 Boolean，
    // 直接强转 Number 会抛 ClassCastException，故在 SQL 侧 CAST 成整型返回。
    @Select("SELECT node_code, node_name, CAST(node_type AS SIGNED) AS node_type, coordinate FROM flow_node " +
        "WHERE definition_id = #{definitionId} AND del_flag = '0' ORDER BY id")
    List<Map<String, Object>> selectNodes(@Param("definitionId") Long definitionId);

    /**
     * 查询流程定义连线（用于 BPMN 风格图形渲染）
     *
     * @param definitionId 定义 ID
     * @return now_node_code / next_node_code / skip_name / skip_type / skip_condition
     */
    @Select("SELECT now_node_code, next_node_code, skip_name, skip_type, skip_condition FROM flow_skip " +
        "WHERE definition_id = #{definitionId} AND del_flag = '0' ORDER BY id")
    List<Map<String, Object>> selectSkips(@Param("definitionId") Long definitionId);

    /**
     * 查询实例当前待办（flow_task）
     *
     * @param instanceId 实例 ID
     * @return id / node_code / node_name / create_time
     */
    @Select("SELECT id, node_code, node_name, create_time FROM flow_task " +
        "WHERE instance_id = #{instanceId} AND del_flag = '0'")
    List<Map<String, Object>> selectTasks(@Param("instanceId") Long instanceId);

    /**
     * 查询实例历史轨迹（flow_his_task）
     *
     * @param instanceId 实例 ID
     * @return node_code / node_name / flow_status / approver / create_time
     */
    @Select("SELECT node_code, node_name, flow_status, approver, create_time FROM flow_his_task " +
        "WHERE instance_id = #{instanceId} AND del_flag = '0' ORDER BY id")
    List<Map<String, Object>> selectHisTasks(@Param("instanceId") Long instanceId);

    /**
     * 查询实例状态
     *
     * @param instanceId 实例 ID
     * @return id / flow_status / business_id / create_time
     */
    @Select("SELECT id, definition_id, flow_status, business_id, create_time FROM flow_instance WHERE id = #{instanceId}")
    Map<String, Object> selectInstance(@Param("instanceId") Long instanceId);
}
