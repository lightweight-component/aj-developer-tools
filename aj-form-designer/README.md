# aj-form-designer

基于 Vue 3、View UI Plus 和 Pinia 的表单可视化设计器。它只覆盖明确的表单控件与两列布局，输出稳定的 `FormSchema`，而不是尝试设计全部 View UI 组件。

## 本地运行

```bash
npm install
npm run dev
npm run test
npm run build
```

完整操作说明见 [使用教程](docs/使用教程.md)。

## Schema

`FormSchema` 固定使用 `version: 1`，根级可放字段或两列栅格；字段名必须唯一。设计器可导入、导出该 JSON。

旧版通用设计器 JSON 也可导入：只迁移 `Form`、`FormItem`、`Row`、`Col` 和受支持表单字段。其它节点会被跳过，并显示迁移提示。

## 生成 Vue SFC

工具栏的“生成 SFC”会根据当前 Schema 生成 Vue 3 单文件组件。生成内容包括：

- View UI Plus 的 `Form`、字段 `v-model` 与两列布局；
- 字段 `v-model`、选择类控件的选项及两列 `Row/Col`；
- 画布中的 Button 原样生成，默认是普通操作按钮，不隐式提交或重置。

Button 可配置为普通按钮、触发事件或自定义 JS。事件动作会生成 `emit("事件名", formData)`；自定义代码会生成独立点击函数，函数体可使用 `formData`、`emit` 和 `event`。自定义 JS 不会在设计器预览中执行。

生成器不会额外添加按钮：画布中有什么字段和 Button，生成组件就包含什么。

生成组件假定宿主应用已全局注册 View UI Plus；如按需注册，请在宿主工程中注册 Schema 所使用的组件。
