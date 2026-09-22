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
 * 后端待归位主数据行（对应 CmdHierarchyUnassignedVo）
 * 说明：口径是「已审批通过成为主数据（active），但还没挂到 A3-A2-A1 树上」。
 * 批准后主数据会自动出现在这里，Data Steward 归位后即进入层级树。
 */
export interface CmdHierarchyUnassignedRow {
  oneId?: string;
  legalName?: string;
  buScope?: string;
  customerStatus?: string;
  sourceSystem?: string;
  dqScore?: number;
  approvedTime?: string;
  /** 是否已在层级节点表登记（true=已登记待归位，false=尚未登记） */
  registered?: boolean;
  nodeCode?: string;
  /** 建议层级级别（POC 默认 A1） */
  suggestedLevel?: string;
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
 *      本行与 cmd_customer 表业务列一一对应（除 delFlag），客户详情弹窗按此完整展示。
 */
export interface CmdCustomerRow {
  id?: number;
  oneId?: string;
  legalName?: string;
  legalNameEn?: string;
  shortName?: string;
  customerType?: string;
  customerLevel?: string;
  productLine?: string;
  buScope?: string;
  gcScopeFlag?: string;
  sourceSystem?: string;
  sourceId?: string;
  creditCode?: string;
  taxNo?: string;
  country?: string;
  province?: string;
  city?: string;
  address?: string;
  postalCode?: string;
  payerId?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  status?: string;
  dqScore?: number | string | null;
  dqGrade?: string;
  matchState?: string;
  duplicateFlag?: string;
  mergedToOneId?: string;
  versionNo?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  approvedBy?: number | null;
  approvedTime?: string | null;
  flowInstanceId?: number | null;
  flowStatus?: string | null;
  remark?: string | null;
  extJson?: string | null;
  createTime?: string;
  updateTime?: string;
}

/** DQ规则后端行（对应 dq_rule 表） */
/** DQ 规则后端行（对应 dq_rule 表，规则模拟测试弹窗直接消费） */
export interface DqRuleRow {
  id?: number;
  ruleCode?: string;
  ruleName?: string;
  /** 规则类型 TECHNICAL / BUSINESS */
  ruleType?: string;
  /** 质量维度 COMPLETENESS / VALIDITY / CONSISTENCY / UNIQUENESS / TIMELINESS */
  dimension?: string;
  modelCode?: string;
  /** 主校验字段（field_code） */
  fieldCode?: string;
  /** 校验类型 NOT_NULL / REGEX / LENGTH / RANGE / UNIQUE / CROSS_FIELD */
  checkType?: string;
  /** 校验表达式 */
  expression?: string;
  /** 严重级别 ERROR（阻断）/ WARNING / INFO */
  severity?: string;
  scoreWeight?: number | string;
  errorMessage?: string;
  versionNo?: string;
  /** 状态 0草稿 1已发布 2已停用 */
  status?: string;
  remark?: string;
}

/** 匹配规则后端行（对应 match_rule 表） */
export interface MatchRuleRow {
  id?: number;
  ruleCode?: string;
  ruleName?: string;
  modelCode?: string;
  /** 应用场景 CREATE / IMPORT / BATCH / MERGE */
  scene?: string;
  /** 算法 WEIGHTED / EXACT / FUZZY / ML */
  algorithm?: string;
  normalizeRule?: string;
  /** Exact Match 阈值 */
  exactThreshold?: number | string;
  /** Suspected 阈值 */
  suspectThreshold?: number | string;
  /** 超阈值自动合并 Y/N */
  autoMergeFlag?: string;
  /** 参与跨 BU 匹配 Y/N */
  crossBuFlag?: string;
  versionNo?: string;
  /** 状态 0草稿 1已发布 2已停用 */
  status?: string;
  remark?: string;
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
  /** Platform Admin › 平台管理 › Workflow「管理」进入的工作流定义页（无侧边栏菜单项） */
  | 'flowDefinition'
  /** 工作流 › 已激活的工作流：待人工处理的运行中实例（列表页） */
  | 'flowWorkitem'
  /** 工作流 › 已完成的工作流：已结束实例列表（列表页），行内「查看流程跟踪」弹窗看详情 */
  | 'flowDone';

/**
 * 菜单标识：
 * - 叶子菜单＝可路由页面（PageId）；
 * - 二级菜单容器（如「工作流」）＝`sub-` 前缀伪 id，仅用于 el-sub-menu 展开态，**不可路由**。
 */
export type MenuId = PageId | `sub-${string}`;

/** 客户状态 */
/**
 * 客户状态
 * <p>
 * 前四类为业务状态；rejected / returned 由审批流写入
 * （rejected=审批拒绝，returned=退回待补充），列表与详情必须能稳定渲染。
 */
export type CustomerStatus = 'active' | 'pending' | 'inactive' | 'draft' | 'rejected' | 'returned';

/** ------------------------------------------------------------------
 * 1. 客户主数据
 * ------------------------------------------------------------------ */
/**
 * 客户主数据（前端展示对象）
 * <p>
 * 字段一是「列表列」需要的少量主干字段，二是「客户详情弹窗」需要完整展示的
 * cmd_customer 全部业务列（基本信息 / 联络与地址 / 治理与质量 / 生效与流程）。
 * 后端字段名差异（buScope→bu、sourceSystem、updateTime→updatedAt）在 api 层统一转换。
 */
export interface CustomerVO {
  /** One ID，全局唯一且终身稳定 */
  oneId: string;
  /** 工商名称 */
  legalName: string;
  /** 客户英文名称 */
  legalNameEn?: string;
  /** 客户简称 */
  shortName?: string;
  /** 客户类型：Door / Payer / A1 / A2 / A3 */
  customerType: string;
  /** 客户层级（A1 / A2 / A3），来自 cmd_customer.customer_level */
  customerLevel?: string;
  /** 所属 BU，跨 BU 时用 / 分隔 */
  bu: string;
  /** 是否跨 BU 全局可见（Y / N） */
  gcScopeFlag?: string;
  /** Product Line */
  productLine: string;
  /** 来源系统 */
  sourceSystem: string;
  /** 来源系统主键（外部系统原始 ID，追溯用） */
  sourceId?: string;
  /** 统一社会信用代码 */
  creditCode: string;
  /** 税号 */
  taxNo?: string;
  /** 国家/地区 */
  country?: string;
  /** 省份 */
  province?: string;
  /** 城市 */
  city?: string;
  /** 经营地址 */
  address: string;
  /** 邮编 */
  postalCode?: string;
  /** Payer 编码 */
  payerId?: string;
  /** 联系人 */
  contactName?: string;
  /** 联系电话 */
  contactPhone?: string;
  /** 邮箱 */
  contactEmail?: string;
  /** 状态 */
  status: CustomerStatus;
  /** 数据质量总分 */
  dqScore: number;
  /** 质量等级（A / B / C / D） */
  dqGrade?: string;
  /** 匹配状态（EXACT / SUSPECTED / NEW / REVIEW / INVALID） */
  matchState?: string;
  /** 疑似重复标记（Y / N） */
  duplicateFlag?: string;
  /** 合并指向的 One ID（被合并后指向主记录） */
  mergedToOneId?: string;
  /** 当前数据版本 */
  versionNo: number;
  /** 生效时间 */
  effectiveFrom?: string;
  /** 失效时间（逻辑停用写入） */
  effectiveTo?: string;
  /** 审批人用户 ID */
  approvedBy?: number;
  /** 审批时间 */
  approvedTime?: string;
  /** 关联流程实例 ID */
  flowInstanceId?: number;
  /** 工作流实例状态（Warm-Flow 镜像） */
  flowStatus?: string;
  /** 备注 */
  remark?: string;
  /** 扩展属性（未建模的动态字段，JSON 字符串；详情页按键值对展示） */
  extJson?: string | null;
  /** 创建时间 */
  createdAt?: string;
  /** 最近更新时间 */
  updatedAt: string;
  /** 最近更新人 */
  updatedBy?: string;
}

export interface CustomerQuery extends PageQuery {
  /** 名称 / 英文名 / 简称 / One ID / 信用代码 / Payer 编码 模糊匹配 */
  keyword?: string;
  bu?: string;
  status?: CustomerStatus;
  customerType?: string;
}

/**
 * 客户指标概览（后端 CmdCustomerStatsVo）
 * <p>
 * 随「当前筛选条件」实时统计，与列表共用同一套条件，口径不会与列表打架。
 */
export interface CustomerStats {
  /** 客户总数 */
  total: number;
  /** 生效中（status = active） */
  activeCount: number;
  /** 待处理（pending 待审批 + returned 退回待补充） */
  pendingCount: number;
  /** 跨 BU 全局可见（gc_scope_flag = Y） */
  crossBuCount: number;
  /** 疑似重复（duplicate_flag = Y） */
  duplicateCount: number;
  /** 平均质量分（未评分的 0 分不计入分母） */
  avgDqScore: number;
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

/** 客户新建申请提交回执（后端 CmdCustomerSubmitVo） */
export interface CustomerSubmitVO {
  customerId?: number;
  /** One ID（服务端生成，后续不可变更） */
  oneId?: string;
  /** 申请编号（进入「治理与审批」队列后可查） */
  taskNo?: string;
  sceneCode?: string;
  sceneName?: string;
  /** 主档状态（pending） */
  status?: string;
  taskStatus?: string;
  currentNodeCode?: string;
  currentNodeName?: string;
  assigneeRole?: string;
  dqScore?: number;
  riskLevel?: string;
  slaDue?: string;
  flowInstanceId?: number;
  flowStatus?: string;
}

/** ------------------------------------------------------------------
 * 2. 元数据字段（Master Data Extension）
 * ------------------------------------------------------------------ */
export type FieldScope = 'GC Core' | 'BU Specific' | 'Source System';
export type FieldType = 'Text' | 'Number' | 'Enum' | 'Date' | 'Reference';

export interface MetadataFieldVO {
  /** 主键 */
  id?: number;
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
  /** 所属模型版本号（如 v1.0） */
  versionNo?: string;
  /** 发布状态：Draft / Published */
  status: 'Draft' | 'Published';
  /** 不可删原因（核心主数据字段 / 治理标记字段）；有值时前端禁用删除按钮 */
  deleteGuard?: string;
}

export interface MetadataFieldForm extends Omit<MetadataFieldVO, 'status'> {
  status?: MetadataFieldVO['status'];
}

/** 值集维护表单（平台管理 · 字段与值集 → 值集编辑） */
export interface ValueSetForm {
  id?: number;
  /** 值集编码 */
  code: string;
  /** 值集名称 */
  name: string;
  /** 值集类型（Enum / Reference ...） */
  type: string;
  /** 取值范围（逗号分隔） */
  values: string;
  /** 状态：Published / Draft */
  status: 'Published' | 'Draft';
}

/** 模型版本 */
export interface ModelVersionVO {
  version: string;
  /** 较上一版本的差异摘要（基线 / 新增 N · 变更 M / 无变更） */
  diff: string;
  status: 'Current' | 'Draft';
  /** 草稿创建时间 */
  draftCreatedAt?: string;
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

/** DQ 规则模拟请求：测试数据集筛选 + 可选单规则 */
export interface DqSimulateForm {
  customerType?: string;
  bu?: string;
  sourceSystem?: string;
  sampleSize?: number;
  ruleId?: number;
}

/** 单条规则在测试数据集上的模拟结果 */
export interface DqRuleSimulateVO {
  id?: number;
  ruleCode: string;
  ruleName: string;
  dimension: string;
  dimensionName: string;
  fieldCode?: string;
  checkType?: string;
  severity?: string;
  scoreWeight?: number | string;
  status?: string;
  total: number;
  pass: number;
  warn: number;
  block: number;
  skip: number;
  passRate: string;
  note?: string;
  samples: DqSampleErrorVO[];
}

/** 字段级错误样例 */
export interface DqSampleErrorVO {
  oneId: string;
  legalName: string;
  message: string;
}

/** 影响评估摘要（对齐总设计「统计受影响 Active 客户、预计新增异常与 Score 变化」） */
export interface DqImpactSummaryVO {
  affectedCustomers: number;
  blockHits: number;
  warningHits: number;
  avgScoreDelta: string;
}

/** DQ 规则模拟测试整体结果 */
export interface DqSimulateResultVO {
  datasetSize: number;
  ruleCount: number;
  rules: DqRuleSimulateVO[];
  impact: DqImpactSummaryVO;
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
/** 匹配规则模拟请求：样例记录（oneId 回读主档 或 手工输入）+ 可选指定规则 */
export interface MatchSimulateForm {
  oneId?: string;
  legalName?: string;
  creditCode?: string;
  address?: string;
  bu?: string;
  ruleId?: number;
}

/** 匹配规则摘要（模拟结果中回显所用规则与阈值） */
export interface MatchRuleBriefVO {
  ruleCode: string;
  ruleName: string;
  scene?: string;
  algorithm?: string;
  exactThreshold?: string;
  suspectThreshold?: string;
  crossBuFlag?: string;
  autoMergeFlag?: string;
}

/** 样例记录回显 */
export interface MatchSampleVO {
  oneId?: string;
  legalName?: string;
  creditCode?: string;
  address?: string;
  buScope?: string;
}

/** 逐字段匹配贡献（字段 / 权重 / 相似度 / 说明） */
export interface MatchFieldScoreVO {
  field: string;
  label: string;
  weight: number;
  score: number;
  detail: string;
}

/** 匹配候选：候选 One ID + 综合相似度 + Exact / Suspected / Below 分类 */
export interface MatchCandidateVO {
  oneId: string;
  legalName: string;
  creditCode?: string;
  buScope?: string;
  status?: string;
  score: number;
  result: 'EXACT' | 'SUSPECTED' | 'BELOW';
  fields: MatchFieldScoreVO[];
}

/** 结果分布（对齐总设计「比较新旧 Exact / Suspected / New 分布」） */
export interface MatchDistributionVO {
  exact: number;
  suspected: number;
  below: number;
}

/** 匹配规则样例模拟整体结果 */
export interface MatchSimulateResultVO {
  rule: MatchRuleBriefVO;
  sample: MatchSampleVO;
  scanned: number;
  distribution: MatchDistributionVO;
  candidates: MatchCandidateVO[];
}

/** 逐字段匹配状态：一致 / 不一致 / 空缺 */
export type DuplicateFieldStatus = 'MATCH' | 'DIFF' | 'EMPTY';

/** 分组逐字段对比行（借鉴 DCR Matching Review 的字段级命中高亮） */
export interface DuplicateFieldMatch {
  /** 字段名 */
  label: string;
  /** 新申请值 */
  incoming: string;
  /** 现有主档值 */
  existing: string;
  /** 匹配状态 */
  status: DuplicateFieldStatus;
}

/** 对比分组：基本属性 / 证照信息 / 层级与业务 */
export interface DuplicateCandidateGroup {
  name: string;
  fields: DuplicateFieldMatch[];
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
  /** 现有主档 One ID（关联已有动作的目标） */
  existingOneId?: string;
  /** 分组逐字段对比；缺省时回退为左右两栏平铺对比 */
  groups?: DuplicateCandidateGroup[];
  /** 接受「关联已有」时的后果提示，如：申请将转为对已有客户的更新 */
  acceptHint?: string;
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
  /** 业务场景（DOOR / LEGAL / GROUP ...） */
  scene?: string;
  /** 归属 BU */
  buScope?: string;
  /** 模板编码与版本 */
  templateCode?: string;
  templateVersion?: string;
  /** 四类分流统计（Exact / Suspected / New / Invalid）+ 待复核 */
  exactCount?: number;
  suspectedCount?: number;
  newCount?: number;
  reviewCount?: number;
  invalidCount?: number;
  /** 任务备注（策略与审批结论） */
  remark?: string;
}

/** 导入行明细（结果分流下钻，对应 CmdImportRowVo） */
export interface ImportRowVO {
  id?: number;
  jobCode?: string;
  rowNo?: number;
  rowStatus?: string;
  resultType?: string;
  oneId?: string;
  handling?: string;
  legalName?: string;
  creditCode?: string;
  buScope?: string;
  dqScore?: number;
  matchState?: string;
  matchScore?: number;
  errorCount?: number;
  errorSummary?: string;
  /** 原始行数据（键＝Excel 列名，用于「查看上传数据」还原用户上传内容） */
  rawJson?: string;
  /** 解析后数据（键＝字段编码） */
  parsedJson?: string;
  remark?: string;
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
  templateCode?: string;
  templateVersion?: string;
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
  dataType?: string;
  defaultValue?: string;
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
  /** 业务场景（设计节点「新建导入任务」：选择业务场景） */
  scene?: string;
  /** 归属 BU */
  buScope?: string;
  /** 来源系统 */
  sourceSystem?: string;
}

/** 模板字段映射 */
export interface TemplateMappingVO {
  /** 映射主键（新增/编辑/删除要用） */
  id?: number | string;
  /** 模板编码 */
  templateCode?: string;
  /** 源列（上传文件表头名） */
  sourceColumn: string;
  /** 目标字段编码 */
  targetField: string;
  /** 目标字段名称 */
  fieldName?: string;
  /** 数据类型（Text / Number / Date） */
  dataType?: string;
  /** 是否必填（Y/N） */
  isRequired?: string;
  /** 默认值 */
  defaultValue?: string;
  transform: string;
  errorStrategy: string;
}

/** 导入模板字段映射保存表单（平台管理 › 导入Template；id 非空 = 编辑） */
export interface TemplateMappingSaveForm {
  id?: number | string;
  templateCode: string;
  columnName: string;
  fieldCode?: string;
  fieldName?: string;
  dataType?: string;
  isRequired?: string;
  defaultValue?: string;
  convertRule?: string;
  remark?: string;
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
  /** 父节点 One ID（空 = 根节点，用于「返回根节点」判断） */
  parentOneId?: string;
  /** 层级深度（A3=1 / A2=2 / A1=3） */
  depth?: number;
  /** 层级类型（COMMERCIAL / LEGAL / DOOR） */
  hierarchyType?: string;
  /** 完整路径 */
  path: string;
  /** 祖先 One ID 列表（用于展开并高亮树节点） */
  ancestorIds?: string[];
  /** 有效期 */
  validity: string;
  /** 状态 */
  status: 'Active' | 'Future' | 'Expired';
  children?: HierarchyNodeVO[];
  /** 是否为「加载更多子节点」占位节点（点击后按需追加下一批） */
  isLoadMore?: boolean;
  /** 占位节点对应的父节点 One ID（点击时按它去取下一批） */
  loadMoreParentId?: string;
  /** 占位节点文案里的「已显示」数量 */
  loadMoreShown?: number;
  /** 占位节点文案里的子节点总数 */
  loadMoreTotal?: number;
}

/**
 * 层级搜索 / 筛选条件（页面左侧「搜索与导航」四个下拉 + 关键字）
 * 传入后由后端 SQL 过滤，保证下拉切换后结果实时变化。
 */
export interface HierarchySearchFilters {
  /** 层级类型（Legal Hierarchy / Sales Hierarchy / Payer Hierarchy） */
  hierarchyType?: string;
  /** 层级级别（A1 / A2 / A3；「全部层级」= 不过滤） */
  level?: string;
  /** 归属 BU（「All Authorized BU」= 不过滤） */
  buScope?: string;
  /** 状态（Active / Future / Expired） */
  status?: string;
}

/** 后端 cmd_hierarchy_relation 行 */
export interface CmdHierarchyRelationRow {
  id?: number;
  relationCode?: string;
  hierarchyType?: string;
  relationType?: string;
  parentOneId?: string;
  childOneId?: string;
  payerOneId?: string;
  buScope?: string;
  crossBuFlag?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  status?: string;
  changeReason?: string;
  sourceType?: string;
  approvalId?: number;
  createTime?: string;
}

/** 层级关系（前端展示对象） */
export interface HierarchyRelationVO {
  id: number;
  relationCode: string;
  hierarchyType: string;
  relationType: string;
  parentOneId: string;
  childOneId: string;
  payerOneId: string;
  buScope: string;
  /** Y = 跨 BU，需 GC Scope 审批 */
  crossBuFlag: string;
  effectiveFrom: string;
  effectiveTo: string;
  /** Pending / Effective / Expired / Rejected */
  status: string;
  changeReason: string;
  sourceType: string;
}

/** 后端 cmd_hierarchy_relation_hist 行 */
export interface CmdHierarchyRelationHistRow {
  id?: number;
  relationId?: number;
  relationCode?: string;
  versionNo?: number;
  operation?: string;
  hierarchyType?: string;
  relationType?: string;
  parentOneId?: string;
  childOneId?: string;
  payerOneId?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  status?: string;
  snapshotJson?: string;
  changeReason?: string;
  createBy?: number;
  createTime?: string;
}

/** 层级关系历史版本（历史归属追溯） */
export interface HierarchyRelationHistVO {
  id: number;
  relationId: number;
  relationCode: string;
  /** 关系版本号 */
  versionNo: number;
  /** CREATE / UPDATE / EXPIRE */
  operation: string;
  relationType: string;
  parentOneId: string;
  childOneId: string;
  payerOneId: string;
  effectiveFrom: string;
  effectiveTo: string;
  status: string;
  /** 改动前后快照（JSON 字符串） */
  snapshotJson?: string;
  changeReason: string;
  /** 历史发生时间 */
  createTime: string;
}

/** 后端 /cmd/hierarchy/validate 原始返回 */
export interface CmdHierarchyValidateRow {
  checkCode?: string;
  passed?: boolean;
  blockedReason?: string;
  relationLabel?: string;
  parentOneId?: string;
  childOneId?: string;
  childName?: string;
  parentName?: string;
  parentLevel?: string;
  parentDepth?: number;
  childLevel?: string;
  childDepth?: number;
  previewPath?: string;
  previewPathNames?: string;
  crossBu?: boolean;
  requiresGcApproval?: boolean;
  relationType?: string;
  hierarchyType?: string;
  maxDepth?: number;
  childMounted?: boolean;
  childCurrentParentOneId?: string;
  childCurrentLevel?: string;
  checks?: {
    checkType?: string;
    label?: string;
    checkResult?: string;
    message?: string;
    conflictPath?: string;
    suggestion?: string;
  }[];
  executeTime?: string;
  durationMs?: number;
}

/** 单条校验明细 */
export interface HierarchyCheckVO {
  checkType: string;
  label: string;
  /** PASS 通过 / FAIL 阻塞 / WARN 警告（需 GC 决策） */
  checkResult: 'PASS' | 'FAIL' | 'WARN';
  message: string;
  /** 冲突路径证据 */
  conflictPath?: string;
  suggestion?: string;
}

/** 层级关系实时校验结果（提交前校验） */
export interface HierarchyValidateVO {
  checkCode: string;
  passed: boolean;
  blockedReason?: string;
  relationLabel: string;
  parentName: string;
  childName: string;
  parentLevel: string;
  parentDepth: number;
  childLevel: string;
  childDepth: number;
  previewPath: string;
  previewPathNames: string;
  crossBu: boolean;
  requiresGcApproval: boolean;
  relationType: string;
  maxDepth: number;
  childMounted: boolean;
  childCurrentParentOneId: string;
  childCurrentLevel: string;
  checks: HierarchyCheckVO[];
  executeTime: string;
  durationMs: number;
}

/** 层级关系实时校验入参（不落业务数据） */
export interface HierarchyValidateForm {
  /** 编辑场景传关系主键，用于排除自身 */
  id?: number;
  parentOneId: string;
  childOneId: string;
  relationType?: string;
  payerOneId?: string;
  changeReason?: string;
}

/** 增加子节点入参（落库） */
export interface HierarchyChildForm {
  parentOneId: string;
  childOneId: string;
  relationType?: string;
  payerOneId?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  changeReason?: string;
  remark?: string;
}

/** 编辑层级关系入参（落库，历史不覆盖） */
export interface HierarchyRelationEditForm {
  id: number;
  /** 子节点只读回显（服务端不允许替换子节点） */
  childOneId: string;
  parentOneId: string;
  relationType?: string;
  payerOneId?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  changeReason?: string;
  remark?: string;
}

/** 新增层级关系 / 发起申请入参（草稿态关系，需审批） */
export interface HierarchyRelationForm {
  hierarchyType: string;
  relationType: string;
  parentId: string;
  childId: string;
  payerOneId: string;
  effectiveDate: string;
  reason: string;
  /**
   * 演示专用：选择「通过 / 父子相同 / 多父冲突 / 路径循环」四类校验示例，
   * 用于展示 BLOCKED 提示。不提交服务端，后端只收 HierarchyRelationForm 的正式字段。
   */
  validationCase?: string;
}

/**
 * 待归位主数据（客户层级 → 待归位主数据 列表行）
 * 说明：批准成为主数据后自动出现在这里，Data Steward 归位后进入 A3-A2-A1 树。
 */
export interface HierarchyUnassignedVO {
  oneId: string;
  /** 客户名称 */
  name: string;
  bu: string;
  /** 客户状态（Active） */
  status: string;
  /** 来源系统 */
  source: string;
  /** 审批通过时间 */
  approvedTime: string;
  /** 是否已登记待归位节点 */
  registered: boolean;
  nodeCode: string;
  /** 建议层级级别 */
  suggestedLevel: string;
  remark: string;
}

/**
 * 层级归位表单（客户层级 → 待归位主数据 → 归位）
 * 语义：把已批准的主数据挂到某个 A3 / A2 节点之下，使其成为层级树上的 A2 / A1。
 */
export interface HierarchyAssignForm {
  /** 待归位客户 One ID */
  oneId: string;
  /** 目标父节点 One ID */
  parentId: string;
  /** 变更原因 */
  changeReason: string;
  remark?: string;
}

/** ------------------------------------------------------------------
 * 7. 变更 / 逻辑停用
 * ------------------------------------------------------------------ */
/**
 * 页面展示状态（前端口径）。
 * 与后端 cmd_change_request.status 的对应关系在 api 层统一转换，面板不感知后端取值。
 */
export type ChangeStatus =
  | 'Under Review'
  | 'Approved'
  | 'Effective'
  | 'Rejected'
  | 'Returned'
  | 'Cancelled'
  | 'Inactive'
  | 'Draft';

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
  /** 后端原始状态（DRAFT / PENDING / APPROVED / REJECTED / RETURNED / EFFECTIVE / CANCELLED），用于判定可用操作 */
  rawStatus?: string;
  /** 是否关键属性变更（Y / N） */
  isKeyChange?: string;
  /** 目标状态（停用场景：inactive / archived） */
  targetStatus?: string;
  /** 关联关系影响检查结论（PASS / WARN / FAIL） */
  relationCheck?: string;
  /** 关联关系影响检查说明 */
  relationMsg?: string;
  /** 计划生效日期 */
  effectiveDate?: string;
  /** 实际生效时间 */
  effectiveTime?: string;
  /** 关联审批待办编号 */
  approvalTaskNo?: string;
  remark?: string;
}

export interface ChangeRequestQuery extends PageQuery {
  keyword?: string;
  changeType?: 'Update' | 'Deactivate' | '';
  status?: ChangeStatus | '';
  buScope?: string;
  oneId?: string;
}

/** 单行字段变更（表单采集：字段编码 + 新值；Before 由服务端从主档回填） */
export interface ChangeFieldItem {
  /** 字段编码（md_field.field_code，如 credit_code / address） */
  fieldCode: string;
  /** 字段中文名（仅用于展示） */
  fieldName?: string;
  /** 变更后值 */
  afterValue: string;
}

/** 变更申请表单 */
export interface ChangeRequestForm {
  oneId: string;
  /** Update / Deactivate */
  changeType: string;
  /** 变更原因 */
  reason: string;
  /** 计划生效日期（YYYY-MM-DD HH:mm:ss） */
  effectiveDate: string;
  /** 字段级变更明细（属性变更场景必填；停用场景留空，服务端按状态切换生成差异行） */
  fields: ChangeFieldItem[];
}

/** 停用申请表单 */
export interface DeactivateForm {
  oneId: string;
  targetStatus: 'Inactive' | 'Archived';
  reason: string;
  effectiveDate: string;
  remark: string;
}

/** Before / After 差异行（页面展示口径） */
export interface ChangeDiffVO {
  field: string;
  before: string;
  after: string;
  /** 变化类型：ADD / MODIFY / DELETE / SAME */
  changeFlag?: string;
  /** 是否关键字段（Y / N） */
  isKey?: boolean;
  /** 是否敏感字段（Y / N） */
  sensitive?: boolean;
}

/** 审批轨迹行（页面展示口径） */
export interface ApprovalTrailVO {
  time: string;
  role: string;
  action: string;
  result: string;
  /** 操作人 */
  operator?: string;
  /** 流程节点 */
  node?: string;
  /** 审批意见 */
  opinion?: string;
}

/** 版本历史行（页面展示口径，证明「换版本不换 One ID」） */
export interface ChangeVersionVO {
  versionNo: number;
  /** CREATE / UPDATE / DEACTIVATE / MERGE / RESTORE */
  changeType: string;
  changeReason: string;
  /** 本次变更的字段编码列表 */
  changedFields: string;
  status: string;
  sourceSystem: string;
  dqScore?: number;
  /** 变更前快照（JSON 字符串，属性级 Before/After 展示用） */
  beforeJson?: string | null;
  /** 本版本快照（变更后，JSON 字符串） */
  snapshotJson?: string | null;
  /** 是否由本次申请触发（用于详情弹窗高亮） */
  requestCode?: string;
  createTime: string;
}

/** 变更详情（差异 + 影响面 + 轨迹 + 版本上下文） */
export interface ChangeDetailVO {
  requestId: string;
  oneId: string;
  customerName: string;
  changeType: 'Update' | 'Deactivate';
  targetStatus: string;
  isKeyChange: boolean;
  bu: string;
  reason: string;
  status: ChangeStatus;
  rawStatus: string;
  effectiveDate: string;
  effectiveTime: string;
  /** 关联关系影响检查结论 */
  relationCheck: string;
  relationMsg: string;
  /** 影响面清单（逐条人可读） */
  impacts: string[];
  approvalTaskNo: string;
  submittedAt: string;
  approvedByName: string;
  approvedTime: string;
  /** 主档当前版本号 */
  currentVersionNo?: number;
  /** 本次生效后的版本号（未生效为 undefined） */
  effectiveVersionNo?: number;
  diffs: ChangeDiffVO[];
  trail: ApprovalTrailVO[];
  versions: ChangeVersionVO[];
  remark: string;
}

/** 逻辑停用数据库结果 */
export interface DeactivateResultVO {
  businessView: Array<{ key: string; value: string }>;
  /** 后台记录示意（SQL / 字段落库） */
  dbRecords: string[];
  /** 该 One ID 的完整版本链 */
  versions: ChangeVersionVO[];
}

/** 可变更字段目录行（页面口径，来自 md_field 配置） */
export interface ChangeFieldVO {
  /** 字段编码 */
  fieldCode: string;
  /** 字段名称 */
  fieldName: string;
  /** 数据类型 */
  dataType: string;
  /** 值集编码（ENUM 使用） */
  valueSetCode?: string;
  /** 是否必填（Y / N） */
  isRequired: string;
  /** 是否关键字段（Y：变更需更高级别审批） */
  isKeyField: string;
  /** 是否敏感字段（Y：变更需额外留痕） */
  isSensitive: string;
  /** 长度上限 */
  maxLength?: number;
  /** 正则校验表达式 */
  regexPattern?: string;
  /** 物理列名 */
  physicalColumn?: string;
}

/* ---- 后端原始行（保持与 Java VO 字段一一对应，转换在 api 层完成） ---- */

/** 后端可变更字段行（CmdChangeFieldVo） */
export interface CmdChangeFieldRow {
  fieldCode?: string;
  fieldName?: string;
  dataType?: string;
  valueSetCode?: string;
  isRequired?: string;
  isKeyField?: string;
  isSensitive?: string;
  maxLength?: number;
  regexPattern?: string;
  physicalColumn?: string;
  orderNum?: number;
}

/** 后端指标卡行（CmdChangeKpiVo） */
export interface CmdChangeKpiRow {
  label?: string;
  value?: number;
  hint?: string;
}

/** 后端字段差异行（CmdChangeDiffVo） */
export interface CmdChangeDiffRow {
  id?: number;
  requestId?: number;
  requestCode?: string;
  fieldCode?: string;
  fieldName?: string;
  beforeValue?: string;
  afterValue?: string;
  isKeyField?: string;
  isSensitive?: string;
  changeFlag?: string;
  orderNum?: number;
  remark?: string;
}

/** 后端审批轨迹行（CmdChangeTrailVo） */
export interface CmdChangeTrailRow {
  time?: string;
  role?: string;
  operator?: string;
  action?: string;
  node?: string;
  result?: string;
  opinion?: string;
}

/** 后端客户版本快照行（CmdCustomerVersionVo） */
export interface CmdCustomerVersionRow {
  id?: number;
  oneId?: string;
  versionNo?: number;
  changeType?: string;
  changeReason?: string;
  changedFields?: string;
  snapshotJson?: string;
  beforeJson?: string;
  dqScore?: number;
  status?: string;
  sourceSystem?: string;
  changeRequestId?: number;
  createTime?: string;
}

/** 后端变更详情（CmdChangeDetailVo） */
export interface CmdChangeDetailRow {
  id?: number;
  requestCode?: string;
  oneId?: string;
  legalName?: string;
  changeType?: string;
  targetStatus?: string;
  isKeyChange?: string;
  buScope?: string;
  changeReason?: string;
  status?: string;
  effectiveDate?: string;
  effectiveTime?: string;
  relationCheck?: string;
  relationMsg?: string;
  approvalTaskNo?: string;
  flowInstanceId?: number;
  remark?: string;
  createTime?: string;
  approvedByName?: string;
  approvedTime?: string;
  currentVersionNo?: number;
  effectiveVersionNo?: number;
  diffs?: CmdChangeDiffRow[];
  trail?: CmdChangeTrailRow[];
  impacts?: string[];
  versions?: CmdCustomerVersionRow[];
}

/** 后端逻辑停用结果（CmdDeactivateResultVo） */
export interface CmdDeactivateResultRow {
  businessView?: Array<{ key?: string; value?: string }>;
  dbRecords?: string[];
  versions?: CmdCustomerVersionRow[];
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
  /** 客户主数据标识（One ID）—— 全链路追溯主键 */
  oneId?: string;
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
  /** 客户主数据标识（One ID）—— 全链路追溯主键 */
  oneId?: string;
  /** 业务主键：批量导入确认为批次号（Import Job Code），批次级审批不含 One ID */
  bizId?: string;
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

/**
 * 流程跟踪 · 分步骤明细字段（键值对）
 */
export interface FlowStepFieldVO {
  label: string;
  value?: string;
  /** 语义色（success / warning / danger / info），空表示普通文本 */
  tone?: string;
}

/**
 * 流程跟踪 · 分步骤明细表格
 */
export interface FlowStepTableVO {
  title?: string;
  columns: string[];
  /** 与 columns 列序一致的数据行 */
  rows: string[][];
}

/**
 * 流程跟踪 · 分步骤明细
 *
 * 与 FlowTraceStepVO 按 nodeCode 一一对应：点击泳道图某个节点后，
 * 在步骤条下方动态展示该节点的相关内容（录入字段与附件 / OCR 识别结果 /
 * DQ 检查项 / 匹配候选 / 审批动作 / 编码映射 / 集成下发 / 步骤日志 / 审计事件）。
 * 节点语义留在服务端（后端决定每个节点出现哪些区块），前端只负责渲染。
 */
export interface FlowStepDetailVO {
  nodeCode: string;
  nodeName?: string;
  phaseName?: string;
  lane?: string;
  status?: string;
  /** 一句话结论（该节点发生了什么） */
  summary?: string;
  fields?: FlowStepFieldVO[];
  tables?: FlowStepTableVO[];
  /** 提示 / 口径说明 */
  notes?: string[];
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

/** 工作流步骤执行日志（按客户 One ID 串联每一步） */
export interface WorkflowStepVO {
  id?: number;
  /** 客户主数据标识（One ID）—— 全链路追溯主键 */
  oneId?: string;
  /** 审批任务编号（AP-yyyyMMdd-####） */
  taskNo?: string;
  /** Warm-Flow 流程实例 ID */
  flowInstanceId?: number;
  /** 步骤序号（同一 One ID 内从 1 递增） */
  stepSeq?: number;
  /** 步骤类型：SUBMIT / SYSTEM / BUSINESS / ENGINE */
  stepType?: string;
  /** 节点编码 */
  nodeCode?: string;
  /** 节点名称 */
  nodeName?: string;
  /** 动作类型 */
  actionType?: string;
  /** 动作名称 */
  actionName?: string;
  operatorId?: number;
  operatorName?: string;
  operatorRole?: string;
  /** 业务状态（操作前） */
  fromStatus?: string;
  /** 业务状态（操作后） */
  toStatus?: string;
  /** 审批意见 / 升级原因 */
  opinion?: string;
  createTime?: string;
}

/** 工作流：单个 CMD 业务场景（V6.1 总设计业务流） */
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

/** ------------------------------------------------------------------
 * 3.1 工作流配置（平台管理 › Workflow › 工作流定义 › 配置）
 *
 * 对应 V6.1 总设计第 16 页「Workflow配置」：流程节点、路由条件、SLA、超时升级和邮件通知。
 * 数据来源：cmd_flow_scene（场景级配置）+ cmd_flow_node_rule（节点审批人规则）。
 * ------------------------------------------------------------------ */

/**
 * 泳道节点（业务蓝图，只读）
 *
 * locked=true 表示平台固定项（业务入口 / 系统自动节点 / 发布与审计节点），不可删除；
 * configurable=true 表示节点办理方式可按场景配置。
 */
export interface FlowSceneNodeVO {
  /** 阶段序号（1-7） */
  phase?: number;
  /** 阶段名称 */
  phaseName?: string;
  /** 泳道（角色） */
  lane?: string;
  /** 节点编码 */
  nodeCode: string;
  /** 节点名称 */
  nodeName: string;
  /** 节点类型（AUTO 系统自动 / MANUAL 人工 / GATEWAY 分支网关） */
  nodeType?: string;
  /** 节点业务说明 */
  note?: string;
  /** 平台固定（不可删除 / 不可停用） */
  locked: boolean;
  /** 可配置（人工节点由路由规则配置） */
  configurable: boolean;
  /** 约束说明（为什么不可修改 / 可以配置什么） */
  constraint?: string;
}

/**
 * 节点审批人规则（可增删、可调整）—— 场景级「可增加或减少的工作流项目」
 *
 * id 为空 = 新增；status='1' = 停用（即从该场景的工作流中移除该节点）。
 */
export interface FlowSceneRuleVO {
  id?: number | string | null;
  /** Warm-Flow 节点编码（bu_review / gc_review ...） */
  nodeCode: string;
  nodeName?: string;
  /** 命中条件表达式（如 risk_level == "High" || cross_bu） */
  conditionExpr?: string;
  /** 审批人类型（ROLE / USER / DEPT_LEADER / SPEL） */
  assigneeType?: string;
  /** 审批人值（角色编码 / 用户 ID / 表达式） */
  assigneeValue?: string;
  /** 适用范围（GC / BU / CROSS_BU） */
  scopeType?: string;
  /** 多审批人模式（ALL 会签 / ANY 或签 / SEQUENCE 依次） */
  multiMode?: string;
  /** 节点 SLA（小时） */
  slaHours?: number;
  /** 优先级 */
  priority?: number;
  /** 状态（0 正常 / 1 停用） */
  status?: string;
  remark?: string;
  /** 平台固定：不可停用（如 BU 初审主干） */
  locked?: boolean;
  constraint?: string;
}

/** 场景工作流配置（GET /cmd/flow/scene/{sceneCode}/config） */
export interface FlowSceneConfigVO {
  sceneCode: string;
  sceneName: string;
  flowCode: string;
  flowName?: string;
  /** 场景整体 SLA（小时） */
  slaHours?: number;
  /** 超时升级规则（TO_GC / NOTIFY） */
  escalateRule?: string;
  /** 启动条件表达式（满足才走流程，否则直接生效） */
  startConditions?: string;
  /** 审批表单标识 */
  formKey?: string;
  deployed?: boolean;
  version?: number;
  /** 超时动作 */
  timeoutAction?: string;
  /** 通知方式 */
  notifyMode?: string;
  /** 通知对象 */
  notifyTargets?: string[];
  /** 变更说明 */
  changeNote?: string;
  /** 泳道节点蓝图（只读） */
  nodes: FlowSceneNodeVO[];
  /** 可配置的节点审批人规则 */
  rules: FlowSceneRuleVO[];
}

/** 场景工作流配置保存入参（PUT /cmd/flow/scene/{sceneCode}/config） */
export interface FlowSceneConfigBo {
  slaHours?: number;
  escalateRule?: string;
  startConditions?: string;
  timeoutAction?: string;
  notifyMode?: string;
  notifyTargets?: string[];
  changeNote?: string;
  rules?: FlowSceneRuleVO[];
}

/**
 * 流程实例记录（「工作流」两个列表页共用，GET /cmd/flow/instances）
 *
 * 每一次执行过的工作流都留一条记录，可查看进度并用 Graph 回看当时的泳道图。
 */
export interface FlowInstanceVO {
  /** 待办任务主键 */
  id: number | string;
  /**
   * 事务ID（= 申请编号 task_no，AP-yyyyMMdd-####）：提交时即生成，
   * 单条创建与批量导入（批次级）都有，是贯穿整个工作流的追踪键
   * （泳道图 / 流程跟踪 / 实例列表都按它查询）。
   */
  taskNo: string;
  /**
   * 客户主数据标识（One ID）：客户主档的贯通 ID。
   * 注意：它不是工作流追踪键——单条创建批准后才生成、
   * 批量导入批准后逐行生成，故列表中以「事务ID」为主键、One ID 作副行展示。
   */
  oneId?: string;
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
  /**
   * 分步骤明细：与 steps 按 nodeCode 一一对应。
   * 点击泳道图某个节点后，在步骤条下方展示该节点的相关内容。
   */
  stepDetails?: FlowStepDetailVO[];
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
  /** 主键字段标记 Y/N */
  isKeyField?: string;
  /** 匹配字段标记 Y/N */
  isMatchField?: string;
  /** DQ 评分字段标记 Y/N */
  isDqField?: string;
  scopeType?: string;
  ownerBu?: string;
  versionNo?: string;
  status?: string;
  orderNum?: number;
  remark?: string;
  /** 不可删原因（后端回填，非数据库列）；有值即表示受保护，不允许删除 */
  deleteGuard?: string;
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
  diff?: string;
  status?: string;
  draftCreatedAt?: string;
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
  id?: number;
  ruleCode?: string;
  ruleName?: string;
  pattern?: string;
  prefix?: string;
  separator?: string;
  serialLength?: number;
  seqCode?: string;
  genStrategy?: string;
  stablePolicy?: string;
  reusePolicy?: string;
  scopeType?: string;
  status?: string;
  isDefault?: string;
  remark?: string;
}

/** 后端 Legacy 映射行（对应 cmd_legacy_mapping） */
export interface CmdLegacyMappingRow {
  oneId?: string;
  sourceSystem?: string;
  sourceCode?: string;
  sourceName?: string;
  buScope?: string;
  /** 映射类型：LEGACY 来源登记 / MERGE 合并交叉引用 */
  mappingType?: string;
  status?: string;
  effectiveFrom?: string;
  remark?: string;
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
 * 后端集成端点行（对应 IntEndpointVo，status：0 正常 / 1 停用）
 */
export interface CmdIntegrationEndpointRow {
  id?: number;
  endpointCode?: string;
  endpointName?: string;
  direction?: string;
  protocol?: string;
  targetSystem?: string;
  endpointUrl?: string;
  authType?: string;
  bizType?: string;
  messageFormat?: string;
  maxRetry?: number;
  timeoutMs?: number;
  status?: string;
  remark?: string;
  createTime?: string;
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
  changedFields?: string;
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
  /** 客户主数据标识（One ID） */
  oneId?: string;
  /** 业务主键（批量导入确认为批次号） */
  bizId?: string;
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
  stepDetails?: Array<{
    nodeCode?: string;
    nodeName?: string;
    phaseName?: string;
    lane?: string;
    status?: string;
    summary?: string;
    fields?: Array<{ label?: string; value?: string; tone?: string }>;
    tables?: Array<{ title?: string; columns?: string[]; rows?: string[][] }>;
    notes?: string[];
  }>;
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
  id?: number;
  ruleCode?: string;
  ruleName: string;
  status: 'Published' | 'Draft';
  object: string;
  serialLength: string;
  prefix: string;
  separator: string;
  pattern?: string;
  genStrategy?: string;
  stablePolicy?: string;
  reusePolicy?: string;
  seqCode?: string;
  remark?: string;
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
  /** 操作者姓名（审计事件） */
  operator?: string;
  /** 本次变更涉及的字段编码（逗号分隔，合并 / 变更事件有值） */
  changedFields?: string | null;
}

/** 客户合并记录（对应 cmd_merge_record，总设计「审计与合并记录」） */
export interface MergeRecordVO {
  mergeCode: string;
  /** 保留方 One ID（Golden Record） */
  survivorOneId: string;
  /** 被合并方 One ID */
  mergedOneId: string;
  mergeType: string;
  mergeStrategy: string;
  /** 字段级合并决策：源记录补进了目标哪些字段（JSON 字符串） */
  fieldJson?: string | null;
  reason: string;
  /** EFFECTIVE 生效 / ROLLED_BACK 已回滚 */
  status: string;
  /** 是否可回滚（Y / N） */
  canRollback: string;
  /** 关联审批单号（备注里） */
  remark?: string | null;
  createTime: string;
}

/** 后端合并记录行（对应 cmd_merge_record） */
export interface CmdMergeRecordRow {
  mergeCode?: string;
  survivorOneId?: string;
  mergedOneId?: string;
  mergeType?: string;
  mergeStrategy?: string;
  fieldJson?: string;
  reason?: string;
  status?: string;
  canRollback?: string;
  remark?: string;
  createTime?: string;
}

/** Legacy Code ↔ One ID 交叉引用 */
export interface LegacyMappingVO {
  oneId: string;
  sourceSystem: string;
  legacyCode: string;
  /** 来源系统里的客户名称 */
  sourceName?: string;
  /** LEGACY 来源登记 / MERGE 合并交叉引用 */
  mappingType?: string;
  bu: string;
  status: CustomerStatus;
  effectiveFrom?: string;
  remark?: string | null;
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

/** 集成端点展示对象（端点配置 Tab 列表行） */
export interface IntegrationEndpointVO {
  id: number;
  /** 端点编码 */
  code: string;
  /** 端点名称 */
  name: string;
  direction: 'Inbound' | 'Outbound';
  protocol: string;
  /** 目标系统 */
  system: string;
  url: string;
  authType: string;
  bizType: string;
  messageFormat: string;
  maxRetry: number;
  timeoutMs: number;
  status: 'Active' | 'Inactive';
  /** 同步周期 */
  period: string;
  createTime?: string;
}

/** 集成端点配置表单（新增 / 编辑弹窗） */
export interface IntegrationConnForm {
  /** 主键（编辑时传，新增为空） */
  id?: number;
  /** 目标系统 */
  system: string;
  /** 端点名称 */
  name: string;
  /** 协议 */
  protocol: string;
  /** 方向 */
  direction: string;
  /** 同步周期 */
  period: string;
  /** 端点地址 */
  url: string;
  /** 认证方式 */
  authType: string;
  /** 业务类型 */
  bizType: string;
  /** 报文格式 */
  messageFormat: string;
  /** 最大重试次数 */
  maxRetry: number;
  /** 超时时间（毫秒） */
  timeoutMs: number;
  /** 状态（0 正常 / 1 停用） */
  status: string;
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
  /** 客户主数据标识（One ID）：贯穿 ID，可按此 ID 反查该客户的全部审计留痕 */
  oneId?: string;
  /** 关联业务单号（客户新建场景为申请编号 AP-xxxx，变更场景为变更单号） */
  bizId?: string;
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
  /** 真实角色编码（数据库 cmd_role.role_code） */
  roleCode: string;
  /** 角色主键（存在时走更新，否则走新增） */
  id?: number;
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
