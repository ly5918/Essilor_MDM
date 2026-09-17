/**
 * CMD POC 接口层
 *
 * 约定：
 * 1. 所有函数统一返回 Promise<数据本体>，业务层不感知 axios 响应包装；
 * 2. 顶部 USE_MOCK 开关控制走 Mock 还是真实后端，后端 Controller 就绪后
 *    将 VITE_CMD_POC_MOCK 置为 'false' 即可无缝切换，业务代码零改动；
 * 3. 后端路径统一 '/cmd/poc/**'，与 RuoYi-Vue-Plus 的 /demo/** 保持风格一致。
 */
import request from '@/utils/request';
import type { AxiosPromise } from '@/utils/api-types';
import type {
  ApprovalFlowVO,
  ApprovalInstanceVO,
  ApprovalTrailVO,
  AuditEventVO,
  AuditExportForm,
  BatchResultVO,
  ChangeDiffVO,
  ChangeRequestForm,
  ChangeRequestVO,
  CoverageItemVO,
  CustomerForm,
  CustomerQuery,
  CustomerVO,
  DashboardStatVO,
  DeactivateForm,
  DeactivateResultVO,
  DqScorecardVO,
  DqSimulateResultVO,
  DuplicateCandidateVO,
  HierarchyNodeVO,
  HierarchyRelationForm,
  ImportJobVO,
  ImportTemplateVO,
  IntegrationConnForm,
  IntegrationRunVO,
  LegacyMappingVO,
  MatchRuleVO,
  MetadataFieldForm,
  MetadataFieldVO,
  ModelVersionVO,
  NotificationVO,
  OcrResultVO,
  OneIdEventVO,
  OneIdPolicyVO,
  OneIdRuleVO,
  PermissionMatrixVO,
  ReEvaluateForm,
  ReEvaluateImpactVO,
  RolePermissionVO,
  TemplateMappingVO,
  TodoVO,
  WorkflowConfigVO
} from './types';
import * as mock from './mock';

/** Mock 开关：后端就绪后把环境变量 VITE_CMD_POC_MOCK 置为 'false' */
export const USE_MOCK = import.meta.env.VITE_CMD_POC_MOCK !== 'false';

/** 模拟网络延迟，便于演示 loading 态 */
function delay<T>(data: T, ms = 300): Promise<T> {
  return new Promise(resolve => setTimeout(() => resolve(data), ms));
}

/** 真实请求：剥离 axios 响应包装，只返回 data */
async function unwrap<T>(promise: AxiosPromise<T>): Promise<T> {
  const res = await promise;
  return res.data;
}

/* ============================== 1. 工作台 ============================== */
export const getDashboardStats = (): Promise<DashboardStatVO[]> =>
  USE_MOCK ? delay(mock.mockDashboardStats) : unwrap(request({ url: '/cmd/poc/dashboard/stats', method: 'get' }));

export const getTodo = (): Promise<TodoVO> =>
  USE_MOCK ? delay(mock.mockTodo) : unwrap(request({ url: '/cmd/poc/dashboard/todo', method: 'get' }));

export const listNotifications = (): Promise<NotificationVO[]> =>
  USE_MOCK ? delay(mock.mockNotifications) : unwrap(request({ url: '/cmd/poc/dashboard/notifications', method: 'get' }));

/* ============================== 2. 客户主数据 ============================== */
export const listCustomers = (query?: CustomerQuery): Promise<CustomerVO[]> =>
  USE_MOCK ? delay(mock.mockCustomers) : unwrap(request({ url: '/cmd/poc/customer/list', method: 'get', params: query }));

export const submitCustomer = (data: CustomerForm): Promise<string> =>
  USE_MOCK
    ? delay('客户申请已提交，进入DQ与Duplicate Check')
    : unwrap(request({ url: '/cmd/poc/customer', method: 'post', data }));

export const deactivateCustomer = (data: DeactivateForm): Promise<string> =>
  USE_MOCK ? delay('停用申请已提交，等待审批生效') : unwrap(request({ url: '/cmd/poc/customer/deactivate', method: 'put', data }));

