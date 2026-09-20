/**
 * CMD POC 接口层
 *
 * 约定：
 * 1. 所有函数统一返回 Promise<数据本体>，业务层不感知 axios 响应包装；
 * 2. 顶部 USE_MOCK 开关控制走 Mock 还是真实后端，后端 Controller 就绪后
 *    将 VITE_CMD_POC_MOCK 置为 'false' 即可无缝切换，业务代码零改动；
 * 3. 后端路径统一 '/cmd/**'，与 RuoYi-Vue-Plus 的 /demo/** 保持风格一致。
 */
import { ElMessage } from 'element-plus';
import request from '@/utils/request';
import { saveBlob } from '@/utils/save';
import type { AxiosPromise } from '@/utils/api-types';
import type {
  CmdApprovalDetailRow,
  CmdApprovalKpiRow,
  CmdApprovalTaskRow,
  CmdAuditEventRow,
  ApprovalFlowVO,
  ApprovalInstanceVO,
  ApprovalKpiVO,
  ApprovalTaskDetailVO,
  ApprovalTaskVO,
  ApprovalTrailVO,
  AuditEventVO,
  AuditExportForm,
  BatchResultVO,
  ChangeDiffVO,
  ChangeRequestForm,
  ChangeRequestQuery,
  ChangeRequestVO,
  ChangeStatus,
  CmdChangeRequestRow,
  CmdCustomerRow,
  CmdDashboardRow,
  CmdFlowTraceRow,
  CmdHierarchyNodeRow,
  CmdIntegrationRunRow,
  CmdImportJobRow,
  CmdImportResultRow,
  CmdImportTemplateRow,
  CmdTemplateMappingRow,
  CoverageItemVO,
  CustomerForm,
  CustomerQuery,
  CustomerSubmitVO,
  CustomerVO,
  DqRuleRow,
  DashboardStatVO,
  DeactivateForm,
  DeactivateResultVO,
  DqScorecardVO,
  DqSimulateResultVO,
  DuplicateCandidateVO,
  FlowTraceVO,
  FlowGraphNodeVO,
  FlowGraphEdgeVO,
  FlowGraphVO,
  FlowInstanceQuery,
  FlowInstanceVO,
  FlowSceneVO,
  HierarchyNodeVO,
  HierarchyRelationForm,
  ImportJobStatus,
  ImportJobVO,
  ImportTemplateVO,
  ImportUploadForm,
  IntegrationConnForm,
  IntegrationRunVO,
  CmdLegacyMappingRow,
  CmdMdFieldRow,
  CmdOneIdPolicyRow,
  CmdOneIdRuleRow,
  CmdPermissionMatrixRow,
  CmdRoleRow,
  CmdValueSetRow,
  CmdVersionRow,
  LegacyMappingVO,
  MatchRuleRow,
  MatchRuleVO,
  MetadataFieldForm,
  MetadataFieldVO,
  ModelVersionVO,
  NotificationVO,
  OcrResultVO,
  OcrRecognizeVO,
  OneIdEventVO,
  OneIdPolicyVO,
  OneIdRuleVO,
  PageResult,
  PermissionMatrixVO,
  ReEvaluateForm,
  ReEvaluateImpactVO,
  RolePermissionVO,
  TemplateMappingVO,
  TodoVO,
  WorkflowConfigVO,
  WorkflowStepVO
} from './types';
import * as mock from './mock';

/**
 * Mock 开关（运维可调，改 .env.* 即可，无需改代码）：
 * - VITE_CMD_POC_MOCK：全局开关。置为 'false' = 所有模块走真实后端。
 * - VITE_CMD_POC_LIVE_MODULES：已联调模块白名单（逗号分隔）。
 *   全局仍为 Mock 时，只有列在此处的模块走真实后端，其余继续用演示数据。
 *   例：VITE_CMD_POC_LIVE_MODULES=customer,hierarchy,dashboard
 */
export const USE_MOCK = import.meta.env.VITE_CMD_POC_MOCK === 'true';

/** 已联调模块列表（小写逗号分隔，从环境变量读取） */
const LIVE_MODULES: string[] = String(import.meta.env.VITE_CMD_POC_LIVE_MODULES ?? '')
  .split(',')
  .map((s: string) => s.trim())
  .filter(Boolean);

/**
 * 判断某个模块是否走真实后端。
 * 全局关闭 Mock 时全部走真实后端；否则仅白名单内的模块走真实后端。
 */
function useLive(module: string): boolean {
  return !USE_MOCK || LIVE_MODULES.includes(module);
}

/** 模拟网络延迟，便于演示 loading 态 */
function delay<T>(data: T, ms = 300): Promise<T> {
  return new Promise(resolve => setTimeout(() => resolve(data), ms));
}

/** Mock 端按查询条件过滤（真实后端会走 SQL 过滤） */
function filterChangeRequests(rows: ChangeRequestVO[], query?: ChangeRequestQuery): ChangeRequestVO[] {
  if (!query) return rows;
  return rows.filter(row => {
    if (query.changeType && row.changeType !== query.changeType) return false;
    if (query.status && row.status !== query.status) return false;
    if (query.keyword) {
      const k = query.keyword.toLowerCase();
      const text = `${row.requestId} ${row.oneId} ${row.customerName} ${row.content} ${row.submittedBy ?? ''}`.toLowerCase();
      if (!text.includes(k)) return false;
    }
    return true;
  });
}

/** 真实请求：剥离 axios 响应包装，只返回 data */
async function unwrap<T>(promise: AxiosPromise<T>): Promise<T> {
  const res = await promise;
  return res.data;
}

/* ============================== 1. 工作台 ============================== */
export const getDashboardStats = async (): Promise<DashboardStatVO[]> => {
  if (!useLive('dashboard')) return delay(mock.mockDashboardStats);
  const vo = await unwrap<CmdDashboardRow>(request({ url: '/cmd/dashboard/stats', method: 'get' }));
  return [
    { key: 'customerTotal', label: '客户总数', value: vo.customerTotal ?? 0, hint: '当前 Scope 可见' },
    { key: 'customerActive', label: '生效中客户', value: vo.customerActive ?? 0, hint: 'status = active' },
    { key: 'customerPending', label: '待审批客户', value: vo.customerPending ?? 0, hint: 'status = pending' },
    { key: 'hierarchyNodeCount', label: '层级节点', value: vo.hierarchyNodeCount ?? 0, hint: 'A1 / A2 / A3' }
  ];
};

export const getTodo = async (): Promise<TodoVO> => {
  if (!useLive('dashboard')) return delay(mock.mockTodo);
  const vo = await unwrap<CmdDashboardRow>(request({ url: '/cmd/dashboard/stats', method: 'get' }));
  const count = Number(vo.customerPending ?? 0);
  return {
    count,
    label: '待处理任务',
    hint: '客户创建 / 变更申请待审批',
    tag: count > 0 ? '待处理' : '无'
  };
};

