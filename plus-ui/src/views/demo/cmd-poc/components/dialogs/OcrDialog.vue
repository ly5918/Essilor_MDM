<template>
  <div class="poc-dialog-body">
    <!-- 上传区：选择营业执照后本地预览并自动识别 -->
    <div class="ocr-upload">
      <el-upload :auto-upload="false" :limit="1" :show-file-list="false" accept="image/*" :on-change="onFileChange">
        <el-button plain icon="Upload">选择营业执照</el-button>
      </el-upload>
      <span class="upload-name">{{ fileName || '未选择文件（支持 JPG / PNG，建议分辨率 ≥ 1280px）' }}</span>
      <el-button type="primary" plain icon="View" :loading="recognizing" @click="onRecognize">开始识别</el-button>
    </div>

    <!-- 营业执照预览 + 执照原件信息（原型 showOCR：左侧预览，右侧 4 行信息） -->
    <div class="ocr-preview">
      <div class="ocr-image">
        <el-image v-if="previewUrl" :src="previewUrl" fit="contain" class="ocr-img" :preview-src-list="[previewUrl]">
          <template #error>
            <div class="ocr-img-error">预览失败</div>
          </template>
        </el-image>
        <div v-else class="ocr-img-empty">营业执照预览</div>
        <div class="ocr-image-tip">营业执照预览</div>
      </div>

      <div v-loading="recognizing" class="ocr-license">
        <div class="ocr-license-row">
          <span>统一社会信用代码</span>
          <b>{{ license?.creditCode || '—' }}</b>
        </div>
        <div class="ocr-license-row">
          <span>名称</span>
          <b>{{ license?.name || '—' }}</b>
        </div>
        <div class="ocr-license-row">
          <span>类型</span>
          <b>{{ license?.type || '—' }}</b>
        </div>
        <div class="ocr-license-row">
          <span>住所</span>
          <b>{{ license?.address || '—' }}</b>
        </div>
      </div>
    </div>

    <!-- 识别结果：字段 / 识别值 / 置信度 -->
    <el-table v-loading="recognizing" border :data="fields" class="data-table">
      <el-table-column label="字段" prop="field" min-width="130" />
      <el-table-column label="识别值" prop="value" min-width="230" show-overflow-tooltip />
      <el-table-column label="置信度" prop="confidence" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="confidenceType(row.confidence)" size="small">{{ row.confidence }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="ocr-tip">低置信度字段需要人工确认后才能写入新建客户表单。</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage, type UploadFile } from 'element-plus';
import { ocrRecognize } from '@/api/demo/cmdPoc';
import type { OcrLicenseVO, OcrRecognizeVO, OcrResultVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocOcrDialog' });

const emit = defineEmits<{ apply: [results: OcrResultVO[]] }>();

const fields = ref<OcrResultVO[]>([]);
const license = ref<OcrLicenseVO | null>(null);
const fileName = ref('');
const previewUrl = ref('');
const recognizing = ref(false);

const confidenceType = (confidence: string) => {
  const value = parseInt(confidence, 10);
  if (value >= 98) return 'success';
  return value >= 90 ? 'warning' : 'danger';
};

/** 选择文件：本地预览 + 自动识别（原型为打开即展示识别结果） */
const onFileChange = async (file: UploadFile) => {
  fileName.value = file.name ?? '';
  if (file.raw) {
    previewUrl.value = URL.createObjectURL(file.raw);
  }
  ElMessage.success(`已选择文件：${fileName.value}`);
  await onRecognize();
};

const onRecognize = async () => {
  recognizing.value = true;
  try {
    const data: OcrRecognizeVO = await ocrRecognize(fileName.value);
    license.value = data.license ?? null;
    fields.value = data.fields ?? [];
    ElMessage.success('OCR 识别完成');
  } finally {
    recognizing.value = false;
  }
};

const submit = async (): Promise<string> => {
  if (!fields.value.length) await onRecognize();
  emit('apply', fields.value);
  return 'OCR 结果已确认并写入客户表单';
};

defineExpose({ submit });
</script>

<style scoped lang="scss">
.ocr-upload {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;

  .upload-name {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.ocr-preview {
  display: flex;
  gap: 14px;
  margin: 12px 0;
}

.ocr-image {
  width: 300px;
  flex-shrink: 0;
}

.ocr-img {
  width: 300px;
  height: 200px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.ocr-img-empty,
.ocr-img-error {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 300px;
  height: 200px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  background: var(--el-fill-color-lighter);
}

.ocr-image-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.ocr-license {
  flex: 1;
  min-height: 200px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.ocr-license-row {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--el-border-color-lighter);

  &:last-child {
    border-bottom: none;
  }

  span {
    width: 130px;
    flex-shrink: 0;
    color: var(--el-text-color-secondary);
  }

  b {
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}

.ocr-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
