# 页面修改和风格记录

## 2024-04-24

### 1. Tab 样式统一优化

**问题**：计费管理页面的Tab字体太暗，未选中的tab文字显示不清楚。

**修改文件**：
- `data-platform-web/src/styles/index.scss`

**修改内容**：
- 全局统一样式，未选中的tab使用 `--color-text-secondary`（比之前更亮）
- 悬停时变为 `--color-text-primary`
- 选中的tab保持绿色 `--color-primary`

```scss
/* Tab */
.el-tabs {
  --el-tabs-header-text-color: var(--color-text-secondary) !important;
  --el-tabs-active-text-color: var(--color-primary) !important;

  .el-tabs__item {
    color: var(--color-text-secondary) !important;
    transition: all var(--transition-fast) !important;
    font-weight: 500 !important;

    &:hover {
      color: var(--color-text-primary) !important;
    }

    &.is-active {
      color: var(--color-primary) !important;
    }
  }

  .el-tabs__active-bar {
    background-color: var(--color-primary) !important;
  }
}
```

---

### 2. 顶部通知铃铛添加跳转

**问题**：主页右上角的铃铛图标显示小红点数字，但点击没有任何反应。

**修改文件**：
- `data-platform-web/src/views/layout/index.vue`

**修改内容**：
- 给 `el-badge` 添加 `@click` 事件，点击后跳转到监控告警页面 `/monitor`
- 添加hover放大效果，提升交互体验

```vue
<!-- 通知 -->
<el-badge :value="3" :max="99" class="notification-badge" @click="router.push('/monitor')">
  <button class="header-btn">
    ...
  </button>
</el-badge>
```

**样式**：
```scss
.notification-badge {
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: scale(1.1);
  }
}
```

---

### 3. 左侧菜单告警小红点移除

**问题**：左侧菜单「监控告警」旁边的小红点图标与文字不对齐，多次调整仍无法居中。

**修改文件**：
- `data-platform-web/src/views/layout/index.vue`

**修改内容**：
- 移除菜单项中的 `<el-badge>` 组件
- 同时清理了相关的样式代码

---

### 4. 菜单项垂直居中优化

**修改文件**：
- `data-platform-web/src/views/layout/index.vue`

**修改内容**：
- 给菜单项添加 `display: flex; align-items: center;` 确保内容垂直居中

```scss
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 48px;
  line-height: 48px;
  margin: 4px 12px;
  padding: 0 16px !important;
  border-radius: 10px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
}
```