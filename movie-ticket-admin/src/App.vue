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
  --admin-bg: #e7e8e5;
  --admin-card: #e7e8e5;
  --admin-text: #303833;
  --admin-muted: #6b746e;
  --admin-border: #cfd2cc;
  --admin-secondary: #e7e8e5;
  --admin-radius: 16px;
  --admin-shadow-dark: #c2c5bf;
  --admin-shadow-light: #fafbf8;
  --admin-shadow: 8px 8px 16px var(--admin-shadow-dark), -8px -8px 16px var(--admin-shadow-light);

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

/* 新拟物管理界面：统一应用于影院、影厅、影片、排片、订单和用户页面。 */
.el-card {
  border: 0 !important;
  background: var(--admin-card) !important;
  box-shadow: var(--admin-shadow) !important;
}

.el-card__header {
  border-bottom: 0 !important;
  box-shadow:
    inset 0 -3px 6px rgba(194, 197, 191, 0.48),
    inset 0 3px 6px rgba(250, 251, 248, 0.72);
}

.el-button:not(.is-link):not(.is-text) {
  border: 0 !important;
  color: #4b554f;
  background: var(--admin-card);
  box-shadow:
    6px 6px 12px var(--admin-shadow-dark),
    -6px -6px 12px var(--admin-shadow-light);
  transform: none !important;
  transition: box-shadow 0.3s ease-in-out, color 0.3s ease-in-out, background-color 0.3s ease-in-out;
}

.el-button:not(.is-link):not(.is-text):hover {
  color: var(--admin-primary);
  background: var(--admin-card);
  box-shadow:
    3px 3px 7px var(--admin-shadow-dark),
    -3px -3px 7px var(--admin-shadow-light);
}

.el-button:not(.is-link):not(.is-text):active {
  box-shadow:
    inset 4px 4px 8px var(--admin-shadow-dark),
    inset -4px -4px 8px var(--admin-shadow-light);
}

.el-button--primary:not(.is-link):not(.is-text) {
  color: #874900;
  background: #f0d1a8;
  box-shadow:
    6px 6px 12px #c3b298,
    -6px -6px 12px #fff0d4;
}

.el-button--primary:not(.is-link):not(.is-text):hover {
  color: #7b4100;
  background: #f0d1a8;
  box-shadow:
    3px 3px 7px #c3b298,
    -3px -3px 7px #fff0d4;
}

.el-button--primary:not(.is-link):not(.is-text):active {
  box-shadow:
    inset 4px 4px 8px #c3b298,
    inset -4px -4px 8px #fff0d4;
}

.el-button.is-link,
.el-button.is-text {
  box-shadow: none !important;
}

.el-input__wrapper,
.el-select__wrapper,
.el-textarea__inner,
.el-input-number .el-input__wrapper,
.el-date-editor.el-input__wrapper {
  border: 0 !important;
  background: var(--admin-card) !important;
  box-shadow:
    inset 5px 5px 10px var(--admin-shadow-dark),
    inset -5px -5px 10px var(--admin-shadow-light) !important;
  transition: box-shadow 0.3s ease-in-out !important;
}

.el-input__wrapper:hover,
.el-select__wrapper:hover,
.el-textarea__inner:hover {
  box-shadow:
    inset 4px 4px 8px var(--admin-shadow-dark),
    inset -4px -4px 8px var(--admin-shadow-light) !important;
}

.el-input__wrapper.is-focus,
.el-select__wrapper.is-focused,
.el-textarea__inner:focus {
  box-shadow:
    inset 2px 2px 4px var(--admin-shadow-dark),
    inset -2px -2px 4px var(--admin-shadow-light) !important;
}

.el-input__inner,
.el-textarea__inner,
.el-select__placeholder,
.el-date-editor .el-range-input {
  color: var(--admin-text) !important;
}

.el-form--inline,
.search-bar {
  border: 0;
  background: var(--admin-card);
  box-shadow:
    inset 5px 5px 10px var(--admin-shadow-dark),
    inset -5px -5px 10px var(--admin-shadow-light);
}

.el-table {
  --el-bg-color: var(--admin-card);
  --el-fill-color-blank: var(--admin-card);
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(206, 209, 203, 0.32);
  --el-table-row-hover-bg-color: rgba(255, 132, 0, 0.07);
  --el-table-border-color: rgba(166, 173, 166, 0.23);
  padding: 7px;
  background: var(--admin-card) !important;
  box-shadow:
    inset 5px 5px 11px var(--admin-shadow-dark),
    inset -5px -5px 11px var(--admin-shadow-light);
}

.el-table .el-table__inner-wrapper,
.el-table .el-table__header-wrapper,
.el-table .el-table__body-wrapper,
.el-table .el-scrollbar,
.el-table .el-scrollbar__wrap,
.el-table .el-scrollbar__view,
.el-table table,
.el-table tr {
  background: transparent !important;
}

.el-table::before,
.el-table--border::after,
.el-table--border::before,
.el-table__inner-wrapper::before {
  display: none !important;
}

.el-table .el-table__header-wrapper th.el-table__cell,
.el-table .el-table__fixed-header-wrapper th.el-table__cell {
  background: rgba(203, 207, 200, 0.42) !important;
  box-shadow:
    inset 0 -2px 4px rgba(184, 189, 182, 0.42),
    inset 0 2px 4px rgba(250, 251, 248, 0.58);
}

