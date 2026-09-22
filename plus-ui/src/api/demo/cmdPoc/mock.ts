/**
 * CMD POC 演示数据（Mock）
 *
 * 数据 1:1 取自 Essilor CMD POC 原型，后端接口就绪后本文件可整体删除。
 * 注意：仅用于交互演示，不含任何真实业务数据。
 */
import type {
  ApprovalFlowVO,
  ApprovalInstanceVO,
  ApprovalKpiVO,
  ApprovalTaskDetailVO,
  ApprovalTaskVO,
  ApprovalTrailVO,
  AuditEventVO,
  BatchResultVO,
  ChangeDiffVO,
  ChangeRequestVO,
  CoverageItemVO,
  CustomerVO,
  DashboardStatVO,
  DeactivateResultVO,
  DqRuleRow,
  DqScorecardVO,
  DqSimulateResultVO,
  DuplicateCandidateVO,
  FlowTraceStepVO,
  FlowTraceVO,
  FlowGraphEdgeVO,
  FlowGraphNodeVO,
  FlowGraphVO,
  FlowInstanceVO,
  FlowSceneVO,
  HierarchyNodeVO,
  CmdHierarchyUnassignedRow,
  ImportJobVO,
  ImportTemplateVO,
  IntegrationRunVO,
  LegacyMappingVO,
  MatchRuleRow,
  MatchSimulateResultVO,
  MetadataFieldVO,
  ModelVersionVO,
  NotificationVO,
  OcrResultVO,
  OcrLicenseVO,
  OneIdEventVO,
  OneIdPolicyVO,
  OneIdRuleVO,
  PermissionMatrixVO,
  ReEvaluateImpactVO,
  RolePermissionVO,
  TemplateMappingVO,
  TodoVO,
  FlowSceneConfigVO,
  FlowSceneNodeVO,
  FlowSceneRuleVO,
  FlowStepDetailVO,
  FlowStepFieldVO,
  FlowStepTableVO
} from './types';

/** ---------------------------------- 工作台 ---------------------------------- */
export const mockDashboardStats: DashboardStatVO[] = [
  { key: 'todo', label: '待办', value: 8, hint: 'Demo data' },
  { key: 'exception', label: '异常', value: 14, hint: 'Demo data' },
  { key: 'processing', label: '处理中', value: 6, hint: 'Demo data' },
  { key: 'done', label: '已完成', value: 18, hint: 'Demo data' }
];

export const mockTodo: TodoVO = { count: 5, label: '待处理任务', hint: '点击菜单进入详情', tag: '待处理' };

export const mockNotifications: NotificationVO[] = [
  { id: 'NT-001', title: '疑似重复候选 GC-000128 等待 Cross-BU 决策', time: '2026-09-16 10:18', type: '治理' },
  { id: 'NT-002', title: '批量导入 IMP-001 部分成功，18 行需人工治理', time: '2026-09-16 09:41', type: '导入' },
  { id: 'NT-003', title: 'Outbound 同步 OUT-008 失败：HTTP 504 Gateway Timeout', time: '2026-09-16 08:55', type: '集成' },
  { id: 'NT-004', title: '数据质量重评估完成：GC-000128 综合 86 分', time: '2026-09-15 18:00', type: '质量' }
];

/** ---------------------------------- 客户主数据 ---------------------------------- */
/** 1:1 取自原型 customers() 表格三行 */
export const mockCustomers: CustomerVO[] = [
  {
    oneId: 'GC-000128',
    legalName: '上海清视眼镜有限公司',
    customerType: 'Door',
    bu: 'High End / Mainstream',
    productLine: 'Frame',
    sourceSystem: 'Cloud + DMS+',
    creditCode: '91310000XXXXXXXXXX',
    address: '上海市静安区南京西路XXX号',
    payerId: 'GC-PY-0092',
    status: 'active',
    dqScore: 86,
    versionNo: 6,
    updatedAt: '2026-09-15'
  },
  {
    oneId: 'GC-000245',
    legalName: '北京明眸商业有限公司',
    customerType: 'Door',
    bu: 'Mainstream',
    productLine: 'Lens',
    sourceSystem: 'DMS+',
    creditCode: '91110000XXXXXXXXXX',
    address: '北京市朝阳区建国路XXX号',
    status: 'active',
    dqScore: 72,
    versionNo: 7,
    updatedAt: '2026-09-15'
  },
  {
    oneId: 'PENDING',
    legalName: '苏州新视野眼镜有限公司',
    customerType: 'Door',
    bu: 'High End',
    productLine: 'Frame',
    sourceSystem: 'Manual',
    creditCode: '',
    address: '',
    payerId: '',
    status: 'pending',
    dqScore: 0,
    versionNo: 0,
    updatedAt: '2026-09-14'
  }
];

/** ---------------------------------- 元数据字段 ---------------------------------- */
export const mockMetadataFields: MetadataFieldVO[] = [
  { code: 'legal_name', label: '工商名称', scope: 'GC Core', type: 'Text', required: true, bu: 'All', customerType: 'All', status: 'Published' },
  { code: 'credit_code', label: '统一社会信用代码', scope: 'GC Core', type: 'Text', required: true, bu: 'All', customerType: 'All', status: 'Published' },
  { code: 'business_address', label: '经营地址', scope: 'GC Core', type: 'Text', required: true, bu: 'All', customerType: 'All', status: 'Published' },
  { code: 'payer_id', label: 'Payer', scope: 'GC Core', type: 'Reference', required: true, bu: 'All', customerType: 'Door', status: 'Published' },
  { code: 'store_grade', label: '门店等级', scope: 'BU Specific', type: 'Enum', required: false, bu: 'High End', customerType: 'Door', status: 'Draft' }
];

export const mockModelVersions: ModelVersionVO[] = [
  { version: 'v1.4', diff: '基线', status: 'Current', publishedAt: '2026-08-01' },
  { version: 'v1.5', diff: '新增 1', status: 'Draft', draftCreatedAt: '2026-08-05' }
];

/** 值集定义（字段与值集管理 · 值集页签） */
export const mockValueSets: Array<{ code: string; name: string; type: string; values: string; status: 'Published' | 'Draft' }> = [
  { code: 'VS_STORE_GRADE', name: '门店等级', type: 'Enum', values: 'A / B / C', status: 'Draft' },
  { code: 'VS_CUST_TYPE', name: '客户类型', type: 'Enum', values: 'Door / Payer / A1 / A2 / A3', status: 'Published' },
  { code: 'VS_SOURCE_SYSTEM', name: '来源系统', type: 'Enum', values: 'Cloud / DMS+', status: 'Published' }
];

/** ---------------------------------- 数据质量 ---------------------------------- */
export const mockDqScorecard: DqScorecardVO = {
  oneId: 'GC-000128',
  legalName: '上海清视眼镜有限公司',
  overall: 86,
  dimensions: [
    { dimension: '完整性', weight: '40%', score: 92 },
    { dimension: '有效性', weight: '30%', score: 84 },
    { dimension: '一致性', weight: '20%', score: 78 },
    { dimension: '唯一性', weight: '10%', score: 100 }
  ],
  versions: mockModelVersions,
  exceptions: [
    { code: 'payer_required', name: 'Payer Required', type: 'Business', level: 'Blocking', action: '阻止提交', enabled: true },
    { code: 'address_standard', name: 'Address Standardization', type: 'Technical', level: 'Warning', action: '允许提交并标记', enabled: true }
  ]
};

export const mockDqSimulate: DqSimulateResultVO = {
  datasetSize: 3,
  ruleCount: 2,
  rules: [
    {
      ruleCode: 'DQ_C_001',
      ruleName: '客户名称必填',
      dimension: 'COMPLETENESS',
      dimensionName: '完整性',
      fieldCode: 'legal_name',
      checkType: 'NOT_NULL',
      severity: 'ERROR',
      status: '1',
      total: 3,
      pass: 3,
      warn: 0,
      block: 0,
      skip: 0,
      passRate: '100%',
      samples: []
    },
    {
      ruleCode: 'DQ_V_001',
      ruleName: '信用代码格式校验',
      dimension: 'VALIDITY',
      dimensionName: '有效性',
      fieldCode: 'credit_code',
      checkType: 'REGEX',
      severity: 'ERROR',
      status: '1',
      total: 3,
      pass: 2,
      warn: 0,
      block: 1,
      skip: 0,
      passRate: '66.7%',
      samples: [{ oneId: 'GC-000128', legalName: '上海清视眼镜有限公司', message: '统一社会信用代码为空' }]
    }
  ],
  impact: { affectedCustomers: 1, blockHits: 1, warningHits: 0, avgScoreDelta: '-15 分' }
};

/** DQ 规则清单（对应页面「数据质量 → 规则配置」，行契约与后端 DqRuleRow 一致） */
export const mockDqRules: DqRuleRow[] = [
  { id: 1, ruleCode: 'DQ_C_001', ruleName: '客户名称必填', dimension: 'COMPLETENESS', fieldCode: 'legal_name', checkType: 'NOT_NULL', severity: 'ERROR', scoreWeight: 10, status: '1' },
  { id: 2, ruleCode: 'DQ_V_001', ruleName: '信用代码格式校验', dimension: 'VALIDITY', fieldCode: 'credit_code', checkType: 'REGEX', severity: 'ERROR', scoreWeight: 15, status: '1' },
  { id: 3, ruleCode: 'DQ_V_002', ruleName: '邮箱格式校验', dimension: 'VALIDITY', fieldCode: 'contact_email', checkType: 'REGEX', severity: 'WARNING', scoreWeight: 5, status: '1' }
];

/** ---------------------------------- 匹配规则 ---------------------------------- */
export const mockMatchRules: MatchRuleRow[] = [
  { id: 1, ruleCode: 'MR_CUSTOMER_V1', ruleName: '客户匹配规则-标准版', scene: 'CREATE', algorithm: 'WEIGHTED', exactThreshold: 95, suspectThreshold: 70, autoMergeFlag: 'N', crossBuFlag: 'Y', status: '1' },
  { id: 2, ruleCode: 'MR_IMPORT_V1', ruleName: '客户匹配规则-批量导入', scene: 'IMPORT', algorithm: 'WEIGHTED', exactThreshold: 92, suspectThreshold: 65, autoMergeFlag: 'N', crossBuFlag: 'Y', status: '1' }
];

export const mockMatchSimulate: MatchSimulateResultVO = {
  rule: { ruleCode: 'MR_CUSTOMER_V1', ruleName: '客户匹配规则-标准版', algorithm: 'WEIGHTED', exactThreshold: '95.00', suspectThreshold: '70.00' },
  sample: { legalName: '上海清视眼镜有限公司', creditCode: '91310000XXXXXXXXXX', address: '上海市静安区南京西路1688号', buScope: 'High End' },
  scanned: 3,
  distribution: { exact: 1, suspected: 1, below: 1 },
  candidates: [
    {
      oneId: 'GC-000128',
      legalName: '上海清视眼镜有限公司',
      creditCode: '91310000XXXXXXXXXX',
      buScope: 'High End',
      score: 100,
      result: 'EXACT',
      fields: [
        { field: 'credit_code', label: '统一社会信用代码', weight: 40, score: 100, detail: '全等匹配' },
        { field: 'legal_name', label: '客户名称', weight: 30, score: 100, detail: '标准化后一致' },
        { field: 'address', label: '经营地址', weight: 20, score: 100, detail: '相似度 100%' },
        { field: 'bu_scope', label: 'BU 归属', weight: 10, score: 100, detail: '同 BU' }
      ]
    }
  ]
};

