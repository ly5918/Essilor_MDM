<template>
  <div class="poc-dialog-body">
    <el-tabs v-model="activeTab">
      <!-- 流程设计 -->
      <el-tab-pane label="流程设计" name="design">
        <el-steps :active="3" align-center finish-status="success" class="flow-steps">
          <el-step v-for="step in config.steps" :key="step" :title="step" />
        </el-steps>
      </el-tab-pane>

      <!-- 路由条件 -->
      <el-tab-pane label="路由条件" name="route">
        <el-form :model="config" label-width="120px">
          <el-form-item label="路由条件">
            <el-select v-model="config.routeCondition" style="width: 100%">
              <el-option v-for="item in WORKFLOW_ROUTE_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- SLA -->
      <el-tab-pane label="SLA" name="sla">
        <el-form :model="config" label-width="120px">
          <el-form-item label="SLA">
            <el-select v-model="config.sla" style="width: 100%">
              <el-option v-for="item in WORKFLOW_SLA_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="超时动作">
            <el-select v-model="config.timeoutAction" style="width: 100%">
              <el-option v-for="item in WORKFLOW_TIMEOUT_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="通知">
            <el-select v-model="config.notification" style="width: 100%">
              <el-option v-for="item in WORKFLOW_NOTIFY_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getWorkflow, saveWorkflow } from '@/api/demo/cmdPoc';
import type { WorkflowConfigVO } from '@/api/demo/cmdPoc/types';
import {
  WORKFLOW_NOTIFY_OPTIONS,
  WORKFLOW_ROUTE_OPTIONS,
  WORKFLOW_SLA_OPTIONS,
  WORKFLOW_TIMEOUT_OPTIONS
} from '../../constants/options';

defineOptions({ name: 'CmdPocWorkflowDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const activeTab = ref('design');
const config = ref<WorkflowConfigVO>({
  flowName: '',
  steps: [],
  routeCondition: WORKFLOW_ROUTE_OPTIONS[0],
  sla: WORKFLOW_SLA_OPTIONS[0],
  timeoutAction: WORKFLOW_TIMEOUT_OPTIONS[0],
  notification: WORKFLOW_NOTIFY_OPTIONS[0]
});

const submit = async (): Promise<string> => saveWorkflow(config.value);

onMounted(async () => {
  config.value = await getWorkflow();
});

defineExpose({ submit });
</script>
