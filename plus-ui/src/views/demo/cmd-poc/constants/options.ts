/**
 * 业务字典与下拉选项
 *
 * 说明：POC 阶段为前端常量；后续可替换为 useDict('cmd_bu') 等字典接口。
 */
import type { CustomerStatus } from '@/api/demo/cmdPoc/types';

/** ------------------------------ 业务上下文 ------------------------------ */
export const BU_OPTIONS = ['High End', 'Mainstream'];
export const PRODUCT_LINE_OPTIONS = ['Frame', 'Lens'];
export const SOURCE_SYSTEM_OPTIONS = ['Cloud', 'DMS+'];
export const CUSTOMER_TYPE_OPTIONS = ['Door', 'Payer', 'A1', 'A2', 'A3'];

/** ------------------------------ 元数据字段 ------------------------------ */
export const FIELD_TYPE_OPTIONS = ['Text', 'Number', 'Enum', 'Date', 'Reference'];
export const FIELD_SCOPE_OPTIONS = ['BU Specific', 'GC Core', 'Source System'];
export const FIELD_BU_OPTIONS = ['High End', 'Mainstream', 'All'];
export const FIELD_CUSTOMER_TYPE_OPTIONS = ['Door', 'All'];

/**
 * 枚举型元数据字段的可选值（按字段编码）
 * <p>
 * POC 阶段为前端常量：后端 md_value_set 只登记值集本身（set_code / set_name），
 * 不存明细值，故演示期由前端给出选项；接入值集明细接口后改为按 field.valueSetCode 拉取。
 */
export const FIELD_ENUM_OPTIONS: Record<string, string[]> = {
  country: ['中国', '中国香港', '中国澳门', '中国台湾'],
  customer_type: CUSTOMER_TYPE_OPTIONS,
  customer_level: ['A3', 'A2', 'A1'],
  bu_scope: BU_OPTIONS,
  product_line: PRODUCT_LINE_OPTIONS,
  source_system: SOURCE_SYSTEM_OPTIONS,
  status: ['draft', 'pending', 'active', 'inactive']
};

/** ------------------------------ 空值占位 ------------------------------ */

/**
 * 空值统一占位符（全应用唯一出口，改这一处即全局生效）
 * <p>
 * 备选风格，按需要换掉下面的取值即可：
 * <ul>
 *   <li><code>'—'</code> 长破折号：当前取值。与真实数据区分度最高，中文系统最常用</li>
 *   <li><code>'–'</code> 短破折号：更轻，密集表格里不抢视觉焦点</li>
 *   <li><code>'--'</code> 双连字符：数据仓库 / 导出 Excel 的通行写法，纯 ASCII 不会乱码</li>
 *   <li><code>'/'</code> 斜杠：极简，SAP 系报表常见</li>
 *   <li><code>'未填写'</code> / <code>'暂无'</code> / <code>'N/A'</code>：语义最明确，
 *       但一屏出现二三十次时会比破折号吵，适合字段少的详情页</li>
 * </ul>
 * 展示层由 DetailValue 组件统一着色（淡灰 + 降透明度），
 * 因此即便用破折号，也不会和真实数据抢。
 */
export const EMPTY_TEXT = '—';

/**
 * 空值判定
 * <p>
 * 只把「真没有值」的 4 种情况当空：undefined / null / 空串 / NaN。
 * 0 与 false 属于有效数据（如 DQ 0 分是结论，不是缺失），不能被吞成占位符。
 */
export const isEmptyValue = (value: unknown): boolean =>
  value === undefined || value === null || value === '' || (typeof value === 'number' && Number.isNaN(value));

/** ------------------------------ 状态映射 ------------------------------ */
interface StatusMeta {
  label: string;
  type: 'success' | 'warning' | 'danger' | 'info' | 'primary';
}