export const mockDuplicateCandidate: DuplicateCandidateVO = {
  score: 88,
  verdict: 'Suspected Match',
  reason: '信用代码完全一致，经营地址相似度88%',
  incoming: {
    名称: '上海清视眼镜有限公司',
    信用代码: '91310000XXXXXXXXXX',
    地址: '南京西路XXX号'
  },
  existing: {
    'One ID': 'GC-000128',
    信用代码: '91310000XXXXXXXXXX',
    地址: '南京西路XX号'
  },
  existingOneId: 'GC-000128',
  acceptHint: '确认关联后，本申请将转为对已有客户 GC-000128（上海清视眼镜有限公司）的属性更新，不再新建 One ID；原申请中的差异字段将进入变更流程。',
  groups: [
    {
      name: '基本属性',
      fields: [
        { label: '客户名称', incoming: '上海清视眼镜有限公司', existing: '上海清视眼镜有限公司', status: 'MATCH' },
        { label: '统一社会信用代码', incoming: '91310000XXXXXXXXXX', existing: '91310000XXXXXXXXXX', status: 'MATCH' },
        { label: '经营地址', incoming: '上海市静安区南京西路1688号', existing: '上海市静安区南京西路88号', status: 'DIFF' },
        { label: '省 / 市', incoming: '上海市 / 上海市', existing: '上海市 / 上海市', status: 'MATCH' },
        { label: '客户类型', incoming: '眼镜零售门店', existing: '眼镜零售门店', status: 'MATCH' }
      ]
    },
    {
      name: '证照信息',
      fields: [
        { label: '营业执照有效期', incoming: '2020-03-12 至 2040-03-11', existing: '2016-11-02 至 2036-11-01', status: 'DIFF' },
        { label: '经营范围', incoming: '眼镜销售；第三类医疗器械经营', existing: '眼镜销售；第二类医疗器械经营', status: 'DIFF' },
        { label: '客户曾用名', incoming: '', existing: '清视眼镜商行', status: 'EMPTY' }
      ]
    },
    {
      name: '层级与业务',
      fields: [
        { label: '上级客户', incoming: '（待归位）', existing: 'Essilor 中国零售连锁', status: 'EMPTY' },
        { label: '所属 BU', incoming: 'High End', existing: 'Mainstream', status: 'DIFF' },
        { label: '产品线', incoming: '隐形眼镜', existing: '隐形眼镜', status: 'MATCH' }
      ]
    }
  ]
};

/** ---------------------------------- 批量导入 ---------------------------------- */
/** 1:1 取自原型 batch() 表格一行 */
export const mockImportJobs: ImportJobVO[] = [
  { jobId: 'IMP-001', fileName: 'Mainstream_Door_0915.xlsx', totalRows: 100, status: 'Waiting for Review', submittedAt: '2026-09-15 10:24', submittedBy: 'Business User' }
];

export const mockBatchResult: BatchResultVO = {
  jobId: 'IMP-001',
  exact: 42,
  suspected: 18,
  created: 26,
  review: 16,
  invalid: 14,
  routes: [
    { result: 'Exact', handling: '关联已有One ID', owner: 'System', detail: '查看42条' },
    { result: 'Suspected', handling: '进入人工治理', owner: 'BU/GC Steward', detail: '查看18条' },
    { result: 'Review', handling: '规则或业务复核', owner: 'BU Steward', detail: '查看16条' },
    { result: 'New', handling: '审批后生成One ID', owner: 'Steward', detail: '查看26条' },
    { result: 'Invalid', handling: '返回修复', owner: 'Business User', detail: '查看14条' }
  ]
};

export const mockImportTemplates: ImportTemplateVO[] = [
  {
    templateCode: 'TPL_DOOR_MAINSTREAM',
    name: 'Door_Mainstream_Lens',
    context: 'Mainstream·Lens·DMS+',
    version: 'v1.3',
    fieldCount: 5,
    status: 'Published',
    customerType: 'Door',
    bu: 'Mainstream',
    productLine: 'Lens',
    sourceSystem: 'DMS+'
  },
  {
    templateCode: 'TPL_DOOR_HIGHEND',
    name: 'Door_HighEnd_Frame',
    context: 'High End·Frame·Cloud',
    version: 'v1.2',
    fieldCount: 3,
    status: 'Draft',
    customerType: 'Door',
    bu: 'High End',
    productLine: 'Frame',
    sourceSystem: 'Cloud'
  }
];

export const mockTemplateMappings: TemplateMappingVO[] = [
  { sourceColumn: 'CustomerName', targetField: 'legal_name', transform: 'Trim + Normalize', errorStrategy: 'Reject Row' },
  { sourceColumn: 'CreditCode', targetField: 'credit_code', transform: 'Upper Case', errorStrategy: 'Reject Row' },
  { sourceColumn: 'Address', targetField: 'business_address', transform: 'Address Standardization', errorStrategy: 'Warning Row' }
];

/** ---------------------------------- 客户层级 ---------------------------------- */
export const mockHierarchyNodes: Record<string, HierarchyNodeVO> = {
  'CN-CUS-000001': {
    id: 'CN-CUS-000001',
    level: 'A3',
    type: 'Commercial Entity',
    label: 'A3 · 远见集团',
    name: '远见集团',
    oneId: 'CN-CUS-000001',
    payerId: '—',
    payerName: '—',
    childrenCount: 1,
    descendants: 3,
    parent: '无',
    path: '远见集团',
    ancestorIds: [],
    validity: '2026-01-01 → 9999-12-31',
    status: 'Active'
  },
  'CN-CUS-000021': {
    id: 'CN-CUS-000021',
    level: 'A2',
    type: 'Main Account',
    label: 'A2 · 远见华东法人',
    name: '远见华东法人',
    oneId: 'CN-CUS-000021',
    payerId: 'GC-PY-0091',
    payerName: 'GC-PY-0091',
    childrenCount: 2,
    descendants: 2,
    parent: 'CN-CUS-000001',
    parentName: '远见集团',
    path: '远见集团 / 远见华东法人',
    ancestorIds: ['CN-CUS-000001'],
    validity: '2026-01-01 → 9999-12-31',
    status: 'Active'
  },
  'CN-CUS-000125': {
    id: 'CN-CUS-000125',
    level: 'A1',
    type: 'Door',
    label: 'A1 · 上海优视门店',
    name: '上海优视门店',
    oneId: 'CN-CUS-000125',
    payerId: 'GC-PY-0092',
    payerName: 'GC-PY-0092',
    childrenCount: 0,
    descendants: 0,
    parent: 'CN-CUS-000021',
    parentName: '远见华东法人',
    path: '远见集团 / 远见华东法人 / 上海优视门店',
    ancestorIds: ['CN-CUS-000001', 'CN-CUS-000021'],
    validity: '2026-01-01 → 9999-12-31',
    status: 'Active'
  },
  'CN-CUS-000126': {
    id: 'CN-CUS-000126',
    level: 'A1',
    type: 'Door',
    label: 'A1 · 苏州新锐门店',
    name: '苏州新锐门店',
    oneId: 'CN-CUS-000126',
    payerId: 'GC-PY-0092',
    payerName: 'GC-PY-0092',
    childrenCount: 0,
    descendants: 0,
    parent: 'CN-CUS-000021',
    parentName: '远见华东法人',
    path: '远见集团 / 远见华东法人 / 苏州新锐门店',
    ancestorIds: ['CN-CUS-000001', 'CN-CUS-000021'],
    validity: '2026-01-01 → 9999-12-31',
    status: 'Active'
  },
  // 第二个分支：Mainstream · 明眸集团
  'CN-CUS-000101': {
    id: 'CN-CUS-000101',
    level: 'A3',
    type: 'Commercial Entity',
    label: 'A3 · 明眸集团',
    name: '明眸集团',
    oneId: 'CN-CUS-000101',
    payerId: '—',
    payerName: '—',
    childrenCount: 1,
    descendants: 2,
    parent: '无',
    path: '明眸集团',
    ancestorIds: [],
    validity: '2026-01-01 → 9999-12-31',
    status: 'Active'
  },
  'CN-CUS-000121': {
    id: 'CN-CUS-000121',
    level: 'A2',
    type: 'Main Account',
    label: 'A2 · 明眸华北法人',
    name: '明眸华北法人',
    oneId: 'CN-CUS-000121',
    payerId: 'GC-PY-0093',
    payerName: 'GC-PY-0093',
    childrenCount: 1,
    descendants: 1,
    parent: 'CN-CUS-000101',
    parentName: '明眸集团',
    path: '明眸集团 / 明眸华北法人',
    ancestorIds: ['CN-CUS-000101'],
    validity: '2026-01-01 → 9999-12-31',
    status: 'Active'
  },
  'GC-000245': {
    id: 'GC-000245',
    level: 'A1',
    type: 'Door',
    label: 'A1 · 北京明眸门店',
    name: '北京明眸门店',
    oneId: 'GC-000245',
    payerId: 'GC-PY-0093',
    payerName: 'GC-PY-0093',
    childrenCount: 0,
    descendants: 0,
    parent: 'CN-CUS-000121',
    parentName: '明眸华北法人',
    path: '明眸集团 / 明眸华北法人 / 北京明眸门店',
    ancestorIds: ['CN-CUS-000101', 'CN-CUS-000121'],
    validity: '2026-01-01 → 9999-12-31',
    status: 'Active'
  }
};

export const mockHierarchy: HierarchyNodeVO[] = [
  {
    ...mockHierarchyNodes['CN-CUS-000001'],
    children: [
      {
        ...mockHierarchyNodes['CN-CUS-000021'],
        children: [mockHierarchyNodes['CN-CUS-000125'], mockHierarchyNodes['CN-CUS-000126']]
      }
    ]
  },
  {
    ...mockHierarchyNodes['CN-CUS-000101'],
    children: [
      {
        ...mockHierarchyNodes['CN-CUS-000121'],
        children: [mockHierarchyNodes['GC-000245']]
      }
    ]
  }
];

/**
 * 待归位主数据：已审批通过成为主数据（active），但尚未挂到 A3-A2-A1 树上。
 * 对应「客户层级 → 待归位主数据」区；归位后即进入 mockHierarchy 的层级树。
 */
export const mockHierarchyUnassigned: CmdHierarchyUnassignedRow[] = [
  {
    oneId: 'CN-CUS-000129',
    legalName: '上海优视浦东门店',
    buScope: 'High End',
    customerStatus: 'active',
    sourceSystem: 'OCR',
    dqScore: 92,
    approvedTime: '2026-09-18T10:20:00',
    registered: true,
    nodeCode: 'UN-000129',
    suggestedLevel: 'A1',
    remark: '已登记为待归位节点，等待 Data Steward 归位'
  },
  {
    oneId: 'GC-000128',
    legalName: '上海清视眼镜有限公司',
    buScope: 'Mainstream',
    customerStatus: 'active',
    sourceSystem: 'Excel',
    dqScore: 86,
    approvedTime: '2026-09-17T16:05:00',
    registered: true,
    nodeCode: 'UN-000128',
    suggestedLevel: 'A1',
    remark: '已登记为待归位节点，等待 Data Steward 归位'
  },
  {
    oneId: 'GC-000188',
    legalName: '成都明视眼镜有限公司',
    buScope: 'High End',
    customerStatus: 'active',
    sourceSystem: 'API',
    dqScore: 78,
    approvedTime: '2026-09-16T09:40:00',
    registered: false,
    nodeCode: '',
    suggestedLevel: 'A2',
    remark: '批准为主数据，尚未登记层级节点；归位后进入 A3-A2-A1 树'
  }
];

