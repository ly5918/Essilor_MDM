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

/** 后端分页结果（对应 RuoYi PageResult<T>） */
export interface PageResult<T> {
  rows: T[];
  total: number;
}

/**
 * 后端变更申请行（对应 CmdChangeRequestVo）
 */
export interface CmdChangeRequestRow {
  id?: number;
  requestCode?: string;
  oneId?: string;
  legalName?: string;
  changeType?: string;
  targetStatus?: string;
  isKeyChange?: string;
  buScope?: string;
  changeReason?: string;
  effectiveDate?: string;
  status?: string;
  relationCheck?: string;
  relationMsg?: string;
  effectiveTime?: string;
  remark?: string;
  createTime?: string;
}

/**
 * 后端层级节点行（对应 CmdHierarchyNodeVo）
 * 说明：后端返回带父指针的平铺列表，树结构在 api 层按 parentOneId 组装。
 */
export interface CmdHierarchyNodeRow {
  id?: number;
  nodeCode?: string;
  oneId?: string;
  legalName?: string;
  hierarchyType?: string;
  level?: string;
  parentOneId?: string;
  fullPath?: string;
  pathNames?: string;
  depth?: number;
  payerOneId?: string;
  buScope?: string;
  childrenCount?: number;
  descendants?: number;
  status?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  remark?: string;
}

/**
 * 后端工作台统计（对应 CmdDashboardVo）
 * 说明：全部由业务表实时聚合，页面不维护冗余统计。
 */
export interface CmdDashboardRow {
  customerTotal?: number;
  customerActive?: number;
  customerPending?: number;
  customerInactive?: number;
  myTodoCount?: number;
  myDoneCount?: number;
  returnedCount?: number;
  slaOverdueCount?: number;
  govSuspectCount?: number;
  govReviewCount?: number;
  govNewCount?: number;
  govCrossBuCount?: number;
  hierarchyNodeCount?: number;
}

/**
 * 后端客户行（对应 CmdCustomerVo，字段为后端驼峰命名）
 * 说明：后端用 buScope / createTime，前端展示用 bu / updatedAt，
 *      在 api 层做一次映射，面板代码无需感知后端字段差异。
 */
export interface CmdCustomerRow {
  id?: number;
  oneId?: string;
  legalName?: string;
  customerType?: string;
  customerLevel?: string;
  productLine?: string;
  buScope?: string;
  sourceSystem?: string;
  creditCode?: string;
  address?: string;
  payerId?: string;
  status?: string;
  dqScore?: number | string | null;
  versionNo?: number;
  createTime?: string;
  updateTime?: string;
}

/** DQ规则后端行（对应 dq_rule 表） */
export interface DqRuleRow {
  id?: number;
  ruleCode?: string;
  ruleName?: string;
  dimension?: string;
  role?: string;
  threshold?: string;
  result?: string;
  enabled?: boolean;
  version?: string;
  description?: string;
}

