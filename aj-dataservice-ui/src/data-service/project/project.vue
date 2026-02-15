<template>
    <Modal :title="(isCreate ? '新建' : '编辑') + '项目'" :loading="loading" v-model="isShow" ok-text="保存" @on-ok="saveProject" cancel-text="" width="600">
      <i-Form ref="editForm" :model="data" :rules="rules" :label-width="180" style="margin-right:100px">
        <form-item label="项目名称" prop="name">
          <i-Input v-model="data.name" placeholder="项目名称" />
        </form-item>
        <form-item label="项目简介" prop="content">
          <i-Input type="textarea" :autosize="{minRows: 2,maxRows: 5}" placeholder="项目简介，可选的" v-model="data.content" />
        </form-item>
         <form-item label="API 前缀-开发阶段" prop="apiPrefixDev">
          <i-Input v-model="data.apiPrefixDev" placeholder="以 http(s)...开头" />
        </form-item>          
        <form-item label="API 前缀-生产环境" prop="apiPrefixProd">
          <i-Input v-model="data.apiPrefixProd" placeholder="以 http(s)...开头" />
        </form-item>        
      </i-Form>
    </Modal>
</template>

<script lang="ts" src="./index.ts" ></script>