/** ---------------------------------- 变更 / 停用 ---------------------------------- */
export const mockChangeRequests: ChangeRequestVO[] = [
  {
    requestId: 'CHG-0018',
    oneId: 'GC-000128',
    customerName: '上海清视眼镜有限公司',
    changeType: 'Update',
    content: '经营地址',
    status: 'Under Review',
    submittedAt: '2026-09-15 10:10',
    submittedBy: 'Business User'
  },
  {
    requestId: 'DEL-0007',
    oneId: 'GC-000245',
    customerName: '北京明眸商业有限公司',
    changeType: 'Deactivate',
    content: '24个月无交易 · 人工填报',
    status: 'Inactive',
    submittedAt: '2026-09-15 09:02',
    submittedBy: 'Data Steward · BU'
  }
];

export const mockChangeDiffs: ChangeDiffVO[] = [
  { field: '经营地址', before: '南京西路XX号', after: '南京西路888号' },
  { field: 'One ID', before: 'GC-000128', after: 'GC-000128' },
  { field: '状态', before: 'Active v5', after: 'Active v6' }
];

export const mockChangeTrail: ApprovalTrailVO[] = [
  { time: '09-15 10:10', role: 'Business User', action: '提交迁址申请', result: 'Submitted' },
  { time: '09-15 11:20', role: 'BU Steward', action: '验证附件和地址', result: 'Approved' }
];

export const mockDeactivateResult: DeactivateResultVO = {
  businessView: [
    { key: 'One ID', value: 'GC-000245' },
    { key: 'Status', value: 'Inactive' },
    { key: 'Inactive Reason', value: 'No transaction 24M' },
    { key: 'Physical Delete', value: 'No' }
  ],
  dbRecords: [
    "cmd_customer.status = 'inactive'",
    "cmd_customer.del_flag = '0'  -- 记录保留，未物理删除",
    "cmd_customer.one_id = 'GC-000245'  -- One ID 不回收",
    'cmd_customer.version_no = 7  -- 停用同样生成新版本',
    "cmd_change_request.status = 'EFFECTIVE'",
    "audit_event.action = 'DEACTIVATE'  -- 审计留痕保留"
  ],
  versions: [
    {
      versionNo: 7,
      changeType: 'DEACTIVATE',
      changeReason: '24个月无交易 · 人工填报',
      changedFields: 'status',
      status: 'effective',
      sourceSystem: 'CLOUD',
      createTime: '2026-09-15 09:02'
    }
  ]
};

/** ---------------------------------- 审批 ---------------------------------- */
export const mockApprovalFlows: Record<string, ApprovalFlowVO> = {
  approvalHE: {
    key: 'approvalHE',
    title: 'High End审批实例',
    steps: ['Business User', 'Regional Sales Head', 'BU Steward', 'Cloud Status', 'GC Steward*'],
    remark: '*仅Cross-BU或重大治理场景进入GC决策',
    nodes: [
      { node: 1, role: 'Business User', content: '上传营业执照并提交', status: 'Done' },
      { node: 2, role: 'Regional Sales Head', content: '校验High End业务字段', status: 'Done' },
      { node: 3, role: 'BU Steward', content: 'GC Core、DQ、重复初审', status: 'Current' },
      { node: 4, role: 'Cloud', content: '第三方审批状态回传', status: 'Pending' }
    ]
  },
  approvalMS: {
    key: 'approvalMS',
    title: 'Mainstream审批实例',
    steps: ['DMS+ Input', 'Technical Validation', 'BU Steward', 'GC Steward*', 'Return One ID'],
    remark: '*Cross-BU或脏数据冲突时进入GC治理',
    nodes: [
      { node: 1, role: 'DMS+', content: '客户数据进入CMD', status: 'Done' },
      { node: 2, role: 'System', content: '技术校验发现地址异常', status: 'Done' },
      { node: 3, role: 'BU Steward', content: '退回业务修正脏数据', status: 'Returned' },
      { node: 4, role: 'GC Steward', content: 'Cross-BU时人工决策', status: 'Not Started' }
    ]
  }
};

export const mockApprovalInstances: ApprovalInstanceVO[] = [
  { instanceId: 'WF-HE-0012', bu: 'High End', scenario: 'Frame Customer Create', currentNode: 'BU Steward Review', sla: '1d 4h', status: 'In Progress' },
  { instanceId: 'WF-MS-0009', bu: 'Mainstream', scenario: 'DMS+ Dirty Data', currentNode: 'Return for Correction', sla: '6h', status: 'Returned' }
];

/**
 * 侧边导航「数据统计」角标（演示数据，key = 菜单 id，与后端 /cmd/nav/badge 结构一致）
 * 仅 VITE_CMD_POC_MOCK=true 时使用；真实后端运行时数字由业务表实时聚合。
 */
export const mockNavBadges: Record<string, Record<string, number>> = {
  business: { dash: 12, customers: 4, batch: 1, hier: 7, change: 2, flowWorkitem: 2, flowDone: 3 },
  bu: { dash: 17, approval: 8, customers: 4, hier: 7, batch: 1, change: 2, flowWorkitem: 2, flowDone: 3 },
  gc: { dash: 14, approval: 5, customers: 4, hier: 7, batch: 1, change: 2, flowWorkitem: 2, flowDone: 3, audit: 6 },
  admin: { dash: 5, integration: 2, flowWorkitem: 2, flowDone: 3, audit: 6 },
  audit: { dash: 9, audit: 6, customers: 4, hier: 7, flowWorkitem: 2, flowDone: 3 }
};

/**
 * 治理与审批合并工作台（原型 2.2「治理与审批合并版」）
 * BU / GC 两套数据，Tab 与退回 / 已处理通过 detailType 映射详情模板。
 */
export const mockApprovalKpis: Record<'bu' | 'gc', ApprovalKpiVO[]> = {
  bu: [
    { label: '待我处理', value: 8, hint: 'Demo data' },
    { label: '临近SLA', value: 3, hint: 'Demo data' },
    { label: '已超时', value: 1, hint: 'Demo data' },
    { label: '退回待补充', value: 2, hint: 'Demo data' },
    { label: '本周已处理', value: 18, hint: 'Demo data' }
  ],
  gc: [
    { label: '待我决策', value: 5, hint: 'Demo data' },
    { label: '临近SLA', value: 3, hint: 'Demo data' },
    { label: '已超时', value: 1, hint: 'Demo data' },
    { label: '退回待补充', value: 2, hint: 'Demo data' },
    { label: '本周已处理', value: 18, hint: 'Demo data' }
  ]
};

export const mockApprovalTasks: Record<'bu' | 'gc', ApprovalTaskVO[]> = {
  bu: [
    { taskId: 'REQ-0182', customerName: '上海优视眼镜有限公司', taskType: '客户创建', source: '单条申请', bu: 'High End', dq: 'Warning', match: 'Suspected', sla: '3h', risk: 'High', detailType: 'create' },
    { taskId: 'HIER-BU-0018', customerName: '上海优视门店', taskType: '层级关系', source: '业务申请', bu: 'High End', dq: 'Pass', match: '—', sla: '8h', risk: 'Medium', detailType: 'hier' },
    { taskId: 'DQ-BU-0041', customerName: '杭州朗视门店', taskType: 'DQ异常', source: '规则触发', bu: 'High End', dq: 'Block', match: '—', sla: '6h', risk: 'High', detailType: 'create' },
    { taskId: 'MATCH-BU-0026', customerName: '苏州新视野眼镜有限公司', taskType: '疑似重复', source: '批量导入', bu: 'High End', dq: 'Pass', match: 'Suspected', sla: '4h', risk: 'High', detailType: 'create' },
    { taskId: 'BATCH-0091', customerName: 'High End门店批量', taskType: '批量治理', source: 'Import Job', bu: 'High End', dq: '16 Review', match: '18 Suspected', sla: '12h', risk: 'Medium', detailType: 'batch' }
  ],
  gc: [
    { taskId: 'GC-DEC-0003', customerName: '上海清视眼镜有限公司', taskType: '跨BU合并', source: 'BU升级', bu: 'Cross-BU', dq: 'Pass', match: 'Suspected', sla: '4h', risk: 'High', detailType: 'gcdup' },
    { taskId: 'HIER-GC-0003', customerName: '跨BU A2/A3关系', taskType: '层级关系', source: 'BU升级', bu: 'Cross-BU', dq: 'Pass', match: '—', sla: '8h', risk: 'Medium', detailType: 'gchier' },
    { taskId: 'MATCH-GC-0012', customerName: '广州锐目眼镜门店', taskType: '多候选One ID', source: '系统规则', bu: 'Cross-BU', dq: 'Pass', match: 'Multiple', sla: '5h', risk: 'High', detailType: 'gcdup' },
    { taskId: 'MERGE-GC-0008', customerName: '北京明眸商业有限公司', taskType: '合并审批', source: '月度Review', bu: 'Cross-BU', dq: 'Pass', match: 'Suspected', sla: '9h', risk: 'High', detailType: 'gcdup' }
  ]
};

export const mockApprovalReturned: Record<'bu' | 'gc', ApprovalTaskVO[]> = {
  bu: [
    { taskId: 'ESC-BU-0005', customerName: 'Mainstream跨BU候选', taskType: '升级GC', source: 'BU升级', bu: 'Mainstream', dq: 'Pass', match: 'Suspected', sla: '1d', risk: 'Medium', detailType: 'create' }
  ],
  gc: [
    { taskId: 'RET-GC-0002', customerName: '跨BU证据待补充', taskType: '退回BU', source: 'GC退回', bu: 'Cross-BU', dq: '—', match: '—', sla: '1d', risk: 'Medium', detailType: 'gchier' }
  ]
};

export const mockApprovalDone: Record<'bu' | 'gc', ApprovalTaskVO[]> = {
  bu: [
    { taskId: 'BU-DONE-038', customerName: '成都视界客户创建', taskType: '已批准', source: '审批任务', bu: 'High End', dq: 'Pass', match: 'New', sla: 'Done', risk: 'Low', detailType: 'create' }
  ],
  gc: [
    { taskId: 'GC-DONE-017', customerName: '华东渠道客户合并', taskType: '已合并', source: '治理决策', bu: 'Cross-BU', dq: 'Pass', match: 'Merged', sla: 'Done', risk: 'Low', detailType: 'gcdup' }
  ]
};

