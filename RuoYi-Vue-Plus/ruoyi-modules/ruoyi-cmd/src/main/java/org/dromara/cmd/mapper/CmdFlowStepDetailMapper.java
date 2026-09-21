package org.dromara.cmd.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 流程跟踪「分步骤明细」数据层
 * <p>
 * 支撑「流程跟踪」里点开某一个泳道节点后，在其下方动态展示该节点的相关业务内容：
 * 录入与附件（cmd_attachment）、OCR 与智能补全（cmd_ocr_result）、技术与业务 DQ（cmd_customer 冗余分值）、
 * Duplicate Check（cmd_match_candidate / 同信用代码复算）、人工治理（cmd_approval_action）、
 * 生成与关联结果（cmd_legacy_mapping）、发布下游（int_run）、运行追踪（cmd_workflow_step_log）、
 * 审计查询（audit_event）。
 * <p>
 * 约定：
 * <ul>
 *   <li>全部返回 {@code Map}，不做实体映射——明细只用于展示，避免为一次性视图新增领域对象；</li>
 *   <li>数值列统一 {@code CAST(... AS CHAR)} 输出：MySQL tinyint(1) 经 MyBatis 映射为 Boolean，
 *       直接强转 Number 会抛 ClassCastException（历史踩坑）；</li>
 *   <li>别名一律使用驼峰，避免 mapUnderscoreToCamelCase 造成键名歧义。</li>
 * </ul>
 *
 * @author Essilor CMD POC
 */
public interface CmdFlowStepDetailMapper {

    /**
     * 客户主档关键字段（录入 / DQ / 结果节点的数据来源）
     *
     * @param oneId 客户 One ID
     * @return 单行客户主档关键字段
     */
    @Select("SELECT c.one_id AS oneId, c.legal_name AS legalName, c.legal_name_en AS legalNameEn, "
        + "c.credit_code AS creditCode, c.tax_no AS taxNo, c.customer_type AS customerType, "
        + "c.product_line AS productLine, c.bu_scope AS buScope, c.country AS country, "
        + "c.province AS province, c.city AS city, c.address AS address, "
        + "c.contact_name AS contactName, c.contact_phone AS contactPhone, c.contact_email AS contactEmail, "
        + "c.status AS status, c.source_system AS sourceSystem, c.source_id AS sourceId, "
        + "CAST(c.dq_score AS CHAR) AS dqScore, c.dq_grade AS dqGrade, c.match_state AS matchState, "
        + "c.duplicate_flag AS duplicateFlag, CAST(c.version_no AS CHAR) AS versionNo, "
        + "DATE_FORMAT(c.effective_from, '%Y-%m-%d %H:%i') AS effectiveFrom, "
        + "CAST(c.flow_instance_id AS CHAR) AS flowInstanceId, c.flow_status AS flowStatus "
        + "FROM cmd_customer c WHERE c.one_id = #{oneId} AND c.del_flag = '0' LIMIT 1")
    Map<String, Object> selectCustomer(@Param("oneId") String oneId);

    /**
     * 附件清单（「录入与附件」节点）
     *
     * @param bizId 业务主键（客户 One ID / 申请编号）
     * @return 附件行
     */
    @Select("SELECT a.file_name AS fileName, a.category AS category, a.ocr_status AS ocrStatus, "
        + "a.file_type AS fileType, CAST(a.file_size AS CHAR) AS fileSize, "
        + "DATE_FORMAT(a.create_time, '%Y-%m-%d %H:%i') AS createTime "
        + "FROM cmd_attachment a WHERE a.biz_id = #{bizId} AND a.del_flag = '0' ORDER BY a.id")
    List<Map<String, Object>> selectAttachments(@Param("bizId") String bizId);

    /**
     * OCR 识别结果行（「OCR 与智能补全」节点）
     *
     * @param bizId 业务主键（客户 One ID / 申请编号）
     * @return 识别字段行
     */
    @Select("SELECT o.field_code AS fieldCode, o.field_name AS fieldName, o.ocr_value AS ocrValue, "
        + "o.confirmed_value AS confirmedValue, CAST(o.confidence AS CHAR) AS confidence, "
        + "o.needs_review AS needsReview, o.ocr_engine AS ocrEngine, "
        + "DATE_FORMAT(o.create_time, '%Y-%m-%d %H:%i') AS createTime "
        + "FROM cmd_ocr_result o WHERE o.biz_id = #{bizId} AND o.del_flag = '0' ORDER BY o.id")
    List<Map<String, Object>> selectOcrResults(@Param("bizId") String bizId);