/* ============================== 3. 元数据字段 ============================== */
export const listMetadataFields = (): Promise<MetadataFieldVO[]> =>
  USE_MOCK ? delay(mock.mockMetadataFields) : unwrap(request({ url: '/cmd/poc/metadata/field/list', method: 'get' }));

export const saveMetadataField = (data: MetadataFieldForm): Promise<string> =>
  USE_MOCK
    ? delay('字段已保存为Draft；模拟发布后将进入业务表单')
    : unwrap(request({ url: '/cmd/poc/metadata/field', method: 'post', data }));

export const listModelVersions = (): Promise<ModelVersionVO[]> =>
  USE_MOCK ? delay(mock.mockModelVersions) : unwrap(request({ url: '/cmd/poc/metadata/version/list', method: 'get' }));

export const listValueSets = (): Promise<typeof mock.mockValueSets> =>
  USE_MOCK ? delay(mock.mockValueSets) : unwrap(request({ url: '/cmd/poc/metadata/valueset/list', method: 'get' }));

export const publishModelVersion = (): Promise<string> =>
  USE_MOCK
    ? delay('配置版本v1.5已发布；Business User表单将按元数据自动刷新')
    : unwrap(request({ url: '/cmd/poc/metadata/version/publish', method: 'put' }));

/* ============================== 4. 数据质量 ============================== */
export const getDqScorecard = (oneId?: string): Promise<DqScorecardVO> =>
  USE_MOCK ? delay(mock.mockDqScorecard) : unwrap(request({ url: '/cmd/poc/dq/scorecard', method: 'get', params: { oneId } }));

export const simulateDq = (data: Record<string, string>): Promise<DqSimulateResultVO[]> =>
  USE_MOCK ? delay(mock.mockDqSimulate) : unwrap(request({ url: '/cmd/poc/dq/simulate', method: 'post', data }));

export const reEvaluateDq = (data: ReEvaluateForm): Promise<string> =>
  USE_MOCK ? delay('历史数据重评估任务已创建，旧规则版本与旧分数保留') : unwrap(request({ url: '/cmd/poc/dq/reEvaluate', method: 'post', data }));

/** 重评估影响预估（Demo） */
export const getReEvaluateImpact = (): Promise<ReEvaluateImpactVO[]> =>
  USE_MOCK ? delay(mock.mockReEvaluateImpact) : unwrap(request({ url: '/cmd/poc/dq/reEvaluate/impact', method: 'get' }));

/* ============================== 5. 匹配与重复治理 ============================== */
export const listMatchRules = (): Promise<MatchRuleVO[]> =>
  USE_MOCK ? delay(mock.mockMatchRules) : unwrap(request({ url: '/cmd/poc/match/rule/list', method: 'get' }));

export const simulateMatch = (): Promise<MatchRuleVO[]> =>
  USE_MOCK ? delay(mock.mockMatchRules) : unwrap(request({ url: '/cmd/poc/match/simulate', method: 'post' }));

export const getDuplicateCandidate = (): Promise<DuplicateCandidateVO> =>
  USE_MOCK ? delay(mock.mockDuplicateCandidate) : unwrap(request({ url: '/cmd/poc/duplication/candidate', method: 'get' }));

export const linkExistingOneId = (oneId: string): Promise<string> =>
  USE_MOCK ? delay(`已关联One ID ${oneId}`) : unwrap(request({ url: '/cmd/poc/duplication/link', method: 'put', data: { oneId } }));

export const confirmNewCustomer = (): Promise<string> =>
  USE_MOCK ? delay('已进入新客户审批') : unwrap(request({ url: '/cmd/poc/duplication/confirmNew', method: 'put' }));

/* ============================== 6. 批量导入 ============================== */
export const listImportJobs = (): Promise<ImportJobVO[]> =>
  USE_MOCK ? delay(mock.mockImportJobs) : unwrap(request({ url: '/cmd/poc/import/job/list', method: 'get' }));

export const getBatchResult = (jobId: string): Promise<BatchResultVO> =>
  USE_MOCK ? delay(mock.mockBatchResult) : unwrap(request({ url: `/cmd/poc/import/job/${jobId}/result`, method: 'get' }));

export const createImportJob = (fileName: string): Promise<string> =>
  USE_MOCK ? delay(`导入任务已创建：${fileName}`) : unwrap(request({ url: '/cmd/poc/import/job', method: 'post', data: { fileName } }));