/**
 * 客户主档状态字典
 * <p>
 * 用 Record&lt;string&gt; 而非 Record&lt;CustomerStatus&gt;：数据库里除 active/pending/inactive/draft
 * 之外还存在审批流产生的 rejected（已拒绝）/ returned（已退回，待补充），
 * 若按 CustomerStatus 收窄，列表渲染到这两类行时会取到 undefined 而抛错。
 */
export const CUSTOMER_STATUS_MAP: Record<string, StatusMeta> = {
  active: { label: 'Active', type: 'success' },
  pending: { label: 'Pending', type: 'warning' },
  inactive: { label: 'Inactive', type: 'danger' },
  draft: { label: 'Draft', type: 'info' },
  rejected: { label: 'Rejected', type: 'danger' },
  returned: { label: 'Returned', type: 'warning' },
  // 总设计 MERGE 场景：源记录并入目标 One ID 后的终态（merged_to_one_id 指向保留的 Golden Record）
  merged: { label: 'Merged · 已合并', type: 'warning' }
};

/** 状态兜底：未知状态不抛错，退化为 info 标签展示原值 */
export const customerStatusMeta = (status?: string): StatusMeta =>
  CUSTOMER_STATUS_MAP[status ?? ''] ?? { label: status || '—', type: 'info' };

export const CUSTOMER_STATUS_OPTIONS = (Object.keys(CUSTOMER_STATUS_MAP) as CustomerStatus[]).map(value => ({
  value,
  label: CUSTOMER_STATUS_MAP[value].label
}));

export const IMPORT_STATUS_MAP: Record<string, StatusMeta> = {
  'Waiting for Review': { label: 'Waiting for Review', type: 'warning' },
  'In Progress': { label: 'In Progress', type: 'primary' },
  'Partial Success': { label: 'Partial Success', type: 'warning' },
  Completed: { label: 'Completed', type: 'success' },
  Failed: { label: 'Failed', type: 'danger' }
};

export const CHANGE_STATUS_MAP: Record<string, StatusMeta> = {
  'Under Review': { label: 'Under Review', type: 'warning' },
  Approved: { label: 'Approved', type: 'success' },
  Effective: { label: 'Effective', type: 'success' },
  Rejected: { label: 'Rejected', type: 'danger' },
  Returned: { label: 'Returned', type: 'warning' },
  Cancelled: { label: 'Cancelled', type: 'info' },
  Inactive: { label: 'Inactive', type: 'danger' },
  Draft: { label: 'Draft', type: 'info' }
};

/** 变更状态筛选项（页面页签与下拉共用，口径与后端状态一一对应） */
export const CHANGE_STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'Under Review', label: '待审批' },
  { value: 'Approved', label: '已批准待生效' },
  { value: 'Effective', label: '已生效' },
  { value: 'Rejected', label: '已拒绝' },
  { value: 'Returned', label: '退回补充' },
  { value: 'Cancelled', label: '已撤回' }
];

/** 关联关系影响检查结论 → 标签样式 */
export const CHANGE_RELATION_MAP: Record<string, StatusMeta> = {
  PASS: { label: 'PASS', type: 'success' },
  WARN: { label: 'WARN', type: 'warning' },
  FAIL: { label: 'FAIL', type: 'danger' }
};

/** 客户版本变更类型 → 标签样式 */
export const CHANGE_VERSION_TYPE_MAP: Record<string, StatusMeta> = {
  CREATE: { label: 'CREATE', type: 'success' },
  UPDATE: { label: 'UPDATE', type: 'primary' },
  DEACTIVATE: { label: 'DEACTIVATE', type: 'danger' },
  MERGE: { label: 'MERGE', type: 'warning' },
  RESTORE: { label: 'RESTORE', type: 'info' }
};

export const APPROVAL_NODE_STATUS_MAP: Record<string, StatusMeta> = {
  Done: { label: 'Done', type: 'success' },
  Current: { label: 'Current', type: 'primary' },
  Pending: { label: 'Pending', type: 'info' },
  Returned: { label: 'Returned', type: 'warning' },
  'Not Started': { label: 'Not Started', type: 'info' }
};

