/**
 * 角色与菜单配置（1:1 对齐原型 R 常量）
 *
 * 5 类技术角色，BU / GC 通过 Data Steward 的 Scope 区分。
 * 后端接入时可替换为「根据当前登录用户角色返回菜单」。
 */
import type { MenuId, RoleKey } from '@/api/demo/cmdPoc/types';

export interface PocMenu {
  /** 菜单标识：叶子=可路由页面；二级菜单容器=`sub-` 前缀伪 id（不可路由） */
  id: MenuId;
  /** 菜单名称（同时作为面包屑末级文案） */
  label: string;
  /**
   * 是否为「审批类」菜单：只有需要人工审批/复核的菜单才在侧栏显示待办统计角标。
   *
   * 严格对齐业务口径——NOT 每个菜单都需要统计。当前仅有：
   * - Data Steward · BU Scope：「治理与审批」「批量治理」
   * - Data Steward · GC Scope：「全局治理决策」「批量治理」
   * 其余菜单（工作台 / 客户主档 / 客户层级 / 变更与停用 / 流程中心 / 审计 / 集成 / 平台管理）
   * 以及 Business User、Platform Admin、Auditor 角色的全部菜单均不显示角标。
   */
  requiresApproval?: boolean;
  /** 菜单图标（纯文字占位，避免引入图标库差异） */
  icon: string;
  /** 子菜单（RuoYi 二级菜单样式，如「工作流」下的两个实例列表视图） */
  children?: PocMenu[];
}

export interface PocRole {
  key: RoleKey;
  /** 角色名称 */
  name: string;
  /** 头像缩写 */
  alias: string;
  /** 数据范围 */
  scope: string;
  /** 角色主题色 */
  color: string;
  /** 是否只读（Auditor） */
  readOnly: boolean;
  menus: PocMenu[];
}

/**
 * 「工作流」二级菜单（RuoYi 子菜单样式）：运行态视图，5 个角色通用。
 *
 * 两个子页分工（列表页分离，避免「一个页面既当列表又当详情」）：
 * - 已激活工作流：运行中、未结束的实例，行内可看「流程跟踪」；
 * - 已完成的工作流：已结束实例的**列表**（可按状态筛），行内「查看流程跟踪」
 *   打开**弹窗**看该次执行的完整链路。
 *
 * 「流程跟踪」刻意**不做菜单项**：它是「某一次执行」的详情，
 * 属于从列表下钻的二级动作，形态与「工作流定义 → 某行配置」一致（点开弹窗），
 * 而不是一个可以独立进入的页面——否则会出现「进了页面却不知道看哪条实例」的空态。
 *
 * 注意：「工作流定义」也不在这里——按 V6.1 第 3 / 10 / 16 页，
 * Workflow 属于 Platform Admin 的平台管理能力（配置态），
 * 入口为「平台管理 → Workflow 卡片 → 管理」，因此它没有侧边栏菜单项。
 */
const FLOW_CENTER_MENU: PocMenu = {
  id: 'sub-flowCenter',
  label: '流程中心',
  icon: '流',
  children: [
    { id: 'flowWorkitem', label: '已激活工作流', icon: '活' },
    { id: 'flowDone', label: '已完成的工作流', icon: '完' }
  ]
};

export const ROLE_LIST: PocRole[] = [
  {
    key: 'business',
    name: 'Business User',
    alias: 'BU',
    scope: 'High End · Frame',
    color: '#176c9f',
    readOnly: false,
    menus: [
      { id: 'dash', label: '工作台', icon: '工' },
      { id: 'customers', label: '客户管理', icon: '客' },
      { id: 'batch', label: '批量导入', icon: '批' },
      { id: 'hier', label: '客户层级', icon: '层' },
      { id: 'change', label: '变更与停用', icon: '变' },
      FLOW_CENTER_MENU
    ]
  },
  {
    key: 'bu',
    name: 'Data Steward',
    alias: 'DS',
    scope: 'BU Scope · High End',
    color: '#4d944e',
    readOnly: false,
    menus: [
      { id: 'dash', label: '工作台', icon: '工' },
      { id: 'approval', label: '治理与审批', icon: '审', requiresApproval: true },
      { id: 'customers', label: '客户主档', icon: '客' },
      { id: 'hier', label: '客户层级', icon: '层' },
      { id: 'batch', label: '批量治理', icon: '批', requiresApproval: true },
      { id: 'change', label: '变更与停用', icon: '变' },
      FLOW_CENTER_MENU
    ]
  },
  {
    key: 'gc',
    name: 'Data Steward',
    alias: 'GC',
    scope: 'GC Scope · Cross-BU',
    color: '#db7c18',
    readOnly: false,
    menus: [
      { id: 'dash', label: '全局工作台', icon: '工' },
      { id: 'approval', label: '全局治理决策', icon: '审', requiresApproval: true },
      { id: 'customers', label: '全局客户主档', icon: '客' },
      { id: 'hier', label: '客户层级', icon: '层' },
      { id: 'batch', label: '批量治理', icon: '批', requiresApproval: true },
      { id: 'change', label: '变更与停用', icon: '变' },
      FLOW_CENTER_MENU,
      { id: 'audit', label: '治理审计', icon: '审' }
    ]
  },
  {
    key: 'admin',
    name: 'Platform Admin',
    alias: 'AD',
    scope: 'Platform & Integration',
    color: '#d59b22',
    readOnly: false,
    menus: [
      { id: 'dash', label: '管理工作台', icon: '工' },
      { id: 'admin', label: '平台管理', icon: '管' },
      { id: 'integration', label: '集成监控', icon: '集' },
      FLOW_CENTER_MENU,
      { id: 'audit', label: '管理员日志', icon: '审' }
    ]
  },
  {
    key: 'audit',
    name: 'Auditor',
    alias: 'AU',
    scope: 'Read Only · Authorized',
    color: '#7955a8',
    readOnly: true,
    menus: [
      { id: 'dash', label: '审计工作台', icon: '工' },
      { id: 'audit', label: '审计中心', icon: '审' },
      { id: 'customers', label: '客户只读查询', icon: '客' },
      { id: 'hier', label: '层级只读查询', icon: '层' },
      FLOW_CENTER_MENU
    ]
  }
];

/** 顶部「模拟角色」下拉文案（对齐原型 <select> 选项） */
export const ROLE_DROPDOWN_LABELS: Record<RoleKey, string> = {
  business: 'Business User',
  bu: 'Data Steward · BU Scope',
  gc: 'Data Steward · GC Scope',
  admin: 'Platform Admin',
  audit: 'Auditor · Read Only'
};

/** 工作台标题（按角色区分，对齐原型规则） */
export const DASHBOARD_TITLES: Record<RoleKey, string> = {
  business: 'Business User工作台',
  bu: 'BU Scope治理工作台',
  gc: 'GC Scope全局治理工作台',
  admin: 'Platform Admin工作台',
  audit: 'Auditor工作台'
};

export const getRole = (key: RoleKey): PocRole => ROLE_LIST.find(item => item.key === key) ?? ROLE_LIST[0];
