<script setup lang="ts">
// 数据类型管理页面
</script>

<template>
  <div class="datatype-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>数据类型列表</span>
          <el-button type="primary">新增数据类型</el-button>
        </div>
      </template>
      <el-empty description="数据类型管理页面建设中..." />
    </el-card>
  </div>
</template>

<style scoped>
.datatype-page {
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