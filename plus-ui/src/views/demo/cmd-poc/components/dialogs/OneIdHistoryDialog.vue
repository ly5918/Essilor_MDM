<template>
  <div class="poc-dialog-body">
    <div class="timeline-v">
      <div v-for="event in events" :key="event.date + event.stage" class="event">
        <b>{{ event.date }} · {{ event.stage }}</b>
        <div class="event-desc">{{ event.description }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getOneIdHistory } from '@/api/demo/cmdPoc';
import type { OneIdEventVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocOneIdHistoryDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const events = ref<OneIdEventVO[]>([]);

onMounted(async () => {
  events.value = await getOneIdHistory((props.payload?.oneId as string) ?? 'GC-000128');
});
</script>
