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
  ChangeDetailVO,
  ChangeDiffVO,
  ChangeFieldVO,
  ChangeRequestForm,
  ChangeRequestQuery,
  ChangeRequestVO,
  ChangeStatus,
  ChangeVersionVO,
  CmdChangeDetailRow,
  CmdChangeDiffRow,
  CmdChangeFieldRow,
  CmdChangeKpiRow,
  CmdChangeRequestRow,
  CmdChangeTrailRow,
  CmdCustomerVersionRow,
  CmdDeactivateResultRow,
  CmdCustomerRow,
  CmdDashboardRow,
  CmdFlowTraceRow,
  CmdHierarchyNodeRow,
  CmdHierarchyRelationRow,
  CmdHierarchyRelationHistRow,
  CmdHierarchyUnassignedRow,
  CmdHierarchyValidateRow,
  CmdIntegrationRunRow,
  CmdIntegrationEndpointRow,
  IntegrationEndpointVO,
  CmdImportJobRow,
  CmdImportResultRow,
  CmdImportTemplateRow,
  CmdTemplateMappingRow,
  CoverageItemVO,
  CustomerForm,
  CustomerQuery,
  CustomerStats,
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
  HierarchyAssignForm,
  HierarchyChildForm,
  HierarchyRelationEditForm,
  HierarchyRelationForm,
  HierarchyRelationHistVO,
  HierarchyRelationVO,
  HierarchySearchFilters,
  HierarchyUnassignedVO,
  HierarchyValidateForm,
  HierarchyValidateVO,
  ImportJobStatus,
  ImportJobVO,
  ImportRowVO,
  ImportTemplateVO,
  ImportUploadForm,
  IntegrationConnForm,
  IntegrationRunVO,
  CmdLegacyMappingRow,
  CmdMergeRecordRow,
  CmdMdFieldRow,
  CmdOneIdPolicyRow,
  CmdOneIdRuleRow,
  CmdPermissionMatrixRow,
  CmdRoleRow,
  CmdValueSetRow,
  CmdVersionRow,
  LegacyMappingVO,
  MergeRecordVO,
  MatchRuleRow,
  MatchRuleBriefVO,
  MatchSimulateForm,
  MatchSimulateResultVO,
  DqSimulateForm,
  MetadataFieldForm,
  MetadataFieldVO,
  ModelVersionVO,
  ValueSetForm,
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
  TemplateMappingSaveForm,
  TemplateMappingVO,
  TodoVO,
  FlowSceneConfigBo,
  FlowSceneConfigVO,
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

/**
 * 取 R.msg 文案（后端 R.ok(String) 单参重载会把提示文案放进 msg、data 为 null）。
 * 用于 test/publish/retry/delete 等以「结果文案」为返回值的集成接口。
 */
async function unwrapMsg(promise: AxiosPromise<unknown>): Promise<string> {
  const res = (await promise) as unknown as { msg?: string; data?: unknown };
  const msg = (res as { msg?: string })?.msg ?? '';
  return msg || String((res as { data?: unknown })?.data ?? '');
}

/**
 * 后端 LocalDateTime → 页面展示串
 * 后端返回 'yyyy-MM-dd HH:mm:ss'（application.yml 已配置全局格式），
 * 也兼容 ISO 'yyyy-MM-ddTHH:mm:ss'，因此统一做一次归一化。
 */
function toDateText(value?: string | null): string {
  if (!value) return '';
  return String(value).replace('T', ' ').slice(0, 10);
}

function toDateTimeText(value?: string | null): string {
  if (!value) return '';
  return String(value).replace('T', ' ').slice(0, 19);
}

/* ============================== 1. 工作台 ============================== */
export const getDashboardStats = async (buScope?: string): Promise<DashboardStatVO[]> => {
  if (!useLive('dashboard')) return delay(mock.mockDashboardStats);
  const vo = await unwrap<CmdDashboardRow>(request({ url: '/cmd/dashboard/stats', method: 'get', params: { buScope } }));
  return [
    { key: 'customerTotal', label: '客户总数', value: vo.customerTotal ?? 0, hint: '当前 Scope 可见' },
    { key: 'customerActive', label: '生效中客户', value: vo.customerActive ?? 0, hint: 'status = active' },
    { key: 'customerPending', label: '待审批客户', value: vo.customerPending ?? 0, hint: 'status = pending' },
    { key: 'hierarchyNodeCount', label: '层级节点', value: vo.hierarchyNodeCount ?? 0, hint: 'A1 / A2 / A3' }
  ];
};

export const getTodo = async (buScope?: string): Promise<TodoVO> => {
  if (!useLive('dashboard')) return delay(mock.mockTodo);
  const vo = await unwrap<CmdDashboardRow>(request({ url: '/cmd/dashboard/stats', method: 'get', params: { buScope } }));
  const count = Number(vo.customerPending ?? 0);
  return {
    count,
    label: '待处理任务',
    hint: '客户创建 / 变更申请待审批',
    tag: count > 0 ? '待处理' : '无'
  };
};

/** 获取当前用户审批待办统计（myTodo / myDone / returned / slaOverdue），用于菜单 badge */
export const getApprovalStats = async (): Promise<Record<string, number>> => {
  if (!useLive('approval')) return delay({ myTodo: 0, myDone: 0, returned: 0, slaOverdue: 0 });
  const stats = await unwrap<Record<string, number>>(request({ url: '/cmd/approval/stats', method: 'get' }));
  return stats ?? {};
};

export const listNotifications = (): Promise<NotificationVO[]> =>
  USE_MOCK ? delay(mock.mockNotifications) : unwrap(request({ url: '/cmd/dashboard/notifications', method: 'get' }));

/* ============================== 2. 客户主数据 ============================== */
/**
 * 后端行 → 前端展示对象
 * <p>
 * 做两件事：① 字段名对齐（buScope→bu、updateTime→updatedAt）；② 空值收敛，
 * 保证详情弹窗每个字段都有稳定的展示值（空串走「—」占位），面板代码无需感知后端差异。
 * 字段覆盖 cmd_customer 全部业务列，详情弹窗据此完整展示，无需二次查询。
 */
function toCustomerVO(row: CmdCustomerRow): CustomerVO {
  return {
    oneId: row.oneId ?? '',
    legalName: row.legalName ?? '',
    legalNameEn: row.legalNameEn ?? '',
    shortName: row.shortName ?? '',
    customerType: row.customerType ?? '',
    customerLevel: row.customerLevel ?? '',
    bu: row.buScope ?? '',
    gcScopeFlag: row.gcScopeFlag ?? '',
    productLine: row.productLine ?? '',
    sourceSystem: row.sourceSystem ?? '',
    sourceId: row.sourceId ?? '',
    creditCode: row.creditCode ?? '',
    taxNo: row.taxNo ?? '',
    country: row.country ?? '',
    province: row.province ?? '',
    city: row.city ?? '',
    address: row.address ?? '',
    postalCode: row.postalCode ?? '',
    payerId: row.payerId ?? '',
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    contactEmail: row.contactEmail ?? '',
    status: (row.status ?? 'active') as CustomerVO['status'],
    dqScore: Number(row.dqScore ?? 0),
    dqGrade: row.dqGrade ?? '',
    matchState: row.matchState ?? '',
    duplicateFlag: row.duplicateFlag ?? '',
    mergedToOneId: row.mergedToOneId ?? '',
    versionNo: row.versionNo ?? 1,
    effectiveFrom: row.effectiveFrom ?? '',
    effectiveTo: row.effectiveTo ?? '',
    approvedBy: row.approvedBy ?? undefined,
    approvedTime: row.approvedTime ?? '',
    flowInstanceId: row.flowInstanceId ?? undefined,
    flowStatus: row.flowStatus ?? '',
    remark: row.remark ?? '',
    extJson: row.extJson ?? '',
    createdAt: row.createTime ?? '',
    updatedAt: row.updateTime ?? row.createTime ?? ''
  };
}

/**
 * 前端筛选条件 → 后端查询参数
 * <p>
 * 只做字段名对齐（前端 bu ↔ 后端 buScope），空串一律转 undefined，
 * 避免把空条件当等值条件传给后端（会查出 0 条）。
 * 列表与指标统计共用，保证两者条件完全一致。
 */
function toCustomerParams(query?: CustomerQuery) {
  return {
    keyword: query?.keyword?.trim() || undefined,
    buScope: query?.bu || undefined,
    customerType: query?.customerType || undefined,
    status: query?.status || undefined
  };
}

/** mock 模式下的本地条件过滤（真实模式由后端 SQL 完成，仅用于关闭后端联调时兜底） */
function filterMockCustomers(query?: CustomerQuery): CustomerVO[] {
  const kw = (query?.keyword ?? '').trim().toLowerCase();
  return mock.mockCustomers.filter(row => {
    const matchKeyword =
      !kw ||
      [row.legalName, row.legalNameEn, row.shortName, row.oneId, row.creditCode, row.payerId].some(value =>
        (value ?? '').toLowerCase().includes(kw)
      );
    const matchBu = !query?.bu || (row.bu ?? '').includes(query.bu);
    const matchType = !query?.customerType || row.customerType === query.customerType;
    const matchStatus = !query?.status || row.status === query.status;
    return matchKeyword && matchBu && matchType && matchStatus;
  });
}

