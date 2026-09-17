<template>
  <div class="poc-dialog-body">
    <el-form :model="form" label-width="130px">
      <el-form-item label="目标规则版本">
        <el-select v-model="form.targetVersion" style="width: 100%">
          <el-option v-for="item in RE_EVAL_VERSION_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="数据范围">
        <el-select v-model="form.dataScope" style="width: 100%">
          <el-option v-for="item in RE_EVAL_SCOPE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行模式">
        <el-select v-model="form.execMode" style="width: 100%">
          <el-option v-for="item in RE_EVAL_MODE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="异常处理">
        <el-select v-model="form.exceptionHandling" style="width: 100%">
          <el-option v-for="item in RE_EVAL_EXCEPTION_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="kpi-row">
      <div v-for="item in impacts" :key="item.label" class="kpi">
        <b>{{ item.value }}</b>
        <span>{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { getReEvaluateImpact, reEvaluateDq } from '@/api/demo/cmdPoc';
import type { ReEvaluateForm, ReEvaluateImpactVO } from '@/api/demo/cmdPoc/types';
import {
  RE_EVAL_EXCEPTION_OPTIONS,
  RE_EVAL_MODE_OPTIONS,
  RE_EVAL_SCOPE_OPTIONS,
  RE_EVAL_VERSION_OPTIONS
} from '../../constants/options';

defineOptions({ name: 'CmdPocReEvaluateDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const form = reactive<ReEvaluateForm>({
  targetVersion: RE_EVAL_VERSION_OPTIONS[0],
  dataScope: RE_EVAL_SCOPE_OPTIONS[0],
  execMode: 'Simulation Only',
  exceptionHandling: RE_EVAL_EXCEPTION_OPTIONS[0]
});

const impacts = ref<ReEvaluateImpactVO[]>([]);

const submit = async (): Promise<string> => reEvaluateDq(form);

onMounted(async () => {
  impacts.value = await getReEvaluateImpact();
});

defineExpose({ submit });
</script>