export const mockApprovalDetail: Record<string, ApprovalTaskDetailVO> = {
  create: {
    id: 'REQ-0182',
    name: '上海优视眼镜有限公司',
    scene: '客户创建',
    submitter: 'Business User · High End',
    currentNode: 'BU Steward Review',
    sla: '3h',
    dq: '1 Warning · 0 Block',
    duplicate: '1个Suspected候选',
    evidence: '营业执照OCR已完成；信用代码一致；地址相似度需业务确认。',
    decisions: ['批准本BU申请', '确认非重复', '升级GC Scope', '退回修改'],
    actions: [
      { key: 'approve', label: '批准', type: 'primary' },
      { key: 'escalate', label: '升级GC', type: 'warning' },
      { key: 'reject', label: '退回修改', type: 'danger' }
    ]
  },
  hier: {
    id: 'HIER-BU-0018',
    name: '上海优视门店',
    scene: '层级关系',
    submitter: 'Business User · High End',
    currentNode: 'BU Steward Review',
    sla: '8h',
    dq: 'Loop Check Pass',
    duplicate: '无',
    evidence: 'A3 → A2 → A1有效；Payer GC-PY-0092；未发现循环路径。',
    decisions: ['批准本BU申请', '确认非重复', '升级GC Scope', '退回修改'],
    actions: [
      { key: 'approve', label: '批准', type: 'primary' },
      { key: 'escalate', label: '升级GC', type: 'warning' },
      { key: 'reject', label: '退回修改', type: 'danger' }
    ]
  },
  batch: {
    id: 'BATCH-0091',
    name: 'High End门店批量',
    scene: '批量治理',
    submitter: 'Business User · High End',
    currentNode: 'BU Steward Review',
    sla: '12h',
    dq: '16 Review / 14 Invalid',
    duplicate: '18 Suspected',
    evidence: '有效行继续治理；异常行可退回并下载错误明细。',
    decisions: ['批准本BU申请', '确认非重复', '升级GC Scope', '退回修改'],
    actions: [
      { key: 'approve', label: '批准', type: 'primary' },
      { key: 'reject', label: '退回修改', type: 'danger' }
    ]
  },
  gcdup: {
    id: 'GC-DEC-0003',
    name: '上海清视眼镜有限公司',
    scene: 'Cross-BU Duplicate',
    submitter: 'BU Data Steward · High End',
    currentNode: 'GC Scope Decision',
    sla: '4h',
    dq: 'GC Core Pass',
    duplicate: 'High End与Mainstream候选',
    evidence: '信用代码一致；经营地址存在差异；需决定关联已有One ID或确认新建One ID。',
    decisions: ['关联已有One ID', '确认创建新One ID', '确认合并', '排除重复'],
    actions: [
      { key: 'link', label: '关联已有One ID', type: 'primary' },
      { key: 'confirmNew', label: '确认创建新One ID', type: 'success' },
      { key: 'merge', label: '确认合并', type: 'warning' },
      { key: 'exclude', label: '排除重复', type: 'danger' }
    ]
  },
  gchier: {
    id: 'HIER-GC-0003',
    name: '跨BU A2/A3关系',
    scene: '层级关系',
    submitter: 'BU Data Steward · Cross-BU',
    currentNode: 'GC Scope Decision',
    sla: '8h',
    dq: 'Loop Check Pass',
    duplicate: '无',
    evidence: 'A3 → A2 → A1有效；Payer GC-PY-0092；未发现循环路径。',
    decisions: ['关联已有One ID', '确认创建新One ID', '确认合并', '排除重复'],
    actions: [
      { key: 'approve', label: '批准', type: 'primary' },
      { key: 'reject', label: '退回修改', type: 'danger' }
    ]
  }
};

/** ---------------------------------- 流程跟踪 ---------------------------------- */
/**
 * 泳道图场景一模板（总设计：单条客户创建，发现 BU 匹配重复并关联已有 One ID）。
 * 7 阶段 × 11 步，与后端 CmdFlowTraceServiceImpl.createSceneTemplate 保持一致。
 */
const FLOW_SCENE1_STEPS: Array<Pick<FlowTraceStepVO, 'phase' | 'phaseName' | 'lane' | 'nodeCode' | 'nodeName' | 'nodeType' | 'note'>> = [
  { phase: 1, phaseName: '发起', lane: 'Business User', nodeCode: 'APPLY', nodeName: '创建客户申请', nodeType: 'MANUAL', note: '选择客户类型、BU、产品线与来源系统' },
  { phase: 2, phaseName: '数据准备', lane: 'Business User', nodeCode: 'INPUT', nodeName: '录入与附件', nodeType: 'MANUAL', note: 'OCR Core + 营业执照上传' },
  { phase: 2, phaseName: '数据准备', lane: '系统自动处理', nodeCode: 'OCR', nodeName: 'OCR 与智能补全', nodeType: 'AUTO', note: '提取工商名称、注册代码与地址标准化' },
  { phase: 3, phaseName: '自动校验', lane: '系统自动处理', nodeCode: 'DQ', nodeName: '技术与业务 DQ', nodeType: 'AUTO', note: '必填、格式、值域、GC Core、合规校验' },
  { phase: 4, phaseName: '匹配分流', lane: '系统自动处理', nodeCode: 'DUP', nodeName: 'Duplicate Check', nodeType: 'GATEWAY', note: 'SUSPECT 进入人工治理' },
  { phase: 5, phaseName: '人工治理', lane: 'Data Steward BU Scope', nodeCode: 'BU_REVIEW', nodeName: 'BU Scope 初审', nodeType: 'MANUAL', note: '确认 Same-BU 或升级 Cross-BU' },
  { phase: 5, phaseName: '人工治理', lane: 'Data Steward GC Scope', nodeCode: 'GC_REVIEW', nodeName: 'GC Scope 决策', nodeType: 'MANUAL', note: '决定关联已有或新创新数据 One ID' },
  { phase: 6, phaseName: '审批发布', lane: '系统自动处理', nodeCode: 'RESULT', nodeName: '生成 / 关联结果', nodeType: 'AUTO', note: '建立 BU 本地组织映射' },
  { phase: 6, phaseName: '审批发布', lane: 'Platform Admin', nodeCode: 'PUBLISH', nodeName: '发布前下游', nodeType: 'MANUAL', note: '失败即 Retry / Resubmit' },
  { phase: 7, phaseName: '追踪审计', lane: 'Platform Admin', nodeCode: 'TRACE', nodeName: '运行追踪', nodeType: 'AUTO', note: '任务状态、失败原因、重发布记录' },
  { phase: 7, phaseName: '追踪审计', lane: 'Auditor（只读）', nodeCode: 'AUDIT', nodeName: '审计查询', nodeType: 'AUTO', note: 'Before / After 审查证据' }
];

/**
 * 分步骤明细（mock）：结构 1:1 对齐后端 `CmdFlowTraceServiceImpl#buildStepDetail`，
 * 让「纯 mock」与「live 后端」两种模式渲染同一套 UI（FlowTraceDetail 不做分支）。
 */