/** 匹配规则后端行（对应 match_rule 表） */
export interface MatchRuleRow {
  id?: number;
  ruleCode?: string;
  ruleName?: string;
  dimension?: string;
  role?: string;
  threshold?: string;
  result?: string;
  enabled?: boolean;
  version?: string;
  description?: string;
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
  | 'dqscore'
  | 'flowCenter';

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

/**
 * 后端导入任务行（对应 CmdImportJobVo）
 * job_status 取值：WAIT_REVIEW / RUNNING / PARTIAL_SUCCESS / FAILED / COMPLETED
 */
export interface CmdImportJobRow {
  id?: number;
  jobCode?: string;
  jobName?: string;
  scene?: string;
  buScope?: string;
  fileName?: string;
  totalCount?: number;
  exactCount?: number;
  suspectedCount?: number;
  newCount?: number;
  reviewCount?: number;
  invalidCount?: number;
  jobStatus?: string;
  progress?: number;
  submitBy?: string;
  submitTime?: string;
  remark?: string;
  createTime?: string;
}

/** 后端导入结果行（对应 CmdImportResultVo） */
export interface CmdImportResultRow {
  jobCode?: string;
  exact?: number;
  suspected?: number;
  created?: number;
  review?: number;
  invalid?: number;
  routes?: Array<{ result: string; handling: string; owner: string; detail?: string }>;
}

/** 后端导入模板行（对应 CmdImportTemplateVo） */
export interface CmdImportTemplateRow {
  id?: number;
  templateCode?: string;
  templateName?: string;
  scene?: string;
  buScope?: string;
  /** 客户类型（Door / Payer / A1 / A2 / A3），下载模板筛选项 */
  customerType?: string;
  /** 产品线（Lens / Frame），下载模板筛选项 */
  productLine?: string;
  /** 来源系统（DMS+ / Cloud），下载模板筛选项 */
  sourceSystem?: string;
  versionNo?: string;
  fieldCount?: number;
  status?: string;
  filePath?: string;
  remark?: string;
}

/** 后端模板字段映射行（对应 CmdImportTemplateMappingVo） */
export interface CmdTemplateMappingRow {
  id?: number;
  templateCode?: string;
  columnName?: string;
  fieldCode?: string;
  fieldName?: string;
  convertRule?: string;
  errorStrategy?: string;
  isRequired?: string;
  orderNum?: number;
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

/** 导入模板（下载模板弹窗使用，维度与原型 4 个下拉一致） */
export interface ImportTemplateVO {
  /** 模板编码：下载时用于定位模板 */
  templateCode: string;
  name: string;
  context: string;
  version: string;
  fieldCount: number;
  status: 'Published' | 'Draft';
  /** 客户类型：Door / Payer / A1 / A2 / A3 */
  customerType?: string;
  /** 归属 BU：High End / Mainstream */
  bu?: string;
  /** Product Line：Lens / Frame */
  productLine?: string;
  /** 来源系统：DMS+ / Cloud */
  sourceSystem?: string;
}

/** 上传导入文件的入参 */
export interface ImportUploadForm {
  /** 选中的文件 */
  file: File;
  /** 使用的模板编码 */
  templateCode: string;
  /** 错误策略 */
  errorStrategy?: string;
  /** 重复策略 */
  duplicateStrategy?: string;
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
  /** 父节点显示名（有值时优先展示） */
  parentName?: string;
  /** 完整路径 */
  path: string;
  /** 祖先 One ID 列表（用于展开并高亮树节点） */
  ancestorIds?: string[];
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
  /** 客户名称 */
  customerName: string;
  /** 所属 BU */
  bu?: string;
  /** 属性变更 / 逻辑停用 */
  changeType: 'Update' | 'Deactivate';
  /** 变更内容摘要 / 停用原因 */
  content: string;
  status: ChangeStatus;
  submittedAt: string;
  submittedBy?: string;
}

export interface ChangeRequestQuery extends PageQuery {
  keyword?: string;
  changeType?: 'Update' | 'Deactivate' | '';
  status?: ChangeStatus | '';
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
 * 8.1 治理与审批合并工作台（原型 2.2「治理与审批合并版」）
 * ------------------------------------------------------------------ */
/** 顶部 KPI 卡片 */
export interface ApprovalKpiVO {
  label: string;
  value: number | string;
  hint: string;
}

/** 统一任务清单行（审批 / 治理复核 / 升级退回 / 已处理 共用） */
export interface ApprovalTaskVO {
  /** 任务编号 */
  taskId: string;
  /** 客户名称或主题 */
  customerName: string;
  /** 任务类型：客户创建 / 层级关系 / DQ异常 / 疑似重复 / 批量治理 / 跨BU合并 / 合并审批 */
  taskType: string;
  /** 来源：单条申请 / 业务申请 / 规则触发 / 批量导入 / Import Job / BU升级 / 系统规则 / 月度Review */
  source: string;
  /** BU：High End / Mainstream / Cross-BU */
  bu: string;
  /** 数据质量结论：Pass / Warning / Block / 16 Review */
  dq: string;
  /** 匹配结论：Suspected / Multiple / — / New / Merged */
  match: string;
  /** SLA */
  sla: string;
  /** 风险等级 */
  risk: 'High' | 'Medium' | 'Low';
  /** 详情模板类型：create / hier / batch / gcdup / gchier */
  detailType: string;
}

/** 任务详情（右侧面板） */
export interface ApprovalTaskDetailVO {
  id: string;
  name: string;
  scene: string;
  submitter: string;
  currentNode: string;
  sla: string;
  /** DQ 自动检查结果 */
  dq: string;
  /** 重复检查 / 匹配结论 */
  duplicate: string;
  /** 治理证据 */
  evidence: string;
  /** 决策 / 判断标签（BU 初审判断 或 GC 治理决策） */
  decisions: string[];
  /** 操作按钮组 */
  actions: Array<{ key: string; label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }>;
}

/** ------------------------------------------------------------------
 * 8.2 流程跟踪（泳道图步骤条 + Warm-Flow 实例进度，参考 HCP Merge 流程跟踪视图）
 * ------------------------------------------------------------------ */
/** 步骤执行状态 */
export type FlowStepStatus = 'COMPLETED' | 'CURRENT' | 'PENDING' | 'TERMINATED';

/** 泳道图单步骤（场景模板 + 实时轨迹合并） */
export interface FlowTraceStepVO {
  order: number;
  /** 阶段序号（泳道图 1-7） */
  phase: number;
  phaseName: string;
  /** 泳道角色：Business User / 系统自动处理 / Data Steward BU·GC Scope / Platform Admin / Auditor */
  lane: string;
  nodeCode: string;
  nodeName: string;
  /** AUTO 系统自动 / MANUAL 人工 / GATEWAY 分支网关 */
  nodeType: 'AUTO' | 'MANUAL' | 'GATEWAY';
  status: FlowStepStatus;
  /** 审批人（人工节点，来自节点审批人规则） */
  assignee?: string;
  /** 实际操作人 / 时间 / 意见（来自轨迹） */
  operator?: string;
  actionTime?: string;
  opinion?: string;
  /** 路由说明（如 SUSPECT 进入 / Cross-BU 升级） */
  note?: string;
}

/** BPMN 风格流程图节点（引擎 flow_node + 实例状态） */
export interface FlowGraphNodeVO {
  nodeCode: string;
  nodeName: string;
  /** CIRCLE 开始/结束 · RECT 任务 · DIAMOND 网关 */
  shape: 'CIRCLE' | 'RECT' | 'DIAMOND';
  nodeType?: number;
  /** 泳道（角色，泳道图定义视图） */
  lane?: string;
  /** 阶段序号（1-7，泳道图定义视图） */
  phase?: number;
  /** 阶段名称（泳道图定义视图） */
  phaseName?: string;
  /** 节点业务说明（泳道图定义视图） */
  note?: string;
  x: number;
  y: number;
  status: 'COMPLETED' | 'CURRENT' | 'PENDING' | 'TERMINATED';
  approver?: string;
  actionTime?: string;
}

/** BPMN 风格连线（引擎 flow_skip） */
export interface FlowGraphEdgeVO {
  from: string;
  to: string;
  label: string;
  skipType: string;
  condition?: string;
  passed?: boolean;
}

export interface FlowGraphVO {
  definitionId?: number | string;
  flowCode?: string;
  /** 泳道顺序（自上而下，泳道图行标签） */
  lanes?: string[];
  nodes: FlowGraphNodeVO[];
  edges: FlowGraphEdgeVO[];
}

/** 流程中心：单个 CMD 业务场景（V6.1 总设计业务流） */
export interface FlowSceneVO {
  /** 场景编码（cmd_flow_scene.scene_code） */
  sceneCode: string;
  /** 场景名称 */
  sceneName: string;
  /** Warm-Flow 流程编码 */
  flowCode: string;
  /** Warm-Flow 流程名称 */
  flowName?: string;
  /** 场景整体 SLA（小时） */
  slaHours?: number;
  /** 是否已部署并发布到 Warm-Flow 引擎 */
  deployed: boolean;
  /** 已发布的流程定义 ID（未部署为 null） */
  definitionId?: number | string;
  /** 流程版本号 */
  version?: number;
  /** 流程节点数 */
  nodeCount?: number;
}

/**
 * 流程实例记录（流程中心「流程实例记录」列表，GET /cmd/flow/instances）
 *
 * 每一次执行过的工作流都留一条记录，可查看进度并用 Graph 回看当时的泳道图。
 */
export interface FlowInstanceVO {
  /** 待办任务主键 */
  id: number | string;
  /** 申请编号（Label 列） */
  taskNo: string;
  /** 业务标题（Description 列） */
  bizTitle?: string;
  /** 业务类型（中文，如「客户创建」） */
  bizType?: string;
  /** 场景编码（归一后） */
  sceneCode: string;
  /** 场景名称（Parent workflow 列） */
  sceneName?: string;
  /** Warm-Flow 流程名称 */
  flowName?: string;
  /** Warm-Flow 流程编码 */
  flowCode?: string;
  /** Warm-Flow 实例 ID（空表示尚未启动实例） */
  flowInstanceId?: number | string;
  /** 是否已启动 Warm-Flow 实例 */
  engineBound: boolean;
  /** 发起人（Creator 列） */
  applicantName?: string;
  /** 当前处理人 */
  assigneeName?: string;
  /** 当前处理角色 */
  assigneeRole?: string;
  /** 优先级（取风险等级，Priority 列） */
  priority?: string;
  /** 任务状态 */
  status: string;
  /** 当前节点名称 */
  currentNodeName?: string;
  /** 已完成步骤数（泳道图口径） */
  completedSteps?: number;
  /** 总步骤数（泳道图口径） */
  totalSteps?: number;
  /** 进度百分比 */
  progressPercent?: number;
  /** SLA 状态 */
  slaState?: string;
  /** 提交时间 */
  submitTime?: string;
  /** 完成时间 */
  finishTime?: string;
  /** 处理时长（小时） */
  durationHours?: number;
  /** 创建时间（Creation date 列） */
  createTime?: string;
}

/** 流程实例记录查询条件 */
export interface FlowInstanceQuery {
  keyword?: string;
  status?: string;
  bizType?: string;
  /** RUNNING 进行中 / DONE 已完成 / NEW 未启动 */
  runState?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 流程跟踪视图（GET /cmd/flow/trace/{taskNo}） */
export interface FlowTraceVO {
  /** 是否已接入 Warm-Flow 引擎（flow_instance_id 非空） */
  engineBound?: boolean;
  /** BPMN 风格流程图（引擎节点/连线 + 进度高亮） */
  graph?: FlowGraphVO;
  taskNo: string;
  bizTitle: string;
  bizType: string;
  sceneCode: string;
  sceneName: string;
  status: string;
  currentNodeName: string;
  assigneeName?: string;
  assigneeRole?: string;
  buScope?: string;
  riskLevel?: string;
  slaState?: string;
  submitTime?: string;
  slaDue?: string;
  /** Warm-Flow 流程编码 / 名称（cmd_flow_scene 映射） */
  flowCode?: string;
  flowName?: string;
  slaHours?: number;
  /** Warm-Flow 引擎关联（镜像字段，不直查 flow_* 表） */
  flowInstanceId?: number | string;
  flowTaskId?: number | string;
  flowDefinitionId?: number | string;
  flowStatus?: string;
  totalSteps: number;
  completedSteps: number;
  progressPercent: number;
  /** 泳道图旁路节点（规则与参数配置，不打断主流程） */
  bypass?: { lane: string; nodeName: string; note: string };
  steps: FlowTraceStepVO[];
  /** Data context state 变量（value 为空渲染 not defined） */
  contextVars: Array<{ name: string; value?: string }>;
  actions: Array<{
    actionType: string;
    actionName?: string;
    operatorName?: string;
    operatorRole?: string;
    actionTime?: string;
    opinion?: string;
  }>;
}

/**
 * 后端元数据字段行（对应 md_field）
 */
export interface CmdMdFieldRow {
  id?: number;
  modelCode?: string;
  fieldCode?: string;
  fieldName?: string;
  dataType?: string;
  valueSetCode?: string;
  isRequired?: string;
  scopeType?: string;
  ownerBu?: string;
  versionNo?: string;
  status?: string;
  orderNum?: number;
  remark?: string;
}

/** 后端值集行（对应 md_value_set） */
export interface CmdValueSetRow {
  id?: number;
  setCode?: string;
  setName?: string;
  setType?: string;
  status?: string;
  remark?: string;
}

/** 后端模型版本行（对应 PlatformVersionVo） */
export interface CmdVersionRow {
  version?: string;
  ruleCount?: number;
  status?: string;
  publishedAt?: string;
}

/** 后端角色行（对应 cmd_role） */
export interface CmdRoleRow {
  id?: number;
  roleCode?: string;
  roleName?: string;
  roleType?: string;
  scopeType?: string;
  defaultBu?: string;
  description?: string;
  status?: string;
  orderNum?: number;
}

/** 后端 One ID 规则行（对应 oneid_rule） */
export interface CmdOneIdRuleRow {
  ruleCode?: string;
  ruleName?: string;
  pattern?: string;
  prefix?: string;
  serialLength?: number;
  genStrategy?: string;
  scopeType?: string;
  status?: string;
}

/** 后端 Legacy 映射行（对应 cmd_legacy_mapping） */
export interface CmdLegacyMappingRow {
  oneId?: string;
  sourceSystem?: string;
  sourceCode?: string;
  sourceName?: string;
  buScope?: string;
  status?: string;
}

/** 后端权限矩阵行（对应 PermissionMatrixVo） */
export interface CmdPermissionMatrixRow {
  capability?: string;
  business?: string;
  steward?: string;
  admin?: string;
  auditor?: string;
}

/** 后端 One ID 策略行（对应 OneIdPolicyVo） */
export interface CmdOneIdPolicyRow {
  event?: string;
  handling?: string;
}

/**
 * 后端集成运行行（对应 IntRunVo，runStatus：SUCCESS / FAILED / RETRYING / RUNNING）
 */
export interface CmdIntegrationRunRow {
  id?: number;
  runCode?: string;
  endpointCode?: string;
  endpointName?: string;
  direction?: string;
  targetSystem?: string;
  runStatus?: string;
  totalCount?: number;
  successCount?: number;
  failedCount?: number;
  attemptCount?: number;
  maxAttempt?: number;
  errorMessage?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * 后端审计事件行（对应 AuditEventVo，result：SUCCESS / TEST / FAILED）
 */
export interface CmdAuditEventRow {
  id?: number;
  eventId?: string;
  eventType?: string;
  eventName?: string;
  bizType?: string;
  bizId?: string;
  oneId?: string;
  operatorName?: string;
  operatorRole?: string;
  eventTime?: string;
  result?: string;
  riskLevel?: string;
  remark?: string;
}

/**
 * 后端统一待办行（对应 CmdApprovalTaskVo）
 * taskCategory：APPROVAL / GOVERNANCE / RETURNED / DONE
 * slaState：NORMAL / DUE_SOON / OVERDUE
 */
export interface CmdApprovalTaskRow {
  id?: number;
  taskNo?: string;
  taskCategory?: string;
  bizType?: string;
  bizId?: string;
  bizTitle?: string;
  oneId?: string;
  sceneCode?: string;
  applicantName?: string;
  buScope?: string;
  scope?: string;
  currentNodeName?: string;
  assigneeName?: string;
  status?: string;
  riskLevel?: string;
  dqScore?: number | string;
  duplicateState?: string;
  crossBuFlag?: string;
  slaState?: string;
  createTime?: string;
}

/** 后端审批 KPI 行（对应 CmdApprovalKpiVo） */
export interface CmdApprovalKpiRow {
  label?: string;
  value?: number;
  hint?: string;
}

/** 后端审批详情（对应 CmdApprovalDetailVo） */
export interface CmdApprovalDetailRow {
  id?: number;
  taskId?: string;
  name?: string;
  scene?: string;
  submitter?: string;
  currentNode?: string;
  sla?: string;
  dq?: string;
  duplicate?: string;
  evidence?: string;
  decisions?: string[];
  actions?: Array<{ key?: string; label?: string; type?: string }>;
}

/** 后端流程跟踪（对应 CmdFlowTraceVo，字段均为可选） */
export type CmdFlowTraceRow = Partial<FlowTraceVO> & {
  steps?: Array<Partial<FlowTraceStepVO>>;
  contextVars?: Array<{ name?: string; value?: string }>;
  actions?: Array<{
    actionType?: string;
    actionName?: string;
    operatorName?: string;
    operatorRole?: string;
    actionTime?: string;
    opinion?: string;
  }>;
};

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
  /** 字段编码（写入客户表单用） */
  code?: string;
  field: string;
  value: string;
  confidence: string;
}

/** 营业执照原件信息（原型「营业执照预览」右侧 4 行） */
export interface OcrLicenseVO {
  /** 统一社会信用代码 */
  creditCode: string;
  /** 名称 */
  name: string;
  /** 类型 */
  type: string;
  /** 住所 */
  address: string;
}

/** OCR 一次识别的完整结果：执照信息 + 字段识别值 */
export interface OcrRecognizeVO {
  license: OcrLicenseVO;
  fields: OcrResultVO[];
}
