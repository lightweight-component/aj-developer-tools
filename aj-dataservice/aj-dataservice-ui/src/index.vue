<template>
  <DataService />
</template>

<script>
import DataService from "./data-service/data-service.vue";

function get(url, successCallback, errorCallback) {
  const xhr = new XMLHttpRequest();
  xhr.open('GET', url, true); // true 表示异步请求
  xhr.onload = function () {
    if (xhr.status >= 200 && xhr.status < 300) {

      try {
        const response = JSON.parse(xhr.responseText); // 假设返回的是 JSON
        successCallback(response);
      } catch (e) {
        errorCallback('解析响应失败');
      }
    } else {
      errorCallback(new Error('请求失败，状态码：' + xhr.status));
    }
  };

  xhr.onerror = function () {
    errorCallback(new Error('网络错误'));
  };

  xhr.send();
}

get('/iam_api/user/info', (json) => {
  if (json.status && json.data) {
    console.log(json.data)

    // this.userInfo = json.data;
    // this.loginState = true;
  } else {

  }
}, (error) => {
  console.error('请求失败:', error);
  if (confirm('你未登录！是否跳转到登录页面？')) {
    // location.assign(`../../iam_api/oidc/authorization?response_type=code&client_id=lKi9p9FyicBd6eA` +
    //     `&state=${Math.random().toString(36).substring(2, 15)}` +
    //     `&nonce=${Math.random().toString(36).substring(2, 15)}` +
    //     `&web_uri=${encodeURIComponent(location.href)}` +
    //     `&redirect_uri=${encodeURIComponent('../../iam_api/client/callback')}`);

    location.assign(`/api/client/to_login?web_url=${encodeURIComponent(location.href)}`);
  }
});

// aj.IAM.getLoginInfo(window.config.loginUrl, window.config.thisPageUrl);

export default {
  components: {
    DataService
  },
  methods: {
    routeTo(route) {
      location.hash = "#/" + route;
    },
    open(route) {
      window.open("#/" + route);
    },
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
body {
  overflow: hidden;
}

html,
body,
.main>.ivu-menu {
  height: 100%;
}

/* 分页控件有点问题，修改下 */
.ivu-mt.ivu-text-right {
  text-align: right;
  margin-top: 20px;
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

.ivu-menu-submenu-title {
  border-top: 1px solid #eee;
}
</style>