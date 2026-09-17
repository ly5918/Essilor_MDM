<template>
  <div class="poc-dialog-body">
    <div class="section-title">Before / After</div>
    <el-table border :data="diffs" class="data-table">
      <el-table-column label="字段" prop="field" min-width="140" />
      <el-table-column label="Before" min-width="180">
        <template #default="{ row }"><span class="diff-old">{{ row.before }}</span></template>
      </el-table-column>
      <el-table-column label="After" min-width="180">
        <template #default="{ row }"><span class="diff-new">{{ row.after }}</span></template>
      </el-table-column>
    </el-table>

    <div class="section-title m-t-16">审批轨迹</div>
    <el-table border :data="trail" class="data-table">
      <el-table-column label="时间" prop="time" width="140" align="center" />
      <el-table-column label="角色" prop="role" width="160" align="center" />
      <el-table-column label="动作 / 意见" prop="action" min-width="200" />
      <el-table-column label="结果" prop="result" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="row.result === 'Approved' ? 'success' : 'info'" size="small">{{ row.result }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getChangeDetail } from '@/api/demo/cmdPoc';
import type { ApprovalTrailVO, ChangeDiffVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocChangeDetailDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const diffs = ref<ChangeDiffVO[]>([]);
const trail = ref<ApprovalTrailVO[]>([]);

onMounted(async () => {
  const detail = await getChangeDetail((props.payload?.requestId as string) ?? 'CHG-0018');
  diffs.value = detail.diffs;
  trail.value = detail.trail;
});
</script>
