/**
 * Customer Master Data (CMD) POC 数据契约
 *
 * 说明：
 * - 本文件仅描述「前端 ⇄ 后端」的数据形状，不包含任何实现。
 * - 后端 Controller 就绪后，只需保证返回结构与此一致即可直接切换 API 开关。
 * - 命名遵循 RuoYi-Vue-Plus 代码生成规范：列表查询 XxxQuery、表单 XxxForm、返回 XxxVO。
 */

/** 分页查询基类 */
export interface PageQuery {
  pageNum?: number;
  pageSize?: number;
}

/** 角色编码（对应原型顶部「模拟角色」下拉的 5 类技术角色） */
export type RoleKey = 'business' | 'bu' | 'gc' | 'admin' | 'audit';

/** 页面编码（对应原型左侧菜单 id） */
export type PageId =
  | 'dash'
  | 'customers'
  | 'batch'
  | 'gov'
  | 'hier'
  | 'change'
  | 'approval'
  | 'admin'
  | 'integration'
  | 'audit'
  | 'coverage'
  | 'oneid'
  | 'dqscore';

/** 客户状态 */
export type CustomerStatus = 'active' | 'pending' | 'inactive' | 'draft';

/** ------------------------------------------------------------------
 * 1. 客户主数据
 * ------------------------------------------------------------------ */
export interface CustomerVO {
  /** One ID，全局唯一且终身稳定 */
  oneId: string;
  /** 工商名称 */
  legalName: string;
  /** 客户类型：Door / Payer / A1 / A2 / A3 */
  customerType: string;
  /** 所属 BU，跨 BU 时用 / 分隔 */
  bu: string;
  /** Product Line */
  productLine: string;
  /** 来源系统 */
  sourceSystem: string;
  /** 统一社会信用代码 */
  creditCode: string;
  /** 经营地址 */
  address: string;
  /** Payer 编码 */
  payerId?: string;
  /** 状态 */
  status: CustomerStatus;
  /** 数据质量总分 */
  dqScore: number;
  /** 当前数据版本 */
  versionNo: number;
  /** 最近更新时间 */
  updatedAt: string;
  /** 最近更新人 */
  updatedBy?: string;
}

export interface CustomerQuery extends PageQuery {
  /** 名称 / One ID / 信用代码 模糊匹配 */
  keyword?: string;
  bu?: string;
  status?: CustomerStatus;
  customerType?: string;
}

export interface CustomerForm {
  oneId?: string;
  legalName: string;
  creditCode: string;
  address: string;
  customerType: string;
  bu: string;
  productLine: string;
  sourceSystem: string;
  payerId?: string;
  /** 动态元数据字段值：key 为字段编码 */
  dynamicValues?: Record<string, string>;
}

/** ------------------------------------------------------------------
 * 2. 元数据字段（Master Data Extension）
 * ------------------------------------------------------------------ */
export type FieldScope = 'GC Core' | 'BU Specific' | 'Source System';
export type FieldType = 'Text' | 'Number' | 'Enum' | 'Date' | 'Reference';

export interface MetadataFieldVO {
  /** 字段编码 */
  code: string;
  /** 显示名称 */
  label: string;
  /** 数据层级 */
  scope: FieldScope;
  /** 字段类型 */
  type: FieldType;
  /** 是否必填 */
  required: boolean;
  /** 适用 BU */
  bu: string;
  /** 适用 Customer Type */
  customerType: string;
  /** 默认值 */
  defaultValue?: string;
  /** 发布状态：Draft / Published */
  status: 'Draft' | 'Published';
}

export interface MetadataFieldForm extends Omit<MetadataFieldVO, 'status'> {
  status?: MetadataFieldVO['status'];
}

/** 模型版本 */
export interface ModelVersionVO {
  version: string;
  ruleCount: number;
  status: 'Current' | 'Draft';
  publishedAt?: string;
}

/** ------------------------------------------------------------------
 * 3. 数据质量
 * ------------------------------------------------------------------ */
export type DqRuleLevel = 'Blocking' | 'Warning';
export type DqRuleType = 'Technical' | 'Business';
export type DqResult = 'Pass' | 'Warning' | 'Failed' | 'Block';

/** DQ 规则 */
export interface DqRuleVO {
  code: string;
  name: string;
  type: DqRuleType;
  level: DqRuleLevel;
  /** 命中后的系统动作 */
  action: string;
  enabled: boolean;
}

