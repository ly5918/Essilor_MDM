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
    /** 退回补充（申请人待补材料后重新提交） */
    String CUST_STATUS_RETURNED = "returned";

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

    // ==================== 变更与停用 cmd_change_request ====================
    // 对应页面：变更与停用 change（申请列表 / 详情 / 生效 / 版本历史）。
    // 关键约束：停用是「逻辑停用」，只改 status 与 effective_to，记录与历史版本全部保留；
    //          变更生效只递增 version_no，One ID 终身不变、绝不重新生成。

    /** 变更类型：属性变更 */
    String CHG_TYPE_UPDATE = "Update";
    /** 变更类型：逻辑停用 */
    String CHG_TYPE_DEACTIVATE = "Deactivate";

    /** 申请状态：草稿（未提交） */
    String CHG_STATUS_DRAFT = "DRAFT";
    /** 申请状态：待审批 */
    String CHG_STATUS_PENDING = "PENDING";
    /** 申请状态：审批通过（待生效） */
    String CHG_STATUS_APPROVED = "APPROVED";
    /** 申请状态：已驳回 */
    String CHG_STATUS_REJECTED = "REJECTED";
    /** 申请状态：退回补充 */
    String CHG_STATUS_RETURNED = "RETURNED";
    /** 申请状态：已生效 */
    String CHG_STATUS_EFFECTIVE = "EFFECTIVE";
    /** 申请状态：已撤回 */
    String CHG_STATUS_CANCELLED = "CANCELLED";

    /** 关联关系影响检查：通过 */
    String REL_CHECK_PASS = "PASS";
    /** 关联关系影响检查：警告（不阻塞，但需 GC 复核） */
    String REL_CHECK_WARN = "WARN";
    /** 关联关系影响检查：阻塞 */
    String REL_CHECK_FAIL = "FAIL";

    /** 字段差异变化类型：新增 */
    String DIFF_ADD = "ADD";
    /** 字段差异变化类型：修改 */
    String DIFF_MODIFY = "MODIFY";
    /** 字段差异变化类型：清空 */
    String DIFF_DELETE = "DELETE";
    /** 字段差异变化类型：未变 */
    String DIFF_SAME = "SAME";

    /** 业务类型：变更 / 停用（与 cmd_flow_scene.biz_type 对齐） */
    String BIZ_TYPE_CHANGE = "CHANGE";
    /** 业务类型：批量导入（审批任务联动批量导入确认流 IMPORT_BATCH） */
    String BIZ_TYPE_IMPORT = "IMPORT";
    /** 业务类型：跨 BU 客户合并（审批任务联动客户合并审批流 MERGE，总设计 MERGE 场景） */
    String BIZ_TYPE_MERGE = "MERGE";

    /** 客户主档状态：已合并（merged_to_one_id 指向保留的 Golden Record） */
    String CUST_STATUS_MERGED = "merged";

    // ==================== 统一待办分类 cmd_approval_task.task_category ====================

    /** 审批类 */
    String APPR_CAT_APPROVAL = "APPROVAL";
    /** 治理复核类 */
    String APPR_CAT_GOVERNANCE = "GOVERNANCE";
    /** 升级/退回类 */
    String APPR_CAT_RETURNED = "RETURNED";
    /** 我已处理 */
    String APPR_CAT_DONE = "DONE";
    /**
     * 全部待办（页面聚合口径，非建表分类）
     * <p>
     * 「全部待办」= 所有还需要当前 Steward 处理的任务（待处理 + 退回待补充），
     * 后端收到该值时按 status 聚合，不做 task_category 列匹配，
     * 保证 Steward 打开页面第一眼就能看到待办（测试报告 BUG-6）。
     */
    String APPR_CAT_ALL = "ALL";

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
    /** 批量导入确认流（cmd_flow_scene.scene_code，Warm-Flow 场景编码） */
    String SCENE_IMPORT_BATCH = "IMPORT_BATCH";
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

    // ==================== 客户层级 cmd_hierarchy_node ====================
    // 主数据（cmd_customer）与层级节点（cmd_hierarchy_node）的关系：
    //   1) 审批通过 → 客户成为 Golden Record（status=active），系统自动登记一个「待归位」层级节点；
    //   2) 待归位节点还没有父节点，不进 A3-A2-A1 树，只在「客户层级 → 待归位主数据」区展示；
    //   3) Data Steward 把它挂到某个 A3/A2 之下（归位）后，节点才进入层级树。
    // 这样既保证「批准即纳入层级体系」，又不破坏「A3-A2-A1 必须由 Steward 治理」的原则。

    /** 层级类型：Commercial Entity（A3 集团 / 法人实体） */
    String HIER_TYPE_COMMERCIAL = "COMMERCIAL";
    /** 层级类型：Main Account（A2 主账户） */
    String HIER_TYPE_LEGAL = "LEGAL";
    /** 层级类型：Door（A1 门店） */
    String HIER_TYPE_DOOR = "DOOR";
    /** 层级类型：待归位（已批准主数据，尚未挂到 A3-A2-A1 树上） */
    String HIER_TYPE_UNASSIGNED = "UNASSIGNED";

    /** 层级级别：A3（顶层集团） */
    String HIER_LEVEL_A3 = "A3";
    /** 层级级别：A2（主账户） */
    String HIER_LEVEL_A2 = "A2";
    /** 层级级别：A1（门店 / 终端） */
    String HIER_LEVEL_A1 = "A1";
    /** 层级级别：未定级（待归位节点占位值） */
    String HIER_LEVEL_NONE = "—";

    /** 层级节点状态：生效（已归位、在树上） */
    String HIER_NODE_ACTIVE = "active";
    /** 层级节点状态：待生效（未来某日起生效） */
    String HIER_NODE_FUTURE = "future";
    /** 层级节点状态：已失效 */
    String HIER_NODE_EXPIRED = "expired";
    /** 层级节点状态：待归位（已登记、未挂父节点） */
    String HIER_NODE_PENDING = "pending";

    /** 页面下拉「全部层级」占位文案（收到即按不过滤处理） */
    String HIER_FILTER_ALL_LEVEL = "全部层级";
    /** 页面下拉「全部类型」占位文案（收到即按不过滤处理） */
    String HIER_FILTER_ALL_TYPE = "全部类型";
    /** 页面下拉「All Authorized BU」占位文案（收到即按不过滤处理） */
    String HIER_FILTER_ALL_BU = "All Authorized BU";

    /** 待归位节点编码前缀（UN-xxxx，与树上 A1-/A2-/A3- 编码区分） */
    String HIER_NODE_CODE_UNASSIGNED_PREFIX = "UN-";

    /** 层级最大深度（A3=1 → A2=2 → A1=3），超出即视为非法关系 */
    int HIER_MAX_DEPTH = 3;

    /** 树上「加载更多子节点」每批加载条数（与页面 Lazy Load 提示保持一致） */
    int HIER_CHILD_PAGE_SIZE = 10;

    /** 层级关系类型：A3 → A2 */
    String HIER_REL_A3_A2 = "A3_A2";
    /** 层级关系类型：A2 → A1 */
    String HIER_REL_A2_A1 = "A2_A1";
    /** 层级关系类型：Main Account → Door */
    String HIER_REL_MAIN_DOOR = "MAIN_DOOR";
    /** 层级关系类型：Payer 挂钩 */
    String HIER_REL_PAYER_LINK = "PAYER_LINK";

    /** 层级关系状态：待审批 */
    String HIER_REL_STATUS_PENDING = "Pending";
    /** 层级关系状态：已生效 */
    String HIER_REL_STATUS_EFFECTIVE = "Effective";
    /** 层级关系状态：已失效（被新版本取代，历史保留） */
    String HIER_REL_STATUS_EXPIRED = "Expired";
    /** 层级关系状态：已拒绝 */
    String HIER_REL_STATUS_REJECTED = "Rejected";

    /** 关系历史操作类型：新建 */
    String HIER_HIST_OP_CREATE = "CREATE";
    /** 关系历史操作类型：编辑（父节点 / Payer / 有效期 / 关系类型变更） */
    String HIER_HIST_OP_UPDATE = "UPDATE";
    /** 关系历史操作类型：失效 */
    String HIER_HIST_OP_EXPIRE = "EXPIRE";

    /** 环路检测类型：父子相同（自引用） */
    String LOOP_CHECK_SELF_REF = "SELF_REF";
    /** 环路检测类型：父节点自身被挂到子节点下 */
    String LOOP_CHECK_SELF_MOUNT = "SELF_MOUNT";
    /** 环路检测类型：层级级别约束（A3→A2→A1 最多三级） */
    String LOOP_CHECK_LEVEL_RULE = "LEVEL_RULE";
    /** 环路检测类型：多父冲突 */
    String LOOP_CHECK_MULTI_PARENT = "MULTI_PARENT";
    /** 环路检测类型：完整路径循环 */
    String LOOP_CHECK_CYCLE = "CYCLE";
    /** 环路检测类型：跨 BU */
    String LOOP_CHECK_CROSS_BU = "CROSS_BU";

    /** 环路检测结果：通过 */
    String LOOP_CHECK_PASS = "PASS";
    /** 环路检测结果：阻塞 */
    String LOOP_CHECK_FAIL = "FAIL";
    /** 环路检测结果：警告（不阻塞提交，但需 GC 决策） */
    String LOOP_CHECK_WARN = "WARN";

    /**
     * 父节点级别 → 关系类型
     *
     * @param parentLevel A3 / A2
     * @return A3_A2 / A2_A1
     */
    static String hierRelationTypeOfParentLevel(String parentLevel) {
        return HIER_LEVEL_A3.equals(parentLevel) ? HIER_REL_A3_A2 : HIER_REL_A2_A1;
    }

    /**
     * 层级类型 → 关系类型（归位与增加子节点共用）
     *
     * @param parentLevel 父节点级别
     * @return 关系类型
     */
    static String hierRelationTypeOrDefault(String parentLevel, String relationType) {
        return relationType == null || relationType.isBlank()
            ? hierRelationTypeOfParentLevel(parentLevel) : relationType;
    }

    /**
     * 层级级别 → 节点类型（写入 cmd_hierarchy_node.hierarchy_type）
     *
     * @param level A1 / A2 / A3
     * @return COMMERCIAL / LEGAL / DOOR
     */
    static String hierTypeOfLevel(String level) {
        return switch (level == null ? "" : level) {
            case HIER_LEVEL_A3 -> HIER_TYPE_COMMERCIAL;
            case HIER_LEVEL_A2 -> HIER_TYPE_LEGAL;
            default -> HIER_TYPE_DOOR;
        };
    }

    /**
     * 层级深度 → 层级级别（A3=1 → A2=2 → A1=3）
     *
     * @param depth 层级深度
     * @return 层级级别
     */
    static String hierLevelOfDepth(int depth) {
        return switch (depth) {
            case 1 -> HIER_LEVEL_A3;
            case 2 -> HIER_LEVEL_A2;
            default -> HIER_LEVEL_A1;
        };
    }

    // ==================== 工作流步骤类型 cmd_workflow_step_log.step_type ====================

    /** 提交申请 */
    String STEP_SUBMIT = "SUBMIT";
    /** 系统自动（OCR / DQ / 查重 等） */
    String STEP_SYSTEM = "SYSTEM";
    /** 人工决策（批准 / 拒绝 / 退回 / 升级） */
    String STEP_BUSINESS = "BUSINESS";
    /** 引擎节点推进（Warm-Flow 节点流转） */
    String STEP_ENGINE = "ENGINE";

    /** 工作流节点中文名（用于步骤日志与泳道图展示） */
    static String nodeName(String code) {
        return switch (code == null ? "" : code) {
            case "APPLY" -> "创建客户申请";
            case "INPUT" -> "数据装配";
            case "OCR" -> "OCR 与智能补全";
            case "DQ" -> "技术与业务 DQ";
            case "DUP" -> "Duplicate Check";
            case "BU_REVIEW" -> "BU Scope 初审";
            case "GC_REVIEW" -> "GC Scope 决策";
            // 变更与停用链路（对应泳道图「关键客户属性变更」「客户逻辑停用」）
            case "CHANGE_APPLY" -> "发起属性变更";
            case "DEACT_APPLY" -> "发起停用申请";
            case "IMPACT_CHECK" -> "关联关系影响检查";
            case "EFFECT" -> "生成主档新版本";
            case "END" -> "结束";
            default -> code;
        };
    }
}
