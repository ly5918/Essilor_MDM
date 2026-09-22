<template>
  <section class="page dash-page">
    <!-- BU / GC：治理工作台（原型 dashboard 特殊分支） -->
    <template v-if="roleKey === 'bu' || roleKey === 'gc'">
      <div class="ap-kpis" v-loading="loadingKpi">
        <div v-for="item in apKpis" :key="item.label" class="ap-kpi">
          <b>{{ item.value }}</b>
          <span>{{ item.label }}</span>
        </div>
      </div>

      <div class="grid2">
        <!-- 高优先级审批 / 高优先级治理决策 -->
        <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
          <template #header>
            <span class="card-title">{{ roleKey === 'gc' ? '高优先级治理决策' : '高优先级审批' }}</span>
          </template>
          <div class="panel-body">
            <el-table v-loading="loadingKpi" :data="priorityTasks" class="mini-table" size="small" :show-header="true">
              <el-table-column prop="taskId" label="任务" min-width="100" />
              <el-table-column prop="scene" label="场景" min-width="120" />
              <el-table-column prop="risk" label="风险" min-width="80">
                <template #default="{ row }">
                  <span class="ap-risk" :class="'risk-' + row.risk.toLowerCase()">{{ row.risk }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="sla" label="SLA" min-width="70" />
              <el-table-column label="操作" min-width="70">
                <template #default>
                  <el-button link type="primary" size="small" @click="goMenu('approval')">处理</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <!-- 治理快捷入口：跳过工作台、治理与审批 -->
        <el-card class="page-card" shadow="never" :body-style="{ padding: '20px' }">
          <template #header><span class="card-title">治理快捷入口</span></template>
          <div class="quick-grid governance-quick">
            <div
              v-for="menu in governanceQuickMenus"
              :key="menu.id"
              class="quick-card"
              @click="goQuick(menu)"
            >
              <b>{{ menu.label }}</b>
              <p>进入{{ menu.label }}并继续下钻</p>
            </div>
          </div>
        </el-card>
      </div>
    </template>

    <!-- business / admin / audit：通用工作台 -->
    <template v-else>
      <div class="stat-grid">
        <el-card v-for="item in stats" :key="item.key" class="stat-card" shadow="never" :body-style="{ padding: '18px 20px' }">
          <div class="stat-top"></div>
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-foot">{{ item.hint }}</div>
        </el-card>
      </div>

      <div class="dash-bottom">
        <el-card class="quick-panel page-card" shadow="never" :body-style="{ padding: '20px' }">
          <template #header><span class="card-title">快捷入口</span></template>
          <div class="quick-grid">
            <el-card
              v-for="menu in quickMenus"
              :key="menu.id"
              class="quick-card"
              shadow="hover"
              :body-style="{ padding: '18px' }"
              @click="goQuick(menu)"
            >
              <div class="q-ico" :style="{ background: role.color }">{{ menu.icon }}</div>
              <div class="q-title">{{ menu.label }}</div>
              <div class="q-desc">进入{{ menu.label }}并继续下钻</div>
            </el-card>
          </div>
        </el-card>

        <el-card v-if="roleKey !== 'audit'" class="todo-panel page-card" shadow="never" :body-style="{ padding: '20px' }">
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
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getDashboardStats, getTodo, getApprovalKpis, listApprovalTasks } from '@/api/demo/cmdPoc';
import type { DashboardStatVO, PageId, TodoVO, ApprovalKpiVO, ApprovalTaskVO } from '@/api/demo/cmdPoc/types';
import type { PocMenu } from '../../constants/roles';
import { useCmdPoc } from '../../composables/useCmdPoc';

/** 高优先级任务行（BU / GC 工作台表格） */
interface PriorityTask {
  taskId: string;
  scene: string;
  risk: string;
  sla: string;
}

defineOptions({ name: 'CmdPocDashPanel' });

const { role, roleKey, goMenu } = useCmdPoc();

const stats = ref<DashboardStatVO[]>([]);
const todo = ref<TodoVO>({ count: 0, label: '待处理任务', hint: '点击菜单进入详情', tag: '待处理' });

/** BU / GC 5 张 KPI 卡片 — 从后端 /cmd/approval/kpi 实时获取 */
const apKpis = ref<{ value: number; label: string }[]>([]);
const priorityTasks = ref<PriorityTask[]>([]);
const loadingKpi = ref(false);

/** BU / GC 角色的 scope 参数 */
const scope = computed<'bu' | 'gc'>(() => (roleKey.value === 'gc' ? 'gc' : 'bu'));

/** 将审批任务 VO 映射为工作台高优先级任务行 */
function toPriorityTask(t: ApprovalTaskVO): PriorityTask {
  return {
    taskId: t.taskId,
    scene: t.taskType,
    risk: t.risk,
    sla: t.sla
  };
}

/** 加载 BU / GC 治理工作台数据（KPI + 高优先级任务） */
const loadGovernanceData = async () => {
  if (roleKey.value !== 'bu' && roleKey.value !== 'gc') return;
  loadingKpi.value = true;
  try {
    const [kpis, tasks] = await Promise.all([
      getApprovalKpis(scope.value),
      listApprovalTasks(scope.value, 1, 5)
    ]);
    apKpis.value = kpis.map(k => ({ value: Number(k.value), label: k.label }));
    // 取前 5 条待办作为高优先级任务展示
    priorityTasks.value = (tasks.rows ?? []).slice(0, 5).map(toPriorityTask);
  } finally {
    loadingKpi.value = false;
  }
};

/**
 * 快捷入口点击：叶子菜单直接跳转；二级菜单容器（如「工作流」）跳到其第一个子页。
 * 容器 id 是 `sub-` 前缀伪 id，本身不可路由，因此这里必须取子页面。
 */
const goQuick = (menu: PocMenu) => {
  const target = menu.children?.length ? menu.children[0] : menu;
  goMenu(target.id as PageId);
};

/** 通用工作台快捷入口：排除工作台本身 */
const quickMenus = computed<PocMenu[]>(() => role.value.menus.filter(menu => menu.id !== 'dash'));

/** BU / GC 治理快捷入口：排除工作台、治理与审批 */
const governanceQuickMenus = computed<PocMenu[]>(() =>
  role.value.menus.filter(menu => menu.id !== 'dash' && menu.id !== 'approval')
);

/** BU / GC 切换时重新拉取治理数据 */
watch(scope, () => loadGovernanceData());

onMounted(async () => {
  [stats.value, todo.value] = await Promise.all([getDashboardStats(), getTodo()]);
  await loadGovernanceData();
});
</script>
