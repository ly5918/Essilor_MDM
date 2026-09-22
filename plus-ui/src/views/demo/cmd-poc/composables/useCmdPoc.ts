/**
 * CMD POC 工作台共享状态
 *
 * 采用 provide / inject 单例注入，避免各面板组件之间层层透传。
 * 职责边界：
 * - 只放「跨页面共享」的状态：当前角色、当前页面、弹窗调度、主数据缓存；
 * - 页面内部的查询条件、表单值由各面板自行维护。
 */
import { computed, inject, provide, reactive, ref, type ComputedRef, type InjectionKey, type Ref } from 'vue';

import * as cmdPocApi from '@/api/demo/cmdPoc';
import type { CustomerVO, HierarchyNodeVO, MetadataFieldVO, OcrResultVO, PageId, RoleKey } from '@/api/demo/cmdPoc/types';
import { DIALOG_MAP, type DialogKey } from '../constants/dialogs';
import { PAGE_META } from '../constants/pages';
import { getRole, type PocRole } from '../constants/roles';

/** 客户在层级体系中的位置（客户列表「层级」列 / 客户详情展示） */
export interface HierarchyIndexItem {
  /** A3 / A2 / A1；待归位时为 '—' */
  level: string;
  /** 完整路径（已归位时才有值） */
  path: string;
  /** true=已挂到 A3-A2-A1 树；false=已批准但待归位 */
  mounted: boolean;
  /** 主数据名称（客户详情「层级关联」对端节点展示用） */
  name?: string;
}

export interface DialogState {
  /** 当前打开的弹窗 key，空串表示无 */
  current: DialogKey | '';
  /** 弹窗入参（如 One ID、Request ID） */
  payload: Record<string, unknown>;
}

export interface CmdPocContext {
  /** 当前角色编码 */
  roleKey: Ref<RoleKey>;
  /** 当前角色配置 */
  role: ComputedRef<PocRole>;
  /** 是否只读角色（Auditor） */
  readOnly: ComputedRef<boolean>;
  /** 当前页面 */
  currentPage: Ref<PageId>;
  /** 面包屑上级页面（下钻场景保留来源） */
  currentSub: Ref<string>;
  /** 当前页面标题 */
  pageTitle: ComputedRef<string>;
  /** 当前页面菜单项 */
  currentMenu: ComputedRef<PocRole['menus'][number] | undefined>;
  /** 弹窗状态 */
  dialog: DialogState;
  /** 打开弹窗 */
  openDialog: (key: DialogKey, payload?: Record<string, unknown>) => void;
  /** 关闭弹窗 */
  closeDialog: () => void;
  /** 菜单 / 快捷入口跳转 */
  goMenu: (id: PageId, sub?: string) => void;
  /** 客户主数据缓存 */
  customers: Ref<CustomerVO[]>;
  loadCustomers: () => Promise<void>;
  /**
   * 层级索引：oneId → 层级位置。
   * 由「已归位的 A3-A2-A1 树」+「待归位主数据」合并而成，
   * 是客户列表「层级」列与客户详情展示层级归属的唯一依据。
   */
  hierarchyIndex: Ref<Map<string, HierarchyIndexItem>>;
  /** 拉取层级索引（树 + 待归位） */
  loadHierarchyIndex: () => Promise<void>;
  /** 层级变更版本号：归位成功后自增，客户层级页据此重新拉取树与待归位列表 */
  hierarchyVersion: Ref<number>;
  /** 标记层级已变更 */
  markHierarchyChanged: () => void;
  /**
   * 变更 / 停用数据版本号：提交申请、生效、撤回成功后自增，
   * 「变更与停用」面板据此重新拉取指标卡、列表与版本历史。
   */
  changeVersion: Ref<number>;
  /** 标记变更 / 停用数据已变更 */
  markChangeChanged: () => void;
  /** 元数据字段缓存（Master Data Extension 演示核心） */
  metadataFields: Ref<MetadataFieldVO[]>;
  loadMetadataFields: () => Promise<void>;
  /** 新增 / 更新元数据字段（本地缓存 + 提示） */
  upsertMetadataField: (field: MetadataFieldVO) => void;
  /** 发布模型版本：Draft 字段全部转为 Published */
  publishMetadata: (version?: string) => Promise<string>;
  /** 按业务上下文过滤已发布字段（动态表单渲染依据） */
  publishedFields: ComputedRef<MetadataFieldVO[]>;
  /**
   * OCR 回填通道：全局「查看OCR识别结果」弹窗确认后暂存识别结果，
   * 打开「新建客户申请」时自动写入表单（否则那次「写回表单」无处可写）。
   */
  ocrPrefill: Ref<OcrResultVO[] | null>;
  /** 暂存 / 清空 OCR 识别结果 */
  setOcrPrefill: (results: OcrResultVO[] | null) => void;
  /** 左侧菜单是否折叠 */
  sidebarCollapsed: Ref<boolean>;
  /** 切换左侧菜单折叠 */
  toggleSidebar: () => void;
  /** badge 刷新信号（自增计数器，审批/提交等操作后触发，PocSidebar 监听后重新获取 badge） */
  badgeVersion: Ref<number>;
  /** 触发菜单 badge 刷新 */
  refreshBadge: () => void;
}

export const CMD_POC_KEY: InjectionKey<CmdPocContext> = Symbol('cmdPoc');

/**
 * 创建工作台上下文（仅在外壳 index.vue 调用一次）
 * @param defaultRole 默认角色，由 role-*.vue 传入
 */
