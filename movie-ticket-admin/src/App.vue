<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;600;700&display=swap');

:root {
  --admin-primary: #ff8400;
  --admin-primary-hover: #ee7600;
  --admin-primary-soft: #fff3e0;
  --admin-bg: #f2f3f0;
  --admin-card: #ffffff;
  --admin-text: #111111;
  --admin-muted: #666666;
  --admin-border: #cbccc9;
  --admin-secondary: #e7e8e5;
  --admin-radius: 16px;
  --admin-shadow: 0 8px 30px rgba(17, 17, 17, 0.06);

  --el-color-primary: var(--admin-primary);
  --el-color-primary-light-3: #ffa84d;
  --el-color-primary-light-5: #ffc580;
  --el-color-primary-light-7: #ffe0b3;
  --el-color-primary-light-8: #ffecd1;
  --el-color-primary-light-9: var(--admin-primary-soft);
  --el-color-primary-dark-2: #cc6a00;
  --el-border-radius-base: 10px;
  --el-border-radius-small: 8px;
  --el-text-color-primary: var(--admin-text);
  --el-text-color-regular: #4f504d;
  --el-text-color-secondary: var(--admin-muted);
  --el-border-color: var(--admin-border);
  --el-border-color-light: #dedfdb;
  --el-border-color-lighter: #ecece9;
  --el-fill-color-light: #f7f7f5;
  --el-fill-color-lighter: #fafaf8;
  --el-bg-color-page: var(--admin-bg);
  --el-font-family: 'Geist', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  min-width: 320px;
  color: var(--admin-text);
  background: var(--admin-bg);
  font-family: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
}

#app {
  min-height: 100vh;
}

button,
input,
textarea,
select {
  font: inherit;
}

/* 让所有管理页共享用户端的卡片、表单和数据表视觉。 */
.el-card {
  border: 1px solid var(--admin-border) !important;
  border-radius: var(--admin-radius) !important;
  background: var(--admin-card) !important;
  box-shadow: none !important;
  overflow: hidden;
}

.el-card__header {
  min-height: 64px;
  padding: 18px 22px !important;
  border-bottom: 1px solid var(--admin-secondary) !important;
  font-size: 16px;
  font-weight: 700;
}

.el-card__body {
  padding: 22px !important;
}

.card-header > span,
.el-card__header > span {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.el-button {
  min-height: 38px;
  padding: 8px 16px;
  border-radius: 999px !important;
  font-weight: 600;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.el-button:not(.is-link):not(.is-text):hover {
  transform: translateY(-1px);
}

.el-button--primary:not(.is-link) {
  color: #111;
  box-shadow: 0 5px 14px rgba(255, 132, 0, 0.2);
}

.el-button.is-link,
.el-button.is-text {
  min-height: auto;
  padding-inline: 7px;
}

.el-input__wrapper,
.el-select__wrapper,
.el-textarea__inner,
.el-input-number .el-input__wrapper,
.el-date-editor.el-input__wrapper {
  min-height: 40px;
  border-radius: 10px !important;
  background: #fff !important;
  box-shadow: 0 0 0 1px var(--admin-border) inset !important;
  transition: box-shadow 0.15s ease;
}

.el-input__wrapper:hover,
.el-select__wrapper:hover,
.el-textarea__inner:hover {
  box-shadow: 0 0 0 1px #a9aaa6 inset !important;
}

.el-input__wrapper.is-focus,
.el-select__wrapper.is-focused,
.el-textarea__inner:focus {
  box-shadow: 0 0 0 2px rgba(255, 132, 0, 0.28) inset !important;
}

.el-form--inline,
.search-bar {
  padding: 16px;
  margin-bottom: 20px !important;
  border: 1px solid var(--admin-secondary);
  border-radius: 14px;
  background: #fafaf8;
}

.el-form--inline .el-form-item {
  margin-bottom: 0;
}

.el-table {
  --el-table-header-bg-color: #f7f7f5;
  --el-table-row-hover-bg-color: #fff8ef;
  --el-table-border-color: #e5e5e1;
  border-radius: 12px;
  overflow: hidden;
  color: #343532;
}

.el-table th.el-table__cell {
  height: 50px;
  color: #555653;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.el-table td.el-table__cell {
  padding-block: 13px;
}

.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background: #fafaf8;
}

.el-tag {
  border-radius: 999px !important;
  font-weight: 600;
}

.el-pagination {
  gap: 5px;
}

.el-pagination button,
.el-pagination .el-pager li {
  border-radius: 8px !important;
}

.el-pagination .el-pager li.is-active {
  color: #111 !important;
}

.el-dialog {
  border: 1px solid var(--admin-border);
  border-radius: 20px !important;
  box-shadow: 0 24px 70px rgba(17, 17, 17, 0.18) !important;
  overflow: hidden;
}

.el-dialog__header {
  padding: 22px 24px 18px !important;
  border-bottom: 1px solid var(--admin-secondary);
}

.el-dialog__title {
  color: var(--admin-text) !important;
  font-size: 19px !important;
  font-weight: 700;
}

.el-dialog__body {
  padding: 24px !important;
}

.el-dialog__footer {
  padding: 16px 24px 22px !important;
  border-top: 1px solid var(--admin-secondary);
}

.el-message-box {
  border-radius: 18px !important;
}

.el-dropdown-menu {
  padding: 7px !important;
  border-radius: 12px !important;
}

.el-dropdown-menu__item {
  border-radius: 8px;
}

@media (max-width: 900px) {
  .el-card__body,
  .el-card__header {
    padding: 16px !important;
  }

  .el-form--inline .el-form-item {
    margin-bottom: 10px;
    margin-right: 8px;
  }

  .el-dialog {
    width: calc(100% - 24px) !important;
    margin-top: 4vh !important;
  }
}
</style>
