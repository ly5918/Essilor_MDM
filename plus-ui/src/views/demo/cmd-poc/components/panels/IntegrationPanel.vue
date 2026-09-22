<template>
  <section class="page">
    <el-tabs v-model="activeTab" class="m-t-12">
      <!-- ============ 端点配置 ============ -->
      <el-tab-pane label="端点配置" name="endpoint">
        <div class="toolbar m-b-12">
          <el-button type="primary" @click="openEndpointDialog()">新增端点</el-button>
          <div class="toolbar-right text-gray">
            配置下游业务系统的集成端点；地址填 <b>local://deliver</b> 指向本机接收台（真实连通性测试 + 发布成功），<b>local://fail</b> 指向故障台（真实失败，用于演示 Retry）；也可填真实 http(s):// 地址做真实网络探活
          </div>
        </div>
        <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
          <el-table v-loading="endpointLoading" border :data="endpoints" class="data-table">
            <el-table-column label="端点编码" prop="code" width="140" />
            <el-table-column label="端点名称" prop="name" min-width="160" />
            <el-table-column label="方向" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'Outbound' ? 'primary' : 'success'" size="small" effect="plain">
                  {{ row.direction }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="协议" prop="protocol" width="100" align="center" />
            <el-table-column label="目标系统" prop="system" width="130" align="center" />
            <el-table-column label="端点地址" prop="url" min-width="240" show-overflow-tooltip />
            <el-table-column label="认证" prop="authType" width="100" align="center" />
            <el-table-column label="报文" prop="messageFormat" width="90" align="center" />
            <el-table-column label="重试" width="80" align="center">
              <template #default="{ row }">{{ row.maxRetry }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'Active' ? 'success' : 'info'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :loading="testingId === toRow(row).id" @click="onTest(toRow(row))">测试连接</el-button>
                <el-button link type="success" :loading="publishingId === toRow(row).id" @click="onPublish(toRow(row))">发布</el-button>
                <el-button link type="primary" @click="openEndpointDialog(toRow(row))">编辑</el-button>
                <el-button link type="danger" @click="onDelete(toRow(row))">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ 运行监控 ============ -->
      <el-tab-pane label="运行监控" name="run">
        <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
          <template #header><span class="card-title">集成运行记录</span></template>
          <el-table v-loading="runLoading" border :data="runs" class="data-table">
            <el-table-column label="Run ID" prop="runId" width="200" />
            <el-table-column label="方向" prop="direction" width="130" align="center">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'Outbound' ? 'primary' : 'success'" size="small" effect="plain">
                  {{ row.direction }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="系统" prop="system" width="140" align="center" />
            <el-table-column label="状态" width="140" align="center">
              <template #default="{ row }">
                <el-tag :type="INTEGRATION_STATUS_MAP[row.status].type" size="small">
                  {{ INTEGRATION_STATUS_MAP[row.status].label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="尝试" prop="attempt" width="100" align="center" />
            <el-table-column label="记录数" prop="record" width="100" align="center" />
            <el-table-column label="错误明细" prop="detail" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDialog('integration', { runId: row.runId })">查看 / Retry</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listIntegrationRuns,
  listIntegrationEndpoints,
  deleteIntegrationEndpoint,
  testIntegrationConn,
  publishIntegration
} from '@/api/demo/cmdPoc';
import type { IntegrationRunVO, IntegrationEndpointVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { INTEGRATION_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocIntegrationPanel' });

const { openDialog } = useCmdPoc();

const activeTab = ref<'endpoint' | 'run'>('endpoint');

/* ------------------------------ 端点配置 ------------------------------ */
const endpointLoading = ref(false);
const endpoints = ref<IntegrationEndpointVO[]>([]);
const testingId = ref<number | null>(null);
const publishingId = ref<number | null>(null);

/** el-table slot scope row 为宽松类型，统一在此断言为业务 VO */
const toRow = (row: unknown): IntegrationEndpointVO => row as IntegrationEndpointVO;

const loadEndpoints = async () => {
  endpointLoading.value = true;
  try {
    endpoints.value = await listIntegrationEndpoints();
  } finally {
    endpointLoading.value = false;
  }
};

const openEndpointDialog = (row?: IntegrationEndpointVO) => {
  openDialog('integrationConn', row ? { row } : {});
};

const onTest = async (row: IntegrationEndpointVO) => {
  testingId.value = row.id;
  try {
    const msg = await testIntegrationConn(row.id);
    ElMessage.success(msg);
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '连通性测试失败');
  } finally {
    testingId.value = null;
  }
};

const onPublish = async (row: IntegrationEndpointVO) => {
  publishingId.value = row.id;
  try {
    const msg = await publishIntegration(row.id);
    ElMessage.success(msg);
    await loadRuns();
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '发布失败');
  } finally {
    publishingId.value = null;
  }
};

const onDelete = async (row: IntegrationEndpointVO) => {
  await ElMessageBox.confirm(`确认删除端点「${row.name}」？删除后不可恢复。`, '删除确认', { type: 'warning' });
  const msg = await deleteIntegrationEndpoint(row.id);
  ElMessage.success(msg);
  await loadEndpoints();
};

/* ------------------------------ 运行监控 ------------------------------ */
const runLoading = ref(false);
const runs = ref<IntegrationRunVO[]>([]);

const loadRuns = async () => {
  runLoading.value = true;
  try {
    runs.value = await listIntegrationRuns();
  } finally {
    runLoading.value = false;
  }
};

onMounted(async () => {
  await Promise.all([loadEndpoints(), loadRuns()]);
});
</script>
