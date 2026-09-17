<template>
  <div class="poc-dialog-body">
    <el-table border :data="flow.nodes" class="data-table">
      <el-table-column label="节点" prop="node" width="80" align="center" />
      <el-table-column label="角色" prop="role" min-width="180" />
      <el-table-column label="处理内容" prop="content" min-width="220" />
      <el-table-column label="状态" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="APPROVAL_NODE_STATUS_MAP[row.status].type" size="small">
            {{ APPROVAL_NODE_STATUS_MAP[row.status].label }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-alert class="m-t-12" type="info" :closable="false" show-icon :title="flow.remark" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getApprovalFlow } from '@/api/demo/cmdPoc';
import type { ApprovalFlowVO } from '@/api/demo/cmdPoc/types';
import { APPROVAL_NODE_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocApprovalFlowContent' });

const props = defineProps<{ flowKey: string }>();

const flow = ref<ApprovalFlowVO>({ key: props.flowKey, title: '', steps: [], remark: '', nodes: [] });

onMounted(async () => {
  flow.value = await getApprovalFlow(props.flowKey);
});
</script>