const buildMockStepDetails = (taskNo: string, isGc: boolean): FlowStepDetailVO[] => {
  const f = (label: string, value: string, tone?: string): FlowStepFieldVO => ({ label, value, tone });
  const t = (title: string, columns: string[], rows: string[][]): FlowStepTableVO => ({ title, columns, rows });

  const NAME = '上海优视眼镜有限公司';
  const CREDIT = '91310115MA1K35Q71N';
  const oneId = isGc ? 'GC-000128' : 'GC-52A19C3D';

  const apply: FlowStepDetailVO = {
    nodeCode: 'APPLY',
    summary: `王视野 于 2026-09-15 10:12 提交「${NAME}」的${isGc ? '跨BU合并' : '客户创建'}申请`,
    fields: [
      f('申请编号', taskNo),
      f('客户主题', NAME),
      f('业务类型', isGc ? '跨BU合并' : '客户创建'),
      f('归属 BU', isGc ? 'Cross-BU' : 'High End · 镜片'),
      f('风险等级', 'High', 'danger'),
      f('申请人', '王视野'),
      f('提交时间', '2026-09-15 10:12'),
      f('场景流程', isGc ? '客户合并审批流（cmd_customer_merge）' : '客户创建审批流（cmd_customer_create）')
    ],
    notes: [
      '提交同时生成 One ID 与首版本快照（cmd_customer / cmd_customer_version），并写入 SUBMIT 轨迹。',
      'OCR / DQ / Duplicate Check 为系统自动节点，随提交一次执行完，不占用流程引擎用户任务。'
    ]
  };

  const input: FlowStepDetailVO = {
    nodeCode: 'INPUT',
    summary: '录入客户主档核心字段并上传营业执照等证明材料，提交时完成落库与首版本快照',
    fields: [
      f('法定名称', NAME),
      f('英文名称', 'Shanghai Youshi Optical Co., Ltd.'),
      f('统一社会信用代码', CREDIT),
      f('税号', CREDIT),
      f('客户类型', 'SoldTo'),
      f('产品线', 'High End'),
      f('国家 / 省 / 市', '中国 / 上海市 / 上海市'),
      f('注册地址', '上海市浦东新区张江路 88 号'),
      f('联系人', '—', 'warning'),
      f('联系电话', '—', 'warning'),
      f('来源系统', 'OCR')
    ],
    tables: [
      t('附件清单（cmd_attachment，biz_id = One ID）', ['文件名', '分类', 'OCR 状态', '类型', '大小(字节)', '上传时间'], [
        ['license_01.png', 'BUSINESS_LICENSE', 'DONE', 'png', '182340', '2026-09-15 10:12']
      ])
    ],
    notes: ['附件落 cmd_attachment（biz_type=CUSTOMER，biz_id=One ID），营业执照是 OCR 与统一社会信用代码校验的输入。']
  };

  const ocr: FlowStepDetailVO = {
    nodeCode: 'OCR',
    summary: '营业执照识别完成，抽取客户法定名称 / 统一社会信用代码 / 注册地址 / 省份 / 城市 并回填主档',
    fields: [
      f('识别引擎', 'POC 预置识别（CmdOcrServiceImpl）'),
      f('识别字段数', '5'),
      f('识别结果来源', '主档回填值（未落识别明细表）')
    ],
    tables: [
      t('识别字段与回填结果', ['字段', '识别值', '回填状态', '置信度'], [
        ['客户法定名称', NAME, '已回填主档', '98.60'],
        ['统一社会信用代码', CREDIT, '已回填主档', '96.20'],
        ['注册地址', '上海市浦东新区张江路 88 号', '已回填主档', '88.40'],
        ['省份', '上海市', '已回填主档', '99.10'],
        ['城市', '上海市', '已回填主档', '99.10']
      ])
    ],
    notes: [
      'POC 环境 OCR 按文件名匹配预置结果（license_0x.png），因此 cmd_ocr_result 为空；上表为识别后实际写入主档的字段值。',
      '置信度低于阈值的字段由 OCR Core 置 needs_review=Y，在申请页提示确认后才允许提交。'
    ]
  };

  const dq: FlowStepDetailVO = {
    nodeCode: 'DQ',
    summary: 'DQ 质量分 52.00（等级 D）：命中 5 项扣分，共扣 48 分',
    fields: [
      f('DQ 总分', '52.00', 'warning'),
      f('质量等级', 'D', 'danger'),
      f('扣分合计', '-48'),
      f('路由结论', '存在扣分项，进入人工治理时需重点复核')
    ],
    tables: [
      t('DQ 检查项明细（dq_rule 规则集）', ['检查维度', '规则', '当前值', '结果', '扣分'], [
        ['客户法定名称', '必填', NAME, '通过', '0'],
        ['统一社会信用代码', '必填且格式合法', CREDIT, '通过', '0'],
        ['注册地址', '地址标准化', '相似度 0.72（未达 0.85 阈值）', '扣分', '-8'],
        ['Payer 编码', '必填（Business Blocking）', '缺失', '扣分', '-12'],
        ['一致性', '与来源系统（OCR）一致', '2 个字段不一致', '扣分', '-10'],
        ['唯一性', '强制合并判定', '1 个 SUSPECT 候选待判', '扣分', '-10'],
        ['联系人 + 联系电话', '完整性', '均缺失', '扣分', '-8']
      ])
    ],
    notes: [
      '分值与等级取 POC 演示常量（52 / D，对应 cmd_customer.dq_score），检查项对应 dq_rule 的 Blocking / Warning / Info 三档。',
      '提交时的 POC 确定性打分口径：缺信用代码 -12、缺地址 -8、缺省市 -4、缺联系人 -4、缺电话 -4（≥90→A、≥75→B、≥60→C、<60→D）。'
    ]
  };

  const dup: FlowStepDetailVO = {
    nodeCode: 'DUP',
    summary: '匹配结论 SUSPECTED，候选 1 条',
    fields: [
      f('匹配状态', 'SUSPECTED', 'warning'),
      f('疑似重复标记', '是'),
      f('路由结论', '疑似重复：转入 BU Scope 人工治理，确认 Same-BU 或升级 Cross-BU')
    ],
    tables: [
      t('匹配候选（cmd_match_candidate）', ['One ID', '法定名称', '统一社会信用代码', '归属 BU', '匹配分', '判定'], [
        ['GC-000128', '清视眼镜（High End）', CREDIT, 'High End', '0.87', '最佳候选']
      ])
    ],
    notes: [
      'SUSPECTED 候选必须经 BU Scope 初审确认（Same-BU 关联）或升级 GC 做跨 BU 决策，不允许自动合并。',
      '字段级比对（名称 / 信用代码 / 地址 / 联系人）写入 field_compare_json，作为人工判断的证据。'
    ]
  };

  const buReview: FlowStepDetailVO = {
    nodeCode: 'BU_REVIEW',
    summary: 'Same-BU 证据充分，升级 Cross-BU 由 GC Steward 决策',
    fields: [
      f('节点', 'BU Scope 初审'),
      f('泳道', 'Data Steward BU Scope'),
      f('办理人', 'BU_STEWARD'),
      f('办理角色', 'BU_STEWARD'),
      f('决策时限（SLA）', '2026-09-17 10:12'),
      f('到达时间', '2026-09-15 10:14'),
      f('执行状态', '已完成', 'success'),
      f('会签方式', '或签（任一办理人通过）'),
      f('适用范围', 'BU'),
      f('规则默认角色', 'BU_STEWARD')
    ],
    tables: [
      t('BU 初审动作（cmd_approval_action）', ['时间', '动作', '操作人', '角色', '状态流转', '意见'], [
        ['2026-09-15 10:30:00', '初审认领', 'BU Steward', 'BU_STEWARD', 'PENDING → PENDING', '—'],
        ['2026-09-16 09:30:00', '审批通过', 'BU Steward', 'BU_STEWARD', 'PENDING → APPROVED', 'Same-BU 证据充分，升级 Cross-BU']
      ])
    ],
    notes: ['BU 初审结果：确认 Same-BU（关联本地组织）/ 升级 GC（Cross-BU）/ 退回补充证据。']
  };

  const gcReview: FlowStepDetailVO = {
    nodeCode: 'GC_REVIEW',
    summary: isGc ? '关联已有 One ID：GC-000128' : '等待 GC Steward 做跨 BU 证据核对与 One ID 决策',
    fields: [
      f('节点', 'GC Scope 决策'),
      f('泳道', 'Data Steward GC Scope'),
      f('办理人', 'GC_STEWARD'),
      f('办理角色', 'GC_STEWARD'),
      f('决策时限（SLA）', '2026-09-17 10:12'),
      f('到达时间', '2026-09-16 09:30'),
      f('执行状态', isGc ? '已完成' : '进行中', isGc ? 'success' : 'warning'),
      f('会签方式', '或签（任一办理人通过）'),
      f('适用范围', 'GC'),
      f('规则默认角色', 'GC_STEWARD')
    ],
    tables: isGc
      ? [
          t('GC 决策动作（cmd_approval_action）', ['时间', '动作', '操作人', '角色', '状态流转', '意见'], [
            ['2026-09-16 15:20:00', '关联已有 One ID', 'GC Steward', 'GC_STEWARD', 'APPROVED → APPROVED', '关联已有 One ID：GC-000128']
          ])
        ]
      : [],
    notes: ['GC 决策结果：关联已有 One ID（LINK）/ 新创主数据（CREATE_NEW）/ 确认合并（MERGE）。']
  };

  const result: FlowStepDetailVO = {
    nodeCode: 'RESULT',
    summary: `生成 / 关联结果：One ID ${oneId}，主数据状态 active`,
    fields: [
      f('One ID', oneId),
      f('主数据状态', 'active', 'success'),
      f('质量等级', 'D', 'danger'),
      f('匹配状态', 'SUSPECTED'),
      f('版本号', '1'),
      f('生效时间', '2026-09-16 15:20'),
      f('流程实例 ID', '1801')
    ],
    tables: [
      t('来源系统编码映射（cmd_legacy_mapping）', ['来源系统', '来源编码', '来源名称', 'BU', '类型', '状态'], [
        ['Cloud', 'HE-NEW-0231', '清视眼镜（High End 新建）', 'High End', 'LEGACY', '有效'],
        ['DMS+', 'MS-CN-88421', '清视眼镜（Mainstream 门店）', 'Mainstream', 'LEGACY', '有效']
      ])
    ],
    notes: ['批准后调用层级服务登记节点（hierarchy_type=UNASSIGNED 占位），归位由 Steward 在「客户层级」页完成。']
  };

  const publish: FlowStepDetailVO = {
    nodeCode: 'PUBLISH',
    summary: '按主数据发布契约，把 One ID 与编码映射下发到下游系统（DMS+ / SAP / Cloud）',
    fields: [f('下发端点', 'DMS+ 门店同步 / SAP 主数据回写 / Cloud 平台客户接入')],
    tables: [
      t('集成通道运行记录（int_run，按通道最近 5 次）', ['运行编号', '端点', '目标系统', '方向', '状态', '成功/总数', '失败', '重试', '耗时(ms)', '开始时间'], [
        ['RUN-20260918-0001', 'DMS+ 门店同步', 'DMS+', 'Outbound', 'SUCCESS', '120 / 120', '0', '1', '200000', '2026-09-18 02:00'],
        ['RUN-20260918-0002', 'SAP 主数据回写', 'SAP', 'Outbound', 'FAILED', '0 / 1', '1', '3', '30000', '2026-09-18 08:25'],
        ['RUN-20260918-0003', 'Cloud 平台客户接入', 'Cloud', 'Inbound', 'RETRYING', '41 / 45', '4', '2', '—', '2026-09-18 09:15']
      ])
    ],
    notes: [
      'int_run 为通道级运行记录（POC 未建客户级下发明细 int_message），用于说明下发通路的实时健康度。',
      '下发失败不阻塞主流程，由 Retry / Resubmit 机制重投；可在「集成监控」页下钻失败原因并手动重试。'
    ]
  };

  const trace: FlowStepDetailVO = {
    nodeCode: 'TRACE',
    summary: '按 One ID 串联的步骤执行总账共 6 条，任务状态 APPROVED',
    fields: [
      f('One ID', oneId),
      f('任务编号', taskNo),
      f('流程实例 ID', '1801'),
      f('引擎状态镜像', '8'),
      f('当前节点', '结束')
    ],
    tables: [
      t('步骤执行日志（cmd_workflow_step_log）', ['序号', '类型', '节点', '动作', '操作人', '角色', '时间', '意见'], [
        ['1', 'SUBMIT', '创建客户申请', '提交申请', '王视野', 'BU_STEWARD', '2026-09-15 10:12:00', '—'],
        ['2', 'SYSTEM', '数据装配', '系统自动完成', '系统自动处理', 'SYS', '2026-09-15 10:12:01', '系统自动完成'],
        ['3', 'SYSTEM', 'OCR 与智能补全', '营业执照识别', '系统自动处理', 'SYS', '2026-09-15 10:12:02', '营业执照识别完成，关键字段已抽取'],
        ['4', 'SYSTEM', '技术与业务 DQ', '自动校验', '系统自动处理', 'SYS', '2026-09-15 10:12:03', 'DQ 质量分=52，1 Warning'],
        ['5', 'SYSTEM', 'Duplicate Check', '匹配分派', '系统自动处理', 'SYS', '2026-09-15 10:12:04', '匹配结论=SUSPECTED'],
        ['6', 'BUSINESS', 'BU Scope 初审', '审批通过', 'BU Steward', 'BU_STEWARD', '2026-09-16 09:30:00', 'Same-BU 证据充分，升级 Cross-BU']
      ])
    ],
    notes: ['cmd_workflow_step_log 以 One ID 为追溯主键，是 CMD / Warm-Flow / 下游对账时对齐进度的唯一依据。']
  };

  const audit: FlowStepDetailVO = {
    nodeCode: 'AUDIT',
    summary: '该 One ID 相关审计事件共 3 条（含变更、合并与集成事件）',
    fields: [],
    tables: [
      t('审计事件（audit_event，按 One ID）', ['事件编号', '类型', '事件', '操作人', '结果', '风险', '时间'], [
        ['AE-20260915-0001', 'CREATE', '创建客户：上海优视眼镜有限公司', '王视野', 'SUCCESS', 'Medium', '2026-09-15 10:12'],
        ['AE-20260916-0003', 'MERGE', '合并客户：成都明视眼镜 → GC-000128', 'GC Steward', 'SUCCESS', 'High', '2026-09-16 15:20'],
        ['AE-20260917-0004', 'UPDATE', '变更客户经营地址', '张清', 'SUCCESS', 'Medium', '2026-09-17 11:05']
      ])
    ],
    notes: ['Before / After 快照存 audit_event.before_json / after_json，审计数据只增不改（del_flag 逻辑保留），Auditor 只读。']
  };

  return [apply, input, ocr, dq, dup, buReview, gcReview, result, publish, trace, audit];
};

/**
 * 流程跟踪演示数据：按任务 detailType 推导当前节点，
 * 已完成步骤补系统轨迹时间，与后端实时推导口径一致。
 */
