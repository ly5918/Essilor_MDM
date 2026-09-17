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
      @select="onSelect"
    >
      <el-menu-item v-for="menu in role.menus" :key="menu.id" :index="menu.id">
        <span class="cn-ico">{{ menu.icon }}</span>
        <span class="cn-txt">{{ menu.label }}</span>
        <span v-if="menu.badge" class="cn-badge">{{ menu.badge }}</span>
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<script setup lang="ts">
import type { PageId } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../composables/useCmdPoc';

const { role, currentPage, goMenu } = useCmdPoc();

const onSelect = (index: string) => goMenu(index as PageId);
</script>
