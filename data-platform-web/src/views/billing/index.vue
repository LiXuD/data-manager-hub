<template>
  <div class="page-container">
    <div class="card">
      <div class="search-bar">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
        <el-button type="primary" style="margin-left:10px">查询</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="billingDate" label="账单日期" width="120"/>
        <el-table-column prop="tenantName" label="租户" min-width="120"/>
        <el-table-column prop="callCount" label="调用次数" width="100"/>
        <el-table-column prop="totalCost" label="总费用" width="100"/>
      </el-table>
    </div>
  </div>
</template>
<script setup lang="ts">
import {ref} from "vue"
const tableData = ref([])
const loading = ref(false)
const dateRange = ref([])
</script>
