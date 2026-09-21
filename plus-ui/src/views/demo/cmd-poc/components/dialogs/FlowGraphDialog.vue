<template>
  <div v-loading="loading" class="fg-dialog-body">
    <FlowSwimlane
      v-if="graph"
      :graph="graph"
      :scene-code="sceneCode"
      :scene-name="sceneName"
      :task-no="taskNo"
      :is-instance="isInstance"
      :steps="steps"
      :steps-loading="stepsLoading"
    />
    <el-empty v-else description="未获取到流程图形" :image-size="48" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getFlowGraphByScene, getWorkflowSteps } from '@/api/demo/cmdPoc';
import type { FlowGraphVO, WorkflowStepVO } from '@/api/demo/cmdPoc/types';
import FlowSwimlane from '../FlowSwimlane.vue';

defineOptions({ name: 'CmdPocFlowGraphDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const loading = ref(false);
const graph = ref<FlowGraphVO | null>(null);

/** 工作流步骤执行日志（按客户 One ID 串联每一步，数据库 cmd_workflow_step_log） */
const steps = ref<WorkflowStepVO[]>([]);
const stepsLoading = ref(false);

const sceneCode = computed(() => String(props.payload?.sceneCode ?? ''));
const sceneName = computed(() => String(props.payload?.sceneName ?? sceneCode.value));
/** 任务编号：传入 = 实例视图（按实际进度点亮），不传 = 定义视图（蓝图） */
const taskNo = computed(() => String(props.payload?.taskNo ?? ''));
const isInstance = computed(() => !!taskNo.value);

onMounted(async () => {
  loading.value = true;
  try {
    graph.value = await getFlowGraphByScene(sceneCode.value, taskNo.value || undefined);
    if (isInstance.value) {
      stepsLoading.value = true;
      try {
        steps.value = await getWorkflowSteps({ taskNo: taskNo.value });
      } finally {
        stepsLoading.value = false;
      }
    }
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped lang="scss">
.fg-dialog-body {
  min-height: 200px;
}
</style>
