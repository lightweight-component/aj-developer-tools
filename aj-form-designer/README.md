# aj-form-designer

基于 Vue 3、View UI Plus 和 Pinia 的表单可视化设计器。它只覆盖明确的表单控件与两列布局，输出稳定的 `FormSchema`，而不是尝试设计全部 View UI 组件。

## 本地运行

```bash
npm install
npm run dev
npm run test
npm run build
```

## Schema

`FormSchema` 固定使用 `version: 1`，根级可放字段或两列栅格；字段名必须唯一。设计器可导入、导出该 JSON。

旧版通用设计器 JSON 也可导入：只迁移 `Form`、`FormItem`、`Row`、`Col` 和受支持表单字段。其它节点会被跳过，并显示迁移提示。

## 生成 Vue SFC

工具栏的“生成 SFC”会根据当前 Schema 生成 Vue 3 单文件组件。生成内容包括：

- View UI Plus 的 `Form`、必填校验及重置按钮；
- 字段 `v-model`、选择类控件的选项及两列 `Row/Col`；
- `submit` 事件，校验成功时传出 `Record<string, unknown>` 表单数据。

生成组件假定宿主应用已全局注册 View UI Plus；如按需注册，请在宿主工程中注册 Schema 所使用的组件。
