<template>
  <div class="page-header">
    <div class="header-left">
      <h2>{{ title }}</h2>
      <p v-if="description" class="header-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.action" class="header-right">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title: string
  description?: string
}

defineProps<Props>()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.header-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
}

.header-right {
  display: flex;
  gap: 12px;
}
</style>