export const DQ_RESULT_MAP: Record<string, StatusMeta> = {
  Pass: { label: 'Pass', type: 'success' },
  Warning: { label: 'Warning', type: 'warning' },
  Failed: { label: 'Failed', type: 'danger' },
  Block: { label: 'Block', type: 'danger' }
};

export const INTEGRATION_STATUS_MAP: Record<string, StatusMeta> = {
  Success: { label: 'Success', type: 'success' },
  Failed: { label: 'Failed', type: 'danger' },
  Retrying: { label: 'Retrying', type: 'warning' }
};

export const COVERAGE_STATUS_MAP: Record<string, StatusMeta> = {
  已覆盖: { label: '已覆盖', type: 'success' },
  已增强: { label: '已增强', type: 'primary' },
  部分: { label: '部分', type: 'warning' }
};

export const FIELD_STATUS_MAP: Record<string, StatusMeta> = {
  Published: { label: 'Published', type: 'success' },
  Draft: { label: 'Draft', type: 'warning' }
};

export const AUDIT_RESULT_MAP: Record<string, StatusMeta> = {
  Success: { label: 'Success', type: 'success' },
  Tested: { label: 'Tested', type: 'primary' },
  Failed: { label: 'Failed', type: 'danger' }
};

/** ------------------------------ 表单选项 ------------------------------ */
export const CHANGE_FIELD_OPTIONS = ['经营地址', '统一社会信用代码'];
export const CHANGE_TYPE_OPTIONS = ['关键属性变更', '一般属性变更'];
export const DEACTIVATE_STATUS_OPTIONS = ['Inactive', 'Archived'];
export const DEACTIVATE_REASON_OPTIONS = ['24个月无交易 · 人工填报', '门店关闭', '重复记录'];
export const HIER_TYPE_OPTIONS = [
  { value: '全部类型', label: '全部类型' },
  { value: 'LEGAL', label: 'Legal · 法人 (A2)' },
  { value: 'COMMERCIAL', label: 'Commercial · 商业 (A3)' },
  { value: 'DOOR', label: 'Door · 门店 (A1)' }
];
export const HIER_LEVEL_OPTIONS = ['全部层级', 'A3', 'A2', 'A1'];
export const HIER_BU_OPTIONS = ['High End', 'Mainstream', 'All Authorized BU'];
export const HIER_STATUS_OPTIONS = ['全部状态', 'Active', 'Future', 'Expired'];
export const HIER_RELATION_OPTIONS = ['A3 Commercial Entity → A2 Main Account', 'A2 Main Account → A1 Door'];
export const HIER_VALIDATION_CASE_OPTIONS = [
  { value: 'pass', label: '通过示例：合法新增关系' },
  { value: 'same', label: '失败示例：父子节点相同' },
  { value: 'multiple', label: '失败示例：多父冲突' },
  { value: 'loop', label: '失败示例：完整路径循环' }
];
export const RULE_SET_OPTIONS = ['Door · High End · Frame', 'Door · Mainstream · Lens'];
export const RE_EVAL_VERSION_OPTIONS = ['v1.5 Draft'];
export const RE_EVAL_SCOPE_OPTIONS = ['High End · Active Customer', 'Mainstream · Active Customer', 'All Active Customer'];
export const RE_EVAL_MODE_OPTIONS = ['Simulation Only', 'Create Re-evaluation Job'];
export const RE_EVAL_EXCEPTION_OPTIONS = ['生成Exception Task', '仅记录不处理'];
export const AUDIT_RANGE_OPTIONS = ['最近7天', '最近30天', '最近90天'];
export const AUDIT_EVENT_OPTIONS = ['全部敏感操作', '仅变更类', '仅审批类'];
export const AUDIT_FORMAT_OPTIONS = ['Excel', 'CSV'];
export const AUDIT_MASKING_OPTIONS = ['按Auditor权限', '全量脱敏'];
export const INTEGRATION_PROTOCOL_OPTIONS = ['REST API', 'SFTP CSV', 'Email'];
export const INTEGRATION_PERIOD_OPTIONS = ['实时', '每 15 分钟', '每小时', '每日'];
export const WORKFLOW_TIMEOUT_OPTIONS = ['Notify + Escalate', 'Auto Approve', 'Auto Reject'];
export const WORKFLOW_NOTIFY_OPTIONS = ['Email Notification Only', 'Email + System Message'];

