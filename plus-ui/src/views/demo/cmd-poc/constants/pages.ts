/**
 * 页面元信息配置（标题 + 副标题，1:1 对齐原型各页面 page-title）
 */
import type { PageId } from '@/api/demo/cmdPoc/types';

export interface PocPageMeta {
  /** 页面标题（缺省时取当前角色菜单名称） */
  title?: string;
  /** 副标题 */
  sub: string;
}

/** 原型中固定标题的页面；其余页面标题跟随角色菜单文案 */
export const PAGE_META: Record<PageId, PocPageMeta> = {
  dash: { sub: '根据角色与Scope动态显示' },
  customers: { title: '客户管理', sub: '查询、主档、变更与逻辑停用' },
  batch: { title: '批量导入中心', sub: '预检、DQ、匹配、治理、部分成功' },
  gov: { title: '治理任务', sub: '疑似重复与Cross-BU决策' },
  hier: { title: '客户层级', sub: 'A3-A2-A1、Payer与Loop Check' },
  admin: { title: '平台管理', sub: '字段、值集、规则、模板、流程、权限与集成' },
  integration: { title: '集成监控', sub: 'Inbound、Outbound、Retry' },
  oneid: { title: 'One ID规则管理', sub: '命名规则、自动生成、稳定性与本地编码映射' },
  change: { title: '客户变更与逻辑停用', sub: 'Update / Delete全生命周期、审批和数据库结果' },
  approval: { title: '审批实例', sub: 'High End与Mainstream不同流程实例' },
  dqscore: { title: 'Data Quality Scorecard', sub: '技术规则、业务规则、分数卡与历史重评估' },
  coverage: { title: 'POC覆盖检查', sub: '12个正式Demo Topic与当前交互原型' },
  audit: { title: '审计中心', sub: '变更、审批、合并、权限与管理员操作' }
};
