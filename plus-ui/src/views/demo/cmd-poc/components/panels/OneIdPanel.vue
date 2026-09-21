<template>
  <section class="page">
    <!-- 操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <div class="card-head">
          <span class="card-title">操作区</span>
          <div class="card-toolbar-right">
            <el-button v-if="!readOnly" plain icon="CopyDocument" @click="onCopy">复制为新版本</el-button>
            <el-button v-if="!readOnly" type="primary" plain icon="Promotion" @click="onPublish">发布规则</el-button>
          </div>
        </div>
      </template>
      <p class="text-tip">配置 One ID 编码模式、生成策略与 Legacy Code 映射。</p>
    </el-card>

    <div class="two-col">
      <!-- 编码模式配置 -->
      <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 18px' }">
        <template #header><span class="card-title">编码模式配置</span></template>
        <el-form :model="rule" label-width="96px" :disabled="readOnly">
          <el-form-item label="规则名称"><el-input v-model="rule.ruleName" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="rule.status" style="width: 100%">
              <el-option label="Published" value="Published" />
              <el-option label="Draft" value="Draft" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象">
            <el-select v-model="rule.object" style="width: 100%">
              <el-option v-for="item in OBJECT_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="流水长度">
            <el-select v-model="rule.serialLength" style="width: 100%">
              <el-option v-for="item in SERIAL_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="前缀"><el-input v-model="rule.prefix" /></el-form-item>
          <el-form-item label="分隔符"><el-input v-model="rule.separator" /></el-form-item>
        </el-form>

        <!-- 编码预览 -->
        <div class="pattern-preview">
          <span class="pattern-part">{{ rule.prefix }}</span>
          <b>{{ rule.separator }}</b>
          <span class="pattern-part">{{ sampleSerial }}</span>
          <b>→</b>
          <strong>{{ preview }}</strong>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="规则页面为POC配置示例；最终编码格式仍需客户确认。系统原则：生成后终身稳定，字段变更不重新生成。"
        />
      </el-card>

      <!-- 生成与状态策略 -->
      <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 18px' }">
        <template #header><span class="card-title">生成与状态策略</span></template>
        <el-table border :data="policies" class="data-table" max-height="320">
          <el-table-column label="事件" prop="event" min-width="180" />
          <el-table-column label="处理" prop="handling" min-width="180" />
        </el-table>
        <el-button class="m-t-12" plain icon="Clock" @click="openDialog('oneIdHistory', { oneId: 'GC-000128' })">
          查看One ID历史
        </el-button>
      </el-card>
    </div>

    <!-- Legacy Code 交叉引用 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">Legacy Code ↔ One ID交叉引用</span></template>
      <el-table border :data="mappings" class="data-table">
        <el-table-column label="One ID" prop="oneId" width="140" />
        <el-table-column label="Source System" prop="sourceSystem" width="150" align="center" />
        <el-table-column label="Legacy Code" prop="legacyCode" min-width="160" />
        <el-table-column label="BU" prop="bu" width="140" align="center" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="customerStatusMeta(row.status).type" size="small">{{ customerStatusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { copyOneIdRule, getOneIdRule, listLegacyMappings, listOneIdPolicies, publishOneIdRule } from '@/api/demo/cmdPoc';
import type { LegacyMappingVO, OneIdPolicyVO, OneIdRuleVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { customerStatusMeta } from '../../constants/options';

defineOptions({ name: 'CmdPocOneIdPanel' });

const { readOnly, openDialog } = useCmdPoc();

const OBJECT_OPTIONS = ['Customer / A1', 'Payer', 'A2', 'A3'];
const SERIAL_OPTIONS = ['6 digits', '8 digits'];

const rule = ref<OneIdRuleVO>({ ruleName: '', status: 'Published', object: 'Customer / A1', serialLength: '6 digits', prefix: 'GC', separator: '-' });
const policies = ref<OneIdPolicyVO[]>([]);
const mappings = ref<LegacyMappingVO[]>([]);

const serialLength = computed(() => parseInt(rule.value.serialLength, 10) || 6);
const sampleSerial = computed(() => '128'.padStart(serialLength.value, '0'));
const preview = computed(() => `${rule.value.prefix}${rule.value.separator}${sampleSerial.value}`);

const onCopy = async () => ElMessage.success(await copyOneIdRule());
const onPublish = async () => ElMessage.success(await publishOneIdRule());

onMounted(async () => {
  rule.value = await getOneIdRule();
  [policies.value, mappings.value] = await Promise.all([listOneIdPolicies(), listLegacyMappings()]);
});
</script>

<style lang="scss" scoped>
.text-tip {
  margin: 0;
  font-size: 13px;
  color: var(--g-text2);
}
</style>