export const buildMockFlowTrace = (taskNo: string, detailType = 'create'): FlowTraceVO => {
  const isGc = detailType.startsWith('gc');
  const isDone = detailType === 'done';
  const currentNode = isDone ? '_DONE_' : isGc ? 'GC_REVIEW' : 'BU_REVIEW';

  // 已完成节点的时间线（演示用固定时刻）
  const doneTimes: Record<string, { operator: string; actionTime: string; opinion?: string }> = {
    APPLY: { operator: '王视野', actionTime: '2026-09-15 10:12:00', opinion: '提交客户创建申请（High End · 镜片）' },
    INPUT: { operator: '王视野', actionTime: '2026-09-15 10:12:30' },
    OCR: { operator: '系统任务', actionTime: '2026-09-15 10:13:05' },
    DQ: { operator: '系统任务', actionTime: '2026-09-15 10:13:40', opinion: 'DQ 52 分：1 Warning（地址相似度低）' },
    DUP: { operator: '系统任务', actionTime: '2026-09-15 10:14:02', opinion: '命中 1 个 SUSPECT 候选（GC-000128 相似度 0.87）' },
    BU_REVIEW: { operator: 'BU Steward', actionTime: '2026-09-16 09:30:00', opinion: 'Same-BU 证据充分，升级 Cross-BU' },
    GC_REVIEW: { operator: 'GC Steward', actionTime: '2026-09-16 15:20:00', opinion: '关联已有 One ID：GC-000128' },
    RESULT: { operator: '系统任务', actionTime: '2026-09-16 15:20:10' },
    PUBLISH: { operator: 'Platform Admin', actionTime: '2026-09-16 15:25:00', opinion: 'DMS+ / SAP 下发成功' },
    TRACE: { operator: '系统任务', actionTime: '2026-09-16 15:25:05' },
    AUDIT: { operator: 'Auditor', actionTime: '2026-09-16 16:00:00' }
  };

  const order = ['APPLY', 'INPUT', 'OCR', 'DQ', 'DUP', 'BU_REVIEW', 'GC_REVIEW', 'RESULT', 'PUBLISH', 'TRACE', 'AUDIT'];
  const currentIdx = order.indexOf(currentNode);

  const steps: FlowTraceStepVO[] = FLOW_SCENE1_STEPS.map((tpl, i) => {
    const done = currentIdx < 0 || i < currentIdx || (isDone && i <= order.length);
    const trace = doneTimes[tpl.nodeCode];
    return {
      ...tpl,
      order: i + 1,
      status: (done ? 'COMPLETED' : i === currentIdx ? 'CURRENT' : 'PENDING') as FlowTraceStepVO['status'],
      assignee: tpl.nodeCode === 'BU_REVIEW' ? 'BU_STEWARD' : tpl.nodeCode === 'GC_REVIEW' ? 'GC_STEWARD' : undefined,
      operator: done ? trace?.operator : undefined,
      actionTime: done ? trace?.actionTime : undefined,
      opinion: done ? trace?.opinion : undefined
    };
  });

  const completedSteps = steps.filter(s => s.status === 'COMPLETED').length;
  return {
    taskNo,
    bizTitle: '上海优视眼镜有限公司',
    bizType: isGc ? '跨BU合并' : '客户创建',
    sceneCode: isGc ? 'MERGE' : 'CUSTOMER_CREATE',
    sceneName: isGc ? '客户合并' : '客户创建',
    status: isDone ? 'APPROVED' : 'PENDING',
    currentNodeName: isDone ? '已完成' : isGc ? 'GC Steward决策' : 'BU Steward初审',
    assigneeName: isGc ? 'GC Steward' : 'BU Steward',
    assigneeRole: isGc ? 'GC_STEWARD' : 'BU_STEWARD',
    buScope: isGc ? 'Cross-BU' : 'High End',
    riskLevel: 'High',
    slaState: 'OVERDUE',
    submitTime: '2026-09-15 10:12:00',
    slaDue: '2026-09-17 10:12:00',
    flowCode: isGc ? 'cmd_customer_merge' : 'cmd_customer_create',
    flowName: isGc ? '客户合并审批流' : '客户创建审批流',
    slaHours: isGc ? 24 : 48,
    flowInstanceId: 1801,
    flowStatus: '1',
    totalSteps: steps.length,
    completedSteps,
    progressPercent: Math.round((completedSteps * 100) / steps.length),
    engineBound: true,
    graph: buildMockGraph(isGc ? 'GC_REVIEW' : 'BU_REVIEW', isDone),
    bypass: {
      lane: 'Platform Admin',
      nodeName: '规则与参数配置',
      note: '配置 DQ 规则、匹配规则和审批试验，参数配置不打断主流程'
    },
    steps,
    stepDetails: buildMockStepDetails(taskNo, isGc),
    contextVars: [
      { name: 'record', value: `${taskNo} / 上海优视眼镜有限公司` },
      { name: 'dataset', value: 'customer' },
      { name: 'scene', value: isGc ? 'MERGE' : 'CUSTOMER_CREATE' },
      { name: 'flowDefinition', value: isGc ? 'cmd_customer_merge' : 'cmd_customer_create' },
      { name: 'buScope', value: isGc ? 'Cross-BU' : 'High End' },
      { name: 'riskLevel', value: 'High' },
      { name: 'dqScore', value: '52.00' },
      { name: 'duplicateState', value: 'SUSPECTED' },
      { name: 'crossBu', value: isGc ? 'Y' : 'N' },
      { name: 'mergeRequestStatus', value: 'PENDING' },
      { name: 'oneId', value: isGc ? 'GC-000128' : '[not defined]' },
      { name: 'assignee', value: isGc ? 'GC Steward' : 'BU Steward' },
      { name: 'submitTime', value: '2026-09-15T10:12:00' },
      { name: 'slaDue', value: '2026-09-17T10:12:00' },
      { name: 'flowInstanceId', value: '1801' }
    ],
    actions: [
      { actionType: 'SUBMIT', actionName: '创建客户申请', operatorName: '王视野', operatorRole: 'BUSINESS_USER', actionTime: '2026-09-15 10:12:00' },
      { actionType: 'SYSTEM', actionName: 'OCR 与智能补全', operatorName: '系统任务', operatorRole: 'SYSTEM', actionTime: '2026-09-15 10:13:05' },
      { actionType: 'SYSTEM', actionName: '技术与业务 DQ', operatorName: '系统任务', operatorRole: 'SYSTEM', actionTime: '2026-09-15 10:13:40', opinion: 'DQ 52 分：1 Warning' },
      { actionType: 'SYSTEM', actionName: 'Duplicate Check', operatorName: '系统任务', operatorRole: 'SYSTEM', actionTime: '2026-09-15 10:14:02', opinion: 'SUSPECT → BU Scope 初审' },
      { actionType: 'CLAIM', actionName: '初审认领', operatorName: 'BU Steward', operatorRole: 'BU_STEWARD', actionTime: '2026-09-15 10:30:00' }
    ]
  };
};

/** BPMN 风格流程图（引擎 flow_node / flow_skip 结构，与后端 CmdFlowEngineServiceImpl.buildNodes 一致） */
export const buildMockGraph = (currentNode: string, isDone = false): FlowGraphVO => {
  const order = ['START', 'APPLY', 'BU_REVIEW', 'GC_REVIEW', 'END'];
  const currentIdx = order.indexOf(currentNode);
  const pos: Record<string, { x: number; y: number }> = {
    START: { x: 60, y: 90 },
    APPLY: { x: 180, y: 90 },
    BU_REVIEW: { x: 310, y: 90 },
    GC_REVIEW: { x: 440, y: 90 },
    END: { x: 570, y: 90 }
  };
  const names: Record<string, string> = {
    START: '开始',
    APPLY: '创建客户申请',
    BU_REVIEW: 'BU Scope 初审',
    GC_REVIEW: 'GC Scope 决策',
    END: '结束'
  };
  return {
    definitionId: 1,
    flowCode: 'cmd_customer_create',
    nodes: order.map(code => {
      const idx = order.indexOf(code);
      return {
        nodeCode: code,
        nodeName: names[code],
        shape: (code === 'START' || code === 'END' ? 'CIRCLE' : 'RECT') as FlowGraphNodeVO['shape'],
        x: pos[code].x,
        y: pos[code].y,
        status: (isDone || idx < currentIdx ? 'COMPLETED' : idx === currentIdx ? 'CURRENT' : 'PENDING') as FlowGraphNodeVO['status']
      };
    }),
    edges: [
      { from: 'START', to: 'APPLY', label: '提交', skipType: 'PASS', passed: true },
      { from: 'APPLY', to: 'BU_REVIEW', label: '进入 BU 初审', skipType: 'PASS', passed: true },
      { from: 'BU_REVIEW', to: 'GC_REVIEW', label: '升级 Cross-BU', skipType: 'PASS', passed: currentNode === 'GC_REVIEW' || isDone },
      { from: 'BU_REVIEW', to: 'END', label: '批准', skipType: 'PASS', passed: isDone },
      { from: 'GC_REVIEW', to: 'END', label: '决策完成', skipType: 'PASS', passed: isDone }
    ]
  };
};

/** ---------------------------------- One ID ---------------------------------- */
export const mockOneIdRule: OneIdRuleVO = {
  ruleName: 'GC Customer One ID',
  status: 'Published',
  object: 'Customer / A1',
  serialLength: '6 digits',
  prefix: 'GC',
  separator: '-'
};

export const mockOneIdPolicies: OneIdPolicyVO[] = [
  { event: '100%匹配已有客户', handling: '复制已有One ID' },
  { event: '新客户审批通过', handling: '按规则生成新One ID' },
  { event: '审批拒绝', handling: '不激活 / 状态保留' },
  { event: '属性变更', handling: 'One ID保持不变' },
  { event: '逻辑停用', handling: 'One ID保留，状态Inactive' },
  { event: '合并', handling: '保留映射和历史证据' }
];

export const mockOneIdEvents: OneIdEventVO[] = [
  { date: '2026-09-01', stage: 'Draft', description: '客户申请创建，尚未生成Active One ID' },
  { date: '2026-09-01', stage: 'Match Review', description: '发现已有GC-000128，进入Cross-BU治理' },
  { date: '2026-09-02', stage: 'Active', description: '关联已有One ID GC-000128，建立Legacy Code映射' },
  { date: '2026-09-10', stage: 'Attribute Changed', description: '经营地址变更，One ID保持不变' }
];

export const mockLegacyMappings: LegacyMappingVO[] = [
  { oneId: 'GC-000128', sourceSystem: 'Cloud', legacyCode: 'HE-NEW-0231', bu: 'High End', status: 'active' },
  { oneId: 'GC-000128', sourceSystem: 'DMS+', legacyCode: 'MS-CN-88421', bu: 'Mainstream', status: 'active' }
];

/** ---------------------------------- 集成监控 ---------------------------------- */
/** 1:1 取自原型 integrationPage() 表格一行 */
export const mockIntegrationRuns: IntegrationRunVO[] = [
  {
    runId: 'OUT-008',
    direction: 'Outbound',
    system: 'Cloud',
    status: 'Failed',
    detail: 'HTTP 504 Gateway Timeout',
    record: 'GC-000128',
    attempt: '1/3'
  }
];

/** ---------------------------------- 审计 / 权限 / 覆盖 ---------------------------------- */
/** 1:1 取自原型 auditPage() 表格两行 */
export const mockAuditEvents: AuditEventVO[] = [
  { id: 'AU-001', time: '10:18', event: '关联本地客户至GC-000128', role: 'GC Scope', result: 'Success' },
  { id: 'AU-002', time: '10:05', event: '匹配规则v1.4模拟测试', role: 'Admin', result: 'Tested' }
];

export const mockPermissionMatrix: PermissionMatrixVO[] = [
  { capability: '新建客户', business: '✓', steward: '—', admin: '—', auditor: '—' },
  { capability: '重复治理', business: '—', steward: '✓ Scope', admin: '—', auditor: '—' },
  { capability: '规则配置', business: '—', steward: '—', admin: '✓', auditor: '—' },
  { capability: '审计查询', business: '本人', steward: '范围内', admin: '管理员日志', auditor: '全量只读' }
];