export const listNotifications = (): Promise<NotificationVO[]> =>
  USE_MOCK ? delay(mock.mockNotifications) : unwrap(request({ url: '/cmd/dashboard/notifications', method: 'get' }));

/* ============================== 2. 客户主数据 ============================== */
/** 后端行 → 前端展示对象：字段名对齐（buScope→bu、createTime→updatedAt），面板无需感知后端差异 */
function toCustomerVO(row: CmdCustomerRow): CustomerVO {
  return {
    oneId: row.oneId ?? '',
    legalName: row.legalName ?? '',
    customerType: row.customerType ?? '',
    bu: row.buScope ?? '',
    productLine: row.productLine ?? '',
    sourceSystem: row.sourceSystem ?? '',
    creditCode: row.creditCode ?? '',
    address: row.address ?? '',
    payerId: row.payerId ?? '',
    status: (row.status ?? 'active') as CustomerVO['status'],
    dqScore: Number(row.dqScore ?? 0),
    versionNo: row.versionNo ?? 1,
    updatedAt: (row.updateTime ?? row.createTime ?? '').slice(0, 10)
  };
}

export const listCustomers = async (query?: CustomerQuery): Promise<CustomerVO[]> => {
  if (!useLive('customer')) return delay(mock.mockCustomers);
  const page = await unwrap<PageResult<CmdCustomerRow>>(request({ url: '/cmd/customer/list', method: 'get', params: query }));
  return (page?.rows ?? []).map(toCustomerVO);
};

/** CUSTOMER 模型的动态字段 → 客户主档列（其余动态字段整体进 ext_json 扩展属性） */
function toCmdCustomerPayload(data: CustomerForm) {
  const dynamic = data.dynamicValues ?? {};
  const extras: Record<string, string> = {};
  Object.entries(dynamic).forEach(([code, value]) => {
    if (value !== undefined && value !== null && value !== '') extras[code] = value;
  });
  // 已被主档列承载的核心字段不再重复写入扩展属性
  delete extras.legal_name;
  delete extras.credit_code;
  delete extras.address;
  return {
    legalName: data.legalName || dynamic.legal_name || '',
    creditCode: data.creditCode || dynamic.credit_code || '',
    address: data.address || dynamic.address || '',
    customerType: data.customerType,
    buScope: data.bu,
    productLine: data.productLine,
    sourceSystem: data.sourceSystem,
    payerId: data.payerId || dynamic.payer_id,
    country: dynamic.country,
    province: dynamic.province,
    city: dynamic.city,
    postalCode: dynamic.postal_code,
    taxNo: dynamic.tax_no,
    contactName: dynamic.contact_name,
    contactPhone: dynamic.contact_phone,
    contactEmail: dynamic.contact_email,
    matchState: 'NEW',
    status: 'pending',
    extJson: Object.keys(extras).length ? JSON.stringify(extras) : undefined
  };
}

/**
 * 提交客户新建申请：后端落主档 + 自动检查 + 生成统一待办 + 拉起 Warm-Flow 流程实例
 *
 * @param data 表单数据（业务上下文 + 动态字段 + OCR 回填结果）
 * @return 提交回执（One ID / 申请编号 / 当前节点 / 流程实例）
 */
export const submitCustomer = async (data: CustomerForm): Promise<CustomerSubmitVO> => {
  if (!useLive('customer')) {
    return delay<CustomerSubmitVO>({
      oneId: 'GC-DEMO0001',
      taskNo: 'AP-DEMO-0001',
      sceneCode: 'CUSTOMER_CREATE',
      sceneName: '客户创建',
      status: 'pending',
      currentNodeName: 'BU Scope 初审',
      assigneeRole: 'BU_STEWARD',
      dqScore: 92
    });
  }
  const vo = await unwrap<CustomerSubmitVO>(
    request({ url: '/cmd/customer', method: 'post', data: toCmdCustomerPayload(data) })
  );
  return vo ?? {};
};

export const deactivateCustomer = async (data: DeactivateForm): Promise<string> => {
  if (!useLive('customer')) return delay('停用申请已提交，等待审批生效');
  await unwrap(
    request({ url: `/cmd/customer/deactivate/${data.oneId}`, method: 'put', data: { reason: data.reason } })
  );
  return '停用申请已提交，等待审批生效';
};

/* ============================== 3. 元数据字段 ============================== */
/** 后端作用范围 → 前端数据层级 */
const FIELD_SCOPE_TEXT: Record<string, MetadataFieldVO['scope']> = {
  GC: 'GC Core',
  BU: 'BU Specific',
  SOURCE: 'Source System'
};

/** 后端字段类型 → 前端展示类型 */
const FIELD_TYPE_TEXT: Record<string, MetadataFieldVO['type']> = {
  STRING: 'Text',
  NUMBER: 'Number',
  DECIMAL: 'Number',
  ENUM: 'Enum',
  DATE: 'Date',
  REFERENCE: 'Reference'
};

export const listMetadataFields = async (): Promise<MetadataFieldVO[]> => {
  if (!useLive('metadata')) return delay(mock.mockMetadataFields);
  const rows = await unwrap<CmdMdFieldRow[]>(request({ url: '/cmd/metadata/field/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    code: row.fieldCode ?? '',
    label: row.fieldName ?? '',
    scope: FIELD_SCOPE_TEXT[row.scopeType ?? ''] ?? 'GC Core',
    type: FIELD_TYPE_TEXT[(row.dataType ?? '').toUpperCase()] ?? 'Text',
    required: row.isRequired === 'Y',
    bu: row.ownerBu ?? 'All',
    // 说明：md_field.model_code 是「数据模型」（如 CUSTOMER），不是客户类型，
    // 早期实现直接把它当 customerType 过滤条件，导致动态字段全部被过滤为空。
    // 字段按模型维度适用于全部客户类型，故统一取 All（与原型「根据业务上下文加载字段」一致）。
    customerType: 'All',
    status: row.status === '1' ? 'Published' : 'Draft'
  }));
};

export const saveMetadataField = async (data: MetadataFieldForm): Promise<string> => {
  if (!useLive('metadata')) return delay('字段已保存为Draft；模拟发布后将进入业务表单');
  await unwrap(
    request({
      url: '/cmd/metadata/field',
      method: 'post',
      data: {
        id: data.id,
        fieldCode: data.code,
        fieldName: data.label,
        dataType: (data.type ?? 'Text').toUpperCase(),
        scopeType: data.scope === 'BU Specific' ? 'BU' : data.scope === 'Source System' ? 'SOURCE' : 'GC',
        isRequired: data.required ? 'Y' : 'N',
        ownerBu: data.bu,
        modelCode: data.customerType,
        status: data.status === 'Published' ? '1' : '0'
      }
    })
  );
  return '字段已保存；发布后将进入业务表单';
};