/** 与后端 CmdCustomerServiceImpl#selectCustomerStats 相同的统计口径 */
function buildMockStats(rows: CustomerVO[]): CustomerStats {
  const scored = rows.filter(row => Number(row.dqScore ?? 0) > 0);
  return {
    total: rows.length,
    activeCount: rows.filter(row => row.status === 'active').length,
    pendingCount: rows.filter(row => row.status === 'pending' || row.status === 'returned').length,
    crossBuCount: rows.filter(row => row.gcScopeFlag === 'Y').length,
    duplicateCount: rows.filter(row => row.duplicateFlag === 'Y').length,
    avgDqScore: scored.length
      ? Math.round(scored.reduce((sum, row) => sum + Number(row.dqScore ?? 0), 0) / scored.length)
      : 0
  };
}

/**
 * 分页查询客户主档（服务端分页 + 服务端条件过滤）
 * <p>
 * 每次调用都实时查库，不做前端缓存；不传 pageNum/pageSize 时后端返回权限内全量
 * （供「客户层级」「One ID 管理」等需要全量列表的页面复用）。
 */
export const listCustomers = async (query?: CustomerQuery): Promise<PageResult<CustomerVO>> => {
  if (!useLive('customer')) {
    const rows = filterMockCustomers(query);
    const pageNum = query?.pageNum ?? 1;
    const pageSize = query?.pageSize ?? rows.length;
    return delay({ rows: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize), total: rows.length });
  }
  const page = await unwrap<PageResult<CmdCustomerRow>>(
    request({
      url: '/cmd/customer/list',
      method: 'get',
      params: { ...toCustomerParams(query), pageNum: query?.pageNum, pageSize: query?.pageSize }
    })
  );
  return {
    rows: (page?.rows ?? []).map(toCustomerVO),
    total: page?.total ?? 0
  };
};

/**
 * 按当前筛选条件统计客户指标概览（列表顶部指标带）
 * <p>
 * 与 listCustomers 同条件实时查库，保证「指标」与「列表」永远对得上。
 */
export const getCustomerStats = async (query?: CustomerQuery): Promise<CustomerStats> => {
  if (!useLive('customer')) return delay(buildMockStats(filterMockCustomers(query)));
  const stats = await unwrap<CustomerStats>(
    request({ url: '/cmd/customer/stats', method: 'get', params: toCustomerParams(query) })
  );
  return (
    stats ?? {
      total: 0,
      activeCount: 0,
      pendingCount: 0,
      crossBuCount: 0,
      duplicateCount: 0,
      avgDqScore: 0
    }
  );
};

/**
 * 查询单个客户完整主档（客户详情弹窗数据源）
 * <p>
 * 走 /cmd/customer/oneId/{oneId}，保证详情看到的是数据库当前值，
 * 而不是列表缓存里的快照（列表与详情可能因版本变更产生时间差）。
 */
export const getCustomerDetail = async (oneId: string): Promise<CustomerVO | null> => {
  if (!useLive('customer')) {
    const hit = mock.mockCustomers.find(item => item.oneId === oneId);
    return delay(hit ? { ...hit } : null);
  }
  const row = await unwrap<CmdCustomerRow>(
    request({ url: `/cmd/customer/oneId/${encodeURIComponent(oneId)}`, method: 'get' })
  );
  return row ? toCustomerVO(row) : null;
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

/** 后端行 → 前端字段 VO（id / deleteGuard 用于字段目录的删除操作与删除保护） */
/**
 * 版本号比较（用于「当前生效版本 / 工作版本」判定）。
 * 按数字段逐位比较，保证 v1.10 > v1.9（字符串比较会判反）。
 */
export const compareVersion = (a: string, b: string): number => {
  const seg = (v: string) => v.replace(/^v/i, '').split('.').map(n => Number(n) || 0);
  const pa = seg(a);
  const pb = seg(b);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
};

const toMetadataFieldVO = (row: CmdMdFieldRow): MetadataFieldVO => ({
  id: row.id,
  code: row.fieldCode ?? '',
  label: row.fieldName ?? '',
  scope: FIELD_SCOPE_TEXT[row.scopeType ?? ''] ?? 'GC Core',
  type: FIELD_TYPE_TEXT[(row.dataType ?? '').toUpperCase()] ?? 'Text',
  required: row.isRequired === 'Y',
  bu: row.ownerBu ?? 'All',
  customerType: 'All',
  versionNo: row.versionNo,
  status: row.status === '0' ? 'Published' : 'Draft',
  deleteGuard: row.deleteGuard
});

export const listMetadataFields = async (): Promise<MetadataFieldVO[]> => {
  if (!useLive('metadata')) return delay(mock.mockMetadataFields);
  const rows = await unwrap<CmdMdFieldRow[]>(request({ url: '/cmd/metadata/field/list', method: 'get' }));
  const all = (rows ?? []).filter(row => !!row.fieldCode);
  // md_field 是「按版本快照」存储的：同一 field_code 会在 v1 / v1.1 / … / 当前版本各有一行。
  // 业务表单只应加载「当前生效版本」的已发布字段，否则：
  //   ① 平铺会让表单出现重复字段（如「客户法定名称」出现 5 次）；
  //   ② 早期实现「按 field_code 去重保留首条」，而建字段时 order_num 落 0，会排到最前面，
  //      结果保留下来的是已退役（status=1）的历史行 → 所有字段都被判成 Draft → 动态字段区渲染 0 个（历史 bug）。
  // 现改为：先定位当前生效版本（存活行中版本号最大的、存在 status='0' 行的版本），再只取该版本的已发布字段。
  const published = all.filter(row => row.status === '0');
  const currentVersion = published
    .map(row => row.versionNo ?? '')
    .filter(Boolean)
    .toSorted((a, b) => compareVersion(b, a))[0];
  const scoped = currentVersion ? published.filter(row => (row.versionNo ?? '') === currentVersion) : published;
  // 同版本内同编码仍可能有多行（历史测试数据）→ 按 field_code 去重，保留首条
  const seen = new Set<string>();
  return scoped
    .filter(row => {
      const code = row.fieldCode ?? '';
      if (!code || seen.has(code)) return false;
      seen.add(code);
      return true;
    })
    .map(toMetadataFieldVO);
};

/**
 * 字段目录专用：不做 field_code 去重，逐行返回（带 id）。
 * 字段目录需要看见并管理每一条记录（含同编码的历史/重复行），
 * 而业务表单渲染走 listMetadataFields（去重，避免重复字段）。
 */
export const listMetadataFieldRows = async (): Promise<MetadataFieldVO[]> => {
  if (!useLive('metadata')) return delay(mock.mockMetadataFields);
  const rows = await unwrap<CmdMdFieldRow[]>(request({ url: '/cmd/metadata/field/list', method: 'get' }));
  return (rows ?? []).filter(row => !!row.fieldCode).map(toMetadataFieldVO);
};

/**
 * 删除字段（逻辑删除：后端 UPDATE del_flag='1'，行与历史保留）。
 * 总设计点名的核心主数据字段（匹配依据 / DQ 维度 / 生命周期状态）后端会拒绝删除并抛错。
 */
export const deleteMetadataField = async (id: number): Promise<string> => {
  if (!useLive('metadata')) return delay('字段已删除（演示模式未落库）');
  return unwrap<string>(request({ url: `/cmd/metadata/field/${id}`, method: 'delete' }));
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
        versionNo: data.versionNo,
        // 与 listMetadataFields 的读取映射保持同向：Published → '0'（正常/启用）、Draft → '1'（停用）
        status: data.status === 'Published' ? '0' : '1'
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
    diff: row.diff ?? '—',
    status: row.status === 'Draft' ? 'Draft' : 'Current',
    draftCreatedAt: row.draftCreatedAt,
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

/** 保存 / 编辑值集（平台管理：值集维护） */
export const saveValueSet = async (data: ValueSetForm): Promise<string> => {
  if (!useLive('metadata')) return delay(`值集 ${data.code} 已保存`);
  await unwrap(
    request({
      url: '/cmd/metadata/valueset',
      method: 'post',
      data: {
        id: data.id,
        setCode: data.code,
        setName: data.name,
        setType: data.type,
        remark: data.values,
        status: data.status === 'Published' ? '0' : '1'
      }
    })
  );
  return `值集 ${data.code} 已保存`;
};

/** 基于当前已发布版本，克隆出一条新的 Draft 版本 */
export const createModelVersion = async (): Promise<string> => {
  if (!useLive('metadata')) {
    const next = `v${(mock.mockModelVersions.length + 1)}.0`;
    mock.mockModelVersions.push({ version: next, diff: '克隆基线', status: 'Draft' });
    return next;
  }
  return unwrap(request({ url: '/cmd/metadata/version', method: 'post' }));
};

export const publishModelVersion = async (version?: string): Promise<string> => {
  if (!useLive('metadata')) return delay(`配置版本${version ?? ''}已发布；Business User表单将按元数据自动刷新`);
  const url = version ? `/cmd/metadata/version/publish?version=${encodeURIComponent(version)}` : '/cmd/metadata/version/publish';
  return unwrap(request({ url, method: 'put' }));
};

/* ============================== 4. 数据质量 ============================== */
/** DQ 规则清单：行契约 = dq_rule 表实体（ruleCode / ruleName / dimension / fieldCode / checkType ...） */
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

/** DQ 规则模拟：选择测试数据集（Customer Type / BU / Source System / 样例数）对真实主档执行规则 */
export const simulateDq = (data: DqSimulateForm): Promise<DqSimulateResultVO> =>
  useLive('dq') ? unwrap(request({ url: '/cmd/dq/simulate', method: 'post', data })) : delay(mock.mockDqSimulate);

export const reEvaluateDq = (data: ReEvaluateForm): Promise<string> =>
  useLive('dq') ? unwrap(request({ url: '/cmd/dq/reEvaluate', method: 'post', data })) : delay('历史数据重评估任务已创建，旧规则版本与旧分数保留');

/** 重评估影响预估（Demo） */
export const getReEvaluateImpact = (): Promise<ReEvaluateImpactVO[]> =>
  useLive('dq') ? unwrap(request({ url: '/cmd/dq/reEvaluate/impact', method: 'get' })) : delay(mock.mockReEvaluateImpact);

/* ============================== 5. 匹配与重复治理 ============================== */
/** 匹配规则清单：行契约 = match_rule 表实体 */
export const listMatchRules = async (): Promise<MatchRuleRow[]> => {
  if (!useLive('match')) return delay(mock.mockMatchRules);
  const rows = await unwrap<MatchRuleRow[]>(request({ url: '/cmd/match/rule/list', method: 'get' }));
  return rows ?? [];
};

export const saveMatchRule = async (rule: MatchRuleRow): Promise<string> => {
  if (!useLive('match')) return delay('匹配规则已保存');
  return unwrap(request({ url: '/cmd/match/rule', method: 'post', data: rule }));
};

export const deleteMatchRule = async (id: number): Promise<string> => {
  if (!useLive('match')) return delay('匹配规则已删除');
  return unwrap(request({ url: `/cmd/match/rule/${id}`, method: 'delete' }));
};

/** 匹配规则样例模拟：样例记录对全量主档执行标准化 + 加权相似度，输出候选与 Exact / Suspected / New 分布 */
export const simulateMatch = async (data: MatchSimulateForm): Promise<MatchSimulateResultVO> => {
  return useLive('match') ? unwrap(request({ url: '/cmd/match/simulate', method: 'post', data })) : delay(mock.mockMatchSimulate);
};

export const getDuplicateCandidate = (): Promise<DuplicateCandidateVO> =>
  useLive('governance') ? unwrap(request({ url: '/cmd/duplication/candidate', method: 'get' })) : delay(mock.mockDuplicateCandidate);

export const linkExistingOneId = (oneId: string): Promise<string> =>
  useLive('governance') ? unwrap(request({ url: '/cmd/duplication/link', method: 'put', data: { oneId } })) : delay(`已关联One ID ${oneId}`);

export const confirmNewCustomer = (): Promise<string> =>
  useLive('governance') ? unwrap(request({ url: '/cmd/duplication/confirmNew', method: 'put' })) : delay('已进入新客户审批');

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
    submittedBy: row.submitBy ?? '',
    scene: row.scene ?? '',
    buScope: row.buScope ?? '',
    templateCode: row.templateCode ?? '',
    templateVersion: '',
    exactCount: row.exactCount ?? 0,
    suspectedCount: row.suspectedCount ?? 0,
    newCount: row.newCount ?? 0,
    reviewCount: row.reviewCount ?? 0,
    invalidCount: row.invalidCount ?? 0,
    remark: row.remark ?? ''
  };
}

