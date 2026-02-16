<template>
    <ul class="toolbar">
        <li @click="refreshConfig" style="float:right" >
        <div>
            <div class="icon">
            <Icon type="md-refresh" size="25" style="color:darkcyan" />
            </div>
            <span class="text">刷新配置</span>
        </div>
        </li>
        <li @click="$parent.dataSource.isShowDataSource = true" v-if=" $parent.enableDatasource" style="float:right" >
            <div>
                <div class="icon">
                <Icon type="md-swap" size="25" style="color:blueviolet" />
                </div>
                <span class="text">数据源</span>
            </div>
        </li> 
        <li @click="createService">
            <div>
                <div class="icon">
                <Icon type="md-add" size="25" style="color:green" />
                </div>
                <span class="text">新建服务</span>
            </div>
        </li>
         <li @click="save" :class="{disabled: activeTab == 'index'}">
            <div>
                <div class="icon">
                <Icon type="ios-create" size="25" style="color:#f90" />
                </div>
                <span class="text">保存</span>
            </div>
        </li>
        <li @click="$parent.$parent.del($event)" :class="{disabled: activeTab == 'index'}">
            <div>
                <div class="icon">
                <Icon type="md-close" size="25" style="color:red" />
                </div>
                <span class="text">删除</span>
            </div>
        </li> 
        <li @click="refreshTree">
            <div>
                <div class="icon">
                <Icon type="md-refresh" size="25" style="color:#f90" />
                </div>
                <span class="text">重新加载</span>
            </div>
        </li>
    </ul>
</template>

<script lang="ts" src="./index.ts"></script>
<style lang="less" scoped src="./style.less"></style>