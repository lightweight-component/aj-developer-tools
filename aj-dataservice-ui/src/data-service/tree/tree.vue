<template>
    <span>
        <div class="search-panel ">
          <i-Input suffix="ios-search" placeholder="搜索数据服务……" style="width: 90%" />
        </div>

        <Tree ref="treeCmp" :data="treeData" :load-data="loadTreeData" style="height: 93%;overflow-y: auto;margin-left: 10px;" 
          @on-contextmenu="handleContextMenu" @on-select-change="$parent.$parent.openTab">
          <template slot="contextMenu">
            <span v-if="isProjectNode">
              <Dropdown-Item @click.native="$parent.$parent.$refs.project.create" style="color:green">
                <Icon type="ios-add" /> 新建项目</Dropdown-Item>
              <Dropdown-Item @click.native="$parent.$parent.$refs.project.update">
                <Icon type="ios-create" /> 编辑项目</Dropdown-Item>
              <Dropdown-Item @click.native="$parent.$parent.$refs.project.deletePorject" style="color: #ed4014">
                <Icon type="ios-trash" /> 删除项目
              </Dropdown-Item>
            </span>
            <span v-if="!isProjectNode">
              <Dropdown-Item style="color: #ed4014">删除服务</Dropdown-Item>
            </span>
          </template>
        </Tree>
    </span>
</template>

<script lang="ts" src="./index.ts"></script>

<style lang="less" scoped>
.search-panel {
    border-bottom: 1px solid lightgray;
    background-image: linear-gradient(#fefefe, #e6e6e6);
    height: 69px;
    padding-top: 14px;
    text-align: center;
}
</style>