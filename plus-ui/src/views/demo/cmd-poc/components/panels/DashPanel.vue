<template>
  <section class="page dash-page">
    <!-- 工作台概览 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header><span class="card-title">工作台概览</span></template>
      <div class="stat-grid">
        <el-card v-for="item in stats" :key="item.key" class="stat-card" shadow="never" :body-style="{ padding: '18px 20px' }">
          <div class="stat-top"></div>
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-foot">Demo data</div>
        </el-card>
      </div>
    </el-card>

    <div class="dash-bottom">
      <!-- 快捷入口：当前角色除工作台外的全部菜单 -->
      <el-card class="quick-panel page-card" shadow="never" :body-style="{ padding: '20px' }">
        <template #header><span class="card-title">快捷入口</span></template>
        <div class="quick-grid">
          <el-card
            v-for="menu in quickMenus"
            :key="menu.id"
            class="quick-card"
            shadow="hover"
            :body-style="{ padding: '18px' }"
            @click="goMenu(menu.id)"
          >
            <div class="q-ico" :style="{ background: role.color }">{{ menu.icon }}</div>
            <div class="q-title">{{ menu.label }}</div>
            <div class="q-desc">进入{{ menu.label }}并继续下钻</div>
          </el-card>
        </div>
      </el-card>

      <!-- 待办事项 -->
      <el-card class="todo-panel page-card" shadow="never" :body-style="{ padding: '20px' }">
        <template #header><span class="card-title">待办事项</span></template>
        <div class="task">
          <span class="n" :style="{ background: role.color }">{{ todo.count }}</span>
          <div class="task-body">
            <b>{{ todo.label }}</b>
            <small>{{ todo.hint }}</small>
          </div>
          <el-tag :type="todo.tag === '待处理' ? 'warning' : 'info'" size="small">{{ todo.tag }}</el-tag>
        </div>
      </el-card>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getDashboardStats, getTodo } from '@/api/demo/cmdPoc';
import type { DashboardStatVO, TodoVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocDashPanel' });

const { role, goMenu } = useCmdPoc();

const stats = ref<DashboardStatVO[]>([]);
const todo = ref<TodoVO>({ count: 0, label: '待处理任务', hint: '点击菜单进入详情', tag: '待处理' });

/** 快捷入口：排除工作台本身 */
const quickMenus = computed(() => role.value.menus.filter(menu => menu.id !== 'dash'));

onMounted(async () => {
  [stats.value, todo.value] = await Promise.all([getDashboardStats(), getTodo()]);
});
</script>
