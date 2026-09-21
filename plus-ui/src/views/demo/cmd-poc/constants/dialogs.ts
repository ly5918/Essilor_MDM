/**
 * 弹窗注册表
 *
 * 统一维护「弹窗 key → 标题 / 宽度」，避免各页面各自硬编码标题。
 *
 * wide 与 confirmable 均 1:1 对齐原型：
 * - wide = true 对应原型 modal() 中追加的 .modal.wide（900px），否则标准 680px；
 * - 原型弹窗底部恒为「关闭 + 确认」，因此除**只读查看器**（如 flowTrace 流程跟踪）外，
 *   confirmable 均为 true（纯查看类弹窗的确认动作只写审计日志，不修改业务数据）。
 */
export type DialogKey =
  | 'fields'
  | 'newFieldForm'
  | 'newCustomer'
  | 'dq'
  | 'match'
  | 'template'
  | 'workflow'
  | 'permissions'
  | 'batchResult'
  | 'batchUpload'
  | 'batchSource'
  | 'hierAdd'
  | 'hierAssign'
  | 'loop'
  | 'integration'
  | 'auditExport'
  | 'oneIdHistory'
  | 'changeRequest'
  | 'deactivate'
  | 'changeDetail'
  | 'deactivateResult'
  | 'approvalHE'
  | 'approvalMS'
  | 'flowTrace'
  | 'reEvaluate'
  | 'ocr'
  | 'flowGraph'
  | 'customerDetail';

export type DialogTitle = string | ((payload?: Record<string, unknown>) => string);
export type DialogButtonText = string | ((payload?: Record<string, unknown>) => string);

export interface DialogMeta {
  key: DialogKey;
  title: DialogTitle;
  width: string;
  wide: boolean;
  /** 弹窗底部是否显示「确认」按钮（原型恒为 true） */
  confirmable: boolean;
  /** 确认按钮文案 */
  confirmText?: DialogButtonText;
}

const define = (key: DialogKey, title: DialogTitle, wide = false, confirmText: DialogButtonText = '确认'): DialogMeta => ({
  key,
  title,
  width: wide ? '900px' : '680px',
  wide,
  confirmable: true,
  confirmText
});

