<template>
  <div class="poc-dialog-body">
    <div class="ocr-upload">
      <el-upload :auto-upload="false" :limit="1" :show-file-list="false" :on-change="onFileChange">
        <el-button plain icon="Upload">选择营业执照</el-button>
      </el-upload>
      <span class="upload-name">{{ fileName || '未选择文件（支持 JPG / PNG，建议分辨率 ≥ 1280px）' }}</span>
      <el-button type="primary" plain icon="View" :loading="recognizing" :disabled="!fileName" @click="onRecognize">
        开始识别
      </el-button>
    </div>

    <el-table v-loading="recognizing" border :data="results" class="data-table">
      <el-table-column label="字段" prop="field" min-width="140" />
      <el-table-column label="识别值" prop="value" min-width="220" show-overflow-tooltip />
      <el-table-column label="置信度" prop="confidence" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="confidenceType(row.confidence)" size="small">{{ row.confidence }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, type UploadFile } from 'element-plus';
import { ocrRecognize } from '@/api/demo/cmdPoc';
import type { OcrResultVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocOcrDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();
const emit = defineEmits<{ apply: [results: OcrResultVO[]] }>();

const results = ref<OcrResultVO[]>([]);
const fileName = ref('');
const recognizing = ref(false);

const confidenceType = (confidence: string) => {
  const value = parseInt(confidence, 10);
  if (value >= 98) return 'success';
  return value >= 90 ? 'warning' : 'danger';
};

const onFileChange = (file: UploadFile) => {
  fileName.value = file.name;
  ElMessage.success(`已选择文件：${file.name}`);
};

const onRecognize = async () => {
  recognizing.value = true;
  try {
    results.value = await ocrRecognize();
    ElMessage.success('OCR 识别完成');
  } finally {
    recognizing.value = false;
  }
};

const submit = async (): Promise<string> => {
  if (!results.value.length) results.value = await ocrRecognize();
  emit('apply', results.value);
  return 'OCR 结果已确认并写入客户表单';
};

/** 打开即执行一次识别，与原型「直接展示识别结果」保持一致 */
onMounted(async () => {
  recognizing.value = true;
  try {
    results.value = await ocrRecognize();
  } finally {
    recognizing.value = false;
  }
});

defineExpose({ submit });
</script>