export const listModelVersions = async (): Promise<ModelVersionVO[]> => {
  if (!useLive('metadata')) return delay(mock.mockModelVersions);
  const rows = await unwrap<CmdVersionRow[]>(request({ url: '/cmd/metadata/version/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    version: row.version ?? '',
    ruleCount: row.ruleCount ?? 0,
    status: row.status === 'Draft' ? 'Draft' : 'Current',
    publishedAt: row.publishedAt
  }));
};

export const listValueSets = async (): Promise<typeof mock.mockValueSets> => {
  if (!useLive('metadata')) return delay(mock.mockValueSets);
  const rows = await unwrap<CmdValueSetRow[]>(request({ url: '/cmd/metadata/valueset/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    code: row.setCode ?? '',
    name: row.setName ?? '',
    type: row.setType ?? 'Enum',
    values: row.remark ?? '-',
    status: row.status === '0' ? 'Published' : 'Draft'
  }));
};

export const publishModelVersion = async (): Promise<string> => {
  if (!useLive('metadata')) return delay('配置版本v1.5已发布；Business User表单将按元数据自动刷新');
  return unwrap(request({ url: '/cmd/metadata/version/publish', method: 'put' }));
};

/* ============================== 4. 数据质量 ============================== */
/** DQ 规则清单：前端表格直接消费后端行契约（ruleCode / ruleName / dimension / ...） */
export const listDqRules = async (): Promise<DqRuleRow[]> => {
  if (!useLive('dq')) return delay(mock.mockDqRules);
  const rows = await unwrap<DqRuleRow[]>(request({ url: '/cmd/dq/rule/list', method: 'get' }));
  return rows ?? [];
};

export const saveDqRule = async (rule: DqRuleRow): Promise<string> => {
  if (!useLive('dq')) return delay('DQ规则已保存');
  return unwrap(request({ url: '/cmd/dq/rule', method: 'post', data: rule }));
};

export const deleteDqRule = async (id: number): Promise<string> => {
  if (!useLive('dq')) return delay('DQ规则已删除');
  return unwrap(request({ url: `/cmd/dq/rule/${id}`, method: 'delete' }));
};

export const getDqScorecard = (oneId?: string): Promise<DqScorecardVO> =>
  useLive('dq') ? unwrap(request({ url: '/cmd/dq/scorecard', method: 'get', params: { oneId } })) : delay(mock.mockDqScorecard);

export const simulateDq = (data: Record<string, string>): Promise<DqSimulateResultVO[]> =>
  useLive('dq') ? unwrap(request({ url: '/cmd/dq/simulate', method: 'post', data })) : delay(mock.mockDqSimulate);

export const reEvaluateDq = (data: ReEvaluateForm): Promise<string> =>
  useLive('dq') ? unwrap(request({ url: '/cmd/dq/reEvaluate', method: 'post', data })) : delay('历史数据重评估任务已创建，旧规则版本与旧分数保留');

/** 重评估影响预估（Demo） */
export const getReEvaluateImpact = (): Promise<ReEvaluateImpactVO[]> =>
  useLive('dq') ? unwrap(request({ url: '/cmd/dq/reEvaluate/impact', method: 'get' })) : delay(mock.mockReEvaluateImpact);

/* ============================== 5. 匹配与重复治理 ============================== */
export const listMatchRules = async (): Promise<MatchRuleVO[]> => {
  if (!useLive('match')) return delay(mock.mockMatchRules);
  const rows = await unwrap<MatchRuleRow[]>(request({ url: '/cmd/match/rule/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    dimension: row.dimension ?? '',
    role: row.role ?? '',
    threshold: row.threshold ?? '',
    result: row.result ?? '',
    enabled: row.enabled ?? true
  }));
};

export const saveMatchRule = async (rule: MatchRuleVO): Promise<string> => {
  if (!useLive('match')) return delay('匹配规则已保存');
  return unwrap(request({ url: '/cmd/match/rule', method: 'post', data: rule }));
};

export const deleteMatchRule = async (id: number): Promise<string> => {
  if (!useLive('match')) return delay('匹配规则已删除');
  return unwrap(request({ url: `/cmd/match/rule/${id}`, method: 'delete' }));
};

export const simulateMatch = async (): Promise<MatchRuleVO[]> => {
  return useLive('match') ? unwrap(request({ url: '/cmd/match/simulate', method: 'post' })) : delay(mock.mockMatchRules);
};

export const getDuplicateCandidate = (): Promise<DuplicateCandidateVO> =>
  USE_MOCK ? delay(mock.mockDuplicateCandidate) : unwrap(request({ url: '/cmd/duplication/candidate', method: 'get' }));

export const linkExistingOneId = (oneId: string): Promise<string> =>
  USE_MOCK ? delay(`已关联One ID ${oneId}`) : unwrap(request({ url: '/cmd/duplication/link', method: 'put', data: { oneId } }));

export const confirmNewCustomer = (): Promise<string> =>
  USE_MOCK ? delay('已进入新客户审批') : unwrap(request({ url: '/cmd/duplication/confirmNew', method: 'put' }));

/* ============================== 6. 批量导入 ============================== */
/** 后端任务状态 → 前端展示文案 */
const IMPORT_STATUS_TEXT: Record<string, ImportJobStatus> = {
  WAIT_REVIEW: 'Waiting for Review',
  RUNNING: 'In Progress',
  PARTIAL_SUCCESS: 'Partial Success',
  FAILED: 'Failed',
  COMPLETED: 'Completed'
};

/** 后端行 → 前端展示对象 */
function toImportJobVO(row: CmdImportJobRow): ImportJobVO {
  return {
    jobId: row.jobCode ?? '',
    fileName: row.fileName ?? '',
    totalRows: row.totalCount ?? 0,
    status: IMPORT_STATUS_TEXT[row.jobStatus ?? ''] ?? 'Waiting for Review',
    submittedAt: row.submitTime ?? row.createTime ?? '',
    submittedBy: row.submitBy ?? ''
  };
}

export const listImportJobs = async (): Promise<ImportJobVO[]> => {
  if (!useLive('import')) return delay(mock.mockImportJobs);
  const page = await unwrap<PageResult<CmdImportJobRow>>(
    request({ url: '/cmd/import/job/list', method: 'get', params: { pageNum: 1, pageSize: 100 } })
  );
  return (page.rows ?? []).map(toImportJobVO);
};

export const getBatchResult = async (jobId: string): Promise<BatchResultVO> => {
  if (!useLive('import')) return delay(mock.mockBatchResult);
  const vo = await unwrap<CmdImportResultRow>(request({ url: `/cmd/import/job/${jobId}/result`, method: 'get' }));
  return {
    jobId: vo.jobCode ?? jobId,
    exact: vo.exact ?? 0,
    suspected: vo.suspected ?? 0,
    created: vo.created ?? 0,
    review: vo.review ?? 0,
    invalid: vo.invalid ?? 0,
    routes: vo.routes ?? []
  };
};

export const createImportJob = async (fileName: string): Promise<string> => {
  if (!useLive('import')) return delay(`导入任务已创建：${fileName}`);
  const jobCode = await unwrap<string>(request({ url: '/cmd/import/job', method: 'post', data: { fileName } }));
  return `导入任务已创建：${jobCode}`;
};

