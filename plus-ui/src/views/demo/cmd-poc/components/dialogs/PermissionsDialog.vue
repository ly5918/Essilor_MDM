<template>
  <div class="poc-dialog-body">
    <el-tabs v-model="activeTab">
      <!-- 角色矩阵 -->
      <el-tab-pane label="角色矩阵" name="matrix">
        <el-table border :data="matrix" class="data-table">
          <el-table-column label="能力" prop="capability" min-width="160" />
          <el-table-column label="Business" prop="business" align="center" />
          <el-table-column label="Steward" prop="steward" align="center" />
          <el-table-column label="Admin" prop="admin" align="center" />
          <el-table-column label="Auditor" prop="auditor" align="center" />
        </el-table>
        <el-alert class="m-t-12" type="info" :closable="false" show-icon title="技术角色保持4类；BU / GC通过Data Steward Scope区分。" />
      </el-tab-pane>

      <!-- Scope 分配 -->
      <el-tab-pane label="Scope分配" name="scope">
        <el-table border :data="roles" class="data-table">
          <el-table-column label="角色" prop="role" min-width="180" />
          <el-table-column label="Scope" prop="scope" min-width="200" />
          <el-table-column label="权限点" prop="points" min-width="220" />
          <el-table-column label="启用" width="90" align="center">
            <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 字段权限 -->
      <el-tab-pane label="字段权限" name="field">
        <el-table border :data="metadataFields" class="data-table" max-height="320">
          <el-table-column label="字段编码" prop="code" min-width="150" />
          <el-table-column label="显示名称" prop="label" min-width="150" />
          <el-table-column label="层级" prop="scope" width="140" align="center" />
          <el-table-column label="Business" width="120" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.scope === 'GC Core' ? 'success' : 'info'">
                {{ row.scope === 'GC Core' ? '可编辑' : '可见' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Steward" width="120" align="center">
            <template #default><el-tag size="small" type="success">可编辑</el-tag></template>
          </el-table-column>
          <el-table-column label="Auditor" width="120" align="center">
            <template #default><el-tag size="small" type="info">只读</el-tag></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getPermissionMatrix, listRolePermissions, saveRolePermissions } from '@/api/demo/cmdPoc';
import type { PermissionMatrixVO, RolePermissionVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocPermissionsDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const { metadataFields } = useCmdPoc();

const activeTab = ref('matrix');
const matrix = ref<PermissionMatrixVO[]>([]);
const roles = ref<RolePermissionVO[]>([]);

const submit = async (): Promise<string> => {
  const message = await saveRolePermissions(roles.value);
  roles.value = await listRolePermissions();
  return message;
};

onMounted(async () => {
  [matrix.value, roles.value] = await Promise.all([getPermissionMatrix(), listRolePermissions()]);
});

defineExpose({ submit });
</script>
