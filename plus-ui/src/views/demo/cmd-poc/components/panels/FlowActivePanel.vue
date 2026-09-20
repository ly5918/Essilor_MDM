<template>
  <WorkflowViewTable view="active" />
</template>

<script setup lang="ts">
import WorkflowViewTable from './WorkflowViewTable.vue';

defineOptions({ name: 'CmdPocFlowActivePanel' });
</script>
