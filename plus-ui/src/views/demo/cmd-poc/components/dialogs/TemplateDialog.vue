<template>
  <div class="poc-dialog-body">
    <!-- 下载模板（设计节点②「上传 Excel / CSV」的前置：按模板表头填写数据）。
         说明：字段映射 / 模板规则维护属于总设计「模板与规则配置」虚线旁路（Platform Admin 配置），
         不在 Business User 导入主流程展示，故此处仅保留模板列表与下载。 -->
    <el-table v-loading="loading" border :data="templates" class="data-table">
      <el-table-column label="模板" prop="name" min-width="180" />
      <el-table-column label="业务上下文" min-width="170">
        <template #default="{ row }">
          <span>{{ contextText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="版本" prop="version" width="90" align="center" />
      <el-table-column label="字段数" prop="fieldCount" width="90" align="center" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'Published' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button link type="primary" :loading="downloading === row.templateCode" @click="onDownload(row)">
            下载
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="empty-hint">暂无可用模板</span>
      </template>
    </el-table>

    <el-alert
      class="m-t-12"
      type="info"
      :closable="false"
      show-icon
      title="建议下载「Published」状态的模板，按表头填写数据后，回到「新建导入任务」上传。"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { downloadImportTemplate, listImportTemplates } from '@/api/demo/cmdPoc';
import type { ImportTemplateVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocTemplateDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const loading = ref(false);
const downloading = ref('');
const templates = ref<ImportTemplateVO[]>([]);

/**
 * 业务上下文展示：Customer Type · BU · Product Line · Source System
 * 说明：el-table 插槽的 row 类型为 DefaultRow，这里按模板结构取字段。
 */
const contextText = (row: ImportTemplateVO | Record<string, unknown>) => {
  const item = row as ImportTemplateVO;
  return [item.customerType, item.bu, item.productLine, item.sourceSystem].filter(Boolean).join(' · ') || item.context;
};

/** 下载模板：后端按字段映射动态生成仅含表头的 Excel */
const onDownload = async (row: ImportTemplateVO | Record<string, unknown>) => {
  const item = row as ImportTemplateVO;
  downloading.value = item.templateCode;
  try {
    await downloadImportTemplate(item.templateCode, `${item.name}_${item.version}.xlsx`);
    ElMessage.success(`模板已下载：${item.name}_${item.version}.xlsx`);
  } finally {
    downloading.value = '';
  }
};

const submit = async (): Promise<string> => `共 ${templates.value.length} 个导入模板可用`;

onMounted(async () => {
  loading.value = true;
  try {
    templates.value = await listImportTemplates();
  } finally {
    loading.value = false;
  }
});

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.empty-hint {
  font-size: 12px;
  color: var(--g-text2);
}
</style>