export const listImportJobs = async (pageNum = 1, pageSize = 10): Promise<PageResult<ImportJobVO>> => {
  if (!useLive('import')) return delay({ rows: mock.mockImportJobs, total: mock.mockImportJobs.length });
  const page = await unwrap<PageResult<CmdImportJobRow>>(
    request({ url: '/cmd/import/job/list', method: 'get', params: { pageNum, pageSize } })
  );
  return {
    rows: (page.rows ?? []).map(toImportJobVO),
    total: page.total ?? 0
  };
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

/**
 * 分页查询导入行明细（结果分流下钻「查看 N 条」）
 * @param jobId      任务编号
 * @param resultType 结果分流（EXACT / SUSPECTED / NEW / INVALID，可空查全部）
 */
export const listImportJobRows = async (jobId: string, resultType?: string, pageNum = 1, pageSize = 20): Promise<PageResult<ImportRowVO>> => {
  if (!useLive('import')) return delay({ rows: [], total: 0 });
  const page = await unwrap<PageResult<ImportRowVO>>(
    request({
      url: `/cmd/import/job/${jobId}/rows`,
      method: 'get',
      params: { resultType: resultType || undefined, pageNum, pageSize }
    })
  );
  return { rows: page.rows ?? [], total: page.total ?? 0 };
};

/**
 * 行级治理动作（BU Scope 治理：LINK 关联已有 One ID / EXCLUDE 排除 / RETURN 退回修复）
 */
export const importRowAction = async (rowId: number, action: 'LINK' | 'EXCLUDE' | 'RETURN', oneId?: string): Promise<string> => {
  if (!useLive('import')) return delay(`演示模式：已执行 ${action}`);
  return unwrap<string>(
    request({ url: `/cmd/import/row/${rowId}/action`, method: 'post', data: { action, oneId } })
  );
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
  if (data.scene) formData.append('scene', data.scene);
  if (data.buScope) formData.append('buScope', data.buScope);
  if (data.sourceSystem) formData.append('sourceSystem', data.sourceSystem);
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
    id: row.id,
    templateCode: row.templateCode ?? '',
    sourceColumn: row.columnName ?? '',
    targetField: row.fieldCode ?? '',
    fieldName: row.fieldName ?? '',
    dataType: row.dataType ?? 'Text',
    isRequired: row.isRequired ?? 'N',
    defaultValue: row.defaultValue ?? '',
    transform: row.convertRule ?? '',
    errorStrategy: row.errorStrategy ?? ''
  }));
};

/**
 * 保存模板字段映射（平台管理 › 导入Template：新增上传字段 / 编辑必填与转换规则）
 * <p>管线全链路模板驱动：新增列自动进入模板下载表头、上传表头预检、行级 DQ 与行明细 JSON。</p>
 */
export const saveTemplateMapping = async (form: TemplateMappingSaveForm): Promise<string> => {
  if (!useLive('import')) {
    return delay(form.id ? `演示模式：字段「${form.columnName}」已更新` : `演示模式：字段「${form.columnName}」已新增`);
  }
  return unwrap<string>(request({ url: '/cmd/import/template/mapping', method: 'post', data: form }));
};

/** 删除模板字段映射（主键字段 legal_name / credit_code 后端会拒绝） */
export const deleteTemplateMapping = async (id: number | string): Promise<string> => {
  if (!useLive('import')) return delay('演示模式：字段已移除');
  return unwrap<string>(request({ url: `/cmd/import/template/mapping/${id}`, method: 'delete' }));
};


/* ============================== 7. 客户层级 ============================== */
/* ---- 层级：后端返回平铺列表 + 父指针，树结构与展示字段在此统一转换 ---- */
const HIERARCHY_TYPE_LABEL: Record<string, string> = {
  COMMERCIAL: 'Commercial Entity',
  LEGAL: 'Main Account',
  DOOR: 'Door',
  // 待归位节点：已批准主数据尚未挂到父节点，类型未定型
  UNASSIGNED: '待归位'
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
    // 根节点（parentOneId 为空）用于「返回根节点」定位；depth / hierarchyType 用于详情与级别推导展示
    parentOneId: row.parentOneId ?? '',
    depth: row.depth ?? 0,
    hierarchyType: row.hierarchyType ?? '',
    path: (row.pathNames ?? name).split('/').join(' / '),
    ancestorIds,
    validity: `${from || '—'} → ${to || '9999-12-31'}`,
    status: row.status === 'active' ? 'Active' : row.status === 'expired' ? 'Expired' : 'Future',
    children: []
  };
}

