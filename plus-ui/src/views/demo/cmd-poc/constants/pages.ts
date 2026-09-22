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
  customers: { title: 'Customer Management', sub: '客户创建、查看、OCR识别与数据权限控制' },
  batch: { title: '批量导入中心', sub: '预检、DQ、匹配、治理、部分成功' },
  gov: { title: '治理任务', sub: 'Suspect与Review记录处理' },
  hier: { title: '客户层级工作台', sub: '搜索、定位、浏览、编辑关系与增加子节点' },
  admin: { title: '平台管理', sub: '字段、值集、规则、模板、流程与权限' },
  integration: { title: '集成监控', sub: 'Inbound、Outbound、Retry' },
  oneid: { title: 'One ID规则管理', sub: '命名规则、自动生成、稳定性与本地编码映射' },
  change: { title: '客户变更与逻辑停用', sub: 'Update / Delete全生命周期、审批和Before / After' },
  approval: { sub: '审批、治理与变更统一处理（按角色区分 BU / GC）' },
  dqscore: { title: 'Data Quality Scorecard', sub: '技术规则、业务规则、分数卡与历史重评估' },
  coverage: { title: 'POC覆盖检查', sub: '12个正式Demo Topic与当前交互原型' },
  audit: { title: '审计中心', sub: '变更、审批、合并、权限与管理员操作' },
  flowDefinition: { title: '工作流定义', sub: 'CMD 业务场景与 Warm-Flow 流程定义映射、部署、按场景配置与泳道图查看' },
  flowWorkitem: { title: '已激活工作流', sub: '运行中、未结束的实例（事务ID 贯穿查询），点「流程跟踪」看该单的逐步明细与泳道图' },
  flowDone: { title: '已完成的工作流', sub: '已结束的实例（已批准 / 已拒绝 / 已取消 / 已完成），点「流程跟踪」弹窗看该次执行的完整链路，弹窗内可再看泳道图' }
};