/* ------------------------------ 工作流配置（平台管理 › Workflow › 工作流定义 › 配置） ------------------------------ */

/**
 * 场景 SLA 可选时长（小时）：与 cmd_flow_scene.sla_hours 口径一致（列表按小时展示）
 */
export const WORKFLOW_SLA_HOURS_OPTIONS = [24, 48, 72, 120];

/**
 * 超时升级规则（cmd_flow_scene.escalate_rule）
 * <p>取值与种子数据一致：TO_GC / NOTIFY。</p>
 */
export const WORKFLOW_ESCALATE_OPTIONS = [
  { value: 'TO_GC', label: '升级至 GC Scope 决策' },
  { value: 'NOTIFY', label: '仅提醒，不升级' }
];

/** 会签 / 或签（cmd_flow_node_rule.multi_mode，对应 Warm-Flow node_ratio） */
export const WORKFLOW_MULTI_MODE_OPTIONS = [
  { value: 'ANY', label: '或签（任一办理人通过即可）' },
  { value: 'ALL', label: '会签（全部办理人通过）' },
  { value: 'SEQUENCE', label: '依次审批（按顺序流转）' }
];

/** 节点办理角色（cmd_flow_node_rule.assignee_value） */
export const WORKFLOW_ASSIGNEE_OPTIONS = [
  { value: 'BU_STEWARD', label: 'BU Steward（BU Scope）' },
  { value: 'GC_STEWARD', label: 'GC Steward（GC Scope）' },
  { value: 'PLATFORM_ADMIN', label: 'Platform Admin' }
];

/** 邮件通知对象（写入 cmd_flow_scene.ext_json.notifyTargets） */
export const WORKFLOW_NOTIFY_TARGET_OPTIONS = ['申请人', '当前节点办理人', 'BU Steward', 'GC Steward', 'Platform Admin'];

/** 可增删的节点（新增节点规则时的候选；节点编码与 Warm-Flow flow_node.node_code 对齐） */
export const WORKFLOW_NODE_OPTIONS = [
  { value: 'bu_review', label: 'BU Scope 初审（bu_review）' },
  { value: 'gc_review', label: 'GC Scope 决策（gc_review）' }
];

/**
 * 路由变量（只读说明）
 * <p>
 * 路由条件表达式里可引用的变量：取值来自业务上下文与 DQ / Match 结果，
 * 配置侧只决定「命中后走哪个节点」，不改变取值来源。
 * </p>
 */
export const WORKFLOW_ROUTE_VARIABLES = [
  { name: 'risk_level', desc: 'DQ Scorecard 得出的风险等级（High / Medium / Low）', source: 'DQ Engine' },
  { name: 'cross_bu', desc: '申请 BU 与命中候选是否为跨 BU', source: 'Match Engine' },
  { name: 'is_key_change', desc: '变更是否涉及关键属性（信用代码、经营地址等）', source: '业务上下文' },
  { name: 'relation_check', desc: '逻辑停用前的关联引用检查结果（PASS / FAIL）', source: '层级与引用校验' },
  { name: 'match_scope', desc: '匹配范围（Cross-BU / Single BU）', source: 'Match Engine' }
];
export const TRANSFORM_OPTIONS = ['Trim + Normalize', 'Upper Case', 'Address Standardization'];
export const ERROR_STRATEGY_OPTIONS = ['Reject Row', 'Warning Row', 'Skip Row'];
