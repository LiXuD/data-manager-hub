<script setup lang="ts">
// 调用记录页面
</script>

<template>
  <div class="call-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>调用记录列表</span>
        </div>
      </template>
      <el-empty description="调用记录页面建设中..." />
    </el-card>
  </div>
</template>

<style scoped>
.call-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>