<template>
  <div class="api">
    <span :class="'httpMethod ' + getHttpMethod()" style="font-weight: bold">
      {{ getHttpMethod() }}
    </span>
    {{ apiUrl }}
    <a :href="apiUrl" target="_blank" title="打开连接">&#x1f517;</a>&nbsp;
    <a href="javascript:void(0)" @click="copy" title="复制">📄</a>&nbsp;
    <a href="javascript:alert('TODO')" style="text-decoration: underline">测试</a>
  </div>
</template>
<script>
import { copyToClipboard } from "@ajaxjs/util/dist/util/utils";

export default {
  props: {
    page: { type: Boolean, require: false },
    apiUrl: { type: String, require: true }
  },
  data() {
    return {
      httpMethod: this.$parent.editorData
        ? this.$parent.editorData.type
        : "GET",
    };
  },
  methods: {
    copy() {
      copyToClipboard(this.apiUrl);
      this.$Message.success("复制成功");
    },
    getHttpMethod() {
      if (this.$parent.editorData)
        switch (this.$parent.editorData.type) {
          case "info":
          case "list":
            return "GET";
          case "create":
            return "POST";
          case "update":
            return "PUT";
          case "delete":
            return "DELETE";
        }
      else {
      }
    },
  },
};
</script>

<style lang="less" scoped>
.api {
  border-left: 4px solid lightgray;
  padding-left: 15px;
  font-family: "Courier New", Courier, monospace;
  font-size: 0.95rem;

  .GET {
    color: green;
  }

  .POST {
    color: burlywood;
  }

  .PUT {
    color: blueviolet;
  }

  .DELETE {
    color: red;
  }
}
</style>