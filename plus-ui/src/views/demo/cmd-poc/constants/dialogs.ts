/**
 * 弹窗注册表
 *
 * 统一维护「弹窗 key → 标题 / 宽度」，避免各页面各自硬编码标题。
 *
 * wide 与 confirmable 均 1:1 对齐原型：
 * - wide = true 对应原型 modal() 中追加的 .modal.wide（900px），否则标准 680px；
 * - 原型弹窗底部恒为「关闭 + 确认」，因此所有弹窗 confirmable 均为 true
 *   （纯查看类弹窗的确认动作只写审计日志，不修改业务数据）。
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
  | 'search'
  | 'batchResult'
  | 'batchUpload'
  | 'hierAdd'
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
  | 'reEvaluate'
  | 'ocr';

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
  template: define('template', '导入模板管理', true),
  workflow: define('workflow', 'Workflow配置', true),
  permissions: define('permissions', '角色与权限管理', true),
  search: define('search', '客户查询结果', true, '查询'),
  batchResult: define('batchResult', '批量结果分流', true),
  batchUpload: define('batchUpload', '新建批量导入任务', true, '提交'),
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
  integration: define('integration', '集成任务详情', true, 'Retry'),
  auditExport: define('auditExport', '导出审计报告', false, '导出'),
  oneIdHistory: define('oneIdHistory', 'One ID生命周期历史', true),
  changeRequest: define('changeRequest', '发起属性变更', true, '提交变更'),
  deactivate: define('deactivate', '申请逻辑停用', true, '提交申请'),
  changeDetail: define('changeDetail', '变更详情 · Before / After', true),
  deactivateResult: define('deactivateResult', '逻辑停用 · 数据库结果', true),
  approvalHE: define('approvalHE', 'High End审批实例', true),
  approvalMS: define('approvalMS', 'Mainstream审批实例', true),
  reEvaluate: define('reEvaluate', '历史DQ重评估', true, '执行重评估'),
  ocr: define('ocr', 'OCR识别结果', false, '写回表单')
};

export const DIALOG_KEYS = Object.keys(DIALOG_MAP) as DialogKey[];