export const listImportTemplates = async (): Promise<ImportTemplateVO[]> => {
  if (!useLive('import')) return delay(mock.mockImportTemplates);
  const rows = await unwrap<CmdImportTemplateRow[]>(request({ url: '/cmd/import/template/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    templateCode: row.templateCode ?? '',
    name: row.templateName ?? '',
    context: row.scene ?? '',
    version: row.versionNo ?? '',
    fieldCount: row.fieldCount ?? 0,
    status: row.status === 'Published' ? 'Published' : 'Draft',
    customerType: row.customerType ?? '',
    bu: row.buScope ?? '',
    productLine: row.productLine ?? '',
    sourceSystem: row.sourceSystem ?? ''
  }));
};

/**
 * 下载导入模板
 * <p>
 * 模板不在前端生成：后端按 cmd_import_template_mapping 动态生成仅含表头的 Excel，
 * 业务填写后再上传。返回的是文件流，因此不能用 unwrap（会取到 Blob.data）。
 */
export const downloadImportTemplate = async (templateCode: string, fileName: string): Promise<void> => {
  if (!useLive('import')) {
    ElMessage.info(`演示模式：模板 ${templateCode} 下载（接入后端后可获取真实文件）`);
    return;
  }
  const blob = (await request({
    url: `/cmd/import/template/${templateCode}/download`,
    method: 'get',
    responseType: 'blob'
  })) as unknown as Blob;
  saveBlob(blob, fileName);
};

/**
 * 上传填写好的模板文件
 * 数据流向：文件落盘 → 按字段映射解析 → 写 cmd_import_job + cmd_import_row
 */
