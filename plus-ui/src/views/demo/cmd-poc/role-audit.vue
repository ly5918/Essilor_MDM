<template>
  <cmd-poc default-role="audit" />
</template>

<script setup lang="ts">
// Auditor 工作台（Read Only · Authorized）
import CmdPoc from './index.vue';

defineOptions({ name: 'CmdPocRoleAudit' });
</script>