/** 规则模拟测试结果 */
export interface DqSimulateResultVO {
  rule: string;
  result: DqResult;
  message?: string;
}

/** 分数卡维度 */
export interface DqDimensionVO {
  dimension: string;
  weight: string;
  score: number;
}

/** 客户质量分数卡 */
export interface DqScorecardVO {
  oneId: string;
  legalName: string;
  /** 综合得分 */
  overall: number;
  dimensions: DqDimensionVO[];
  /** 规则版本影响 */
  versions: ModelVersionVO[];
  /** 质量异常清单 */
  exceptions: DqRuleVO[];
}

/** 历史重评估影响预估 */
export interface ReEvaluateImpactVO {
  label: string;
  value: string;
}

/** 历史重评估表单 */
export interface ReEvaluateForm {
  targetVersion: string;
  dataScope: string;
  execMode: 'Simulation Only' | 'Create Re-evaluation Job';
  exceptionHandling: string;
}

/** ------------------------------------------------------------------
 * 4. 匹配规则 / 重复治理
 * ------------------------------------------------------------------ */
export interface MatchRuleVO {
  /** 匹配维度 */
  dimension: string;
  /** 作用：主依据 / 辅助线索 */
  role: string;
  /** 结果：Exact / 88% / Similar */
  result: string;
  /** 权重或阈值 */
  threshold?: string;
  enabled: boolean;
}

/** 疑似重复候选对比 */
export interface DuplicateCandidateVO {
  /** 匹配度 */
  score: number;
  /** 结论：Suspected Match / Exact Match / New */
  verdict: string;
  /** 结论依据描述 */
  reason: string;
  /** 新申请（左侧） */
  incoming: Record<string, string>;
  /** 现有主档（右侧） */
  existing: Record<string, string>;
}

/** ------------------------------------------------------------------
 * 5. 批量导入
 * ------------------------------------------------------------------ */
export type ImportJobStatus = 'Waiting for Review' | 'In Progress' | 'Partial Success' | 'Failed' | 'Completed';

export interface ImportJobVO {
  jobId: string;
  fileName: string;
  totalRows: number;
  status: ImportJobStatus;
  submittedAt: string;
  submittedBy: string;
}

/** 批量导入结果分流 */
export interface BatchResultVO {
  jobId: string;
  exact: number;
  suspected: number;
  created: number;
  review: number;
  invalid: number;
  /** 分流处理策略 */
  routes: Array<{ result: string; handling: string; owner: string; detail?: string }>;
}

/** 导入模板 */
export interface ImportTemplateVO {
  name: string;
  context: string;
  version: string;
  fieldCount: number;
  status: 'Published' | 'Draft';
}

/** 模板字段映射 */
export interface TemplateMappingVO {
  sourceColumn: string;
  targetField: string;
  transform: string;
  errorStrategy: string;
}

/** ------------------------------------------------------------------
 * 6. 客户层级
 * ------------------------------------------------------------------ */
export interface HierarchyNodeVO {
  id: string;
  /** A1 / A2 / A3 */
  level: string;
  /** Commercial Entity / Main Account / Door */
  type: string;
  label: string;
  name: string;
  oneId: string;
  payerId: string;
  payerName?: string;
  /** 直接子节点数量 */
  childrenCount: number;
  /** 全部后代数量 */
  descendants: number;
  /** 父节点名称 */
  parent: string;
  /** 完整路径 */
  path: string;
  /** 有效期 */
  validity: string;
  /** 状态 */
  status: 'Active' | 'Future' | 'Expired';
  children?: HierarchyNodeVO[];
}

export interface HierarchyRelationForm {
  hierarchyType: string;
  relationType: string;
  parentId: string;
  childId: string;
  payerOneId: string;
  effectiveDate: string;
  reason: string;
  /** 校验示例：pass / same / multiple / loop */
  validationCase: string;
}

/** ------------------------------------------------------------------
 * 7. 变更 / 逻辑停用
 * ------------------------------------------------------------------ */
export type ChangeStatus = 'Under Review' | 'Approved' | 'Rejected' | 'Inactive' | 'Draft';

export interface ChangeRequestVO {
  requestId: string;
  oneId: string;
  /** 属性变更 / 逻辑停用 */
  changeType: 'Update' | 'Deactivate';
  /** 变更内容摘要 */
  content: string;
  status: ChangeStatus;
  submittedAt: string;
  submittedBy?: string;
}

