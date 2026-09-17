<template>
  <div class="poc-dialog-body">
    <el-tabs v-model="activeTab">
      <!-- 模板列表 -->
      <el-tab-pane label="模板列表" name="list">
        <el-table border :data="templates" class="data-table">
          <el-table-column label="模板" prop="name" min-width="200" />
          <el-table-column label="业务上下文" prop="context" min-width="200" />
          <el-table-column label="版本" prop="version" width="100" align="center" />
          <el-table-column label="字段数" prop="fieldCount" width="100" align="center" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Published' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 字段映射 -->
      <el-tab-pane label="字段映射" name="mapping">
        <el-table border :data="mappings" class="data-table">
          <el-table-column label="来源列" prop="sourceColumn" min-width="160" />
          <el-table-column label="目标字段" prop="targetField" min-width="160" />
          <el-table-column label="转换规则" prop="transform" min-width="180" />
          <el-table-column label="错误策略" prop="errorStrategy" width="140" align="center" />
        </el-table>

        <div class="form-section">新增映射</div>
        <el-form :model="newMapping" label-width="100px" class="m-t-12">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="来源列"><el-input v-model="newMapping.sourceColumn" placeholder="如 CustomerName" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标字段"><el-input v-model="newMapping.targetField" placeholder="如 legal_name" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="转换规则">
                <el-select v-model="newMapping.transform" style="width: 100%">
                  <el-option v-for="item in TRANSFORM_OPTIONS" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="错误策略">
                <el-select v-model="newMapping.errorStrategy" style="width: 100%">
                  <el-option v-for="item in ERROR_STRATEGY_OPTIONS" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <el-button type="primary" plain size="small" icon="Plus" @click="onAddMapping">添加映射</el-button>
      </el-tab-pane>

      <!-- 版本历史 -->
      <el-tab-pane label="版本历史" name="version">
        <el-table border :data="templates" class="data-table">
          <el-table-column label="模板" prop="name" min-width="200" />
          <el-table-column label="版本" prop="version" width="100" align="center" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Published' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listImportTemplates, listTemplateMappings } from '@/api/demo/cmdPoc';
import type { ImportTemplateVO, TemplateMappingVO } from '@/api/demo/cmdPoc/types';
import { ERROR_STRATEGY_OPTIONS, TRANSFORM_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocTemplateDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const activeTab = ref('list');
const templates = ref<ImportTemplateVO[]>([]);
const mappings = ref<TemplateMappingVO[]>([]);

const newMapping = reactive<TemplateMappingVO>({
  sourceColumn: '',
  targetField: '',
  transform: TRANSFORM_OPTIONS[0],
  errorStrategy: ERROR_STRATEGY_OPTIONS[0]
});

const onAddMapping = () => {
  if (!newMapping.sourceColumn || !newMapping.targetField) {
    ElMessage.warning('请填写来源列与目标字段');
    return;
  }
  mappings.value.push({ ...newMapping });
  newMapping.sourceColumn = '';
  newMapping.targetField = '';
  ElMessage.success('映射已添加（Draft）');
};

const submit = async (): Promise<string> => '模板与字段映射已保存';

onMounted(async () => {
  [templates.value, mappings.value] = await Promise.all([listImportTemplates(), listTemplateMappings()]);
});

defineExpose({ submit });
</script>
