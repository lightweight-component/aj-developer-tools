<template>
    <nav>
      <div style="float:right;">
        <user /> | 
        <span v-if="$parent.enableDatasource"> 数据源：<span>{{dataSource.name}}</span> | </span>
         <a href="https://dev.ajaxjs.com/docs/data-service/" target="_blank">帮助</a> | <a @click="showAbout">关于</a>
      </div>
      <img src="~@/assets/icon.png" width="16" style="vertical-align: middle;" /> 数据服务 Data Service
    </nav>
</template>

<script>
import user from "@ajaxjs/ui/dist/iam/user.vue";

export default {
    components: { user },
    methods:{
        showAbout() {
            this.$Modal.info({
                title: '关于 DataService',
                content: '<p>DataService：用数据库管理 SQL 语句，快捷生成 API 接口</p><p>Powered by MyBatis + AJAXJS Framework.</p><p>ver 2023.10.31</p>'
            });
        }
    }
}
</script>

<style scoped>
nav {
    height: 30px;
    line-height: 30px;
    padding: 0 15px;
    border-bottom: 1px solid white;
    background-color: #e3e3e3;
    background-image: linear-gradient(#fefefe, #d8d8d8);
    text-shadow: 1px 1px 1px white;
}
</style>