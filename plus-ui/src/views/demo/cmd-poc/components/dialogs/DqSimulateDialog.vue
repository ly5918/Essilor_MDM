<template>
  <div class="poc-dialog-body">
    <el-form :model="form" label-width="110px">
      <el-form-item label="信用代码"><el-input v-model="form.creditCode" /></el-form-item>
      <el-form-item label="经营地址"><el-input v-model="form.address" /></el-form-item>
      <el-form-item label="规则集">
        <el-select v-model="form.ruleSet" style="width: 100%">
          <el-option v-for="item in RULE_SET_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table v-loading="running" border :data="results" class="data-table">
      <el-table-column label="规则" prop="rule" min-width="200" />
      <el-table-column label="结果" prop="result" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="DQ_RESULT_MAP[row.result].type" size="small">{{ DQ_RESULT_MAP[row.result].label }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-alert type="info" :closable="false" show-icon title="模拟测试不修改业务数据；新版本发布后，历史结果保留原规则版本。" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { simulateDq } from '@/api/demo/cmdPoc';
import type { DqSimulateResultVO } from '@/api/demo/cmdPoc/types';
import { DQ_RESULT_MAP, RULE_SET_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocDqSimulateDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const form = reactive({ creditCode: '91310000XXXXXXXXXX', address: '上海市静安区南京西路XXX号', ruleSet: RULE_SET_OPTIONS[0] });
const results = ref<DqSimulateResultVO[]>([]);
const running = ref(false);

const submit = async (): Promise<string> => {
  running.value = true;
  try {
    results.value = await simulateDq({ ...form });
    return `模拟测试完成：${results.value.length} 条规则已执行`;
  } finally {
    running.value = false;
  }
};

onMounted(() => {
  results.value = [];
});

defineExpose({ submit });
</script>
