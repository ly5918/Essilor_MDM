<template>
  <ApprovalFlowContent flow-key="approvalMS" />
</template>

<script setup lang="ts">
import ApprovalFlowContent from './ApprovalFlowContent.vue';

defineOptions({ name: 'CmdPocApprovalMsDialog' });
defineProps<{ payload?: Record<string, unknown> }>();
</script>
