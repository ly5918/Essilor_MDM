<template>
  <ApprovalFlowContent flow-key="approvalHE" />
</template>

<script setup lang="ts">
import ApprovalFlowContent from './ApprovalFlowContent.vue';

defineOptions({ name: 'CmdPocApprovalHeDialog' });
defineProps<{ payload?: Record<string, unknown> }>();
</script>
