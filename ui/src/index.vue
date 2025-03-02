<template>
  <div>
       <Menu class="left-panel" :open-names="['portal-1', 'portal-2']">
            <h3>Dev-tools</h3>
            <Submenu name="portal-1" >
              <template slot="title">用户组织管理</template>
              <MenuItem name="4-1" @click.native="load = 'UserListIndex'">用户列表管理</MenuItem>
              <MenuItem name="4-2" @click.native="load = 'OrgIndex'">组织机构管理</MenuItem>
              <MenuItem name="4-3" @click.native="load = 'RBAC'">权限管理</MenuItem>
              <MenuItem name="4-3" @click.native="load = 'Permission'">权限管理2</MenuItem>
            </Submenu>

            <Submenu name="portal-2">
              <template slot="title">认证管理</template>
              <MenuItem name="user-2-1" @click.native="showList(130, '客户端应用')">客户端应用</MenuItem>
              <MenuItem name="user-2-2" @click.native="showList(129, '租户管理')">租户管理</MenuItem>
              <MenuItem name="user-2-3" @click.native="showList(131, 'Token 列表')">Token 管理</MenuItem>
            </Submenu>
        </Menu>
        <div class="right-panel">
            <OrgIndex v-if="load == 'OrgIndex'" />
            <UserListIndex v-if="load == 'UserListIndex'" />
            <RBAC v-if="load == 'RBAC'" />
            <Permission v-if="load == 'Permission'" />
        </div>
  </div>
</template>

<script>
// import RBAC from './rbac/rbac.vue';
// import Permission from "./permission/permission-index.vue";

export default {
  // components: {RBAC, Permission},
  data() {
    return {
      load: ''
    };
  },
  methods: {
    showList() {

    }
  },
};
</script>

<style lang="less">
.home h2,
.home p {
  max-width: 800px;
  margin: 10px auto;
}
html,
body{
  overflow: hidden;
}
html,
body, body> div,
body> div > .ivu-menu {
  height: 100%;
}

h1.page-title {
  margin: 0 0 2% 1%;
  padding-bottom: 1%;
  border-bottom: 1px solid #eee;
  color: #2f518c;
  letter-spacing: 2px;
  height: 9%;
  line-height: 100px;
}

h3 {
  padding: 30px 22px;
  box-sizing: border-box;
  color: #2f518c;
  width: 100%;
  border-right: 1px solid lightgray;
  font-size: 1.3em;
  font-weight: bold;
  letter-spacing: 1px;
  height: 9%;
}

body > div {
  display: flex;
}

.left-panel {
    flex: 0 0 200px;
    height: 100%;

}

.right-panel {
    flex: 1;
    height: 100%;
}

.ivu-menu-submenu-title {
  border-top: 1px solid #eee;
}
</style>

<style lang="less">
.ivu-menu.ivu-menu-vertical {
    // background: url(~@/assets/user.png) no-repeat 110px bottom;
    background-size: 72%;
}
</style>