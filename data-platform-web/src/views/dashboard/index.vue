<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6"><el-card><div class="stat-card"><h3>调用总量</h3><p class="num">0</p></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-card"><h3>今日调用</h3><p class="num">0</p></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-card"><h3>活跃厂商</h3><p class="num">0</p></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-card"><h3>调用方</h3><p class="num">0</p></div></el-card></el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-card h3 {
  font-size: 14px;
  color: #666;
  margin: 0;
}
.stat-card .num {
  font-size: 28px;
  font-weight: bold;
  margin: 10px 0 0;
}
</style>