<template>
  <div class="cmd-poc" :style="{ '--role-color': role.color }">
    <!-- 顶部导航 -->
    <PocNavbar />

    <div class="cmd-body" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <!-- 左侧角色菜单 -->
      <PocSidebar v-show="!sidebarCollapsed" />

      <!-- 主内容区 -->
      <main class="cmd-main">
        <el-breadcrumb class="crumb" separator="/">
          <el-breadcrumb-item>CMD POC</el-breadcrumb-item>
          <el-breadcrumb-item v-if="currentSub">{{ currentSubLabel }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ pageHeadTitle }}</el-breadcrumb-item>
        </el-breadcrumb>

        <!-- 标签导航：位于内容区标题区域内 -->
        <PocTagsView @refresh="refreshPanel" />

        <div class="page-head">
          <h2>{{ pageHeadTitle }}</h2>
          <p>{{ pageSub }}</p>
        </div>

        <!-- 页面面板：按当前角色菜单动态渲染 -->
        <component :is="currentPanel" :key="`${currentPage}-${panelRefreshTick}`" />
      </main>
    </div>

    <!-- 全局弹窗宿主 -->
    <DialogHost />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue';
import type { PageId, RoleKey } from '@/api/demo/cmdPoc/types';
import { createCmdPoc } from './composables/useCmdPoc';
import { DASHBOARD_TITLES } from './constants/roles';
import { PAGE_META } from './constants/pages';
import PocNavbar from './components/PocNavbar.vue';
import PocSidebar from './components/PocSidebar.vue';
import DialogHost from './components/DialogHost.vue';
import PocTagsView from './components/PocTagsView.vue';

// 页面面板
import DashPanel from './components/panels/DashPanel.vue';
import CustomersPanel from './components/panels/CustomersPanel.vue';
import BatchPanel from './components/panels/BatchPanel.vue';
import GovernancePanel from './components/panels/GovernancePanel.vue';
import HierarchyPanel from './components/panels/HierarchyPanel.vue';
import ChangePanel from './components/panels/ChangePanel.vue';
import ApprovalPanel from './components/panels/ApprovalPanel.vue';
import OneIdPanel from './components/panels/OneIdPanel.vue';
import DqScorePanel from './components/panels/DqScorePanel.vue';
import IntegrationPanel from './components/panels/IntegrationPanel.vue';
import AdminPanel from './components/panels/AdminPanel.vue';
import AuditPanel from './components/panels/AuditPanel.vue';
import CoveragePanel from './components/panels/CoveragePanel.vue';

defineOptions({ name: 'CmdPoc' });

const props = defineProps<{ defaultRole?: RoleKey }>();

const { role, roleKey, currentPage, currentSub, currentMenu, pageTitle, sidebarCollapsed, loadCustomers, loadMetadataFields } = createCmdPoc(
  (props.defaultRole ?? 'business') as RoleKey
);

/** 页面面板注册表 */
const PANEL_MAP: Record<PageId, Component> = {
  dash: DashPanel,
  customers: CustomersPanel,
  batch: BatchPanel,
  gov: GovernancePanel,
  hier: HierarchyPanel,
  change: ChangePanel,
  approval: ApprovalPanel,
  oneid: OneIdPanel,
  dqscore: DqScorePanel,
  integration: IntegrationPanel,
  admin: AdminPanel,
  audit: AuditPanel,
  coverage: CoveragePanel
};

const currentPanel = computed(() => PANEL_MAP[currentPage.value] ?? DashPanel);

/** 标签栏刷新当前面板计数 */
const panelRefreshTick = ref(0);
const refreshPanel = () => {
  panelRefreshTick.value += 1;
};

/** 工作台标题按角色区分，其余页面取固定标题或菜单名（One ID / DQ Scorecard 无菜单项，取固定标题） */
const pageHeadTitle = computed(() => {
  if (currentPage.value === 'dash') return DASHBOARD_TITLES[roleKey.value];
  return PAGE_META[currentPage.value]?.title || pageTitle.value;
});

const pageSub = computed(() => PAGE_META[currentPage.value]?.sub ?? '');

/** 下钻场景的面包屑上级名称 */
const currentSubLabel = computed(() => {
  if (currentSub.value === 'customers') return currentMenu.value?.label ?? '客户管理';
  return '';
});

onMounted(() => {
  loadCustomers();
  loadMetadataFields();
});
</script>

<style lang="scss">
@use './styles/cmd-poc.scss';
</style>
