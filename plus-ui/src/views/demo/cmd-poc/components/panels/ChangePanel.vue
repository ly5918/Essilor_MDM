<template>
  <section class="page">
    <!-- 变更概览 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header><span class="card-title">变更概览</span></template>
      <div class="stat-grid">
        <el-card v-for="item in metrics" :key="item.label" class="stat-card" shadow="never" :body-style="{ padding: '16px 20px' }">
          <div class="stat-top"></div>
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-foot">{{ item.foot }}</div>
        </el-card>
      </div>
    </el-card>

    <!-- 操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <div class="card-head">
          <span class="card-title">操作区</span>
          <div class="card-toolbar-right">
            <el-button v-if="!readOnly" plain icon="Edit" @click="openDialog('changeRequest')">发起属性变更</el-button>
            <el-button v-if="!readOnly" type="warning" plain icon="CircleClose" @click="openDialog('deactivate')">申请逻辑停用</el-button>
          </div>
        </div>
      </template>
      <p class="text-tip">属性变更会触发审批流；逻辑停用后该 One ID 不再参与匹配与同步。</p>
    </el-card>

    <!-- 变更 / 停用记录 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">变更 / 停用记录</span></template>
      <el-table v-loading="loading" border :data="rows" class="data-table">
        <el-table-column label="Request" prop="requestId" width="130" />
        <el-table-column label="One ID" prop="oneId" width="130" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.changeType === 'Update' ? 'warning' : 'danger'" size="small">
              {{ row.changeType === 'Update' ? '属性变更' : '逻辑停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更内容" prop="content" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="CHANGE_STATUS_MAP[row.status].type" size="small">{{ CHANGE_STATUS_MAP[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.changeType === 'Update'" link type="primary" @click="openDialog('changeDetail', { requestId: row.requestId })">
              查看Before / After
            </el-button>
            <el-button v-else link type="primary" @click="openDialog('deactivateResult', { oneId: row.oneId })">查看数据库结果</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listChangeRequests } from '@/api/demo/cmdPoc';
import type { ChangeRequestVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { CHANGE_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocChangePanel' });

const { readOnly, openDialog } = useCmdPoc();

const loading = ref(false);
const rows = ref<ChangeRequestVO[]>([]);

/** 原型固定指标 */
const metrics = [
  { label: '待审批变更', value: 4, foot: 'Update' },
  { label: '待审批停用', value: 2, foot: 'Deactivate' },
  { label: '本月已生效', value: 11, foot: 'Approved' },
  { label: 'One ID重生成', value: 0, foot: 'Stable' }
];

onMounted(async () => {
  loading.value = true;
  try {
    rows.value = await listChangeRequests();
  } finally {
    loading.value = false;
  }
});
</script>

<style lang="scss" scoped>
.text-tip {
  margin: 0;
  font-size: 13px;
  color: var(--g-text2);
}
</style>
