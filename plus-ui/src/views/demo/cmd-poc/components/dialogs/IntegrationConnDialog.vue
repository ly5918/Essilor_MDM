<template>
  <div class="poc-dialog-body">
    <el-alert v-if="isEdit" type="info" :closable="false" show-icon class="m-b-12">
      <template #title>
        <b>编辑端点：</b>{{ form.name || form.system }}（{{ form.code || '—' }}）
      </template>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="目标系统" prop="system">
            <el-input v-model="form.system" placeholder="如 SAP / CRM / EC / DW / Mock" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="端点名称" prop="name">
            <el-input v-model="form.name" placeholder="如 SAP主数据同步" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="方向" prop="direction">
            <el-select v-model="form.direction" style="width: 100%">
              <el-option label="出站 Outbound" value="OUTBOUND" />
              <el-option label="入站 Inbound" value="INBOUND" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="协议" prop="protocol">
            <el-select v-model="form.protocol" style="width: 100%">
              <el-option v-for="p in PROTOCOL_OPTIONS" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="端点地址" prop="url">
        <el-input v-model="form.url" placeholder="如 http://host:port/path 或 mock://local/deliver" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="认证方式" prop="authType">
            <el-select v-model="form.authType" style="width: 100%">
              <el-option v-for="a in AUTH_OPTIONS" :key="a" :label="a" :value="a" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="报文格式" prop="messageFormat">
            <el-select v-model="form.messageFormat" style="width: 100%">
              <el-option v-for="m in MESSAGE_FORMAT_OPTIONS" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="业务类型">
            <el-input v-model="form.bizType" placeholder="如 CUSTOMER / PRODUCT / ORDER" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="同步周期">
            <el-input v-model="form.period" placeholder="如 实时 / 每小时 / 每日 02:00" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="最大重试次数" prop="maxRetry">
            <el-input-number v-model="form.maxRetry" :min="0" :max="10" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="超时(毫秒)" prop="timeoutMs">
            <el-input-number v-model="form.timeoutMs" :min="1000" :max="300000" :step="1000" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="0">启用</el-radio>
          <el-radio value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { saveIntegrationEndpoint } from '@/api/demo/cmdPoc';
import type { IntegrationConnForm, IntegrationEndpointVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocIntegrationConnDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const formRef = ref<FormInstance>();

const PROTOCOL_OPTIONS = ['HTTP', 'HTTPS', 'SFTP', 'KAFKA', 'JDBC', 'FILE'];
const AUTH_OPTIONS = ['NONE', 'BASIC', 'TOKEN', 'OAUTH2', 'CERT'];
const MESSAGE_FORMAT_OPTIONS = ['JSON', 'XML', 'CSV'];

const editRow = computed(() => (props.payload?.row as IntegrationEndpointVO | undefined) ?? undefined);
const isEdit = computed(() => !!editRow.value);

const form = reactive<IntegrationConnForm & { code?: string }>({
  id: undefined,
  code: '',
  system: '',
  name: '',
  protocol: 'HTTP',
  direction: 'OUTBOUND',
  period: '',
  url: '',
  authType: 'NONE',
  bizType: 'CUSTOMER',
  messageFormat: 'JSON',
  maxRetry: 3,
  timeoutMs: 30000,
  status: '0'
});

const rules: FormRules = {
  system: [{ required: true, message: '请输入目标系统', trigger: 'blur' }],
  name: [{ required: true, message: '请输入端点名称', trigger: 'blur' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }],
  protocol: [{ required: true, message: '请选择协议', trigger: 'change' }],
  url: [{ required: true, message: '请输入端点地址', trigger: 'blur' }],
  authType: [{ required: true, message: '请选择认证方式', trigger: 'change' }],
  messageFormat: [{ required: true, message: '请选择报文格式', trigger: 'change' }],
  maxRetry: [{ required: true, message: '请输入最大重试次数', trigger: 'blur' }],
  timeoutMs: [{ required: true, message: '请输入超时时间', trigger: 'blur' }]
};

const fillForm = (row: IntegrationEndpointVO) => {
  form.id = row.id;
  form.code = row.code;
  form.system = row.system;
  form.name = row.name;
  form.protocol = row.protocol;
  form.direction = row.direction === 'Inbound' ? 'INBOUND' : 'OUTBOUND';
  form.period = row.period;
  form.url = row.url;
  form.authType = row.authType;
  form.bizType = row.bizType;
  form.messageFormat = row.messageFormat;
  form.maxRetry = row.maxRetry;
  form.timeoutMs = row.timeoutMs;
  form.status = row.status === 'Active' ? '0' : '1';
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  const { id, system, name, protocol, direction, period, url, authType, bizType, messageFormat, maxRetry, timeoutMs, status } = form;
  const payload: IntegrationConnForm = {
    id,
    system,
    name,
    protocol,
    direction,
    period,
    url,
    authType,
    bizType,
    messageFormat,
    maxRetry,
    timeoutMs,
    status
  };
  return saveIntegrationEndpoint(payload);
};

onMounted(() => {
  if (editRow.value) fillForm(editRow.value);
});

defineExpose({ submit });
</script>