.el-table .el-table__body-wrapper td.el-table__cell,
.el-table .el-table__fixed-body-wrapper td.el-table__cell,
.el-table th.el-table__cell {
  background-color: transparent !important;
  border-bottom-color: rgba(165, 172, 165, 0.2) !important;
}

.el-table--striped .el-table__body tr.el-table__row--striped > td.el-table__cell,
.el-table--striped .el-table__fixed-body-wrapper tr.el-table__row--striped > td.el-table__cell {
  background-color: rgba(244, 245, 242, 0.36) !important;
}

.el-table .el-table__body tr:hover > td.el-table__cell,
.el-table .el-table__fixed-body-wrapper tr:hover > td.el-table__cell,
.el-table .el-table__body tr.hover-row > td.el-table__cell {
  background-color: rgba(255, 132, 0, 0.08) !important;
}

.el-table__fixed-right,
.el-table__fixed,
.el-table__fixed-right-patch,
.el-table .el-table-fixed-column--right,
.el-table .el-table-fixed-column--left {
  background-color: var(--admin-card) !important;
}

.el-table .el-table__header-wrapper .el-table-fixed-column--right,
.el-table .el-table__header-wrapper .el-table-fixed-column--left {
  background-color: #dfe1dc !important;
}

/* 固定列必须使用不透明背景，否则会和其下方的评分、状态等普通列重叠显示。 */
.el-table .el-table__header-wrapper th.el-table__cell.el-table-fixed-column--right,
.el-table .el-table__header-wrapper th.el-table__cell.el-table-fixed-column--left {
  background-color: #dfe1dc !important;
}

.el-table .el-table__body-wrapper td.el-table__cell.el-table-fixed-column--right,
.el-table .el-table__body-wrapper td.el-table__cell.el-table-fixed-column--left {
  background-color: var(--admin-card) !important;
}

.el-table--striped .el-table__body-wrapper tr.el-table__row--striped > td.el-table__cell.el-table-fixed-column--right,
.el-table--striped .el-table__body-wrapper tr.el-table__row--striped > td.el-table__cell.el-table-fixed-column--left {
  background-color: #eff0ec !important;
}

.el-table .el-table__body tr:hover > .el-table-fixed-column--right,
.el-table .el-table__body tr:hover > .el-table-fixed-column--left {
  background-color: #eadfce !important;
}

.el-table .el-table__body-wrapper tr:hover > td.el-table__cell.el-table-fixed-column--right,
.el-table .el-table__body-wrapper tr:hover > td.el-table__cell.el-table-fixed-column--left {
  background-color: #eadfce !important;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  white-space: nowrap;
}

.el-table .el-table__empty-block {
  background: transparent !important;
  color: var(--admin-muted);
}

.el-tag {
  border: 0 !important;
  background: var(--admin-card);
  box-shadow:
    2px 2px 5px rgba(187, 191, 185, 0.72),
    -2px -2px 5px rgba(250, 251, 248, 0.82);
}

.el-pagination button,
.el-pagination .el-pager li {
  border: 0 !important;
  background: var(--admin-card) !important;
  box-shadow:
    3px 3px 6px var(--admin-shadow-dark),
    -3px -3px 6px var(--admin-shadow-light);
  transition: box-shadow 0.3s ease-in-out;
}

.el-pagination button:active,
.el-pagination .el-pager li.is-active {
  box-shadow:
    inset 3px 3px 6px var(--admin-shadow-dark),
    inset -3px -3px 6px var(--admin-shadow-light);
}

.el-pagination .el-pager li.is-active {
  color: #b65f00 !important;
}

.el-dialog,
.el-message-box {
  border: 0 !important;
  background: var(--admin-card) !important;
  box-shadow:
    14px 14px 30px rgba(167, 172, 165, 0.76),
    -14px -14px 30px rgba(250, 251, 248, 0.94) !important;
}

.el-dialog__header,
.el-dialog__footer {
  border: 0 !important;
}

.el-dialog__header {
  box-shadow:
    inset 0 -3px 6px rgba(194, 197, 191, 0.46),
    inset 0 3px 6px rgba(250, 251, 248, 0.7);
}

.el-dialog__footer {
  box-shadow:
    inset 0 3px 6px rgba(194, 197, 191, 0.42),
    inset 0 -3px 6px rgba(250, 251, 248, 0.64);
}

.el-overlay {
  background: rgba(70, 77, 72, 0.25) !important;
  backdrop-filter: blur(3px);
}

.el-dropdown-menu {
  border: 0 !important;
  background: var(--admin-card) !important;
  box-shadow:
    7px 7px 15px rgba(176, 181, 174, 0.76),
    -7px -7px 15px rgba(250, 251, 248, 0.9) !important;
}

.el-dropdown-menu__item:hover {
  background: rgba(255, 132, 0, 0.08) !important;
}

.el-upload-dragger,
.upload-trigger,
.poster-upload-area {
  border-color: transparent !important;
  background: var(--admin-card) !important;
  box-shadow:
    inset 4px 4px 8px var(--admin-shadow-dark),
    inset -4px -4px 8px var(--admin-shadow-light);
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}

.brand-logo-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.logo-mark:has(> .brand-logo-image),
.brand-icon:has(> .brand-logo-image) {
  border-radius: 0;
  background: transparent !important;
  box-shadow: none !important;
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

  .el-card {
    box-shadow:
      4px 4px 9px var(--admin-shadow-dark),
      -4px -4px 9px var(--admin-shadow-light) !important;
  }
}
</style>
