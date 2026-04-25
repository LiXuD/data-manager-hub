<template>
  <el-card class="search-card">
    <div class="search-bar">
      <div class="search-inputs">
        <slot />
      </div>
      <div class="search-btn-group">
        <el-button type="primary" @click="$emit('search')">搜索</el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
defineEmits<{
  search: []
  reset: []
}>()
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.search-inputs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  flex: 1;
}

.search-btn-group {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-btn-group {
    justify-content: flex-end;
  }
}
</style>