export const mockRolePermissions: RolePermissionVO[] = [
  { roleCode: 'BUSINESS_USER', role: 'Business User', scope: 'High End · Frame', points: '查看 / 发起变更', enabled: true },
  { roleCode: 'BU_STEWARD', role: 'Data Steward (BU)', scope: 'BU Scope · High End', points: '查看 / 编辑 / 治理 / 审批', enabled: true },
  { roleCode: 'GC_STEWARD', role: 'Data Steward (GC)', scope: 'GC Scope · Cross-BU', points: '跨BU治理 / 审计 / 审批', enabled: true },
  { roleCode: 'PLATFORM_ADMIN', role: 'Platform Admin', scope: 'Platform & Integration', points: '平台配置 / 集成监控', enabled: true },
  { roleCode: 'AUDITOR', role: 'Auditor', scope: 'Read Only · Authorized', points: '只读查询 / 审计导出', enabled: true }
];

export const mockCoverage: CoverageItemVO[] = [
  { topic: 'AAD Login', status: '部分', evidence: '仅角色模拟，未展示真实AAD配置、Session Timeout' },
  { topic: 'Approval Process', status: '已增强', evidence: 'High End与Mainstream两条独立流程实例、节点与SLA' },
  { topic: 'One ID Management', status: '已增强', evidence: '编码模式、生成策略、生命周期历史、Legacy Code映射' },
  { topic: 'Duplicate & Migration', status: '已覆盖', evidence: '候选对比、关联已有、确认新客户' },
  { topic: 'OCR', status: '已覆盖', evidence: '字段、置信度、写回入口' },
  { topic: 'Delete / Change', status: '已增强', evidence: '变更申请、逻辑停用、Before/After、后台数据库结果' },
  { topic: 'Data Quality', status: '已增强', evidence: 'Scorecard、规则影响、显式历史重评估与版本保留' },
  { topic: 'Hierarchy', status: '已覆盖', evidence: 'A3-A2-A1、新增关系、Loop Check' },
  { topic: 'Log', status: '已增强', evidence: '详细Before/After、审批轨迹、停用数据库结果' },
  { topic: 'System Integration', status: '部分', evidence: '有监控和Retry，无API/CSV/Email三类现场演示' },
  { topic: 'Master Data Extension', status: '已增强', evidence: '新建字段、Draft、发布并动态进入Business User表单' }
];

/** ---------------------------------- 工作流配置（场景级，平台管理 › Workflow › 配置） ---------------------------------- */

/** 平台固定 / 可配置节点判定：与后端 CmdFlowSceneConfigServiceImpl 同一口径 */
const MOCK_CONFIGURABLE_NODES = ['GC_REVIEW'];

const MOCK_LOCKED_NODE_CONSTRAINT: Record<string, string> = {
  APPLY: '业务入口：Business User 发起，平台固定不可删除',
  INPUT: '申请数据录入与附件，平台固定不可删除',
  OCR: '系统自动：OCR 与地址标准化，规则驱动不可删除',
  DQ: '系统自动：DQ 打分，结果写入实例变量驱动路由，不可删除',
  DUP: '系统自动：Duplicate Check（信用代码 + 经营地址为主依据），不可删除',
  BU_REVIEW: '主干审批节点：BU Scope 初审，不可停用；命中条件与办理角色可配置',
  RESULT: '系统自动：One ID 生成 / 关联，One ID 稳定不重新生成，不可删除',
  PUBLISH: '发布下游与 Retry / Resubmit 由平台统一执行，不可删除',
  TRACE: '运行追踪：任务状态与失败原因，平台固定',
  AUDIT: '审计证据链：Who / When / What 与 Before / After，只读保留不可删除'
};

/** [id, nodeCode, nodeName, conditionExpr, assigneeValue, scopeType, multiMode, status, slaHours, priority, remark] */
type MockRuleTuple = [number, string, string, string | null, string, string, string, string, number, number, string];

/** 节点审批人规则种子：与后端 cmd_flow_node_rule 初始化数据逐行一致 */
const MOCK_SCENE_RULE_TUPLES: Record<string, MockRuleTuple[]> = {
  CUSTOMER_CREATE: [
    [1001, 'bu_review', 'BU初审', 'risk_level != "High" && !cross_bu', 'BU_STEWARD', 'BU', 'ANY', '0', 48, 10, '本 BU 数据管家初审'],
    [1002, 'gc_review', 'GC决策', 'risk_level == "High" || cross_bu', 'GC_STEWARD', 'GC', 'ANY', '0', 48, 20, '高风险或跨BU升级 GC Scope']
  ],
  CUSTOMER_CHANGE: [
    [1003, 'bu_review', 'BU初审', '!is_key_change', 'BU_STEWARD', 'BU', 'ANY', '0', 48, 10, '非关键属性变更'],
    [1004, 'gc_review', 'GC决策', 'is_key_change', 'GC_STEWARD', 'GC', 'ANY', '0', 48, 20, '关键属性变更需 GC 审批']
  ],
  DEACTIVATE: [
    [1005, 'bu_review', 'BU初审', 'relation_check == "PASS"', 'BU_STEWARD', 'BU', 'ALL', '0', 72, 10, '无关联引用时 BU 审批即可'],
    [1006, 'gc_review', 'GC决策', 'relation_check != "PASS"', 'GC_STEWARD', 'GC', 'ALL', '0', 72, 20, '存在关联引用需 GC 评估']
  ],
  HIER_RELATION: [
    [1007, 'bu_review', 'BU审核', '!cross_bu', 'BU_STEWARD', 'BU', 'ANY', '0', 48, 10, '本 BU 层级关系'],
    [1008, 'gc_review', 'GC审核', 'cross_bu', 'GC_STEWARD', 'GC', 'ANY', '0', 48, 20, '跨 BU 层级关系']
  ],
  MERGE: [[1009, 'gc_review', 'GC决策', null, 'GC_STEWARD', 'GC', 'ALL', '0', 24, 10, '合并统一由 GC Scope 决策']],
  IMPORT_BATCH: [[1010, 'bu_review', 'BU确认', null, 'BU_STEWARD', 'BU', 'ANY', '0', 24, 10, '导入结果确认']]
};

/** 场景级超时升级规则种子（cmd_flow_scene.escalate_rule） */
const MOCK_SCENE_ESCALATE: Record<string, string> = {
  CUSTOMER_CREATE: 'TO_GC',
  CUSTOMER_CHANGE: 'TO_GC',
  DEACTIVATE: 'TO_GC',
  HIER_RELATION: 'TO_GC',
  IMPORT_BATCH: 'NOTIFY',
  MERGE: 'TO_GC'
};

/**
 * 构造场景级工作流配置（USE_MOCK 时使用；live 模式走后端 /cmd/flow/scene/{sceneCode}/config）
 */
export const buildMockSceneConfig = (sceneCode: string): FlowSceneConfigVO => {
  const scene = mockFlowScenes.find(s => s.sceneCode === sceneCode) ?? mockFlowScenes[0];
  const graph = buildMockSceneGraph(scene.sceneCode);

  const nodes: FlowSceneNodeVO[] = graph.nodes.map(node => {
    const configurable = MOCK_CONFIGURABLE_NODES.includes(node.nodeCode);
    return {
      phase: node.phase,
      phaseName: node.phaseName,
      lane: node.lane,
      nodeCode: node.nodeCode,
      nodeName: node.nodeName,
      nodeType: node.nodeType === 3 ? 'GATEWAY' : node.nodeCode === 'OCR' || node.nodeCode === 'DQ' || node.nodeCode === 'RESULT' || node.nodeCode === 'TRACE' || node.nodeCode === 'AUDIT' ? 'AUTO' : 'MANUAL',
      note: node.note,
      locked: !configurable,
      configurable,
      constraint: configurable
        ? '可配置：可停用（Mainstream 可只走 BU 初审）或调整升级命中条件（V6.1 待确认项）'
        : MOCK_LOCKED_NODE_CONSTRAINT[node.nodeCode] ?? '平台固定节点，不可删除'
    };
  });

  const rules: FlowSceneRuleVO[] = (MOCK_SCENE_RULE_TUPLES[scene.sceneCode] ?? []).map(
    ([id, nodeCode, nodeName, conditionExpr, assigneeValue, scopeType, multiMode, status, slaHours, priority, remark]) => ({
      id,
      nodeCode,
      nodeName,
      conditionExpr: conditionExpr ?? undefined,
      assigneeType: 'ROLE',
      assigneeValue,
      scopeType,
      multiMode,
      slaHours,
      priority,
      status,
      remark,
      locked: nodeCode === 'bu_review',
      constraint:
        nodeCode === 'bu_review'
          ? '主干必经：BU Scope 初审不可停用；命中条件 / 办理角色 / 会签方式 / 节点 SLA 可调整'
          : '可增删：停用后该场景不再走此节点（条件与节点 SLA 可调整）'
    })
  );

  return {
    sceneCode: scene.sceneCode,
    sceneName: scene.sceneName,
    flowCode: scene.flowCode,
    flowName: scene.flowName,
    slaHours: scene.slaHours,
    escalateRule: MOCK_SCENE_ESCALATE[scene.sceneCode] ?? 'TO_GC',
    startConditions: '',
    formKey: `${scene.sceneCode.toLowerCase().replace(/_/g, '-')}-form`,
    deployed: scene.deployed,
    version: scene.version,
    timeoutAction: 'Notify + Escalate',
    notifyMode: 'Email Notification Only',
    notifyTargets: ['申请人', '当前节点办理人'],
    changeNote: '初始版本：平台初始化配置',
    nodes,
    rules
  };
};

/** ---------------------------------- OCR ---------------------------------- */
/**
 * 营业执照原件信息（对应原型「营业执照预览」4 行）
 * 取值与测试素材 docs/cmd-poc/测试素材/营业执照/license_01.png 一致，
 * 与后端 CmdOcrServiceImpl 的默认演示结果保持同一套数据。
 */
export const mockOcrLicense: OcrLicenseVO = {
  creditCode: '91310106MA1FL2X78K',
  name: '上海清视眼镜有限公司',
  type: '有限责任公司（自然人投资或控股）',
  address: '上海市静安区南京西路1266号恒隆广场二期28层'
};

/**
 * 字段识别值（对应弹窗底部表格）
 * field 必须与客户模型元数据 md_field.field_name 完全一致，前端据此回填新建客户表单。
 */
export const mockOcrResults: OcrResultVO[] = [
  { code: 'legal_name', field: '客户法定名称', value: '上海清视眼镜有限公司', confidence: '98%' },
  { code: 'credit_code', field: '统一社会信用代码', value: '91310106MA1FL2X78K', confidence: '99%' },
  { code: 'address', field: '注册地址', value: '上海市静安区南京西路1266号恒隆广场二期28层', confidence: '93%' },
  { code: 'province', field: '省份', value: '上海市', confidence: '96%' },
  { code: 'city', field: '城市', value: '上海市', confidence: '91%' }
];

/** 历史重评估影响预估（Demo） */
export const mockReEvaluateImpact: ReEvaluateImpactVO[] = [
  { label: '受影响记录 · Demo', value: '1,248' },
  { label: '预计新增异常 · Demo', value: '37' },
  { label: '旧分数与规则版本', value: '保留' }
];