export const uploadImportJob = async (data: ImportUploadForm): Promise<string> => {
  if (!useLive('import')) return delay(`导入任务已创建：${data.file.name}`);
  const formData = new FormData();
  formData.append('file', data.file);
  formData.append('templateCode', data.templateCode);
  if (data.errorStrategy) formData.append('errorStrategy', data.errorStrategy);
  if (data.duplicateStrategy) formData.append('duplicateStrategy', data.duplicateStrategy);
  const jobCode = await unwrap<string>(
    request({
      url: '/cmd/import/job/upload',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  );
  return `导入任务已创建：${jobCode}`;
};

export const listTemplateMappings = async (templateCode?: string): Promise<TemplateMappingVO[]> => {
  if (!useLive('import')) return delay(mock.mockTemplateMappings);
  const rows = await unwrap<CmdTemplateMappingRow[]>(
    request({ url: '/cmd/import/template/mapping', method: 'get', params: templateCode ? { templateCode } : {} })
  );
  return (rows ?? []).map(row => ({
    sourceColumn: row.columnName ?? '',
    targetField: row.fieldCode ?? '',
    transform: row.convertRule ?? '',
    errorStrategy: row.errorStrategy ?? ''
  }));
};


/* ============================== 7. 客户层级 ============================== */
/* ---- 层级：后端返回平铺列表 + 父指针，树结构与展示字段在此统一转换 ---- */
const HIERARCHY_TYPE_LABEL: Record<string, string> = {
  COMMERCIAL: 'Commercial Entity',
  LEGAL: 'Main Account',
  DOOR: 'Door'
};

function toHierarchyNode(row: CmdHierarchyNodeRow): HierarchyNodeVO {
  const level = row.level ?? '';
  const name = row.legalName ?? '';
  const from = (row.effectiveFrom ?? '').toString().slice(0, 10);
  const to = (row.effectiveTo ?? '').toString().slice(0, 10);
  // 从后端 fullPath（/oneId/oneId/...）解析祖先 One ID 列表
  const ancestorIds = (row.fullPath ?? '')
    .split('/')
    .map(s => s.trim())
    .filter(Boolean);
  return {
    id: row.oneId ?? row.nodeCode ?? '',
    level,
    type: HIERARCHY_TYPE_LABEL[row.hierarchyType ?? ''] ?? row.hierarchyType ?? '',
    label: `${level} · ${name}`,
    name,
    oneId: row.oneId ?? '',
    payerId: row.payerOneId ?? '—',
    payerName: row.payerOneId ?? '—',
    childrenCount: row.childrenCount ?? 0,
    descendants: row.descendants ?? 0,
    parent: row.parentOneId ?? '无',
    path: (row.pathNames ?? name).split('/').join(' / '),
    ancestorIds,
    validity: `${from || '—'} → ${to || '9999-12-31'}`,
    status: row.status === 'active' ? 'Active' : row.status === 'expired' ? 'Expired' : 'Future',
    children: []
  };
}

/** 平铺列表按 parentOneId 组装成树，并回填父节点显示名 */
function buildHierarchyTree(rows: CmdHierarchyNodeRow[]): HierarchyNodeVO[] {
  const nodes = rows.map(toHierarchyNode);
  const byOneId = new Map<string, HierarchyNodeVO>();
  rows.forEach((row, index) => {
    if (row.oneId) byOneId.set(row.oneId, nodes[index]);
  });
  const roots: HierarchyNodeVO[] = [];
  rows.forEach((row, index) => {
    const parent = row.parentOneId ? byOneId.get(row.parentOneId) : undefined;
    if (parent) {
      nodes[index].parentName = parent.name;
      (parent.children ??= []).push(nodes[index]);
    } else {
      roots.push(nodes[index]);
    }
  });
  return roots;
}

export const getHierarchy = async (): Promise<HierarchyNodeVO[]> => {
  if (!useLive('hierarchy')) return delay(mock.mockHierarchy);
  const rows = await unwrap<CmdHierarchyNodeRow[]>(request({ url: '/cmd/hierarchy/nodes', method: 'get' }));
  return buildHierarchyTree(rows ?? []);
};

export const getHierarchyNode = async (key: string): Promise<HierarchyNodeVO | undefined> => {
  if (!useLive('hierarchy')) return delay(mock.mockHierarchyNodes[key]);
  const row = await unwrap<CmdHierarchyNodeRow>(request({ url: `/cmd/hierarchy/node/${key}`, method: 'get' }));
  return row ? toHierarchyNode(row) : undefined;
};

export const searchHierarchy = async (keyword: string): Promise<HierarchyNodeVO[]> => {
  if (!useLive('hierarchy')) {
    const k = keyword.toLowerCase();
    return delay(
      Object.values(mock.mockHierarchyNodes).filter(
        n => n.name.toLowerCase().includes(k) || n.oneId.toLowerCase().includes(k)
      )
    );
  }
  const rows = await unwrap<CmdHierarchyNodeRow[]>(
    request({ url: '/cmd/hierarchy/nodes', method: 'get', params: { keyword } })
  );
  return (rows ?? []).map(toHierarchyNode);
};

export const addHierarchyRelation = async (data: HierarchyRelationForm): Promise<string> => {
  if (!useLive('hierarchy')) return delay('层级关系已提交，Loop Check 通过');
  // 前端表单字段 → 后端 BO 字段映射（面板代码不感知后端命名）
  await unwrap(
    request({
      url: '/cmd/hierarchy/relation',
      method: 'post',
      data: {
        hierarchyType: data.hierarchyType,
        relationType: data.relationType,
        parentOneId: data.parentId,
        childOneId: data.childId,
        payerOneId: data.payerOneId,
        effectiveFrom: data.effectiveDate,
        changeReason: data.reason
      }
    })
  );
  return '层级关系已保存，Loop Check 通过';
};

export const loopCheck = (): Promise<string> =>
  USE_MOCK
    ? delay('检测到循环路径：A1-000128 → A2-0188 → A1-000128。系统阻止提交，并保留冲突路径用于修正。')
    : unwrap(request({ url: '/cmd/hierarchy/loopCheck', method: 'get' }));

/* ============================== 8. 变更与停用 ============================== */
/* ---- 变更与停用：后端状态 → 页面状态 ---- */
const CHANGE_STATUS_MAP: Record<string, ChangeStatus> = {
  DRAFT: 'Draft',
  PENDING: 'Under Review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  EFFECTED: 'Approved'
};

function toChangeRequestVO(row: CmdChangeRequestRow): ChangeRequestVO {
  return {
    requestId: row.requestCode ?? '',
    oneId: row.oneId ?? '',
    customerName: row.legalName ?? '',
    bu: row.buScope ?? '',
    changeType: row.changeType === 'Deactivate' ? 'Deactivate' : 'Update',
    content: row.changeReason ?? '',
    status: CHANGE_STATUS_MAP[row.status ?? ''] ?? 'Draft',
    submittedAt: (row.createTime ?? '').toString().slice(0, 16).replace('T', ' '),
    submittedBy: 'Business User'
  };
}

export const listChangeRequests = async (query?: ChangeRequestQuery): Promise<ChangeRequestVO[]> => {
  if (!useLive('change')) return delay(filterChangeRequests(mock.mockChangeRequests, query));
  const page = await unwrap<PageResult<CmdChangeRequestRow>>(
    request({
      url: '/cmd/change/list',
      method: 'get',
      // 后端查询字段名与前端略有差异，在此对齐（keyword 模糊匹配 编号/One ID/客户名）
      params: { ...query, keyword: query?.keyword, changeType: query?.changeType, status: query?.status }
    })
  );
  return (page?.rows ?? []).map(toChangeRequestVO);
};

export const submitChangeRequest = async (data: ChangeRequestForm): Promise<string> => {
  if (!useLive('change')) return delay('变更申请已提交，进入审批流程');
  await unwrap(
    request({
      url: '/cmd/change',
      method: 'post',
      data: {
        oneId: data.oneId,
        changeType: data.changeType,
        targetStatus: data.changeType === 'Deactivate' ? 'inactive' : 'active',
        changeReason: data.field ? `${data.field}：${data.newValue}（${data.reason}）` : data.reason
      }
    })
  );
  return '变更申请已保存至数据库，进入审批流程';
};

export const getChangeDetail = (requestId: string): Promise<{ diffs: ChangeDiffVO[]; trail: ApprovalTrailVO[] }> =>
  USE_MOCK
    ? delay({ diffs: mock.mockChangeDiffs, trail: mock.mockChangeTrail })
    : unwrap(request({ url: `/cmd/change/${requestId}/detail`, method: 'get' }));

export const getDeactivateResult = (oneId: string): Promise<DeactivateResultVO> =>
  USE_MOCK ? delay(mock.mockDeactivateResult) : unwrap(request({ url: `/cmd/change/${oneId}/deactivateResult`, method: 'get' }));

/* ============================== 9. 审批 ============================== */
export const getApprovalFlow = (key: string): Promise<ApprovalFlowVO> =>
  USE_MOCK
    ? delay(mock.mockApprovalFlows[key] ?? mock.mockApprovalFlows.approvalHE)
    : unwrap(request({ url: `/cmd/approval/flow/${key}`, method: 'get' }));

export const listApprovalInstances = (): Promise<ApprovalInstanceVO[]> =>
  USE_MOCK ? delay(mock.mockApprovalInstances) : unwrap(request({ url: '/cmd/approval/instance/list', method: 'get' }));

/* ---- 治理与审批合并工作台（原型 2.2） ---- */
/** 后端 SLA 状态 → 前端展示文案 */
const SLA_TEXT: Record<string, string> = { NORMAL: '正常', DUE_SOON: '临近', OVERDUE: '超时' };

/** 后端行 → 前端清单行 */
function toApprovalTaskVO(row: CmdApprovalTaskRow): ApprovalTaskVO {
  return {
    taskId: row.taskNo ?? '',
    oneId: row.oneId ?? '',
    customerName: row.bizTitle ?? '',
    taskType: row.bizType ?? '',
    source: row.sceneCode ?? '',
    bu: row.buScope ?? '',
    dq: row.dqScore == null ? '—' : String(row.dqScore),
    match: row.duplicateState ?? '—',
    sla: SLA_TEXT[row.slaState ?? ''] ?? '正常',
    risk: (['High', 'Medium', 'Low'].includes(row.riskLevel ?? '') ? row.riskLevel : 'Medium') as ApprovalTaskVO['risk'],
    detailType: row.bizType ?? ''
  };
}

/** 按分类拉取清单（后端以 taskCategory 区分队列表） */
async function fetchTasksByCategory(scope: 'bu' | 'gc', category: string): Promise<ApprovalTaskVO[]> {
  const page = await unwrap<PageResult<CmdApprovalTaskRow>>(
    request({
      url: '/cmd/approval/list',
      method: 'get',
      params: { scope: scope.toUpperCase(), taskCategory: category, pageNum: 1, pageSize: 100 }
    })
  );
  return (page.rows ?? []).map(toApprovalTaskVO);
}

export const getApprovalKpis = async (scope: 'bu' | 'gc'): Promise<ApprovalKpiVO[]> => {
  if (!useLive('approval')) return delay(mock.mockApprovalKpis[scope]);
  const rows = await unwrap<CmdApprovalKpiRow[]>(
    request({ url: '/cmd/approval/kpi', method: 'get', params: { scope: scope.toUpperCase() } })
  );
  return (rows ?? []).map(row => ({ label: row.label ?? '', value: row.value ?? 0, hint: row.hint ?? '' }));
};

/** 全部待办 = 审批任务 + 治理复核 + 升级与退回（后端三个分类合并） */
export const listApprovalTasks = async (scope: 'bu' | 'gc'): Promise<ApprovalTaskVO[]> => {
  if (!useLive('approval')) return delay(mock.mockApprovalTasks[scope]);
  const [approval, governance, returned] = await Promise.all([
    fetchTasksByCategory(scope, 'APPROVAL'),
    fetchTasksByCategory(scope, 'GOVERNANCE'),
    fetchTasksByCategory(scope, 'RETURNED')
  ]);
  return [...approval, ...governance, ...returned];
};

export const getApprovalReturned = async (scope: 'bu' | 'gc'): Promise<ApprovalTaskVO[]> => {
  if (!useLive('approval')) return delay(mock.mockApprovalReturned[scope]);
  return fetchTasksByCategory(scope, 'RETURNED');
};

export const getApprovalDone = async (scope: 'bu' | 'gc'): Promise<ApprovalTaskVO[]> => {
  if (!useLive('approval')) return delay(mock.mockApprovalDone[scope]);
  return fetchTasksByCategory(scope, 'DONE');
};

export const getApprovalTaskDetail = async (taskNo: string): Promise<ApprovalTaskDetailVO> => {
  if (!useLive('approval')) return delay(mock.mockApprovalDetail[taskNo] ?? mock.mockApprovalDetail.create);
  const vo = await unwrap<CmdApprovalDetailRow>(request({ url: `/cmd/approval/task/${taskNo}/detail`, method: 'get' }));
  return {
    id: String(vo.id ?? ''),
    oneId: vo.oneId ?? '',
    name: vo.name ?? '',
    scene: vo.scene ?? '',
    submitter: vo.submitter ?? '',
    currentNode: vo.currentNode ?? '',
    sla: vo.sla ?? '',
    dq: vo.dq ?? '',
    duplicate: vo.duplicate ?? '',
    evidence: vo.evidence ?? '',
    decisions: vo.decisions ?? [],
    actions: (vo.actions ?? []).map(a => ({
      key: a.key ?? '',
      label: a.label ?? '',
      type: (['primary', 'success', 'warning', 'danger', 'info'].includes(a.type ?? '')
        ? a.type
        : 'primary') as ApprovalTaskDetailVO['actions'][number]['type']
    }))
  };
};

/** 提交审批动作（批准 / 拒绝 / 退回 / 升级），落库为审批轨迹 */
export const submitApprovalAction = async (data: {
  taskId: number | string;
  actionType: string;
  opinion?: string;
}): Promise<void> => {
  if (!useLive('approval')) return delay(undefined);
  await unwrap(
    request({
      url: '/cmd/approval/action',
      method: 'post',
      data: {
        taskId: data.taskId,
        actionType: data.actionType,
        opinion: data.opinion
      }
    })
  );
};

/**
 * 流程跟踪：泳道图步骤条 + Warm-Flow 实例进度 + Data context state
 * 后端 GET /cmd/flow/trace/{taskNo}（CmdFlowTraceController）
 */
/** 引擎图形映射（BPMN 风格） */
const mapGraph = (g?: FlowTraceVO['graph'] & {
  nodes?: Array<Partial<FlowGraphNodeVO>>;
  edges?: Array<Partial<FlowGraphEdgeVO>>;
}): FlowGraphVO | undefined => {
  if (!g || !g.nodes?.length) return undefined;
  return {
    definitionId: g.definitionId,
    flowCode: g.flowCode,
    lanes: g.lanes,
    nodes: (g.nodes ?? []).map(n => ({
      nodeCode: n.nodeCode ?? '',
      nodeName: n.nodeName ?? '',
      shape: (['CIRCLE', 'RECT', 'DIAMOND'].includes(n.shape ?? '') ? n.shape : 'RECT') as FlowGraphNodeVO['shape'],
      nodeType: n.nodeType,
      lane: n.lane,
      phase: n.phase,
      phaseName: n.phaseName,
      note: n.note,
      x: n.x ?? 0,
      y: n.y ?? 0,
      status: (['COMPLETED', 'CURRENT', 'PENDING'].includes(n.status ?? '')
        ? n.status
        : 'PENDING') as FlowGraphNodeVO['status'],
      approver: n.approver,
      actionTime: n.actionTime
    })),
    edges: (g.edges ?? []).map(e => ({
      from: e.from ?? '',
      to: e.to ?? '',
      label: e.label ?? '',
      skipType: e.skipType ?? '',
      condition: e.condition,
      passed: e.passed ?? false
    }))
  };
};
export const getFlowTrace = async (taskNo: string, detailType = 'create'): Promise<FlowTraceVO> => {
  if (!useLive('approval')) return delay(mock.buildMockFlowTrace(taskNo, detailType));
  const vo = await unwrap<CmdFlowTraceRow>(request({ url: `/cmd/flow/trace/${taskNo}`, method: 'get' }));
  const steps = (vo.steps ?? []).map((s, i) => ({
    order: s.order ?? i + 1,
    phase: s.phase ?? 0,
    phaseName: s.phaseName ?? '',
    lane: s.lane ?? '',
    nodeCode: s.nodeCode ?? '',
    nodeName: s.nodeName ?? '',
    nodeType: (['AUTO', 'MANUAL', 'GATEWAY'].includes(s.nodeType ?? '')
      ? s.nodeType
      : 'AUTO') as FlowTraceVO['steps'][number]['nodeType'],
    status: (['COMPLETED', 'CURRENT', 'PENDING', 'TERMINATED'].includes(s.status ?? '')
      ? s.status
      : 'PENDING') as FlowTraceVO['steps'][number]['status'],
    assignee: s.assignee,
    operator: s.operator,
    actionTime: s.actionTime,
    opinion: s.opinion,
    note: s.note
  }));
  return {
    taskNo: vo.taskNo ?? taskNo,
    bizTitle: vo.bizTitle ?? '',
    bizType: vo.bizType ?? '',
    sceneCode: vo.sceneCode ?? '',
    sceneName: vo.sceneName ?? '',
    status: vo.status ?? '',
    currentNodeName: vo.currentNodeName ?? '',
    assigneeName: vo.assigneeName,
    assigneeRole: vo.assigneeRole,
    buScope: vo.buScope,
    riskLevel: vo.riskLevel,
    slaState: vo.slaState,
    submitTime: vo.submitTime,
    slaDue: vo.slaDue,
    flowCode: vo.flowCode,
    flowName: vo.flowName,
    slaHours: vo.slaHours,
    flowInstanceId: vo.flowInstanceId,
    flowTaskId: vo.flowTaskId,
    flowDefinitionId: vo.flowDefinitionId,
    flowStatus: vo.flowStatus,
    totalSteps: vo.totalSteps ?? steps.length,
    completedSteps: vo.completedSteps ?? 0,
    progressPercent: vo.progressPercent ?? 0,
    engineBound: vo.engineBound ?? false,
    graph: mapGraph(vo.graph),
    bypass: vo.bypass,
    steps,
    contextVars: (vo.contextVars ?? []).map(v => ({ name: v.name ?? '', value: v.value })),
    actions: (vo.actions ?? []).map(a => ({
      actionType: a.actionType ?? '',
      actionName: a.actionName,
      operatorName: a.operatorName,
      operatorRole: a.operatorRole,
      actionTime: a.actionTime,
      opinion: a.opinion
    }))
  };
};

/**
 * 启动 Warm-Flow 流程实例（业务单据提交场景），返回实例 ID
 * 后端 POST /cmd/flow/instance/{taskNo}/start
 */
export const startFlowInstance = async (taskNo: string): Promise<number | string> => {
  if (!useLive('approval')) return delay(1801);
  return unwrap<number>(request({ url: `/cmd/flow/instance/${taskNo}/start`, method: 'post' }));
};

/**
 * 流程中心：列出所有 CMD 业务场景（V6.1 总设计业务流）及其 Warm-Flow 部署状态
 * 后端 GET /cmd/flow/scenes（CmdFlowTraceController）
 */
export const listFlowScenes = async (): Promise<FlowSceneVO[]> => {
  if (!useLive('approval')) return delay(mock.mockFlowScenes);
  return unwrap<FlowSceneVO[]>(request({ url: '/cmd/flow/scenes', method: 'get' }));
};

/**
 * 流程中心：按场景查看流程详细图（泳道图）
 * 后端 GET /cmd/flow/graph/scene/{sceneCode}
 *
 * @param sceneCode 场景编码
 * @param taskNo    可选。传入 = 实例视图（按该次执行进度点亮）；不传 = 定义视图（蓝图）
 */
export const getFlowGraphByScene = async (sceneCode: string, taskNo?: string): Promise<FlowGraphVO> => {
  if (!useLive('approval')) {
    return delay(taskNo ? mock.buildMockInstanceGraph(sceneCode, taskNo) : mock.buildMockSceneGraph(sceneCode));
  }
  const g = await unwrap<FlowGraphVO>(
    request({ url: `/cmd/flow/graph/scene/${sceneCode}`, method: 'get', params: taskNo ? { taskNo } : undefined })
  );
  return mapGraph(g) ?? { nodes: [], edges: [] };
};

/**
 * 工作流步骤执行日志：按客户 One ID 或任务编号查询
 * 后端 GET /cmd/approval/workflow-steps
 * <p>用于「流程跟踪」展示每一步（提交 / 系统自动 / 人工决策），每一步都带客户 One ID。</p>
 *
 * @param params oneId 或 taskNo（二选一）
 */
export const getWorkflowSteps = async (params: { oneId?: string; taskNo?: string }): Promise<WorkflowStepVO[]> => {
  if (!useLive('approval')) return delay<WorkflowStepVO[]>([]);
  return unwrap<WorkflowStepVO[]>(
    request({ url: '/cmd/approval/workflow-steps', method: 'get', params })
  );
};

/**
 * 流程中心：流程实例记录（每一次执行过的工作流，可查看 / 用 Graph 回看泳道图）
 * 后端 GET /cmd/flow/instances（CmdFlowTraceController）
 */
export const listFlowInstances = async (query: FlowInstanceQuery = {}): Promise<PageResult<FlowInstanceVO>> => {
  if (!useLive('approval')) {
    const kw = (query.keyword ?? '').trim().toLowerCase();
    const rows = mock.mockFlowInstances.filter(r => {
      const matchKw =
        !kw ||
        r.taskNo.toLowerCase().includes(kw) ||
        (r.bizTitle ?? '').toLowerCase().includes(kw) ||
        (r.oneId ?? '').toLowerCase().includes(kw);
      const matchStatus = !query.status || r.status === query.status;
      const matchBiz = !query.bizType || r.bizType === query.bizType;
      const runState = query.runState;
      const matchRun = !runState
        || (runState === 'DONE' && r.status === 'APPROVED')
        || (runState === 'RUNNING' && r.status !== 'APPROVED')
        || (runState === 'NEW' && !r.engineBound);
      return matchKw && matchStatus && matchBiz && matchRun;
    });
    return delay({ rows, total: rows.length });
  }
  return unwrap<PageResult<FlowInstanceVO>>(
    request({ url: '/cmd/flow/instances', method: 'get', params: { pageNum: 1, pageSize: 100, ...query } })
  );
};

/**
 * 流程中心：将单个场景部署（幂等）到 Warm-Flow 引擎
 * 后端 POST /cmd/flow/deploy/{sceneCode}
 */
export const deployFlowScene = async (sceneCode: string): Promise<number | string> => {
  if (!useLive('approval')) return delay(1000 + Math.floor(Math.random() * 900));
  return unwrap<number>(request({ url: `/cmd/flow/deploy/${sceneCode}`, method: 'post' }));
};

/* ============================== 10. One ID ============================== */
export const getOneIdRule = async (): Promise<OneIdRuleVO> => {
  if (!useLive('oneid')) return delay(mock.mockOneIdRule);
  const row = await unwrap<CmdOneIdRuleRow>(request({ url: '/cmd/oneid/rule', method: 'get' }));
  return {
    ruleName: row.ruleName ?? '',
    status: row.status === '0' ? 'Published' : 'Draft',
    object: row.scopeType === 'GC' ? 'Customer / A1' : 'Customer / BU',
    serialLength: `${row.serialLength ?? 6} digits`,
    prefix: row.prefix ?? '',
    separator: '-'
  };
};

export const publishOneIdRule = async (): Promise<string> => {
  if (!useLive('oneid')) return delay('One ID规则v1.2已模拟发布');
  return unwrap(request({ url: '/cmd/oneid/rule/publish', method: 'put' }));
};

export const copyOneIdRule = async (): Promise<string> => {
  if (!useLive('oneid')) return delay('已复制规则为Draft v1.2');
  return unwrap(request({ url: '/cmd/oneid/rule/copy', method: 'put' }));
};

export const listOneIdPolicies = async (): Promise<OneIdPolicyVO[]> => {
  if (!useLive('oneid')) return delay(mock.mockOneIdPolicies);
  const rows = await unwrap<CmdOneIdPolicyRow[]>(request({ url: '/cmd/oneid/policy/list', method: 'get' }));
  return (rows ?? []).map(row => ({ event: row.event ?? '', handling: row.handling ?? '' }));
};

export const getOneIdHistory = async (oneId: string): Promise<OneIdEventVO[]> => {
  if (!useLive('oneid')) return delay(mock.mockOneIdEvents);
  const rows = await unwrap<CmdAuditEventRow[]>(request({ url: `/cmd/oneid/${oneId}/history`, method: 'get' }));
  return (rows ?? []).map(row => ({
    date: row.eventTime ?? '',
    stage: row.eventType ?? '',
    description: row.eventName ?? ''
  }));
};

export const listLegacyMappings = async (): Promise<LegacyMappingVO[]> => {
  if (!useLive('oneid')) return delay(mock.mockLegacyMappings);
  const rows = await unwrap<CmdLegacyMappingRow[]>(request({ url: '/cmd/oneid/legacy/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    oneId: row.oneId ?? '',
    sourceSystem: row.sourceSystem ?? '',
    legacyCode: row.sourceCode ?? '',
    bu: row.buScope ?? '',
    status: row.status === '0' ? 'active' : 'inactive'
  }));
};

/* ============================== 11. 集成监控 ============================== */
/** 后端集成运行状态 → 前端展示文案 */
const INTEGRATION_STATUS_TEXT: Record<string, IntegrationRunVO['status']> = {
  SUCCESS: 'Success',
  FAILED: 'Failed',
  RETRYING: 'Retrying',
  RUNNING: 'Retrying'
};

export const listIntegrationRuns = async (): Promise<IntegrationRunVO[]> => {
  if (!useLive('integration')) return delay(mock.mockIntegrationRuns);
  const page = await unwrap<PageResult<CmdIntegrationRunRow>>(
    request({ url: '/cmd/integration/run/list', method: 'get', params: { pageNum: 1, pageSize: 100 } })
  );
  return (page.rows ?? []).map(row => ({
    runId: row.runCode ?? '',
    direction: row.direction === 'INBOUND' ? 'Inbound' : 'Outbound',
    system: row.targetSystem ?? '',
    status: INTEGRATION_STATUS_TEXT[row.runStatus ?? ''] ?? 'Failed',
    detail: row.errorMessage ?? '',
    attempt: `${row.attemptCount ?? 0}/${row.maxAttempt ?? 3}`,
    record: String(row.totalCount ?? 0)
  }));
};

export const retryIntegration = async (runId: string): Promise<string> => {
  if (!useLive('integration')) return delay(`已重试同步：${runId}`);
  return unwrap(request({ url: `/cmd/integration/run/${runId}/retry`, method: 'put' }));
};

export const saveIntegrationConn = async (data: IntegrationConnForm): Promise<string> => {
  if (!useLive('integration')) return delay('集成连接已保存，等待连通性测试');
  const code = await unwrap<string>(request({ url: '/cmd/integration/conn', method: 'post', data }));
  return `集成连接已保存（${code}），等待连通性测试`;
};

/* ============================== 12. 审计 / 权限 / 覆盖 ============================== */
/** 后端审计结果 → 前端展示文案 */
const AUDIT_RESULT_TEXT: Record<string, AuditEventVO['result']> = {
  SUCCESS: 'Success',
  TEST: 'Tested',
  FAILED: 'Failed'
};

export const listAuditEvents = async (keyword?: string): Promise<AuditEventVO[]> => {
  const kw = (keyword ?? '').trim().toLowerCase();
  if (!useLive('audit')) {
    const rows = mock.mockAuditEvents as AuditEventVO[];
    return delay(
      kw ? rows.filter(r => r.id.toLowerCase().includes(kw) || r.event.toLowerCase().includes(kw)) : rows
    );
  }
  const page = await unwrap<PageResult<CmdAuditEventRow>>(
    request({ url: '/cmd/audit/list', method: 'get', params: { pageNum: 1, pageSize: 100, keyword: kw || undefined } })
  );
  return (page.rows ?? []).map(row => ({
    id: row.eventId ?? '',
    time: row.eventTime ?? '',
    event: row.eventName ?? '',
    role: row.operatorRole ?? '',
    result: AUDIT_RESULT_TEXT[row.result ?? ''] ?? 'Success',
    oneId: row.oneId ?? '',
    bizId: row.bizId ?? ''
  }));
};

export const exportAudit = async (data: AuditExportForm): Promise<string> => {
  if (!useLive('audit')) return delay('审计报告已生成并置于下载中心');
  const code = await unwrap<string>(request({ url: '/cmd/audit/export', method: 'post', data }));
  return `审计报告已生成（${code}），置于下载中心`;
};

export const getPermissionMatrix = async (): Promise<PermissionMatrixVO[]> => {
  if (!useLive('permission')) return delay(mock.mockPermissionMatrix);
  const rows = await unwrap<CmdPermissionMatrixRow[]>(request({ url: '/cmd/permission/matrix', method: 'get' }));
  return (rows ?? []).map(row => ({
    capability: row.capability ?? '',
    business: row.business ?? '',
    steward: row.steward ?? '',
    admin: row.admin ?? '',
    auditor: row.auditor ?? ''
  }));
};

export const listRolePermissions = async (): Promise<RolePermissionVO[]> => {
  if (!useLive('permission')) return delay(mock.mockRolePermissions);
  const rows = await unwrap<CmdRoleRow[]>(request({ url: '/cmd/permission/role/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    roleCode: row.roleCode ?? '',
    id: row.id,
    role: row.roleName ?? '',
    scope: row.defaultBu ? `${row.scopeType ?? ''} · ${row.defaultBu}` : (row.scopeType ?? ''),
    points: row.description ?? '',
    enabled: row.status === '0'
  }));
};

export const saveRolePermissions = async (data: RolePermissionVO[]): Promise<string> => {
  if (!useLive('permission')) return delay('角色权限已保存');
  const payload = (data ?? []).map(item => ({
    id: item.id,
    roleCode: item.roleCode,
    roleName: item.role,
    scopeType: item.scope.split('·')[0]?.trim(),
    defaultBu: item.scope.split('·')[1]?.trim(),
    description: item.points,
    status: item.enabled ? '0' : '1'
  }));
  return unwrap(request({ url: '/cmd/permission/role', method: 'put', data: payload }));
};

export const listCoverage = (): Promise<CoverageItemVO[]> =>
  USE_MOCK ? delay(mock.mockCoverage) : unwrap(request({ url: '/cmd/coverage/list', method: 'get' }));

/* ============================== 13. 工作流 / OCR ============================== */
export const getWorkflow = (): Promise<WorkflowConfigVO> =>
  USE_MOCK ? delay(mock.mockWorkflow) : unwrap(request({ url: '/cmd/workflow/config', method: 'get' }));

export const saveWorkflow = (data: WorkflowConfigVO): Promise<string> =>
  USE_MOCK ? delay('工作流配置已保存') : unwrap(request({ url: '/cmd/workflow/config', method: 'put', data }));

/**
 * OCR 识别：返回营业执照原件信息 + 字段识别值（原型「OCR识别结果」弹窗）
 * 后端 POST /cmd/ocr/recognize（返回 OcrRecognizeVO；兼容仅返回数组的旧实现）
 *
 * @param fileName 上传文件名。POC 阶段后端据此在测试素材中匹配对应执照
 *                 （docs/cmd-poc/测试素材/营业执照/license_0x.png），演示时图片与识别结果一致；
 *                 不传或未匹配到时后端返回默认演示结果。
 */
export const ocrRecognize = async (fileName?: string): Promise<OcrRecognizeVO> => {
  if (!useLive('customer')) return delay({ license: mock.mockOcrLicense, fields: mock.mockOcrResults }, 800);
  const data = await unwrap<Partial<OcrRecognizeVO> | OcrResultVO[]>(
    request({ url: '/cmd/ocr/recognize', method: 'post', data: fileName ? { fileName } : undefined })
  );
  if (Array.isArray(data)) {
    return { license: mock.mockOcrLicense, fields: data };
  }
  return {
    license: data.license ?? mock.mockOcrLicense,
    fields: data.fields ?? []
  };
};
