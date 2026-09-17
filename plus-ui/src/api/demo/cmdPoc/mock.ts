/**
 * CMD POC 演示数据（Mock）
 *
 * 数据 1:1 取自 Essilor CMD POC 原型，后端接口就绪后本文件可整体删除。
 * 注意：仅用于交互演示，不含任何真实业务数据。
 */
import type {
  ApprovalFlowVO,
  ApprovalInstanceVO,
  ApprovalTrailVO,
  AuditEventVO,
  BatchResultVO,
  ChangeDiffVO,
  ChangeRequestVO,
  CoverageItemVO,
  CustomerVO,
  DashboardStatVO,
  DeactivateResultVO,
  DqRuleVO,
  DqScorecardVO,
  DqSimulateResultVO,
  DuplicateCandidateVO,
  HierarchyNodeVO,
  ImportJobVO,
  ImportTemplateVO,
  IntegrationRunVO,
  LegacyMappingVO,
  MatchRuleVO,
  MetadataFieldVO,
  ModelVersionVO,
  NotificationVO,
  OcrResultVO,
  OneIdEventVO,
  OneIdPolicyVO,
  OneIdRuleVO,
  PermissionMatrixVO,
  ReEvaluateImpactVO,
  RolePermissionVO,
  TemplateMappingVO,
  TodoVO,
  WorkflowConfigVO
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
export const mockCustomers: CustomerVO[] = [
  {
    oneId: 'GC-000128',
    legalName: '上海清视眼镜有限公司',
    customerType: 'Door',
    bu: 'High End/Mainstream',
    productLine: 'Frame',
    sourceSystem: 'Cloud',
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
    status: 'inactive',
    dqScore: 72,
    versionNo: 7,
    updatedAt: '2026-09-15'
  },
  {
    oneId: 'GC-000311',
    legalName: '广州睛彩光学有限公司',
    customerType: 'Door',
    bu: 'High End',
    productLine: 'Frame',
    sourceSystem: 'Cloud',
    creditCode: '91440100XXXXXXXXXX',
    address: '广州市天河区天河路XXX号',
    status: 'pending',
    dqScore: 81,
    versionNo: 1,
    updatedAt: '2026-09-14'
  },
  {
    oneId: 'GC-000402',
    legalName: '成都视界眼镜连锁有限公司',
    customerType: 'A2',
    bu: 'Mainstream',
    productLine: 'Lens',
    sourceSystem: 'DMS+',
    creditCode: '91510100XXXXXXXXXX',
    address: '成都市锦江区春熙路XXX号',
    status: 'active',
    dqScore: 93,
    versionNo: 3,
    updatedAt: '2026-09-13'
  },
  {
    oneId: 'GC-000517',
    legalName: '武汉明视达贸易有限公司',
    customerType: 'A1',
    bu: 'Mainstream',
    productLine: 'Lens',
    sourceSystem: 'DMS+',
    creditCode: '91420100XXXXXXXXXX',
    address: '武汉市江汉区解放大道XXX号',
    status: 'draft',
    dqScore: 65,
    versionNo: 0,
    updatedAt: '2026-09-12'
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
  { version: 'v1.4', ruleCount: 12, status: 'Current', publishedAt: '2026-08-01' },
  { version: 'v1.5 Draft', ruleCount: 13, status: 'Draft' }
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

export const mockDqSimulate: DqSimulateResultVO[] = [
  { rule: '格式规则', result: 'Pass' },
  { rule: 'GC Core完整性', result: 'Pass' },
  { rule: 'Payer必填', result: 'Block', message: 'Payer 缺失，阻止提交' }
];

/** ---------------------------------- 匹配规则 ---------------------------------- */
export const mockMatchRules: MatchRuleVO[] = [
  { dimension: '统一社会信用代码', role: '主依据', result: 'Exact', threshold: '100%', enabled: true },
  { dimension: '经营地址', role: '主依据', result: '88%', threshold: '85%', enabled: true },
  { dimension: '名称', role: '辅助线索', result: 'Similar', threshold: '80%', enabled: true }
];

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
  }
};

/** ---------------------------------- 批量导入 ---------------------------------- */
export const mockImportJobs: ImportJobVO[] = [
  { jobId: 'IMP-001', fileName: 'Mainstream_Door_0915.xlsx', totalRows: 100, status: 'Waiting for Review', submittedAt: '2026-09-15 10:24', submittedBy: 'Business User' },
  { jobId: 'IMP-002', fileName: 'HighEnd_Frame_0914.xlsx', totalRows: 60, status: 'Partial Success', submittedAt: '2026-09-14 16:02', submittedBy: 'Business User' },
  { jobId: 'IMP-003', fileName: 'CrossBU_Backfill_0912.xlsx', totalRows: 240, status: 'Completed', submittedAt: '2026-09-12 09:10', submittedBy: 'Data Steward · GC' }
];

export const mockBatchResult: BatchResultVO = {
  jobId: 'IMP-001',
  exact: 42,
  suspected: 18,
  created: 26,
  invalid: 14,
  routes: [
    { result: 'Exact', handling: '关联已有One ID', owner: 'System' },
    { result: 'Suspected', handling: '进入人工治理', owner: 'BU/GC Steward' },
    { result: 'New', handling: '审批后生成One ID', owner: 'Steward' },
    { result: 'Invalid 14', handling: '返回修复', owner: 'Business User' }
  ]
};

export const mockImportTemplates: ImportTemplateVO[] = [
  { name: 'Door_Mainstream_Lens', context: 'Mainstream·Lens·DMS+', version: 'v1.3', fieldCount: 56, status: 'Published' },
  { name: 'Door_HighEnd_Frame', context: 'High End·Frame·Cloud', version: 'v1.2', fieldCount: 61, status: 'Draft' }
];

export const mockTemplateMappings: TemplateMappingVO[] = [
  { sourceColumn: 'CustomerName', targetField: 'legal_name', transform: 'Trim + Normalize', errorStrategy: 'Reject Row' },
  { sourceColumn: 'CreditCode', targetField: 'credit_code', transform: 'Upper Case', errorStrategy: 'Reject Row' },
  { sourceColumn: 'Address', targetField: 'business_address', transform: 'Address Standardization', errorStrategy: 'Warning Row' }
];

/** ---------------------------------- 客户层级 ---------------------------------- */
export const mockHierarchy: HierarchyNodeVO[] = [
  {
    id: 'A3-001',
    level: 'A3',
    label: 'A3 · 华东商业实体',
    children: [
      {
        id: 'A2-0188',
        level: 'A2',
        label: 'A2 · 上海重点客户',
        children: [
          {
            id: 'A1-000128',
            level: 'A1',
            label: 'A1 · 上海清视南京西路店 · Payer GC-PY-0092',
            oneId: 'GC-000128',
            payerId: 'GC-PY-0092'
          }
        ]
      }
    ]
  }
];

/** ---------------------------------- 变更 / 停用 ---------------------------------- */
export const mockChangeRequests: ChangeRequestVO[] = [
  {
    requestId: 'CHG-0018',
    oneId: 'GC-000128',
    changeType: 'Update',
    content: '经营地址',
    status: 'Under Review',
    submittedAt: '2026-09-15 10:10',
    submittedBy: 'Business User'
  },
  {
    requestId: 'DEL-0007',
    oneId: 'GC-000245',
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
    "customer_master.status = 'INACTIVE'",
    'customer_master.is_deleted = false',
    'customer_version.version_no = 7',
    "one_id_registry.one_id = 'GC-000245'",
    "audit_event.action = 'DEACTIVATE'"
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
export const mockIntegrationRuns: IntegrationRunVO[] = [
  {
    runId: 'OUT-008',
    direction: 'Outbound',
    system: 'Cloud',
    status: 'Failed',
    detail: 'HTTP 504 Gateway Timeout',
    record: 'GC-000128',
    attempt: '1/3'
  },
  { runId: 'IN-021', direction: 'Inbound', system: 'DMS+', status: 'Success', record: 'GC-000402' },
  { runId: 'OUT-006', direction: 'Outbound', system: 'Cloud', status: 'Retrying', record: 'GC-000311', attempt: '2/3' }
];

/** ---------------------------------- 审计 / 权限 / 覆盖 ---------------------------------- */
export const mockAuditEvents: AuditEventVO[] = [
  { id: 'AU-001', time: '10:18', event: '关联本地客户至GC-000128', role: 'GC Scope', result: 'Success' },
  { id: 'AU-002', time: '10:05', event: '匹配规则v1.4模拟测试', role: 'Admin', result: 'Tested' },
  { id: 'AU-003', time: '09:52', event: '导出审计报告（最近30天）', role: 'Auditor', result: 'Success' },
  { id: 'AU-004', time: '09:31', event: 'Outbound 同步 OUT-008 重试', role: 'System', result: 'Failed' }
];

export const mockPermissionMatrix: PermissionMatrixVO[] = [
  { capability: '新建客户', business: '✓', steward: '—', admin: '—', auditor: '—' },
  { capability: '重复治理', business: '—', steward: '✓ Scope', admin: '—', auditor: '—' },
  { capability: '规则配置', business: '—', steward: '—', admin: '✓', auditor: '—' },
  { capability: '审计查询', business: '本人', steward: '范围内', admin: '管理员日志', auditor: '全量只读' }
];

export const mockRolePermissions: RolePermissionVO[] = [
  { role: 'Business User', scope: 'High End · Frame', points: '查看 / 发起变更', enabled: true },
  { role: 'Data Steward (BU)', scope: 'BU Scope · High End', points: '查看 / 编辑 / 治理 / 审批', enabled: true },
  { role: 'Data Steward (GC)', scope: 'GC Scope · Cross-BU', points: '跨BU治理 / 审计 / 审批', enabled: true },
  { role: 'Platform Admin', scope: 'Platform & Integration', points: '平台配置 / 集成监控', enabled: true },
  { role: 'Auditor', scope: 'Read Only · Authorized', points: '只读查询 / 审计导出', enabled: true }
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

/** ---------------------------------- 工作流配置 ---------------------------------- */
export const mockWorkflow: WorkflowConfigVO = {
  flowName: 'Customer Create & Governance',
  steps: ['Business User Submit', 'BU Scope Initial Review', 'GC Scope Cross-BU Decision', 'Publish One ID'],
  routeCondition: 'Match Scope = Cross-BU',
  sla: '2 Business Days',
  timeoutAction: 'Notify + Escalate',
  notification: 'Email Notification Only'
};

/** ---------------------------------- OCR ---------------------------------- */
export const mockOcrResults: OcrResultVO[] = [
  { field: '工商名称', value: '上海清视眼镜有限公司', confidence: '98%' },
  { field: '信用代码', value: '91310000XXXXXXXXXX', confidence: '99%' },
  { field: '注册地址', value: '上海市静安区南京西路XXX号', confidence: '93%' }
];

/** 历史重评估影响预估（Demo） */
export const mockReEvaluateImpact: ReEvaluateImpactVO[] = [
  { label: '受影响记录 · Demo', value: '1,248' },
  { label: '预计新增异常 · Demo', value: '37' },
  { label: '旧分数与规则版本', value: '保留' }
];