export function createCmdPoc(defaultRole: RoleKey): CmdPocContext {
  const roleKey = ref<RoleKey>(defaultRole);
  const role = computed(() => getRole(roleKey.value));
  const readOnly = computed(() => role.value.readOnly);
  const currentPage = ref<PageId>('dash');
  const currentSub = ref('');

  const currentMenu = computed(() => {
    const flat = role.value.menus.flatMap(menu => (menu.children?.length ? [menu, ...menu.children] : [menu]));
    return flat.find(item => item.id === currentPage.value);
  });
  /**
   * 当前页面标题：
   * 1) 优先取角色菜单文案（同一页面在不同角色下可有不同叫法，如「客户管理 / 客户主档」）；
   * 2) 无菜单项的卡片入口页（工作流定义 / One ID规则 / DQ Scorecard）回退到页面元信息标题，
   *    否则标签栏会错误地显示「工作台」；
   * 3) 最后才是「工作台」兜底。
   */
  const pageTitle = computed(() => currentMenu.value?.label ?? PAGE_META[currentPage.value]?.title ?? '工作台');

  const dialog = reactive<DialogState>({ current: '', payload: {} });
  const openDialog = (key: DialogKey, payload?: Record<string, unknown>) => {
    dialog.current = key;
    dialog.payload = payload ?? {};
  };
  const closeDialog = () => {
    dialog.current = '';
    dialog.payload = {};
  };

  const goMenu = (id: PageId, sub = '') => {
    currentPage.value = id;
    currentSub.value = sub;
  };

  const customers = ref<CustomerVO[]>([]);
  const loadCustomers = async () => {
    customers.value = (await cmdPocApi.listCustomers()).rows ?? [];
  };

  const hierarchyIndex = ref<Map<string, HierarchyIndexItem>>(new Map());
  const hierarchyVersion = ref(0);
  const markHierarchyChanged = () => {
    hierarchyVersion.value += 1;
  };

  const changeVersion = ref(0);
  const markChangeChanged = () => {
    changeVersion.value += 1;
  };

  /**
   * 拉取层级索引：
   * 1) 已归位节点 → level = A1/A2/A3，path = 完整路径；
   * 2) 待归位主数据 → level = '—'，mounted = false。
   */
  const loadHierarchyIndex = async () => {
    const [tree, unassigned] = await Promise.all([
      cmdPocApi.getHierarchy(),
      cmdPocApi.getUnassignedNodes()
    ]);
    const index = new Map<string, HierarchyIndexItem>();
    const walk = (nodes: HierarchyNodeVO[]) => {
      nodes.forEach(node => {
        if (node.oneId) index.set(node.oneId, { level: node.level, path: node.path, mounted: true, name: node.name });
        walk(node.children ?? []);
      });
    };
    walk(tree);
    unassigned.forEach(item => {
      if (item.oneId && !index.has(item.oneId)) {
        index.set(item.oneId, { level: '—', path: '', mounted: false });
      }
    });
    hierarchyIndex.value = index;
  };

  const metadataFields = ref<MetadataFieldVO[]>([]);
  const loadMetadataFields = async () => {
    metadataFields.value = await cmdPocApi.listMetadataFields();
  };

  const upsertMetadataField = (field: MetadataFieldVO) => {
    const index = metadataFields.value.findIndex(item => item.code === field.code);
    if (index >= 0) {
      metadataFields.value.splice(index, 1, field);
    } else {
      metadataFields.value.push(field);
    }
  };

  /**
   * 发布模型版本。version 缺省时自动解析「最新的 Draft」版本（新字段都在 Draft 里）；
   * 不再本地乐观翻转状态 —— 发布会退役其余版本，以重新拉取的 DB 状态为准。
   */
  const publishMetadata = async (version?: string) => {
    let target = version;
    if (!target) {
      const versions = await cmdPocApi.listModelVersions();
      target = versions.find(v => v.status === 'Draft')?.version ?? versions.find(v => v.status === 'Current')?.version;
    }
    const message = await cmdPocApi.publishModelVersion(target);
    await loadMetadataFields();
    return message;
  };

  const publishedFields = computed(() => metadataFields.value.filter(field => field.status === 'Published'));

  const ocrPrefill = ref<OcrResultVO[] | null>(null);
  const setOcrPrefill = (results: OcrResultVO[] | null) => {
    ocrPrefill.value = results;
  };

  const sidebarCollapsed = ref(false);
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  };

  const badgeVersion = ref(0);
  const refreshBadge = () => {
    badgeVersion.value += 1;
  };

  const ctx: CmdPocContext = {
    roleKey,
    role,
    readOnly,
    currentPage,
    currentSub,
    pageTitle,
    currentMenu,
    dialog,
    openDialog,
    closeDialog,
    goMenu,
    customers,
    loadCustomers,
    hierarchyIndex,
    loadHierarchyIndex,
    hierarchyVersion,
    markHierarchyChanged,
    changeVersion,
    markChangeChanged,
    metadataFields,
    loadMetadataFields,
    upsertMetadataField,
    publishMetadata,
    publishedFields,
    ocrPrefill,
    setOcrPrefill,
    sidebarCollapsed,
    toggleSidebar,
    badgeVersion,
    refreshBadge
  };

  provide(CMD_POC_KEY, ctx);
  return ctx;
}

/** 子组件获取上下文 */
export function useCmdPoc(): CmdPocContext {
  const ctx = inject(CMD_POC_KEY);
  if (!ctx) {
    throw new Error('[cmd-poc] useCmdPoc() 必须在 CmdPoc 外壳组件内部调用');
  }
  return ctx;
}

/** 当前弹窗元信息（供 DialogHost 使用） */
export function useDialogMeta(key: Ref<DialogKey | ''>) {
  return computed(() => (key.value ? DIALOG_MAP[key.value] : undefined));
}