export const listImportTemplates = (): Promise<ImportTemplateVO[]> =>
  USE_MOCK ? delay(mock.mockImportTemplates) : unwrap(request({ url: '/cmd/poc/import/template/list', method: 'get' }));

export const listTemplateMappings = (): Promise<TemplateMappingVO[]> =>
  USE_MOCK ? delay(mock.mockTemplateMappings) : unwrap(request({ url: '/cmd/poc/import/template/mapping', method: 'get' }));

/* ============================== 7. 客户层级 ============================== */
export const getHierarchy = (): Promise<HierarchyNodeVO[]> =>
  USE_MOCK ? delay(mock.mockHierarchy) : unwrap(request({ url: '/cmd/poc/hierarchy/tree', method: 'get' }));

export const getHierarchyNode = (key: string): Promise<HierarchyNodeVO | undefined> =>
  USE_MOCK ? delay(mock.mockHierarchyNodes[key]) : unwrap(request({ url: `/cmd/poc/hierarchy/node/${key}`, method: 'get' }));

export const searchHierarchy = (keyword: string): Promise<HierarchyNodeVO[]> =>
  USE_MOCK
    ? delay(
        keyword.includes('苏州')
          ? [mock.mockHierarchyNodes.suzhou]
          : [mock.mockHierarchyNodes.store, mock.mockHierarchyNodes.legal, mock.mockHierarchyNodes.group]
      )
    : unwrap(request({ url: '/cmd/poc/hierarchy/search', method: 'get', params: { keyword } }));

export const addHierarchyRelation = (data: HierarchyRelationForm): Promise<string> =>
  USE_MOCK ? delay('层级关系已提交，Loop Check 通过') : unwrap(request({ url: '/cmd/poc/hierarchy/relation', method: 'post', data }));

export const loopCheck = (): Promise<string> =>
  USE_MOCK
    ? delay('检测到循环路径：A1-000128 → A2-0188 → A1-000128。系统阻止提交，并保留冲突路径用于修正。')
    : unwrap(request({ url: '/cmd/poc/hierarchy/loopCheck', method: 'get' }));

/* ============================== 8. 变更与停用 ============================== */
export const listChangeRequests = (): Promise<ChangeRequestVO[]> =>
  USE_MOCK ? delay(mock.mockChangeRequests) : unwrap(request({ url: '/cmd/poc/change/list', method: 'get' }));

export const submitChangeRequest = (data: ChangeRequestForm): Promise<string> =>
  USE_MOCK
    ? delay('变更申请已提交，进入审批流程')
    : unwrap(request({ url: '/cmd/poc/change', method: 'post', data }));

export const getChangeDetail = (requestId: string): Promise<{ diffs: ChangeDiffVO[]; trail: ApprovalTrailVO[] }> =>
  USE_MOCK
    ? delay({ diffs: mock.mockChangeDiffs, trail: mock.mockChangeTrail })
    : unwrap(request({ url: `/cmd/poc/change/${requestId}/detail`, method: 'get' }));

export const getDeactivateResult = (oneId: string): Promise<DeactivateResultVO> =>
  USE_MOCK ? delay(mock.mockDeactivateResult) : unwrap(request({ url: `/cmd/poc/change/${oneId}/deactivateResult`, method: 'get' }));

/* ============================== 9. 审批 ============================== */
export const getApprovalFlow = (key: string): Promise<ApprovalFlowVO> =>
  USE_MOCK
    ? delay(mock.mockApprovalFlows[key] ?? mock.mockApprovalFlows.approvalHE)
    : unwrap(request({ url: `/cmd/poc/approval/flow/${key}`, method: 'get' }));

export const listApprovalInstances = (): Promise<ApprovalInstanceVO[]> =>
  USE_MOCK ? delay(mock.mockApprovalInstances) : unwrap(request({ url: '/cmd/poc/approval/instance/list', method: 'get' }));

/* ============================== 10. One ID ============================== */
export const getOneIdRule = (): Promise<OneIdRuleVO> =>
  USE_MOCK ? delay(mock.mockOneIdRule) : unwrap(request({ url: '/cmd/poc/oneid/rule', method: 'get' }));

