<template>
  <section class="page">
    <!-- 流程实例 -->
    <div class="flow-compare">
      <el-card v-for="flow in flows" :key="flow.key" class="flow-card page-card" shadow="never" :body-style="{ padding: '16px 18px' }">
        <template #header><span class="card-title">{{ flow.title }}</span></template>
        <el-steps :active="activeStep(flow)" align-center finish-status="success" class="flow-steps">
          <el-step v-for="step in flow.steps" :key="step" :title="step" />
        </el-steps>
        <p class="flow-remark">{{ flow.remark }}</p>
        <el-button type="primary" plain size="small" @click="openDialog(flow.key === 'approvalHE' ? 'approvalHE' : 'approvalMS')">
          查看实例
        </el-button>
      </el-card>
    </div>

    <!-- 流程实例列表 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">流程实例列表</span></template>
      <el-table v-loading="loading" border :data="instances" class="data-table">
        <el-table-column label="Instance" prop="instanceId" width="140" />
        <el-table-column label="BU" prop="bu" width="120" align="center" />
        <el-table-column label="场景" prop="scenario" min-width="200" />
        <el-table-column label="当前节点" prop="currentNode" min-width="180" />
        <el-table-column label="SLA" prop="sla" width="100" align="center" />
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'In Progress' ? 'primary' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getApprovalFlow, listApprovalInstances } from '@/api/demo/cmdPoc';
import type { ApprovalFlowVO, ApprovalInstanceVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocApprovalPanel' });

const { openDialog } = useCmdPoc();

const loading = ref(false);
const flows = ref<ApprovalFlowVO[]>([]);
const instances = ref<ApprovalInstanceVO[]>([]);

/** 当前进行中节点序号 */
const activeStep = (flow: ApprovalFlowVO) => {
  const index = flow.nodes.findIndex(node => node.status === 'Current');
  return index >= 0 ? index : flow.nodes.length;
};

onMounted(async () => {
  loading.value = true;
  try {
    const [he, ms] = await Promise.all([getApprovalFlow('approvalHE'), getApprovalFlow('approvalMS')]);
    flows.value = [he, ms];
    instances.value = await listApprovalInstances();
  } finally {
    loading.value = false;
  }
});
</script>
