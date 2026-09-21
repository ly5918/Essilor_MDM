<template>
  <aside class="sidebar">
    <!-- 左侧栏：角色信息 + 角色动态菜单 -->
    <div class="side-user">
      <div class="side-avatar" :style="{ background: role.color }">{{ role.alias }}</div>
      <div class="side-name">{{ role.name }}</div>
      <div class="side-scope">{{ role.scope }}</div>
    </div>

    <div class="nav-title">ROLE-BASED NAVIGATION</div>

    <el-menu
      class="side-menu"
      :default-active="currentPage"
      background-color="transparent"
      text-color="#C7D2DC"
      active-text-color="#FFFFFF"
      :default-openeds="defaultOpeneds"
      @select="onSelect"
    >
      <template v-for="menu in role.menus" :key="menu.id">
        <!-- RuoYi 二级菜单：有子菜单时渲染可展开的父菜单 -->
        <el-sub-menu v-if="menu.children?.length" :index="menu.id">
          <template #title>
            <span class="cn-ico">{{ menu.icon }}</span>
            <span class="cn-txt">{{ menu.label }}</span>
          </template>
          <el-menu-item v-for="child in menu.children" :key="child.id" :index="child.id" class="side-sub-item">
            <span class="cn-ico cn-ico-sub">{{ child.icon }}</span>
            <span class="cn-txt">{{ child.label }}</span>
            <span v-if="child.requiresApproval && badgeMap[child.id]" class="cn-badge">{{ badgeMap[child.id] }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="menu.id">
          <span class="cn-ico">{{ menu.icon }}</span>
          <span class="cn-txt">{{ menu.label }}</span>
          <span v-if="menu.requiresApproval && badgeMap[menu.id]" class="cn-badge">{{ badgeMap[menu.id] }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { PageId } from '@/api/demo/cmdPoc/types';
import { getNavBadges } from '@/api/demo/cmdPoc';
import { useCmdPoc } from '../composables/useCmdPoc';

const { role, roleKey, currentPage, goMenu, badgeVersion } = useCmdPoc();

/**
 * 菜单「数据统计」角标：**仅审批类菜单**显示待处理条数，不是每个菜单都要统计。
 *
 * 口径：只有需要人工审批 / 复核的菜单才挂角标，由 roles.ts 的 `requiresApproval` 声明
 * （当前 = BU Scope「治理与审批」「批量治理」、GC Scope「全局治理决策」「批量治理」）。
 * 数字由后端按业务表实时聚合，key = 菜单 id，口径与点进页面后的数字一致。
 *
 * 角色若没有任何审批类菜单（Business User / Platform Admin / Auditor），直接跳过请求，
 * 避免无谓查询，也保证这些角色菜单上不会冒出数字。
 */
const navBadges = ref<Record<string, number>>({});

/** 当前角色是否存在需要显示待办的审批类菜单 */
const hasApprovalMenu = computed(() => {
  const walk = (menus: typeof role.value.menus): boolean =>
    menus.some(m => m.requiresApproval || (m.children?.length ? walk(m.children) : false));
  return walk(role.value.menus);
});

const fetchBadges = async () => {
  if (!hasApprovalMenu.value) {
    navBadges.value = {};
    return;
  }
  try {
    navBadges.value = await getNavBadges(roleKey.value);
  } catch {
    /* 拉取失败时保留上一次数字，避免菜单角标莫名消失 */
  }
};

/** 菜单项 badge 映射（模板侧再叠加 requiresApproval 判断；值为 0 时不显示角标） */
const badgeMap = computed<Record<string, number>>(() => navBadges.value);

/** 切换角色时按新角色重新统计（BU / GC / Admin / Auditor 口径不同） */
watch(roleKey, () => fetchBadges());

/** 审批/提交等操作后刷新 badge（badgeVersion 自增触发） */
watch(badgeVersion, () => fetchBadges());

onMounted(fetchBadges);

/** 默认展开包含当前页面的父菜单（RuoYi 行为：进入子页面时父菜单保持展开） */
const defaultOpeneds = computed(() =>
  role.value.menus.filter(menu => menu.children?.some(child => child.id === currentPage.value)).map(menu => menu.id)
);

const onSelect = (index: string) => goMenu(index as PageId);
</script>
