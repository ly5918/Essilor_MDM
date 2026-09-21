<template>
  <FlowTraceDetail :task-no="taskNo" :detail-type="detailType" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import FlowTraceDetail from '../FlowTraceDetail.vue';

defineOptions({ name: 'CmdPocFlowTraceDialog' });

// 只读查看器：由「工作流 › 已完成的工作流」行内「查看流程跟踪」打开（形态同「工作流定义 → 某行配置」），
// 与审批详情右侧的「流程跟踪」按钮共用 FlowTraceDetail，展示内容完全一致。
const props = defineProps<{ payload?: Record<string, unknown> }>();

const taskNo = computed(() => String(props.payload?.taskNo ?? ''));
const detailType = computed(() => String(props.payload?.detailType ?? 'create'));
</script>
