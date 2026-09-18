package org.dromara.cmd.common;

/**
 * CMD（Customer Master Data）业务常量
 * <p>
 * 设计说明：所有枚举值、场景编码、状态编码统一在此定义，避免硬编码散落到各处；
 * 需要与数据库保持一致的状态字典，优先落到 cfg_config / md_value_set 表中（可配置化），
 * 此处仅定义"代码逻辑强依赖"的常量。
 *
 * @author Essilor CMD POC
 */
public interface CmdConstants {

    // ==================== 数据源策略（重要） ====================
    // 统一使用框架主库 ruoyi_plus（master 数据源），业务表不单独建 schema、不加 @DS 注解。
    // 原因：
    //   1) 审批流需 JOIN Warm-Flow 的 flow_* 表、sys_user / sys_dept 等系统表，跨库无法 JOIN；
    //   2) 业务写操作需与流程引擎同事务，跨库会导致 @Transactional 失效；
    //   3) 单库便于备份、迁移、升级框架版本。
    // 隔离方式：业务表统一使用 cmd_ / md_ / dq_ / match_ / oneid_ / int_ / audit_ / cfg_ / poc_ 前缀，
    //   与框架 sys_ / flow_ / gen_ / sj_ 前缀区分，互不覆盖、互不干扰。

    /** 逻辑未删除 */
    String DEL_FLAG_NORMAL = "0";
    /** 逻辑已删除 */
    String DEL_FLAG_DELETED = "1";

    /** 是 */
    String YES = "Y";
    /** 否 */
    String NO = "N";

    // ==================== 客户状态 cmd_customer.status ====================

    /** 草稿 */
    String CUST_STATUS_DRAFT = "draft";
    /** 待审批 */
    String CUST_STATUS_PENDING = "pending";
    /** 已生效 */
    String CUST_STATUS_ACTIVE = "active";
    /** 逻辑停用 */
    String CUST_STATUS_INACTIVE = "inactive";
    /** 已归档 */
    String CUST_STATUS_ARCHIVED = "archived";
    /** 已驳回 */
    String CUST_STATUS_REJECTED = "rejected";

    // ==================== 匹配结论 cmd_customer.match_state ====================

    /** 精确匹配 */
    String MATCH_EXACT = "EXACT";
    /** 疑似重复 */
    String MATCH_SUSPECTED = "SUSPECTED";
    /** 新客户 */
    String MATCH_NEW = "NEW";
    /** 需人工复核 */
    String MATCH_REVIEW = "REVIEW";
    /** 无效数据 */
    String MATCH_INVALID = "INVALID";

    // ==================== 治理任务类型 cmd_governance_task.task_type ====================

    /** 疑似重复 */
    String GOV_TYPE_SUSPECT = "SUSPECT";
    /** 待复核 */
    String GOV_TYPE_REVIEW = "REVIEW";
    /** 新建确认 */
    String GOV_TYPE_NEW = "NEW";
    /** 跨 BU 决策 */
    String GOV_TYPE_CROSS_BU = "CROSS_BU";

    // ==================== 统一待办分类 cmd_approval_task.task_category ====================

    /** 审批类 */
    String APPR_CAT_APPROVAL = "APPROVAL";
    /** 治理复核类 */
    String APPR_CAT_GOVERNANCE = "GOVERNANCE";
    /** 升级/退回类 */
    String APPR_CAT_RETURNED = "RETURNED";
    /** 我已处理 */
    String APPR_CAT_DONE = "DONE";

    // ==================== 待办状态 cmd_approval_task.status ====================

    String APPR_STATUS_DRAFT = "DRAFT";
    String APPR_STATUS_PENDING = "PENDING";
    String APPR_STATUS_APPROVED = "APPROVED";
    String APPR_STATUS_REJECTED = "REJECTED";
    String APPR_STATUS_RETURNED = "RETURNED";
    String APPR_STATUS_ESCALATED = "ESCALATED";
    String APPR_STATUS_CANCELLED = "CANCELLED";
    String APPR_STATUS_COMPLETED = "COMPLETED";

    // ==================== 审批动作 cmd_approval_action.action_type ====================

    String ACTION_SUBMIT = "SUBMIT";
    String ACTION_APPROVE = "APPROVE";
    String ACTION_REJECT = "REJECT";
    String ACTION_RETURN = "RETURN";
    String ACTION_ESCALATE = "ESCALATE";
    String ACTION_TRANSFER = "TRANSFER";
    String ACTION_CLAIM = "CLAIM";
    String ACTION_LINK = "LINK";
    String ACTION_CREATE_NEW = "CREATE_NEW";
    String ACTION_EXCLUDE = "EXCLUDE";
    String ACTION_MERGE = "MERGE";
    String ACTION_WITHDRAW = "WITHDRAW";
    String ACTION_COMMENT = "COMMENT";

    // ==================== 业务场景 cmd_flow_scene.scene_code ====================

    /** 客户新建 */
    String SCENE_CUSTOMER_CREATE = "CUSTOMER_CREATE";
    /** 客户变更 */
    String SCENE_CUSTOMER_CHANGE = "CUSTOMER_CHANGE";
    /** 客户逻辑停用 */
    String SCENE_DEACTIVATE = "DEACTIVATE";
    /** 层级关系新增 */
    String SCENE_HIER_ADD = "HIER_ADD";
    /** 批量导入 */
    String SCENE_IMPORT = "IMPORT";
    /** 客户合并 */
    String SCENE_MERGE = "MERGE";

    // ==================== 审批 Scope ====================

    /** BU 范围 */
    String SCOPE_BU = "BU";
    /** GC 全局 */
    String SCOPE_GC = "GC";
    /** 跨 BU */
    String SCOPE_CROSS_BU = "CROSS_BU";

    // ==================== 风险等级 ====================

    String RISK_HIGH = "High";
    String RISK_MEDIUM = "Medium";
    String RISK_LOW = "Low";

    // ==================== SLA 状态 ====================

    String SLA_NORMAL = "NORMAL";
    String SLA_DUE_SOON = "DUE_SOON";
    String SLA_OVERDUE = "OVERDUE";
}
