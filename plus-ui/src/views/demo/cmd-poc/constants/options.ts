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

/** ------------------------------ 状态映射 ------------------------------ */
interface StatusMeta {
  label: string;
  type: 'success' | 'warning' | 'danger' | 'info' | 'primary';
}

export const CUSTOMER_STATUS_MAP: Record<CustomerStatus, StatusMeta> = {
  active: { label: 'Active', type: 'success' },
  pending: { label: 'Pending', type: 'warning' },
  inactive: { label: 'Inactive', type: 'danger' },
  draft: { label: 'Draft', type: 'info' }
};

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
  Rejected: { label: 'Rejected', type: 'danger' },
  Inactive: { label: 'Inactive', type: 'danger' },
  Draft: { label: 'Draft', type: 'info' }
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
export const HIER_TYPE_OPTIONS = ['Legal Hierarchy', 'Sales Hierarchy', 'Payer Hierarchy'];
export const HIER_LEVEL_OPTIONS = ['全部层级', 'A3', 'A2', 'A1'];
export const HIER_BU_OPTIONS = ['High End', 'Mainstream', 'All Authorized BU'];
export const HIER_STATUS_OPTIONS = ['Active', 'Future', 'Expired'];
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
export const WORKFLOW_ROUTE_OPTIONS = ['Match Scope = Cross-BU', 'Match Scope = Single BU'];
export const WORKFLOW_SLA_OPTIONS = ['1 Business Day', '2 Business Days', '5 Business Days'];
export const WORKFLOW_TIMEOUT_OPTIONS = ['Notify + Escalate', 'Auto Approve', 'Auto Reject'];
export const WORKFLOW_NOTIFY_OPTIONS = ['Email Notification Only', 'Email + System Message'];
export const TRANSFORM_OPTIONS = ['Trim + Normalize', 'Upper Case', 'Address Standardization'];
export const ERROR_STRATEGY_OPTIONS = ['Reject Row', 'Warning Row', 'Skip Row'];