    /**
     * 匹配候选（「Duplicate Check」节点，已落库时优先）
     *
     * @param bizId 业务主键（客户 One ID / 申请编号）
     * @return 候选行
     */
    @Select("SELECT m.one_id AS oneId, m.legal_name AS legalName, m.credit_code AS creditCode, "
        + "m.bu_scope AS buScope, m.customer_level AS customerLevel, "
        + "CAST(m.total_score AS CHAR) AS totalScore, m.is_best AS isBest, m.status AS status "
        + "FROM cmd_match_candidate m JOIN cmd_match_result r ON r.id = m.match_id "
        + "WHERE r.biz_id = #{bizId} AND m.del_flag = '0' ORDER BY m.total_score DESC LIMIT 5")
    List<Map<String, Object>> selectMatchCandidates(@Param("bizId") String bizId);

    /**
     * 同统一社会信用代码的其他主数据（Duplicate Check 未落候选表时的真实复算口径）
     *
     * @param oneId      当前 One ID（排除自身）
     * @param creditCode 统一社会信用代码
     * @return 候选行
     */
    @Select("SELECT c.one_id AS oneId, c.legal_name AS legalName, c.credit_code AS creditCode, "
        + "c.bu_scope AS buScope, CAST(c.dq_score AS CHAR) AS dqScore, c.match_state AS matchState, "
        + "c.status AS status FROM cmd_customer c "
        + "WHERE c.del_flag = '0' AND c.one_id <> #{oneId} AND c.credit_code = #{creditCode} LIMIT 5")
    List<Map<String, Object>> selectSameCreditCandidates(@Param("oneId") String oneId,
                                                         @Param("creditCode") String creditCode);

    /**
     * 批量导入批次（「数据装配」节点在导入场景下的真实数据来源 cmd_import_job）
     *
     * @param jobCode 批次号（cmd_approval_task.biz_id）
     * @return 批次行
     */
    @Select("SELECT j.job_code AS jobCode, j.job_name AS jobName, j.file_name AS fileName, "
        + "j.template_code AS templateCode, j.template_version AS templateVersion, j.bu_scope AS buScope, "
        + "j.scene AS scene, j.error_strategy AS errorStrategy, j.duplicate_strategy AS duplicateStrategy, "
        + "CAST(j.total_count AS CHAR) AS totalCount, CAST(j.success_count AS CHAR) AS successCount, "
        + "CAST(j.exact_count AS CHAR) AS exactCount, CAST(j.suspected_count AS CHAR) AS suspectedCount, "
        + "CAST(j.new_count AS CHAR) AS newCount, CAST(j.invalid_count AS CHAR) AS invalidCount, "
        + "CAST(j.review_count AS CHAR) AS reviewCount, j.job_status AS jobStatus, "
        + "j.submit_by AS submitBy, DATE_FORMAT(j.submit_time, '%Y-%m-%d %H:%i:%s') AS submitTime, "
        + "DATE_FORMAT(j.end_time, '%Y-%m-%d %H:%i:%s') AS endTime, j.remark AS remark "
        + "FROM cmd_import_job j WHERE j.job_code = #{jobCode} AND j.del_flag = '0' LIMIT 1")
    Map<String, Object> selectImportJob(@Param("jobCode") String jobCode);

