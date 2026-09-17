<template>
  <section class="page">
    <!-- 操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <div class="card-head">
          <span class="card-title">操作区</span>
          <div class="card-toolbar-right">
            <el-button v-if="!readOnly" plain icon="RefreshRight" @click="openDialog('loop')">Loop Check</el-button>
            <el-button v-if="!readOnly" type="primary" plain icon="Plus" @click="openDialog('hierAdd')">新增关系</el-button>
          </div>
        </div>
      </template>
      <p class="text-tip">维护客户层级关系：新增母子关系或执行 Loop Check 校验。</p>
    </el-card>

    <!-- 客户层级结构 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header><span class="card-title">客户层级结构</span></template>
      <el-tree :data="treeData" :props="treeProps" node-key="id" default-expand-all class="hier-tree">
        <template #default="{ data }">
          <div class="tree-node">
            <el-tag size="small" effect="dark" :type="levelTagType(data.level)">{{ data.level }}</el-tag>
            <span class="node-label">{{ data.label }}</span>
          </div>
        </template>
      </el-tree>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getHierarchy } from '@/api/demo/cmdPoc';
import type { HierarchyNodeVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocHierarchyPanel' });

const { readOnly, openDialog } = useCmdPoc();

const treeData = ref<HierarchyNodeVO[]>([]);
const treeProps = { label: 'label', children: 'children' };

const levelTagType = (level: string) => (level === 'A3' ? 'primary' : level === 'A2' ? 'warning' : 'success');

onMounted(async () => {
  treeData.value = await getHierarchy();
});
</script>

<style lang="scss" scoped>
.text-tip {
  margin: 0;
  font-size: 13px;
  color: var(--g-text2);
}
</style>