export const DIALOG_MAP: Record<DialogKey, DialogMeta> = {
  fields: define('fields', '字段与值集管理', true),
  newFieldForm: define('newFieldForm', '新建元数据字段', true, '保存为Draft'),
  newCustomer: define('newCustomer', '新建客户申请', true, '提交申请'),
  dq: define('dq', 'DQ规则模拟测试', true, '开始测试'),
  match: define('match', '匹配规则模拟测试', true, '开始测试'),
  template: define('template', '下载模板', true),
  // 按场景打开（平台管理 → Workflow → 工作流定义 → 某一行「配置」）
  // 页签：流程节点 / 路由条件 / SLA 与升级 / 版本与发布（对齐 V6.1 第 16 页 Workflow配置）
  // 宽度取 1120px：节点路由规则表共 6 列（节点/命中条件/办理角色/会签或签/节点SLA/启用），
  // 900px 下最后一列会被 .el-dialog__body 的 overflow-x hidden 截断。
  workflow: {
    ...define(
      'workflow',
      payload => (payload?.sceneName ? `Workflow配置 · ${payload.sceneName}` : 'Workflow配置'),
      true,
      '保存配置'
    ),
    width: '1120px'
  },
  permissions: define('permissions', '角色与权限管理', true),
  batchResult: define('batchResult', '批量结果分流', true),
  batchUpload: define('batchUpload', '新建批量导入任务', true, '提交'),
  /**
   * 上传数据明细（导入任务列表 → 行内「查看上传数据」）
   * <p>
   * 只读查看器：底部只留「关闭」，不显示只会写审计日志的假「确认」按钮（与 flowTrace / flowGraph / customerDetail 同口径）。
   * 宽度 1120px：列名由模板字段映射动态生成（每个 Excel 列一列），窄弹窗会把动态列挤成横向滚动。
   */
  batchSource: {
    ...define('batchSource', payload => `上传数据明细 · ${payload?.jobId ?? ''}`, true),
    width: '1120px',
    confirmable: false
  },
  hierAdd: define(
    'hierAdd',
    payload => {
      const mode = payload?.mode as string | undefined;
      return mode === 'request'
        ? '发起层级关系申请'
        : mode === 'edit'
          ? '编辑层级关系'
          : mode === 'child'
            ? '增加子节点'
            : '新增层级关系';
    },
    true,
    payload => {
      const mode = payload?.mode as string | undefined;
      return mode === 'request' ? '提交申请' : mode === 'edit' ? '保存' : '提交审批';
    }
  ),
  loop: define('loop', 'Loop Check'),
  hierAssign: define(
    'hierAssign',
    payload => `主数据归位 · ${payload?.name ?? payload?.oneId ?? ''}`,
    true,
    '确认归位'
  ),
  integration: define('integration', '集成任务详情', true, 'Retry'),
  auditExport: define('auditExport', '导出审计报告', false, '导出'),
  oneIdHistory: define('oneIdHistory', 'One ID生命周期历史', true),
  changeRequest: define('changeRequest', '发起属性变更', true, '提交变更'),
  deactivate: define('deactivate', '申请逻辑停用', true, '提交申请'),
  // 变更详情是只读查看器：底部只保留「关闭」，业务操作（生效 / 撤回）在弹窗内联处理
  changeDetail: {
    ...define('changeDetail', '变更详情 · Before / After', true),
    confirmable: false
  },
  deactivateResult: define('deactivateResult', '逻辑停用 · 数据库结果', true),
  approvalHE: define('approvalHE', 'High End审批实例', true),
  approvalMS: define('approvalMS', 'Mainstream审批实例', true),
  /**
   * 流程跟踪：不做侧边栏菜单，而是「已完成的工作流」行内「查看流程跟踪」打开的弹窗
   * （形态同「工作流定义 → 某行配置」）。
   * - 宽度 90%：内容含 BPMN 流程图 + 11 步泳道条 + 分步骤明细表 + 上下文变量，
   *   窄弹窗会把明细表挤成横向滚动；
   * - confirmable=false：它是**只读查看器**，底部只保留「关闭」，
   *   不显示一个只会写审计日志的「确认」按钮。
   */
  flowTrace: {
    ...define('flowTrace', payload => `流程跟踪 · ${payload?.taskNo ?? ''}`, true),
    width: '90%',
    confirmable: false
  },
  flowGraph: {
    ...define(
      'flowGraph',
      payload => (payload?.taskNo ? `泳道图 · ${payload.taskNo}` : `泳道图 · ${payload?.sceneName ?? ''}`),
      true
    ),
    width: '90%',
    // 只读查看器：与 flowTrace 同理。之前 confirmable 默认 true，
    // 点「确认」会落入 DialogHost 的通用兜底提示「模拟操作已完成并写入审计日志」并关窗——
    // 泳道图没有任何可提交的表单，不该出现这个假动作。
    confirmable: false
  },
  reEvaluate: define('reEvaluate', '历史DQ重评估', true, '执行重评估'),
  ocr: define('ocr', 'OCR识别结果', false, '写回表单'),
  /**
   * 客户主档详情（客户列表 → 点 One ID / 「查看客户」）
   * <p>
   * 只读查看器，底部只留「关闭」，不显示只会写审计日志的假「确认」按钮（与 flowTrace / flowGraph 同口径）。
   * <p>
   * 内容组织：抬头 + 指标带常驻，其下**每个字段分组一个页签**
   * （标识与名称 / 分类与归属 / 联络与地址 / 来源与数据质量 / 治理状态与生效 / 时间戳与扩展），
   * 外加「One ID 生命周期历史」页签（懒加载）——原先历史是列表操作列里的独立弹窗，
   * 现收敛到详情内，列表操作列只留「查看客户」一个入口。
   * 宽度 1120px：7 个页签需要比 1000px 更宽的横向空间，否则页签会退化成左右箭头翻页。
   */
  customerDetail: {
    ...define('customerDetail', payload => `客户主档 · ${payload?.oneId ?? ''}`, true),
    width: '1120px',
    confirmable: false
  }
};

export const DIALOG_KEYS = Object.keys(DIALOG_MAP) as DialogKey[];
