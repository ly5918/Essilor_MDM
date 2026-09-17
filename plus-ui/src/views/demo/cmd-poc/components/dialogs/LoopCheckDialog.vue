<template>
  <div class="poc-dialog-body">
    <!-- 原型 Loop Check：仅提示环路检测结果，系统阻止提交并保留冲突路径 -->
    <el-alert type="error" :closable="false" show-icon :title="message" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { loopCheck } from '@/api/demo/cmdPoc';

defineOptions({ name: 'CmdPocLoopCheckDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const message = ref('正在执行 Loop Check…');

onMounted(async () => {
  message.value = await loopCheck();
});
</script>