export const publishOneIdRule = (): Promise<string> =>
  USE_MOCK ? delay('One ID规则v1.2已模拟发布') : unwrap(request({ url: '/cmd/poc/oneid/rule/publish', method: 'put' }));

export const copyOneIdRule = (): Promise<string> =>
  USE_MOCK ? delay('已复制规则为Draft v1.2') : unwrap(request({ url: '/cmd/poc/oneid/rule/copy', method: 'put' }));

export const listOneIdPolicies = (): Promise<OneIdPolicyVO[]> =>
  USE_MOCK ? delay(mock.mockOneIdPolicies) : unwrap(request({ url: '/cmd/poc/oneid/policy/list', method: 'get' }));

export const getOneIdHistory = (oneId: string): Promise<OneIdEventVO[]> =>
  USE_MOCK ? delay(mock.mockOneIdEvents) : unwrap(request({ url: `/cmd/poc/oneid/${oneId}/history`, method: 'get' }));

export const listLegacyMappings = (): Promise<LegacyMappingVO[]> =>
  USE_MOCK ? delay(mock.mockLegacyMappings) : unwrap(request({ url: '/cmd/poc/oneid/legacy/list', method: 'get' }));

/* ============================== 11. 集成监控 ============================== */
export const listIntegrationRuns = (): Promise<IntegrationRunVO[]> =>
  USE_MOCK ? delay(mock.mockIntegrationRuns) : unwrap(request({ url: '/cmd/poc/integration/run/list', method: 'get' }));

export const retryIntegration = (runId: string): Promise<string> =>
  USE_MOCK ? delay(`已重试同步：${runId}`) : unwrap(request({ url: `/cmd/poc/integration/run/${runId}/retry`, method: 'put' }));

export const saveIntegrationConn = (data: IntegrationConnForm): Promise<string> =>
  USE_MOCK ? delay('集成连接已保存，等待连通性测试') : unwrap(request({ url: '/cmd/poc/integration/conn', method: 'post', data }));

/* ============================== 12. 审计 / 权限 / 覆盖 ============================== */
export const listAuditEvents = (): Promise<AuditEventVO[]> =>
  USE_MOCK ? delay(mock.mockAuditEvents) : unwrap(request({ url: '/cmd/poc/audit/list', method: 'get' }));

export const exportAudit = (data: AuditExportForm): Promise<string> =>
  USE_MOCK ? delay('审计报告已生成并置于下载中心') : unwrap(request({ url: '/cmd/poc/audit/export', method: 'post', data }));

export const getPermissionMatrix = (): Promise<PermissionMatrixVO[]> =>
  USE_MOCK ? delay(mock.mockPermissionMatrix) : unwrap(request({ url: '/cmd/poc/permission/matrix', method: 'get' }));

export const listRolePermissions = (): Promise<RolePermissionVO[]> =>
  USE_MOCK ? delay(mock.mockRolePermissions) : unwrap(request({ url: '/cmd/poc/permission/role/list', method: 'get' }));

export const saveRolePermissions = (data: RolePermissionVO[]): Promise<string> =>
  USE_MOCK ? delay('角色权限已保存') : unwrap(request({ url: '/cmd/poc/permission/role', method: 'put', data }));

export const listCoverage = (): Promise<CoverageItemVO[]> =>
  USE_MOCK ? delay(mock.mockCoverage) : unwrap(request({ url: '/cmd/poc/coverage/list', method: 'get' }));

/* ============================== 13. 工作流 / OCR ============================== */
export const getWorkflow = (): Promise<WorkflowConfigVO> =>
  USE_MOCK ? delay(mock.mockWorkflow) : unwrap(request({ url: '/cmd/poc/workflow/config', method: 'get' }));

export const saveWorkflow = (data: WorkflowConfigVO): Promise<string> =>
  USE_MOCK ? delay('工作流配置已保存') : unwrap(request({ url: '/cmd/poc/workflow/config', method: 'put', data }));

export const ocrRecognize = (): Promise<OcrResultVO[]> =>
  USE_MOCK ? delay(mock.mockOcrResults, 800) : unwrap(request({ url: '/cmd/poc/ocr/recognize', method: 'post' }));
