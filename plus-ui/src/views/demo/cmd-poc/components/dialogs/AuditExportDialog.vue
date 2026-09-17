<template>
  <div class="poc-dialog-body">
    <el-form :model="form" label-width="110px">
      <el-form-item label="时间范围">
        <el-select v-model="form.range" style="width: 100%">
          <el-option v-for="item in AUDIT_RANGE_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="事件类型">
        <el-select v-model="form.eventType" style="width: 100%">
          <el-option v-for="item in AUDIT_EVENT_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="格式">
        <el-select v-model="form.format" style="width: 100%">
          <el-option v-for="item in AUDIT_FORMAT_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="脱敏">
        <el-select v-model="form.masking" style="width: 100%">
          <el-option v-for="item in AUDIT_MASKING_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-alert type="warning" :closable="false" show-icon title="导出动作本身也写入审计日志。" />
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue';
import { exportAudit } from '@/api/demo/cmdPoc';
import type { AuditExportForm } from '@/api/demo/cmdPoc/types';
import { AUDIT_EVENT_OPTIONS, AUDIT_FORMAT_OPTIONS, AUDIT_MASKING_OPTIONS, AUDIT_RANGE_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocAuditExportDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const form = reactive<AuditExportForm>({
  range: AUDIT_RANGE_OPTIONS[1],
  eventType: AUDIT_EVENT_OPTIONS[0],
  format: 'Excel',
  masking: AUDIT_MASKING_OPTIONS[0]
});

const submit = async (): Promise<string> => exportAudit(form);

defineExpose({ submit });
</script>
