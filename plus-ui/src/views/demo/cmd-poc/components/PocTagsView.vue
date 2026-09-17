<template>
  <div class="poc-tags-view">
    <div ref="scrollRef" class="tags-scroll">
      <div
        v-for="tag in tags"
        :key="tag.key"
        :class="['tags-item', { active: isActive(tag) }]"
        @click="activate(tag)"
      >
        <span class="tags-dot" />
        <span class="tags-title">{{ tag.title }}</span>
        <el-icon v-if="!tag.affix" class="tags-close" @click.stop="closeTag(tag)">
          <Close />
        </el-icon>
      </div>
    </div>

    <el-dropdown class="tags-action" trigger="click" placement="bottom-end" @command="handleCommand">
      <div class="tags-action-btn">
        <el-icon><ArrowDown /></el-icon>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="refresh">
            <el-icon class="dropdown-icon"><RefreshRight /></el-icon>
            <span>刷新当前</span>
          </el-dropdown-item>
          <el-dropdown-item command="close" :disabled="!activeTag || activeTag.affix">
            <el-icon class="dropdown-icon"><Close /></el-icon>
            <span>关闭当前</span>
          </el-dropdown-item>
          <el-dropdown-item command="closeOthers" :disabled="tags.length <= 1">
            <el-icon class="dropdown-icon"><CircleClose /></el-icon>
            <span>关闭其他</span>
          </el-dropdown-item>
          <el-dropdown-item command="closeAll" divided>
            <el-icon class="dropdown-icon"><CircleClose /></el-icon>
            <span>全部关闭</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, CircleClose, Close, RefreshRight } from '@element-plus/icons-vue';
import { computed, ref, watch } from 'vue';
import type { PageId } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../composables/useCmdPoc';

interface TagItem {
  /** 唯一标识 */
  key: string;
  /** 页面 id */
  pageId: PageId;
  /** 显示标题 */
  title: string;
  /** 是否固定（不可关闭） */
  affix: boolean;
}

const emit = defineEmits<{
  (e: 'refresh'): void;
}>();

const { currentPage, pageTitle, goMenu } = useCmdPoc();

const tags = ref<TagItem[]>([
  { key: 'dash', pageId: 'dash', title: '工作台', affix: true }
]);
const scrollRef = ref<HTMLDivElement | null>(null);

const activeTag = computed<TagItem | undefined>(() => tags.value.find(t => t.pageId === currentPage.value));

const isActive = (tag: TagItem) => tag.pageId === currentPage.value;

/** 当前页面尚未在标签栏时自动追加 */
const ensureTag = () => {
  const pageId = currentPage.value;
  if (tags.value.some(t => t.pageId === pageId)) return;
  tags.value.push({
    key: `${pageId}-${Date.now()}`,
    pageId,
    title: pageTitle.value,
    affix: false
  });
};

watch(currentPage, ensureTag, { immediate: true });

const activate = (tag: TagItem) => {
  if (tag.pageId !== currentPage.value) {
    goMenu(tag.pageId);
  }
};

const closeTag = (tag: TagItem) => {
  const index = tags.value.findIndex(t => t.key === tag.key);
  if (index === -1) return;
  tags.value.splice(index, 1);
  if (isActive(tag) && tags.value.length) {
    const last = tags.value[tags.value.length - 1];
    goMenu(last.pageId);
  }
};

const handleCommand = (command: string) => {
  switch (command) {
    case 'refresh':
      emit('refresh');
      break;
    case 'close':
      if (activeTag.value && !activeTag.value.affix) closeTag(activeTag.value);
      break;
    case 'closeOthers':
      if (!activeTag.value) return;
      tags.value = tags.value.filter(t => t.affix || t.key === activeTag.value!.key);
      break;
    case 'closeAll': {
      const keep = tags.value.filter(t => t.affix);
      tags.value = keep;
      if (activeTag.value && !activeTag.value.affix) {
        goMenu('dash');
      }
      break;
    }
  }
};
</script>

<style lang="scss" scoped>
.poc-tags-view {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 6px 10px;
  margin-bottom: 12px;
  background: var(--g-card);
  border: 1px solid var(--g-divider);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.tags-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.tags-item {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  line-height: 25px;
  padding: 0 10px;
  font-size: 12px;
  color: var(--g-text2);
  background: var(--g-content);
  border: 1px solid var(--g-divider);
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;

  &:hover {
    color: var(--g-text);
    border-color: var(--el-color-primary-light-5);
  }

  &.active {
    color: #fff;
    background: var(--el-color-primary);
    border-color: var(--el-color-primary);

    .tags-dot {
      background: #fff;
    }

    .tags-close {
      color: #fff;

      &:hover {
        background: rgba(255, 255, 255, 0.2);
      }
    }
  }
}

.tags-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--g-text2);
}

.tags-title {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tags-close {
  width: 14px;
  height: 14px;
  margin-left: 2px;
  border-radius: 50%;
  color: var(--g-text2);
  transition: background 0.2s ease;

  &:hover {
    background: var(--g-divider);
  }
}

.tags-action {
  flex-shrink: 0;
}

.tags-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: var(--g-text2);
  background: var(--g-content);
  border: 1px solid var(--g-divider);
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;

  &:hover {
    color: var(--g-text);
    border-color: var(--el-color-primary-light-5);
  }
}

.dropdown-icon {
  margin-right: 6px;
}
</style>