    /**
     * 批量导入行明细（「数据装配 / 自动校验」在导入场景下的真实数据 cmd_import_row）
     *
     * @param jobCode 批次号
     * @return 行明细（含解析后的字段 JSON，供展示实际录入值）
     */
    @Select("SELECT CAST(r.row_no AS CHAR) AS rowNo, r.row_status AS rowStatus, r.result_type AS resultType, "
        + "r.one_id AS oneId, r.legal_name AS legalName, r.credit_code AS creditCode, r.bu_scope AS buScope, "
        + "CAST(r.dq_score AS CHAR) AS dqScore, r.match_state AS matchState, "
        + "CAST(r.error_count AS CHAR) AS errorCount, r.error_summary AS errorSummary, r.handling AS handling, "
        // parsed_json 是模板映射后的真实录入字段；JSON 里的 null 经 JSON_UNQUOTE 会变成字符串 'null'，用 NULLIF 兜掉
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.address')), 'null') AS address, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.city')), 'null') AS city, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.province')), 'null') AS province, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.contact_phone')), 'null') AS contactPhone, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.contact_name')), 'null') AS contactName, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.legal_name_en')), 'null') AS legalNameEn, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.tax_no')), 'null') AS taxNo, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.product_line')), 'null') AS productLine, "
        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.parsed_json, '$.contact_email')), 'null') AS contactEmail "
        + "FROM cmd_import_row r WHERE r.job_code = #{jobCode} AND r.del_flag = '0' ORDER BY r.row_no")
    List<Map<String, Object>> selectImportRows(@Param("jobCode") String jobCode);

    /**
     * 审批动作轨迹（人工治理节点）
     *
     * @param taskId 审批任务主键
     * @return 动作行
     */
    @Select("SELECT DATE_FORMAT(a.action_time, '%Y-%m-%d %H:%i:%s') AS actionTime, a.action_type AS actionType, "
        + "a.action_name AS actionName, a.operator_name AS operatorName, a.operator_role AS operatorRole, "
        + "a.from_node_code AS fromNodeCode, a.to_node_code AS toNodeCode, "
        + "a.before_state AS beforeState, a.after_state AS afterState, a.opinion AS opinion "
        + "FROM cmd_approval_action a WHERE a.task_id = #{taskId} AND a.del_flag = '0' "
        + "ORDER BY a.action_time, a.id")
    List<Map<String, Object>> selectApprovalActions(@Param("taskId") Long taskId);

    /**
     * 本地编码映射（「生成 / 关联结果」节点：One ID ↔ DMS+ / SAP / Cloud 编码）
     *
     * @param oneId 客户 One ID
     * @return 映射行
     */
    @Select("SELECT l.source_system AS sourceSystem, l.source_code AS sourceCode, l.source_name AS sourceName, "
        + "l.bu_scope AS buScope, l.mapping_type AS mappingType, l.status AS status "
        + "FROM cmd_legacy_mapping l WHERE l.one_id = #{oneId} AND l.del_flag = '0' ORDER BY l.id")
    List<Map<String, Object>> selectLegacyMappings(@Param("oneId") String oneId);

    /**
     * 最近集成通道运行记录（「发布到下游」节点）
     * <p>
     * int_run 为**通道级**运行记录（POC 未建客户级下发明细），展示时须标注口径。
     *
     * @return 运行记录行（最近 5 次）
     */
    @Select("SELECT r.run_code AS runCode, r.endpoint_name AS endpointName, r.target_system AS targetSystem, "
        + "r.direction AS direction, r.run_status AS runStatus, CAST(r.total_count AS CHAR) AS totalCount, "
        + "CAST(r.success_count AS CHAR) AS successCount, CAST(r.failed_count AS CHAR) AS failedCount, "
        + "CAST(r.attempt_count AS CHAR) AS attemptCount, "
        + "DATE_FORMAT(r.start_time, '%Y-%m-%d %H:%i') AS startTime, "
        + "CAST(r.duration_ms AS CHAR) AS durationMs, r.error_message AS errorMessage "
        + "FROM int_run r WHERE r.del_flag = '0' ORDER BY r.start_time DESC LIMIT 5")
    List<Map<String, Object>> selectRecentIntegrationRuns();

    /**
     * 工作流步骤执行日志（「运行追踪」节点：按 One ID 串联的每一步总账）
     *
     * @param oneId 客户 One ID
     * @return 步骤行
     */
    @Select("SELECT CAST(l.step_seq AS CHAR) AS stepSeq, l.step_type AS stepType, l.node_code AS nodeCode, "
        + "l.node_name AS nodeName, l.action_type AS actionType, l.action_name AS actionName, "
        + "l.operator_name AS operatorName, l.operator_role AS operatorRole, "
        + "DATE_FORMAT(l.create_time, '%Y-%m-%d %H:%i:%s') AS createTime, "
        + "l.from_status AS fromStatus, l.to_status AS toStatus, l.opinion AS opinion "
        + "FROM cmd_workflow_step_log l WHERE l.one_id = #{oneId} AND l.del_flag = '0' "
        + "ORDER BY l.step_seq, l.id")
    List<Map<String, Object>> selectStepLogs(@Param("oneId") String oneId);

    /**
     * 审计事件（「审计查询」节点：Before / After 证据链）
     *
     * @param oneId 客户 One ID
     * @return 审计事件行（最近 10 条）
     */
    @Select("SELECT e.event_id AS eventId, e.event_type AS eventType, e.event_name AS eventName, "
        + "e.operator_name AS operatorName, e.operator_role AS operatorRole, "
        + "DATE_FORMAT(e.event_time, '%Y-%m-%d %H:%i') AS eventTime, e.result AS result, "
        + "e.risk_level AS riskLevel, e.changed_fields AS changedFields "
        + "FROM audit_event e WHERE e.one_id = #{oneId} AND e.del_flag = '0' "
        + "ORDER BY e.event_time DESC LIMIT 10")
    List<Map<String, Object>> selectAuditEvents(@Param("oneId") String oneId);
}