/** 变更申请表单 */
export interface ChangeRequestForm {
  oneId: string;
  changeType: string;
  field: string;
  newValue: string;
  reason: string;
}

/** 停用申请表单 */
export interface DeactivateForm {
  oneId: string;
  targetStatus: 'Inactive' | 'Archived';
  reason: string;
  effectiveDate: string;
  remark: string;
}

/** Before / After 差异行 */
export interface ChangeDiffVO {
  field: string;
  before: string;
  after: string;
}

/** 审批轨迹行 */
export interface ApprovalTrailVO {
  time: string;
  role: string;
  action: string;
  result: string;
}

/** 逻辑停用数据库结果 */
export interface DeactivateResultVO {
  businessView: Array<{ key: string; value: string }>;
  /** 后台记录示意（SQL / 字段落库） */
  dbRecords: string[];
}

/** ------------------------------------------------------------------
 * 8. 审批
 * ------------------------------------------------------------------ */
export interface ApprovalNodeVO {
  node: number;
  role: string;
  content: string;
  status: 'Done' | 'Current' | 'Pending' | 'Returned' | 'Not Started';
}

export interface ApprovalFlowVO {
  key: string;
  title: string;
  /** 流程节点串 */
  steps: string[];
  remark: string;
  nodes: ApprovalNodeVO[];
}

export interface ApprovalInstanceVO {
  instanceId: string;
  bu: string;
  scenario: string;
  currentNode: string;
  sla: string;
  status: string;
}

/** ------------------------------------------------------------------
 * 9. One ID
 * ------------------------------------------------------------------ */
export interface OneIdRuleVO {
  ruleName: string;
  status: 'Published' | 'Draft';
  object: string;
  serialLength: string;
  prefix: string;
  separator: string;
}

/** One ID 生成与状态策略 */
export interface OneIdPolicyVO {
  event: string;
  handling: string;
}

/** One ID 生命周期事件 */
export interface OneIdEventVO {
  date: string;
  stage: string;
  description: string;
}

/** Legacy Code ↔ One ID 交叉引用 */
export interface LegacyMappingVO {
  oneId: string;
  sourceSystem: string;
  legacyCode: string;
  bu: string;
  status: CustomerStatus;
}

/** ------------------------------------------------------------------
 * 10. 集成监控
 * ------------------------------------------------------------------ */
export interface IntegrationRunVO {
  runId: string;
  direction: 'Inbound' | 'Outbound';
  system: string;
  status: 'Success' | 'Failed' | 'Retrying';
  /** 错误明细（HTTP 状态等） */
  detail?: string;
  attempt?: string;
  record?: string;
}

export interface IntegrationConnForm {
  system: string;
  protocol: string;
  period: string;
  url: string;
}

/** ------------------------------------------------------------------
 * 11. 审计 / 权限 / 覆盖检查
 * ------------------------------------------------------------------ */
export interface AuditEventVO {
  id: string;
  time: string;
  event: string;
  role: string;
  result: 'Success' | 'Tested' | 'Failed';
}

export interface AuditExportForm {
  range: string;
  eventType: string;
  format: 'Excel' | 'CSV';
  masking: string;
}

/** 权限矩阵行 */
export interface PermissionMatrixVO {
  capability: string;
  business: string;
  steward: string;
  admin: string;
  auditor: string;
}

/** 角色权限配置行 */
export interface RolePermissionVO {
  role: string;
  scope: string;
  points: string;
  enabled: boolean;
}

/** POC 覆盖检查项 */
export interface CoverageItemVO {
  topic: string;
  status: '已覆盖' | '已增强' | '部分';
  evidence: string;
}

/** 工作流配置 */
export interface WorkflowConfigVO {
  flowName: string;
  steps: string[];
  routeCondition: string;
  sla: string;
  timeoutAction: string;
  notification: string;
}

/** ------------------------------------------------------------------
 * 12. 工作台
 * ------------------------------------------------------------------ */
export interface DashboardStatVO {
  key: string;
  label: string;
  value: number | string;
  hint?: string;
}

export interface TodoVO {
  count: number;
  label: string;
  hint: string;
  tag: string;
}

export interface NotificationVO {
  id: string;
  title: string;
  time: string;
  type: string;
  read?: boolean;
}

/** ------------------------------------------------------------------
 * 13. OCR
 * ------------------------------------------------------------------ */
export interface OcrResultVO {
  field: string;
  value: string;
  confidence: string;
}
