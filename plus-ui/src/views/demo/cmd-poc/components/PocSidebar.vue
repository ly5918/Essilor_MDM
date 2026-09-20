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
        <el-sub-menu v-if="menu.children?.length" :index="`sub-${menu.id}`">
          <template #title>
            <span class="cn-ico">{{ menu.icon }}</span>
            <span class="cn-txt">{{ menu.label }}</span>
          </template>
          <el-menu-item v-for="child in menu.children" :key="child.id" :index="child.id" class="side-sub-item">
            <span class="cn-ico cn-ico-sub">{{ child.icon }}</span>
            <span class="cn-txt">{{ child.label }}</span>
            <span v-if="child.badge" class="cn-badge">{{ child.badge }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="menu.id">
          <span class="cn-ico">{{ menu.icon }}</span>
          <span class="cn-txt">{{ menu.label }}</span>
          <span v-if="menu.badge" class="cn-badge">{{ menu.badge }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { PageId } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../composables/useCmdPoc';

const { role, currentPage, goMenu } = useCmdPoc();

/** 默认展开包含当前页面的父菜单（RuoYi 行为：进入子页面时父菜单保持展开） */
const defaultOpeneds = computed(() =>
  role.value.menus.filter(menu => menu.children?.some(child => child.id === currentPage.value)).map(menu => `sub-${menu.id}`)
);

const onSelect = (index: string) => goMenu(index as PageId);
</script>
