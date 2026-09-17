/**
 * CMD POC 工作台共享状态
 *
 * 采用 provide / inject 单例注入，避免各面板组件之间层层透传。
 * 职责边界：
 * - 只放「跨页面共享」的状态：当前角色、当前页面、弹窗调度、主数据缓存；
 * - 页面内部的查询条件、表单值由各面板自行维护。
 */
import { computed, inject, provide, reactive, ref, type ComputedRef, type InjectionKey, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as cmdPocApi from '@/api/demo/cmdPoc';
import type { CustomerVO, MetadataFieldVO, PageId, RoleKey } from '@/api/demo/cmdPoc/types';
import { DIALOG_MAP, type DialogKey } from '../constants/dialogs';
import { getRole, type PocRole } from '../constants/roles';

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
  /** 元数据字段缓存（Master Data Extension 演示核心） */
  metadataFields: Ref<MetadataFieldVO[]>;
  loadMetadataFields: () => Promise<void>;
  /** 新增 / 更新元数据字段（本地缓存 + 提示） */
  upsertMetadataField: (field: MetadataFieldVO) => void;
  /** 发布模型版本：Draft 字段全部转为 Published */
  publishMetadata: () => Promise<void>;
  /** 按业务上下文过滤已发布字段（动态表单渲染依据） */
  publishedFields: ComputedRef<MetadataFieldVO[]>;
  /** 左侧菜单是否折叠 */
  sidebarCollapsed: Ref<boolean>;
  /** 切换左侧菜单折叠 */
  toggleSidebar: () => void;
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

  const currentMenu = computed(() => role.value.menus.find(menu => menu.id === currentPage.value));
  const pageTitle = computed(() => currentMenu.value?.label ?? '工作台');

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
    customers.value = await cmdPocApi.listCustomers();
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

  const publishMetadata = async () => {
    const message = await cmdPocApi.publishModelVersion();
    metadataFields.value = metadataFields.value.map(field => ({ ...field, status: 'Published' }));
    ElMessage.success(message);
  };

  const publishedFields = computed(() => metadataFields.value.filter(field => field.status === 'Published'));

  const sidebarCollapsed = ref(false);
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value;
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
    metadataFields,
    loadMetadataFields,
    upsertMetadataField,
    publishMetadata,
    publishedFields,
    sidebarCollapsed,
    toggleSidebar
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
