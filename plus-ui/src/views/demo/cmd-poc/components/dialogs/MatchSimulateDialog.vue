<template>
  <div class="poc-dialog-body">
    <el-alert type="info" :closable="false" show-icon title="匹配规则模拟测试：查看、新增、删除规则；点击模拟执行测试。" />
    <el-table border :data="rules" class="data-table m-t-12" max-height="360">
      <el-table-column label="维度" prop="dimension" min-width="180" />
      <el-table-column label="作用" prop="role" width="120" align="center" />
      <el-table-column label="阈值" prop="threshold" width="100" align="center" />
      <el-table-column label="结果" prop="result" width="100" align="center">
        <template #default="{ row }"><el-tag :type="row.result === 'Exact' ? 'success' : 'warning'" size="small">{{ row.result }}</el-tag></template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="onSimulate(asRule(row))">模拟</el-button>
          <el-button link type="danger" @click="onDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button class="m-t-12" type="primary" plain size="small" @click="onAddRule">新增规则</el-button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listMatchRules, saveMatchRule, deleteMatchRule } from '@/api/demo/cmdPoc';
import type { MatchRuleVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocMatchSimulateDialog' });

const rules = ref<MatchRuleVO[]>([]);

/** el-table 插槽行类型为 DefaultRow，此处收敛断言，保证模板调用无需类型体操 */
const asRule = (row: unknown): MatchRuleVO => row as MatchRuleVO;

onMounted(async () => {
  rules.value = await listMatchRules();
});

const onSimulate = async (row: MatchRuleVO) => {
  await saveMatchRule(row);
  ElMessage.success(`模拟完成：${row.dimension} → ${row.result}`);
};

const onDelete = async (id: number | undefined) => {
  if (!id) return;
  await deleteMatchRule(id);
  rules.value = rules.value.filter(r => true);
  ElMessage.success('规则已删除');
};

const onAddRule = () => {
  const newRule: MatchRuleVO = {
    dimension: '新维度',
    role: '辅助线索',
    result: 'Similar',
    threshold: '80%',
    enabled: true
  };
  rules.value.unshift(newRule);
  ElMessage.info('请在表格中编辑维度后点击模拟保存');
};
</script>