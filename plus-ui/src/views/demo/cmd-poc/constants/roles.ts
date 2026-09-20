/**
 * 角色与菜单配置（1:1 对齐原型 R 常量）
 *
 * 5 类技术角色，BU / GC 通过 Data Steward 的 Scope 区分。
 * 后端接入时可替换为「根据当前登录用户角色返回菜单」。
 */
import type { PageId, RoleKey } from '@/api/demo/cmdPoc/types';

export interface PocMenu {
  /** 页面编码 */
  id: PageId;
  /** 菜单名称（同时作为面包屑末级文案） */
  label: string;
  /** 菜单右上角徽标（待办数） */
  badge?: string;
  /** 菜单图标（纯文字占位，避免引入图标库差异） */
  icon: string;
  /** 子菜单（RuoYi 二级菜单样式，如「流程中心」下的工作流三视图） */
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

/** 「流程中心」二级菜单（RuoYi 子菜单样式）：定义 / 三视图 / 流程跟踪 */
const FLOW_CENTER_MENU: PocMenu = {
  id: 'flowCenter',
  label: '流程中心',
  icon: '流',
  children: [
    { id: 'flowCenter', label: '工作流定义', icon: '流' },
    { id: 'flowWorkitem', label: '工作项', icon: '项' },
    { id: 'flowActive', label: '已激活工作流', icon: '启' },
    { id: 'flowDone', label: '已完成的工作流', icon: '毕' }
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
      { id: 'approval', label: '治理与审批', badge: '8', icon: '审' },
      { id: 'customers', label: '客户主档', icon: '客' },
      { id: 'hier', label: '客户层级', icon: '层' },
      { id: 'batch', label: '批量治理', badge: '3', icon: '批' },
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
      { id: 'approval', label: '全局治理决策', badge: '5', icon: '审' },
      { id: 'customers', label: '全局客户主档', icon: '客' },
      { id: 'hier', label: '客户层级', icon: '层' },
      { id: 'batch', label: '批量治理', badge: '2', icon: '批' },
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