/** 平铺列表按 parentOneId 组装成树，并回填父节点显示名 */
function buildHierarchyTree(rows: CmdHierarchyNodeRow[]): HierarchyNodeVO[] {
  // 待归位节点（UNASSIGNED）尚未挂父节点，进树会被误当成根节点，因此在此剔除；
  // 它们由「待归位主数据」区（getUnassignedNodes）单独展示，两者互补覆盖全部主数据。
  const mounted = rows.filter(row => (row.hierarchyType ?? '') !== 'UNASSIGNED');
  const nodes = mounted.map(toHierarchyNode);
  const byOneId = new Map<string, HierarchyNodeVO>();
  mounted.forEach((row, index) => {
    if (row.oneId) byOneId.set(row.oneId, nodes[index]);
  });
  const roots: HierarchyNodeVO[] = [];
  mounted.forEach((row, index) => {
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

export const getHierarchy = async (filters?: HierarchySearchFilters): Promise<HierarchyNodeVO[]> => {
  if (!useLive('hierarchy')) return delay(mock.mockHierarchy);
  const rows = await unwrap<CmdHierarchyNodeRow[]>(
    request({ url: '/cmd/hierarchy/nodes', method: 'get', params: toHierarchyParams(undefined, filters) })
  );
  return buildHierarchyTree(rows ?? []);
};

export const getHierarchyNode = async (key: string): Promise<HierarchyNodeVO | undefined> => {
  if (!useLive('hierarchy')) return delay(mock.mockHierarchyNodes[key]);
  const row = await unwrap<CmdHierarchyNodeRow>(request({ url: `/cmd/hierarchy/node/${key}`, method: 'get' }));
  return row ? toHierarchyNode(row) : undefined;
};

/**
 * 分页查询直接子节点（树上「加载更多子节点 · 已显示 X / Y」点击后按需追加）
 *
 * @param parentOneId 父节点 One ID
 * @param offset      偏移量
 * @param limit       每批条数
 */
export const getHierarchyChildren = async (
  parentOneId: string,
  offset: number,
  limit: number
): Promise<HierarchyNodeVO[]> => {
  if (!useLive('hierarchy')) return delay([]);
  const rows = await unwrap<CmdHierarchyNodeRow[]>(
    request({
      url: `/cmd/hierarchy/childrenPage/${parentOneId}`,
      method: 'get',
      params: { offset, limit }
    })
  );
  return (rows ?? []).map(toHierarchyNode);
};

/**
 * 页面下拉文案 → 后端查询参数
 * 「全部层级」「All Authorized BU」这类占位项不下传（后端按不过滤处理）。
 */
function toHierarchyParams(keyword?: string, filters?: HierarchySearchFilters): Record<string, string> {
  const params: Record<string, string> = {};
  const kw = (keyword ?? '').trim();
  if (kw) params.keyword = kw;
  if (filters?.hierarchyType && filters.hierarchyType !== '全部类型') {
    params.hierarchyType = HIERARCHY_TYPE_REVERSE[filters.hierarchyType] ?? filters.hierarchyType;
  }
  if (filters?.level && filters.level !== '全部层级') params.level = filters.level;
  if (filters?.buScope && filters.buScope !== 'All Authorized BU') params.buScope = filters.buScope;
  if (filters?.status && filters.status !== '全部状态') params.status = filters.status;
  return params;
}

export const searchHierarchy = async (keyword: string, filters?: HierarchySearchFilters): Promise<HierarchyNodeVO[]> => {
  if (!useLive('hierarchy')) {
    const k = keyword.toLowerCase();
    return delay(
      Object.values(mock.mockHierarchyNodes).filter(n => {
        const hitKeyword = !k || n.name.toLowerCase().includes(k) || n.oneId.toLowerCase().includes(k);
        const hitLevel = !filters?.level || filters.level === '全部层级' || n.level === filters.level;
        const hitStatus = !filters?.status || filters.status === '全部状态' || n.status === filters.status;
        const hitType = !filters?.hierarchyType || n.type === filters.hierarchyType;
        return hitKeyword && hitLevel && hitStatus && hitType;
      })
    );
  }
  const rows = await unwrap<CmdHierarchyNodeRow[]>(
    request({ url: '/cmd/hierarchy/nodes', method: 'get', params: toHierarchyParams(keyword, filters) })
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
        hierarchyType: data.hierarchyType ? HIERARCHY_TYPE_REVERSE[data.hierarchyType] ?? data.hierarchyType : undefined,
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

/** 展示文案 → 后端层级类型枚举 */
const HIERARCHY_TYPE_REVERSE: Record<string, string> = {
  'Legal Hierarchy': 'LEGAL',
  'Sales Hierarchy': 'DOOR',
  'Payer Hierarchy': 'PAYER'
};

/** 根节点查询（层级树「返回根节点」）：无父节点的顶层 Commercial Entity */
export const getHierarchyRoots = async (buScope?: string): Promise<HierarchyNodeVO[]> => {
  if (!useLive('hierarchy')) {
    return delay(
      Object.values(mock.mockHierarchyNodes).filter(n => !n.parentOneId)
    );
  }
  const rows = await unwrap<CmdHierarchyNodeRow[]>(
    request({ url: '/cmd/hierarchy/roots', method: 'get', params: buScope ? { buScope } : {} })
  );
  return (rows ?? []).map(toHierarchyNode);
};

/** 层级关系行 → 前端展示对象 */
function toHierarchyRelationVO(row: CmdHierarchyRelationRow): HierarchyRelationVO {
  return {
    id: Number(row.id ?? 0),
    relationCode: row.relationCode ?? '',
    hierarchyType: row.hierarchyType ?? '',
    relationType: row.relationType ?? '',
    parentOneId: row.parentOneId ?? '',
    childOneId: row.childOneId ?? '',
    payerOneId: row.payerOneId ?? '',
    buScope: row.buScope ?? '',
    crossBuFlag: row.crossBuFlag ?? 'N',
    effectiveFrom: toDateText(row.effectiveFrom),
    effectiveTo: toDateText(row.effectiveTo),
    status: row.status ?? 'Pending',
    changeReason: row.changeReason ?? '',
    sourceType: row.sourceType ?? ''
  };
}

/** 查询某节点当前生效的挂载关系（「编辑层级关系」回显，实时读库） */
export const getNodeActiveRelation = async (childOneId: string): Promise<HierarchyRelationVO | undefined> => {
  if (!useLive('hierarchy')) return delay(undefined);
  const row = await unwrap<CmdHierarchyRelationRow>(
    request({ url: `/cmd/hierarchy/relationByChild/${childOneId}`, method: 'get' })
  );
  return row && row.id ? toHierarchyRelationVO(row) : undefined;
};

/** 查询某节点全部层级关系（父 / 子两个方向，历史与现状一并返回） */
export const getHierarchyRelations = async (oneId: string): Promise<HierarchyRelationVO[]> => {
  if (!useLive('hierarchy')) return delay([]);
  const rows = await unwrap<CmdHierarchyRelationRow[]>(
    request({ url: `/cmd/hierarchy/relations/${oneId}`, method: 'get' })
  );
  return (rows ?? []).map(toHierarchyRelationVO);
};

/** 查询层级关系历史版本（历史归属追溯：改过几次、以前挂在谁下面） */
export const getHierarchyRelationHistory = async (query: {
  relationId?: number;
  oneId?: string;
}): Promise<HierarchyRelationHistVO[]> => {
  if (!useLive('hierarchy')) return delay([]);
  const rows = await unwrap<CmdHierarchyRelationHistRow[]>(
    request({ url: '/cmd/hierarchy/relationHistory', method: 'get', params: query })
  );
  return (rows ?? []).map(row => ({
    id: Number(row.id ?? 0),
    relationId: Number(row.relationId ?? 0),
    relationCode: row.relationCode ?? '',
    versionNo: Number(row.versionNo ?? 1),
    operation: row.operation ?? 'UPDATE',
    relationType: row.relationType ?? '',
    parentOneId: row.parentOneId ?? '',
    childOneId: row.childOneId ?? '',
    payerOneId: row.payerOneId ?? '',
    effectiveFrom: toDateText(row.effectiveFrom),
    effectiveTo: toDateText(row.effectiveTo),
    status: row.status ?? '',
    snapshotJson: row.snapshotJson,
    changeReason: row.changeReason ?? '',
    createTime: toDateTimeText(row.createTime)
  }));
};

/**
 * 提交前实时校验（调后端 /cmd/hierarchy/validate，不修改业务数据但会写 Loop Check 证据日志）
 * 校验口径与提交时完全一致，因此弹窗里看到的结论就是数据库的判断。
 */
export const validateHierarchyRelation = async (data: HierarchyValidateForm): Promise<HierarchyValidateVO> => {
  if (!useLive('hierarchy')) {
    return delay({
      checkCode: 'MOCK',
      passed: true,
      relationLabel: `${data.parentOneId} → ${data.childOneId}`,
      parentName: '',
      childName: '',
      parentLevel: 'A2',
      parentDepth: 2,
      childLevel: 'A1',
      childDepth: 3,
      previewPath: `/${data.parentOneId}/${data.childOneId}/`,
      previewPathNames: '',
      crossBu: false,
      requiresGcApproval: false,
      relationType: 'A2_A1',
      maxDepth: 3,
      childMounted: false,
      childCurrentParentOneId: '',
      childCurrentLevel: '',
      checks: [],
      executeTime: '',
      durationMs: 0
    });
  }
  const row = await unwrap<CmdHierarchyValidateRow>(
    request({ url: '/cmd/hierarchy/validate', method: 'post', data })
  );
  return {
    checkCode: row?.checkCode ?? '',
    passed: row?.passed === true,
    blockedReason: row?.blockedReason,
    relationLabel: row?.relationLabel ?? '',
    parentName: row?.parentName ?? '',
    childName: row?.childName ?? '',
    parentLevel: row?.parentLevel ?? '',
    parentDepth: row?.parentDepth ?? 0,
    childLevel: row?.childLevel ?? '',
    childDepth: row?.childDepth ?? 0,
    previewPath: row?.previewPath ?? '',
    previewPathNames: row?.previewPathNames ?? '',
    crossBu: row?.crossBu === true,
    requiresGcApproval: row?.requiresGcApproval === true,
    relationType: row?.relationType ?? '',
    maxDepth: row?.maxDepth ?? 3,
    childMounted: row?.childMounted === true,
    childCurrentParentOneId: row?.childCurrentParentOneId ?? '',
    childCurrentLevel: row?.childCurrentLevel ?? '',
    checks: (row?.checks ?? []).map(item => ({
      checkType: item.checkType ?? '',
      label: item.label ?? '',
      checkResult: (item.checkResult as 'PASS' | 'FAIL' | 'WARN') ?? 'PASS',
      message: item.message ?? '',
      conflictPath: item.conflictPath,
      suggestion: item.suggestion
    })),
    executeTime: toDateTimeText(row?.executeTime),
    durationMs: Number(row?.durationMs ?? 0)
  };
};

/** 增加子节点（真实落库：服务端登记节点 + 级别推导 + 路径重建 + 更新祖先计数 + 关系留痕） */
export const addHierarchyChild = async (data: HierarchyChildForm): Promise<string> => {
  if (!useLive('hierarchy')) return delay(`已新增子节点：${data.childOneId}`);
  await unwrap(request({ url: '/cmd/hierarchy/child', method: 'post', data }));
  return '子节点已新增，层级树与关系历史已同步更新';
};

/** 编辑层级关系（真实落库：支持改挂父节点，历史版本不覆盖） */
export const updateHierarchyRelation = async (data: HierarchyRelationEditForm): Promise<string> => {
  if (!useLive('hierarchy')) return delay('层级关系已保存');
  const { id, ...payload } = data;
  await unwrap(request({ url: `/cmd/hierarchy/relation/${id}`, method: 'put', data: payload }));
  return '层级关系已保存，历史版本已留痕';
};

/** 待归位主数据行 → 页面 VO（批准成为主数据但尚未归位的客户） */
function toUnassignedVO(row: CmdHierarchyUnassignedRow): HierarchyUnassignedVO {
  return {
    oneId: row.oneId ?? '',
    name: row.legalName ?? '',
    bu: row.buScope ?? '—',
    status: row.customerStatus === 'active' ? 'Active' : (row.customerStatus ?? '—'),
    source: row.sourceSystem ?? '—',
    approvedTime: (row.approvedTime ?? '').toString().replace('T', ' ').slice(0, 16) || '—',
    registered: row.registered === true,
    nodeCode: row.nodeCode ?? '',
    suggestedLevel: row.suggestedLevel ?? 'A1',
    remark: row.remark ?? ''
  };
}

/**
 * 查询「待归位主数据」：已批准成为主数据，但还没挂到 A3-A2-A1 树上的客户。
 * 这是「批准 → 主数据 → 层级」之间的衔接环节，归位后即进入层级树。
 */
export const getUnassignedNodes = async (query?: {
  keyword?: string;
  buScope?: string;
}): Promise<HierarchyUnassignedVO[]> => {
  if (!useLive('hierarchy')) return delay(mock.mockHierarchyUnassigned.map(toUnassignedVO));
  const rows = await unwrap<CmdHierarchyUnassignedRow[]>(
    request({ url: '/cmd/hierarchy/unassigned', method: 'get', params: query })
  );
  return (rows ?? []).map(toUnassignedVO);
};

/** 层级归位：把待归位主数据挂到目标父节点之下（服务端做 Loop Check + 级别推导 + 路径重建） */
export const assignHierarchyNode = async (data: HierarchyAssignForm): Promise<string> => {
  if (!useLive('hierarchy')) return delay(`归位完成：${data.oneId} 已挂到 ${data.parentId} 之下`);
  await unwrap(
    request({
      url: '/cmd/hierarchy/assign',
      method: 'post',
      data: {
        oneId: data.oneId,
        parentOneId: data.parentId,
        changeReason: data.changeReason,
        remark: data.remark
      }
    })
  );
  return '归位完成，节点已进入 A3-A2-A1 层级树';
};

export const loopCheck = (): Promise<string> =>
  USE_MOCK
    ? delay('检测到循环路径：A1-000128 → A2-0188 → A1-000128。系统阻止提交，并保留冲突路径用于修正。')
    : unwrap(request({ url: '/cmd/hierarchy/loopCheck', method: 'get' }));

/* ============================== 8. 变更与停用 ============================== */
/* ---- 变更与停用：后端状态 → 页面展示状态 ---- */
/** 后端 cmd_change_request.status → 页面展示状态 */
const CHANGE_STATUS_TEXT: Record<string, ChangeStatus> = {
  DRAFT: 'Draft',
  PENDING: 'Under Review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  RETURNED: 'Returned',
  EFFECTIVE: 'Effective',
  CANCELLED: 'Cancelled'
};

/**
 * 页面展示状态 → 后端查询值。
 * 'Inactive' 是停用已生效的历史展示口径，查询时归并到 EFFECTIVE。
 */
const CHANGE_STATUS_QUERY: Record<string, string> = {
  Draft: 'DRAFT',
  'Under Review': 'PENDING',
  Approved: 'APPROVED',
  Effective: 'EFFECTIVE',
  Rejected: 'REJECTED',
  Returned: 'RETURNED',
  Cancelled: 'CANCELLED',
  Inactive: 'EFFECTIVE'
};

/** 后端变化类型 → 页面文案 */
const CHANGE_FLAG_TEXT: Record<string, string> = {
  ADD: '新增',
  MODIFY: '修改',
  DELETE: '清空',
  SAME: '未变'
};

/** 时间戳 → 'YYYY-MM-DD HH:mm' */
function fmtTime(value?: string): string {
  return (value ?? '').toString().slice(0, 16).replace('T', ' ') || '—';
}

/** 后端字段差异行 → 页面 Before / After 行 */
function toChangeDiffVO(row: CmdChangeDiffRow): ChangeDiffVO {
  return {
    field: row.fieldName ?? row.fieldCode ?? '',
    before: row.beforeValue ?? '(空)',
    after: row.afterValue ?? '(清空)',
    changeFlag: CHANGE_FLAG_TEXT[row.changeFlag ?? ''] ?? row.changeFlag ?? '',
    isKey: row.isKeyField === 'Y',
    sensitive: row.isSensitive === 'Y'
  };
}

/** 后端版本快照行 → 页面版本历史行 */
function toChangeVersionVO(row: CmdCustomerVersionRow): ChangeVersionVO {
  return {
    versionNo: row.versionNo ?? 0,
    changeType: row.changeType ?? '',
    changeReason: row.changeReason ?? '',
    changedFields: row.changedFields ?? '',
    status: row.status ?? '',
    sourceSystem: row.sourceSystem ?? '',
    dqScore: row.dqScore,
    beforeJson: row.beforeJson ?? null,
    snapshotJson: row.snapshotJson ?? null,
    createTime: fmtTime(row.createTime)
  };
}

/** 后端审批轨迹行 → 页面轨迹行 */
function toChangeTrailVO(row: CmdChangeTrailRow): ApprovalTrailVO {
  return {
    time: fmtTime(row.time),
    role: row.role ?? '—',
    action: row.action ?? '—',
    result: row.result ?? '—',
    operator: row.operator ?? '',
    node: row.node ?? '',
    opinion: row.opinion ?? ''
  };
}

/** 后端申请行 → 页面申请行 */
function toChangeRequestVO(row: CmdChangeRequestRow): ChangeRequestVO {
  const rawStatus = row.status ?? 'DRAFT';
  return {
    requestId: row.requestCode ?? '',
    oneId: row.oneId ?? '',
    customerName: row.legalName ?? '',
    bu: row.buScope ?? '',
    changeType: row.changeType === 'Deactivate' ? 'Deactivate' : 'Update',
    content: row.changeReason ?? '',
    status: CHANGE_STATUS_TEXT[rawStatus] ?? 'Draft',
    rawStatus,
    submittedAt: fmtTime(row.createTime),
    submittedBy: 'Business User',
    isKeyChange: row.isKeyChange,
    targetStatus: row.targetStatus,
    relationCheck: row.relationCheck,
    relationMsg: row.relationMsg,
    effectiveDate: fmtTime(row.effectiveDate),
    effectiveTime: row.effectiveTime ? fmtTime(row.effectiveTime) : '',
    remark: row.remark
  };
}

/**
 * 可变更字段目录（配置驱动：读取 md_field 中 CUSTOMER 模型且已配置物理列的字段）
 * 「哪些字段可变更、哪些属关键属性、哪些敏感」全部由平台管理维护，前后端共用同一口径。
 */
export const getChangeFields = async (): Promise<ChangeFieldVO[]> => {
  if (!useLive('change')) {
    return delay(
      [
        { fieldCode: 'legal_name', fieldName: '客户法定名称', dataType: 'STRING', isRequired: 'Y', isKeyField: 'Y', isSensitive: 'N' },
        { fieldCode: 'credit_code', fieldName: '统一社会信用代码', dataType: 'STRING', isRequired: 'N', isKeyField: 'Y', isSensitive: 'N' },
        { fieldCode: 'address', fieldName: '注册地址', dataType: 'STRING', isRequired: 'Y', isKeyField: 'N', isSensitive: 'N' },
        { fieldCode: 'contact_phone', fieldName: '联系电话', dataType: 'STRING', isRequired: 'N', isKeyField: 'N', isSensitive: 'Y' }
      ] as ChangeFieldVO[]
    );
  }
  const rows = await unwrap<CmdChangeFieldRow[]>(request({ url: '/cmd/change/fields', method: 'get' }));
  return (rows ?? []).map(row => ({
    fieldCode: row.fieldCode ?? '',
    fieldName: row.fieldName ?? '',
    dataType: row.dataType ?? 'STRING',
    valueSetCode: row.valueSetCode,
    isRequired: row.isRequired ?? 'N',
    isKeyField: row.isKeyField ?? 'N',
    isSensitive: row.isSensitive ?? 'N',
    maxLength: row.maxLength,
    regexPattern: row.regexPattern,
    physicalColumn: row.physicalColumn
  }));
};

/**
 * 变更与停用指标卡（工作台 4 张卡：待审批变更 / 待审批停用 / 本月已生效 / One ID 重生成）
 * 「One ID 重生成」由后端恒定返回 0，用于量化证明 One ID 稳定、不重新生成。
 */
export const getChangeKpi = async (): Promise<Array<{ label: string; value: number; hint: string }>> => {
  if (!useLive('change')) {
    return delay([
      { label: '待审批变更', value: 4, hint: '属性变更申请处于待审批' },
      { label: '待审批停用', value: 2, hint: '逻辑停用申请处于待审批' },
      { label: '本月已生效', value: 11, hint: '本月内完成生效的变更与停用' },
      { label: 'One ID重生成', value: 0, hint: 'One ID 稳定：变更只递增版本，不重新生成' }
    ]);
  }
  const rows = await unwrap<CmdChangeKpiRow[]>(request({ url: '/cmd/change/kpi', method: 'get' }));
  return (rows ?? []).map(row => ({
    label: row.label ?? '',
    value: row.value ?? 0,
    hint: row.hint ?? ''
  }));
};

/** 分页查询变更 / 停用申请 */
export const listChangeRequests = async (query?: ChangeRequestQuery): Promise<PageResult<ChangeRequestVO>> => {
  if (!useLive('change')) {
    const rows = filterChangeRequests(mock.mockChangeRequests, query);
    return delay({ rows, total: rows.length });
  }
  const page = await unwrap<PageResult<CmdChangeRequestRow>>(
    request({
      url: '/cmd/change/list',
      method: 'get',
      params: {
        pageNum: query?.pageNum,
        pageSize: query?.pageSize,
        keyword: query?.keyword,
        changeType: query?.changeType,
        // 状态到后端取值的映射在 api 层完成，面板只传展示口径
        status: query?.status ? (CHANGE_STATUS_QUERY[query.status] ?? '') : undefined,
        buScope: query?.buScope,
        oneId: query?.oneId
      }
    })
  );
  return { rows: (page?.rows ?? []).map(toChangeRequestVO), total: page?.total ?? 0 };
};

/** 提交属性变更申请（字段级差异由服务端与主档当前值比对后生成 Before） */
export const submitChangeRequest = async (data: ChangeRequestForm): Promise<string> => {
  if (!useLive('change')) return delay('变更申请已提交，进入审批流程');
  await unwrap(
    request({
      url: '/cmd/change',
      method: 'post',
      data: {
        oneId: data.oneId,
        changeType: data.changeType || 'Update',
        changeReason: data.reason,
        effectiveDate: data.effectiveDate,
        diffs: data.fields
          .filter(item => item.fieldCode)
          .map(item => ({
            fieldCode: item.fieldCode,
            fieldName: item.fieldName,
            afterValue: item.afterValue
          }))
      }
    })
  );
  return '变更申请已提交至审批中心，批准后可生效（One ID 不变）';
};

/** 提交逻辑停用申请（不执行物理删除，仅状态切换） */
export const submitDeactivateRequest = async (data: DeactivateForm): Promise<string> => {
  if (!useLive('change')) return delay('停用申请已提交，等待审批生效');
  await unwrap(
    request({
      url: '/cmd/change',
      method: 'post',
      data: {
        oneId: data.oneId,
        changeType: 'Deactivate',
        targetStatus: data.targetStatus,
        changeReason: data.reason,
        effectiveDate: data.effectiveDate,
        remark: data.remark
      }
    })
  );
  return '停用申请已提交至审批中心，批准后生效为 Inactive / Archived（不物理删除）';
};

/** 申请详情：Before / After 差异 + 影响面 + 审批轨迹 + 版本上下文 */
export const getChangeDetail = async (requestId: string): Promise<ChangeDetailVO> => {
  if (!useLive('change')) {
    return delay({
      requestId,
      oneId: mock.mockChangeRequests[0]?.oneId ?? '',
      customerName: mock.mockChangeRequests[0]?.customerName ?? '',
      changeType: 'Update' as const,
      targetStatus: 'active',
      isKeyChange: true,
      bu: 'High End',
      reason: mock.mockChangeRequests[0]?.content ?? '',
      status: 'Under Review' as ChangeStatus,
      rawStatus: 'PENDING',
      effectiveDate: '—',
      effectiveTime: '',
      relationCheck: 'PASS',
      relationMsg: '关联层级与 Payer 关系校验通过',
      impacts: ['层级关系：未挂到 A3-A2-A1 树上'],
      approvalTaskNo: '',
      submittedAt: mock.mockChangeRequests[0]?.submittedAt ?? '',
      approvedByName: '',
      approvedTime: '',
      diffs: mock.mockChangeDiffs,
      trail: mock.mockChangeTrail,
      versions: [],
      remark: ''
    });
  }
  const row = await unwrap<CmdChangeDetailRow>(request({ url: `/cmd/change/${requestId}/detail`, method: 'get' }));
  const rawStatus = row.status ?? 'PENDING';
  return {
    requestId: row.requestCode ?? requestId,
    oneId: row.oneId ?? '',
    customerName: row.legalName ?? '',
    changeType: row.changeType === 'Deactivate' ? 'Deactivate' : 'Update',
    targetStatus: row.targetStatus ?? '',
    isKeyChange: row.isKeyChange === 'Y',
    bu: row.buScope ?? '—',
    reason: row.changeReason ?? '',
    status: CHANGE_STATUS_TEXT[rawStatus] ?? 'Draft',
    rawStatus,
    effectiveDate: fmtTime(row.effectiveDate),
    effectiveTime: row.effectiveTime ? fmtTime(row.effectiveTime) : '',
    relationCheck: row.relationCheck ?? '',
    relationMsg: row.relationMsg ?? '',
    impacts: row.impacts ?? [],
    approvalTaskNo: row.approvalTaskNo ?? '',
    submittedAt: fmtTime(row.createTime),
    approvedByName: row.approvedByName ?? '',
    approvedTime: row.approvedTime ? fmtTime(row.approvedTime) : '',
    currentVersionNo: row.currentVersionNo,
    effectiveVersionNo: row.effectiveVersionNo,
    diffs: (row.diffs ?? []).map(toChangeDiffVO),
    trail: (row.trail ?? []).map(toChangeTrailVO),
    versions: (row.versions ?? []).map(toChangeVersionVO),
    remark: row.remark ?? ''
  };
};

/** 客户主档版本历史（证明「换版本不换 One ID」） */
export const getChangeVersions = async (oneId: string): Promise<ChangeVersionVO[]> => {
  if (!useLive('change')) return delay([] as ChangeVersionVO[]);
  const rows = await unwrap<CmdCustomerVersionRow[]>(
    request({ url: `/cmd/change/versions/${oneId}`, method: 'get' })
  );
  return (rows ?? []).map(toChangeVersionVO);
};

/** 逻辑停用结果（业务视图 + 落库记录，证明无物理删除） */
export const getDeactivateResult = async (oneId: string): Promise<DeactivateResultVO> => {
  if (!useLive('change')) return delay(mock.mockDeactivateResult);
  const row = await unwrap<CmdDeactivateResultRow>(
    request({ url: `/cmd/change/${oneId}/deactivateResult`, method: 'get' })
  );
  return {
    businessView: (row.businessView ?? []).map(item => ({ key: item.key ?? '', value: item.value ?? '' })),
    dbRecords: row.dbRecords ?? [],
    versions: (row.versions ?? []).map(toChangeVersionVO)
  };
};

/** 生效申请：写主档新版本，One ID 保持不变（仅审批通过的申请可生效） */
export const effectChangeRequest = async (requestCode: string): Promise<string> => {
  if (!useLive('change')) return delay('已生效，主档生成新版本，One ID 不变');
  await unwrap(request({ url: `/cmd/change/${requestCode}/effect`, method: 'post' }));
  return '已生效：主档写入新版本，One ID 保持不变';
};

/** 撤回申请（同步取消关联审批待办） */
export const cancelChangeRequest = async (requestCode: string): Promise<string> => {
  if (!useLive('change')) return delay('申请已撤回');
  await unwrap(request({ url: `/cmd/change/${requestCode}/cancel`, method: 'post' }));
  return '申请已撤回，关联审批待办同步取消';
};

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

/**
 * 后端业务类型 → 页面显示名
 *
 * 批量导入确认在库内以业务码 IMPORT 存储（审批回写与流程联动都按业务码判定），
 * 客户类申请则直接存中文；页面统一显示业务名，与总设计场景命名保持一致
 * （总设计场景二：批量导入确认流 IMPORT_BATCH）。
 */
export const BIZ_TYPE_TEXT: Record<string, string> = {
  IMPORT: '批量导入确认',
  CHANGE: '客户变更',
  MERGE: '跨BU合并'
};

/** 场景编码 → 页面显示名（「来源」列：这条待办由哪条业务流产生） */
export const SCENE_TEXT: Record<string, string> = {
  IMPORT_BATCH: '批量导入',
  MERGE: '客户合并',
  CUSTOMER_CREATE: '单条创建',
  CUSTOMER_CHANGE: '属性变更',
  CUSTOMER_DEACTIVATE: '逻辑停用',
  HIERARCHY: '层级调整',
  DQ_RULE_CHANGE: 'DQ 规则变更',
  MATCH_RULE_CHANGE: '匹配规则变更',
  INTEGRATION_FAIL: '集成失败处理'
};

/**
 * 发起跨 BU 客户合并请求（总设计 MERGE 场景）
 * <p>后端创建 sceneCode=MERGE 的审批待办（BU 初审 → GC 决策），
 * 批准后执行 Golden Record 合并、Legacy 交叉引用与审计。
 *
 * @param sourceOneId 合并源 One ID（被并入方）
 * @param targetOneId 合并目标 One ID（保留的 Golden Record）
 * @param reason      发起原因
 * @returns 合并审批任务编号 AP-yyyyMMdd-####
 */
export const launchCustomerMerge = async (sourceOneId: string, targetOneId: string, reason?: string): Promise<string> => {
  if (!useLive('customer')) {
    return delay(`（演示模式）已发起合并请求：${sourceOneId} → ${targetOneId}`);
  }
  return unwrap<string>(
    request({
      url: '/cmd/governance/merge',
      method: 'post',
      params: { sourceOneId, targetOneId, reason: reason || undefined }
    })
  );
};

/** 后端行 → 前端清单行 */
function toApprovalTaskVO(row: CmdApprovalTaskRow): ApprovalTaskVO {
  return {
    taskId: row.taskNo ?? '',
    oneId: row.oneId ?? '',
    customerName: row.bizTitle ?? '',
    taskType: BIZ_TYPE_TEXT[row.bizType ?? ''] ?? row.bizType ?? '',
    source: SCENE_TEXT[row.sceneCode ?? ''] ?? row.sceneCode ?? '',
    bu: row.buScope ?? '',
    dq: row.dqScore == null ? '—' : String(row.dqScore),
    match: row.duplicateState ?? '—',
    sla: SLA_TEXT[row.slaState ?? ''] ?? '正常',
    risk: (['High', 'Medium', 'Low'].includes(row.riskLevel ?? '') ? row.riskLevel : 'Medium') as ApprovalTaskVO['risk'],
    detailType: row.bizType ?? ''
  };
}

/** 按分类拉取清单（后端以 taskCategory 区分队列表） */
async function fetchTasksByCategory(scope: 'bu' | 'gc', category: string, pageNum = 1, pageSize = 10): Promise<PageResult<ApprovalTaskVO>> {
  const page = await unwrap<PageResult<CmdApprovalTaskRow>>(
    request({
      url: '/cmd/approval/list',
      method: 'get',
      params: { scope: scope.toUpperCase(), taskCategory: category, pageNum, pageSize }
    })
  );
  return {
    rows: (page.rows ?? []).map(toApprovalTaskVO),
    total: page.total ?? 0
  };
}

export const getApprovalKpis = async (scope: 'bu' | 'gc'): Promise<ApprovalKpiVO[]> => {
  if (!useLive('approval')) return delay(mock.mockApprovalKpis[scope]);
  const rows = await unwrap<CmdApprovalKpiRow[]>(
    request({ url: '/cmd/approval/kpi', method: 'get', params: { scope: scope.toUpperCase() } })
  );
  return (rows ?? []).map(row => ({ label: row.label ?? '', value: row.value ?? 0, hint: row.hint ?? '' }));
};

/**
 * 侧边导航「数据统计」角标（key = 菜单 id，value = 待处理条数）
 *
 * 全应用统一入口：一次请求拿到当前角色**全部菜单**的实时统计，
 * 由后端按业务表聚合，保证「菜单上的数字」与「点进去页面的数字」一致。
 * 新增菜单只需后端补一条统计，前端无需改动（角标按菜单 id 自动挂载）。
 */
export const getNavBadges = async (role: string): Promise<Record<string, number>> => {
  if (USE_MOCK) return delay(mock.mockNavBadges[role] ?? {});
  const data = await unwrap<Record<string, number>>(
    request({ url: '/cmd/nav/badge', method: 'get', params: { role } })
  );
  const badges: Record<string, number> = {};
  Object.entries(data ?? {}).forEach(([key, value]) => {
    badges[key] = Number(value ?? 0);
  });
  return badges;
};

/**
 * 全部待办：走后端 ALL 聚合口径（待处理 + 退回待补充），服务端分页。
 *
 * 此前在前端把 APPROVAL / GOVERNANCE / RETURNED 三个分类各取一页再 `slice(0, pageSize)`，
 * 结果是：① 审批类任务一满页，治理复核 / 退回任务永远看不到；
 * ② 分页 total 是三类相加、rows 却只有一页，翻页会丢数据。
 * 现在改由后端按 status 聚合，保证「全部待办」与点进去的每个页签口径一致（测试报告 BUG-6）。
 */
export const listApprovalTasks = async (scope: 'bu' | 'gc', pageNum = 1, pageSize = 10): Promise<PageResult<ApprovalTaskVO>> => {
  if (!useLive('approval')) return delay({ rows: mock.mockApprovalTasks[scope], total: mock.mockApprovalTasks[scope].length });
  return fetchTasksByCategory(scope, 'ALL', pageNum, pageSize);
};

/** 按页签分类拉取待办（ALL / APPROVAL / GOVERNANCE / RETURNED / DONE），服务端分页 */
export const listApprovalTasksByCategory = async (
  scope: 'bu' | 'gc',
  category: string,
  pageNum = 1,
  pageSize = 10
): Promise<PageResult<ApprovalTaskVO>> => {
  if (!useLive('approval')) return delay({ rows: mock.mockApprovalTasks[scope], total: mock.mockApprovalTasks[scope].length });
  return fetchTasksByCategory(scope, category, pageNum, pageSize);
};

export const getApprovalReturned = async (scope: 'bu' | 'gc'): Promise<ApprovalTaskVO[]> => {
  if (!useLive('approval')) return delay(mock.mockApprovalReturned[scope]);
  const page = await fetchTasksByCategory(scope, 'RETURNED');
  return page.rows;
};

export const getApprovalDone = async (scope: 'bu' | 'gc'): Promise<ApprovalTaskVO[]> => {
  if (!useLive('approval')) return delay(mock.mockApprovalDone[scope]);
  const page = await fetchTasksByCategory(scope, 'DONE');
  return page.rows;
};

export const getApprovalTaskDetail = async (taskNo: string): Promise<ApprovalTaskDetailVO> => {
  if (!useLive('approval')) return delay(mock.mockApprovalDetail[taskNo] ?? mock.mockApprovalDetail.create);
  const vo = await unwrap<CmdApprovalDetailRow>(request({ url: `/cmd/approval/task/${taskNo}/detail`, method: 'get' }));
  return {
    id: String(vo.id ?? ''),
    oneId: vo.oneId ?? '',
    bizId: vo.bizId ?? '',
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
    /**
     * 分步骤明细（点击步骤后展示）：后端已按节点语义决定每个节点出现哪些区块，
     * 这里只做「缺省值补全」，不做二次拼装，避免与后端口径分叉。
     */
    stepDetails: (vo.stepDetails ?? []).map(d => ({
      nodeCode: d.nodeCode ?? '',
      nodeName: d.nodeName,
      phaseName: d.phaseName,
      lane: d.lane,
      status: d.status,
      summary: d.summary,
      fields: (d.fields ?? []).map(f => ({ label: f.label ?? '', value: f.value, tone: f.tone })),
      tables: (d.tables ?? []).map(t => ({ title: t.title, columns: t.columns ?? [], rows: t.rows ?? [] })),
      notes: d.notes ?? []
    })),
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
 * 工作流：列出所有 CMD 业务场景（V6.1 总设计业务流）及其 Warm-Flow 部署状态
 * 后端 GET /cmd/flow/scenes（CmdFlowTraceController）
 */
export const listFlowScenes = async (): Promise<FlowSceneVO[]> => {
  if (!useLive('approval')) return delay(mock.mockFlowScenes);
  return unwrap<FlowSceneVO[]>(request({ url: '/cmd/flow/scenes', method: 'get' }));
};

/**
 * 工作流：按场景查看流程详细图（泳道图）
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
 * 工作流：流程实例记录（每一次执行过的工作流，可查看 / 用 Graph 回看泳道图）
 * 后端 GET /cmd/flow/instances（CmdFlowTraceController）
 */
/**
 * 业务终态集合：与后端 `CmdFlowTraceServiceImpl.FINAL_STATUSES` 逐字对齐，
 * 保证 Mock 与真实后端的「已完成 / 进行中」口径一致（前端有、后端没有的偏差最难查）。
 */
const FINAL_INSTANCE_STATUSES = ['APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED'];

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
        || (runState === 'DONE' && FINAL_INSTANCE_STATUSES.includes(r.status))
        || (runState === 'RUNNING' && !FINAL_INSTANCE_STATUSES.includes(r.status))
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
 * 工作流：将单个场景部署（幂等）到 Warm-Flow 引擎
 * 后端 POST /cmd/flow/deploy/{sceneCode}
 */
export const deployFlowScene = async (sceneCode: string): Promise<number | string> => {
  if (!useLive('approval')) return delay(1000 + Math.floor(Math.random() * 900));
  return unwrap<number>(request({ url: `/cmd/flow/deploy/${sceneCode}`, method: 'post' }));
};

/* ============================== 10. One ID ============================== */

/** 后端规则 → 页面 VO（补齐页面需要的全部字段） */
function toOneIdRuleVO(row: CmdOneIdRuleRow): OneIdRuleVO {
  return {
    id: row.id,
    ruleCode: row.ruleCode ?? '',
    ruleName: row.ruleName ?? '',
    status: row.status === '0' ? 'Published' : 'Draft',
    object: row.scopeType === 'BU' ? 'Customer / BU' : 'Customer / A1',
    serialLength: `${row.serialLength ?? 6} digits`,
    prefix: row.prefix ?? '',
    separator: row.separator ?? '-',
    pattern: row.pattern ?? '',
    genStrategy: row.genStrategy ?? 'ON_APPROVE',
    stablePolicy: row.stablePolicy ?? 'NEVER_CHANGE',
    reusePolicy: row.reusePolicy ?? 'NEVER_REUSE',
    seqCode: row.seqCode ?? 'ONE_ID',
    remark: row.remark ?? ''
  };
}

export const getOneIdRule = async (): Promise<OneIdRuleVO> => {
  if (!useLive('oneid')) return delay(mock.mockOneIdRule);
  const row = await unwrap<CmdOneIdRuleRow>(request({ url: '/cmd/oneid/rule', method: 'get' }));
  return toOneIdRuleVO(row);
};

/** 保存（更新）当前默认 One ID 规则；保存后状态变为 Draft，需再点「发布规则」才全局生效 */
export const saveOneIdRule = async (form: OneIdRuleVO): Promise<string> => {
  if (!useLive('oneid')) return delay('One ID规则已保存为 Draft（演示模式未落库）');
  const serial = parseInt(form.serialLength, 10) || 6;
  return unwrap<string>(
    request({
      url: '/cmd/oneid/rule',
      method: 'put',
      data: {
        id: form.id,
        ruleName: form.ruleName,
        prefix: form.prefix,
        separator: form.separator,
        serialLength: serial,
        scopeType: form.object === 'Customer / BU' ? 'BU' : 'GC',
        genStrategy: form.genStrategy ?? 'ON_APPROVE',
        stablePolicy: form.stablePolicy ?? 'NEVER_CHANGE',
        reusePolicy: form.reusePolicy ?? 'NEVER_REUSE',
        remark: form.remark
      }
    })
  );
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
    description: row.eventName ?? '',
    operator: row.operatorName ?? '',
    changedFields: row.changedFields ?? null
  }));
};

/** 查询某个 One ID 的合并记录（总设计「审计与合并记录」，保留方 / 被合并方双向） */
export const getMergeRecords = async (oneId: string): Promise<MergeRecordVO[]> => {
  if (!useLive('oneid')) return delay([]);
  const rows = await unwrap<CmdMergeRecordRow[]>(request({ url: `/cmd/oneid/${oneId}/mergeRecords`, method: 'get' }));
  return (rows ?? []).map(row => ({
    mergeCode: row.mergeCode ?? '',
    survivorOneId: row.survivorOneId ?? '',
    mergedOneId: row.mergedOneId ?? '',
    mergeType: row.mergeType ?? 'MANUAL',
    mergeStrategy: row.mergeStrategy ?? '',
    fieldJson: row.fieldJson ?? null,
    reason: row.reason ?? '',
    status: row.status ?? 'EFFECTIVE',
    canRollback: row.canRollback ?? 'Y',
    remark: row.remark ?? null,
    createTime: toDateTimeText(row.createTime)
  }));
};

export const listLegacyMappings = async (): Promise<LegacyMappingVO[]> => {
  if (!useLive('oneid')) return delay(mock.mockLegacyMappings);
  const rows = await unwrap<CmdLegacyMappingRow[]>(request({ url: '/cmd/oneid/legacy/list', method: 'get' }));
  return (rows ?? []).map(row => ({
    oneId: row.oneId ?? '',
    sourceSystem: row.sourceSystem ?? '',
    legacyCode: row.sourceCode ?? '',
    sourceName: row.sourceName ?? '',
    mappingType: row.mappingType ?? 'LEGACY',
    bu: row.buScope ?? '',
    status: row.status === '0' ? 'active' : 'inactive',
    effectiveFrom: toDateText(row.effectiveFrom),
    remark: row.remark ?? null
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
  return unwrapMsg(request({ url: `/cmd/integration/run/${runId}/retry`, method: 'put' }));
};

export const saveIntegrationConn = async (data: IntegrationConnForm): Promise<string> => {
  if (!useLive('integration')) return delay('集成连接已保存，等待连通性测试');
  const code = await unwrap<string>(request({ url: '/cmd/integration/endpoint', method: 'post', data }));
  return `集成端点已保存（${code}），等待连通性测试`;
};

/** 后端端点状态 → 前端展示 */
const ENDPOINT_STATUS_TEXT: Record<string, IntegrationEndpointVO['status']> = {
  '0': 'Active',
  '1': 'Inactive'
};

/** 后端端点行 → 前端展示对象 */
const toEndpointVO = (row: CmdIntegrationEndpointRow): IntegrationEndpointVO => ({
  id: row.id ?? 0,
  code: row.endpointCode ?? '',
  name: row.endpointName ?? '',
  direction: row.direction === 'INBOUND' ? 'Inbound' : 'Outbound',
  protocol: row.protocol ?? '',
  system: row.targetSystem ?? '',
  url: row.endpointUrl ?? '',
  authType: row.authType ?? '',
  bizType: row.bizType ?? '',
  messageFormat: row.messageFormat ?? '',
  maxRetry: row.maxRetry ?? 3,
  timeoutMs: row.timeoutMs ?? 30000,
  status: ENDPOINT_STATUS_TEXT[row.status ?? '0'] ?? 'Active',
  period: row.remark ?? '',
  createTime: row.createTime
});

/** 端点配置列表（端点配置 Tab） */
export const listIntegrationEndpoints = async (): Promise<IntegrationEndpointVO[]> => {
  if (!useLive('integration')) return delay([]);
  const rows = await unwrap<CmdIntegrationEndpointRow[]>(request({ url: '/cmd/integration/endpoint/list', method: 'get' }));
  return (rows ?? []).map(toEndpointVO);
};

/** 保存端点配置（新增 / 编辑） */
export const saveIntegrationEndpoint = async (data: IntegrationConnForm): Promise<string> => {
  if (!useLive('integration')) return delay('集成端点已保存，等待连通性测试');
  const code = await unwrap<string>(request({ url: '/cmd/integration/endpoint', method: 'post', data }));
  return `集成端点已保存（${code}），等待连通性测试`;
};

/** 删除端点 */
export const deleteIntegrationEndpoint = async (id: number): Promise<string> => {
  if (!useLive('integration')) return delay('端点已删除');
  return unwrapMsg(request({ url: `/cmd/integration/endpoint/${id}`, method: 'delete' }));
};

/** 连通性测试 */
export const testIntegrationConn = async (id: number): Promise<string> => {
  if (!useLive('integration')) return delay('连通性测试成功：HTTP 200');
  return unwrapMsg(request({ url: `/cmd/integration/endpoint/${id}/test`, method: 'post' }));
};

/** 手动发布到端点 */
export const publishIntegration = async (id: number, count?: number): Promise<string> => {
  if (!useLive('integration')) return delay('已触发发布，请到运行监控查看结果');
  return unwrapMsg(request({ url: `/cmd/integration/endpoint/${id}/publish`, method: 'post', params: count ? { count } : undefined }));
};

/* ============================== 12. 审计 / 权限 / 覆盖 ============================== */
/** 后端审计结果 → 前端展示文案 */
const AUDIT_RESULT_TEXT: Record<string, AuditEventVO['result']> = {
  SUCCESS: 'Success',
  TEST: 'Tested',
  FAILED: 'Failed'
};

export const listAuditEvents = async (keyword?: string, pageNum = 1, pageSize = 10): Promise<PageResult<AuditEventVO>> => {
  const kw = (keyword ?? '').trim().toLowerCase();
  if (!useLive('audit')) {
    const rows = mock.mockAuditEvents as AuditEventVO[];
    const filtered = kw ? rows.filter(r => r.id.toLowerCase().includes(kw) || r.event.toLowerCase().includes(kw)) : rows;
    return delay({ rows: filtered, total: filtered.length });
  }
  const page = await unwrap<PageResult<CmdAuditEventRow>>(
    request({ url: '/cmd/audit/list', method: 'get', params: { pageNum, pageSize, keyword: kw || undefined } })
  );
  return {
    rows: (page.rows ?? []).map(row => ({
      id: row.eventId ?? '',
      time: row.eventTime ?? '',
      event: row.eventName ?? '',
      role: row.operatorRole ?? '',
      result: AUDIT_RESULT_TEXT[row.result ?? ''] ?? 'Success',
      oneId: row.oneId ?? '',
      bizId: row.bizId ?? ''
    })),
    total: page.total ?? 0
  };
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

/**
 * 场景级工作流配置（平台管理 › Workflow › 工作流定义 › 某一行「配置」）
 * 后端 GET /cmd/flow/scene/{sceneCode}/config
 *
 * 返回 V6.1 第 16 页要求的全部配置项：流程节点（含平台固定 / 可配置标注）、
 * 路由条件、SLA、超时升级与邮件通知。
 */
export const getFlowSceneConfig = async (sceneCode: string): Promise<FlowSceneConfigVO> => {
  if (!useLive('approval')) return delay(mock.buildMockSceneConfig(sceneCode));
  const cfg = await unwrap<FlowSceneConfigVO>(
    request({ url: `/cmd/flow/scene/${sceneCode}/config`, method: 'get' })
  );
  return { ...cfg, nodes: cfg.nodes ?? [], rules: cfg.rules ?? [] };
};

/** 保存场景级工作流配置（后端 PUT /cmd/flow/scene/{sceneCode}/config） */
export const saveFlowSceneConfig = async (sceneCode: string, data: FlowSceneConfigBo): Promise<string> => {
  if (!useLive('approval')) return delay('工作流配置已保存');
  await unwrap(request({ url: `/cmd/flow/scene/${sceneCode}/config`, method: 'put', data }));
  return '工作流配置已保存';
};

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