/** ---------------------------------- 工作流（所有 CMD 流程） ---------------------------------- */
/** 工作流：全部 CMD 业务场景（来自 V6.1 总设计业务流，已部署到 Warm-Flow） */
export const mockFlowScenes: FlowSceneVO[] = [
  { sceneCode: 'CUSTOMER_CREATE', sceneName: '客户创建', flowCode: 'cmd_customer_create', flowName: '客户创建审批流', slaHours: 48, deployed: true, definitionId: 1001, version: 1, nodeCount: 5 },
  { sceneCode: 'CUSTOMER_CHANGE', sceneName: '客户属性变更', flowCode: 'cmd_customer_change', flowName: '客户变更审批流', slaHours: 48, deployed: true, definitionId: 1002, version: 1, nodeCount: 5 },
  { sceneCode: 'DEACTIVATE', sceneName: '客户逻辑停用', flowCode: 'cmd_customer_deactivate', flowName: '客户停用审批流', slaHours: 72, deployed: true, definitionId: 1003, version: 1, nodeCount: 5 },
  { sceneCode: 'HIER_RELATION', sceneName: '层级关系变更', flowCode: 'cmd_hier_relation', flowName: '层级关系审批流', slaHours: 48, deployed: true, definitionId: 1004, version: 1, nodeCount: 5 },
  { sceneCode: 'IMPORT_BATCH', sceneName: '批量导入确认', flowCode: 'cmd_import_batch', flowName: '批量导入确认流', slaHours: 24, deployed: true, definitionId: 1005, version: 1, nodeCount: 5 },
  { sceneCode: 'MERGE', sceneName: '客户合并', flowCode: 'cmd_customer_merge', flowName: '客户合并审批流', slaHours: 24, deployed: true, definitionId: 1006, version: 1, nodeCount: 5 }
];

/** 工作流：按场景构造演示用 BPMN 风格图形（节点均为待执行，定义视图） */
export const buildMockSceneGraph = (sceneCode: string): FlowGraphVO => {
  const flowCode = mockFlowScenes.find(s => s.sceneCode === sceneCode)?.flowCode ?? 'cmd_customer_create';
  const definitionId = mockFlowScenes.find(s => s.sceneCode === sceneCode)?.definitionId ?? 1001;

  // 与后端 CmdFlowEngineServiceImpl.buildSwimlane 保持一致：7 阶段（列） × 6 泳道（行）
  const BASE_X = 300;
  const COL_GAP = 168;
  const BASE_Y = 70;
  const LANE_GAP = 92;

  const lanes = ['Business User', '系统自动处理', 'Data Steward BU Scope', 'Data Steward GC Scope', 'Platform Admin', 'Auditor（只读）'];
  const phaseNames = ['发起', '数据准备', '自动校验', '匹配分流', '人工治理', '审批发布', '追踪审计'];

  type Tpl = [number, number, string, string, string, string];
  // [阶段, 泳道序号, 节点编码, 节点名称, 节点类型, 说明]
  const createTpl: Tpl[] = [
    [1, 0, 'APPLY', '创建客户申请', 'MANUAL', '选择客户类型、BU、产品线与来源系统，保存草稿或提交'],
    [2, 0, 'INPUT', '录入与附件', 'MANUAL', '填写 GC Core、BU 与来源系统字段，上传营业执照'],
    [2, 1, 'OCR', 'OCR 与智能补全', 'AUTO', '提取工商名称、信用代码和地址；底表检索与地址标准化'],
    [3, 1, 'DQ', '技术与业务 DQ', 'AUTO', '必填、格式、值集、GC Core、层级与 Payer 校验'],
    [4, 1, 'DUP', 'Duplicate Check', 'GATEWAY', '以信用代码和经营地址为主依据；SUSPECT 进入人工治理'],
    [5, 2, 'BU_REVIEW', 'BU Scope 初审', 'MANUAL', '查看申请与候选证据；确认 Same-BU 或升级 Cross-BU'],
    [5, 3, 'GC_REVIEW', 'GC Scope 决策', 'MANUAL', '核对跨 BU 证据；决定关联现有或创建新 One ID'],
    [6, 1, 'RESULT', '生成 / 关联结果', 'AUTO', '关联已有 One ID；建立 BU 本地码映射并激活主档'],
    [6, 4, 'PUBLISH', '发布到下游', 'MANUAL', '通过 API / 实时 / 批量发布；失败时 Retry / Resubmit'],
    [7, 4, 'TRACE', '运行追踪', 'AUTO', '查看任务状态、失败原因、重试与发布记录'],
    [7, 5, 'AUDIT', '审计查询', 'AUTO', '记录谁、何时、做了什么及 Before / After 与审批证据']
  ];
  const genericTpl: Tpl[] = [
    [1, 0, 'APPLY', '提交业务申请', 'MANUAL', 'Business User 发起申请'],
    [2, 1, 'INPUT', '数据装配', 'AUTO', '装配申请数据与证据快照'],
    [3, 1, 'DQ', '自动校验', 'AUTO', 'DQ 规则与前置校验（Loop Check / 关联检查等）'],
    [4, 1, 'DUP', '条件分流', 'GATEWAY', '按规则变量路由（Same-BU / Cross-BU / 风险等级）'],
    [5, 2, 'BU_REVIEW', 'BU Scope 初审', 'MANUAL', '本 BU 数据管家初审'],
    [5, 3, 'GC_REVIEW', 'GC Scope 决策', 'MANUAL', '跨 BU 或高风险升级 GC 决策'],
    [6, 1, 'RESULT', '执行与生效', 'AUTO', '审批通过后执行业务结果并生成版本'],
    [6, 4, 'PUBLISH', '发布下游', 'MANUAL', '通知 API / 文件 / 批量发布；失败即 Retry / Resubmit'],
    [7, 4, 'TRACE', '运行追踪', 'AUTO', '任务状态与失败原因追踪'],
    [7, 5, 'AUDIT', '审计查询', 'AUTO', 'Before / After 证据审查']
  ];

  const tpl = sceneCode === 'CUSTOMER_CREATE' ? createTpl : genericTpl;
  const nodes: FlowGraphNodeVO[] = tpl.map(([phase, laneIdx, nodeCode, nodeName, nodeType, note], i) => ({
    nodeCode,
    nodeName,
    shape: (i === 0 || i === tpl.length - 1 ? 'CIRCLE' : nodeType === 'GATEWAY' ? 'DIAMOND' : 'RECT') as FlowGraphNodeVO['shape'],
    nodeType: nodeType === 'GATEWAY' ? 3 : 1,
    lane: lanes[laneIdx],
    phase,
    phaseName: phaseNames[phase - 1],
    note,
    x: BASE_X + (phase - 1) * COL_GAP,
    y: BASE_Y + laneIdx * LANE_GAP,
    status: 'PENDING'
  }));

  const edges: FlowGraphEdgeVO[] = nodes.slice(0, -1).map((n, i) => ({
    from: n.nodeCode,
    to: nodes[i + 1].nodeCode,
    label: '',
    skipType: 'PASS',
    passed: false
  }));

  return { definitionId, flowCode, lanes, nodes, edges };
};

/** 工作流：流程实例记录（每一次执行过的工作流都留一条；USE_MOCK 时的演示数据） */
export const mockFlowInstances: FlowInstanceVO[] = [
  {
    id: 1001, taskNo: 'AP-20260915-0001', bizTitle: '苏州新视野眼镜有限公司', bizType: '客户创建',
    sceneCode: 'CUSTOMER_CREATE', sceneName: '客户创建', flowName: '客户创建审批流', flowCode: 'cmd_customer_create',
    engineBound: false, applicantName: '王视野', assigneeName: 'BU Steward', assigneeRole: 'BU_STEWARD',
    priority: 'High', status: 'PENDING', currentNodeName: 'BU Steward初审',
    completedSteps: 1, totalSteps: 11, progressPercent: 9, slaState: 'OVERDUE', createTime: '2026-09-15 09:20:00'
  },
  {
    id: 1003, taskNo: 'AP-20260916-0003', bizTitle: '成都明视眼镜有限公司', bizType: '疑似重复',
    sceneCode: 'CUSTOMER_CREATE', sceneName: '客户创建', flowName: '客户创建审批流', flowCode: 'cmd_customer_create',
    engineBound: false, applicantName: '系统规则', assigneeName: 'BU Steward', assigneeRole: 'BU_STEWARD',
    priority: 'High', status: 'APPROVED', currentNodeName: '已完成',
    completedSteps: 11, totalSteps: 11, progressPercent: 100, slaState: 'DUE_SOON',
    submitTime: '2026-09-16 10:05:00', finishTime: '2026-09-16 15:30:00', durationHours: 5, createTime: '2026-09-16 10:05:00'
  },
  {
    id: 1005, taskNo: 'AP-20260914-0005', bizTitle: '上海清视眼镜有限公司', bizType: '客户变更',
    sceneCode: 'CUSTOMER_CHANGE', sceneName: '客户属性变更', flowName: '客户变更审批流', flowCode: 'cmd_customer_change',
    engineBound: false, applicantName: '张清', assigneeName: '王视野', assigneeRole: 'BUSINESS_USER',
    priority: 'Medium', status: 'RETURNED', currentNodeName: '退回申请人补充',
    completedSteps: 1, totalSteps: 10, progressPercent: 10, slaState: 'OVERDUE', createTime: '2026-09-14 14:10:00'
  },
  {
    id: 1007, taskNo: 'AP-20260915-0007', bizTitle: '上海清视眼镜有限公司跨BU合并申请', bizType: '跨BU合并',
    sceneCode: 'MERGE', sceneName: '客户合并', flowName: '客户合并审批流', flowCode: 'cmd_customer_merge',
    engineBound: false, applicantName: 'BU Steward', assigneeName: 'GC Steward', assigneeRole: 'GC_STEWARD',
    priority: 'High', status: 'PENDING', currentNodeName: 'GC Steward决策',
    completedSteps: 5, totalSteps: 10, progressPercent: 50, slaState: 'DUE_SOON', createTime: '2026-09-15 11:00:00'
  }
];

/** 工作流：在场景图形上按实例进度点亮节点（Mock 版，与后端 applyStepStatus 口径一致） */
export const buildMockInstanceGraph = (sceneCode: string, taskNo: string): FlowGraphVO => {
  const graph = buildMockSceneGraph(sceneCode);
  const inst = mockFlowInstances.find(i => i.taskNo === taskNo);
  if (!inst) return graph;
  const name = inst.currentNodeName ?? '';
  const currentCode =
    inst.status === 'APPROVED' || inst.status === 'COMPLETED' || name.includes('已完成')
      ? '_DONE_'
      : name.includes('GC')
        ? 'GC_REVIEW'
        : name.includes('BU')
          ? 'BU_REVIEW'
          : name.includes('退回')
            ? 'INPUT'
            : 'APPLY';
  const finished = currentCode === '_DONE_';
  const stopped = inst.status === 'REJECTED' || inst.status === 'CANCELLED';
  const currentIdx = graph.nodes.findIndex(n => n.nodeCode === currentCode);

  graph.nodes = graph.nodes.map((n, i) => {
    if (finished) return { ...n, status: 'COMPLETED' as const };
    if (stopped && i > currentIdx) return { ...n, status: 'TERMINATED' as const };
    if (i === currentIdx) return { ...n, status: 'CURRENT' as const };
    if (i < currentIdx) return { ...n, status: 'COMPLETED' as const };
    return { ...n, status: 'PENDING' as const };
  });
  graph.edges = graph.edges.map((e, i) => ({ ...e, passed: graph.nodes[i]?.status === 'COMPLETED' }));
  return graph;
};
