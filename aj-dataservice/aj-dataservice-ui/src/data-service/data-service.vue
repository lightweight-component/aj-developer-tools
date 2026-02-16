<template>
  <div class="data-service">
    <topToolabar />
    <Split v-model="split1" style="border-top: 1px solid lightgray;">
      <div slot="left" class="split-pane-left">
        <leftTree ref="leftTreeCmp" />
      </div>

      <div slot="right" class="split-pane-right">
        <toolbar :active-tab="activeTab" />

        <Tabs ref="tab" class="content mainTab" name="mainTab" :value="activeTab" :animated="false" type="card"
          @on-click="onTabClick" @on-tab-remove="onTabClose">
          <tab-pane label="首页" name="index" :index="0" :closable="false" tab="mainTab" style="padding: 0 10px">
            <h1>Welcome to DataService</h1>
          </tab-pane>
          <tab-pane v-for="tab in mainTabs" :key="tab.label" :index="tab.tabId" :label="tab.label" :name="tab.name"
            :closable="tab.closable" tab="mainTab" style="padding: 0 10px;">
            <!--服务配置-->
            {{ tab.data.type  }}
            <sInfo ref="sInfo" v-if="tab.data.type == 'SINGLE'" :main="$parent" :data="tab" />
            <info ref="info" v-if="tab.data.type == 'CRUD'" :data="tab" />
          </tab-pane>
        </Tabs>
      </div>
    </Split>

    <Modal title="数据源配置" v-model="dataSource.isShowDataSource" ok-text="关闭" cancel-text="" width="900">
      <Datasource @change_datasource="changeDatasource"></Datasource>
    </Modal>

    <project ref="project" />

    <Modal v-model="createSelect" title="选择创建服务的类型" :footer-hide="true" width="500">
      在项目 <b>{{ project.name }}{{ project.parentServiceName ? '/' + project.parentServiceName : '' }}</b> 下新建……
      <div style="margin: 5% auto;width: 240px;">
        <Button type="primary" @click="addNewByTable">新建 CRUD 服务（从表中选择）</Button>
        <br />
        <br />
        <Button type="primary" @click="addNew">新建 CRUD 服务</Button>
        <br />
        <br />
        <Button type="primary" @click="addNew(true)">新建 自定义 SQL 服务</Button>
      </div>
    </Modal>

    <!--选择表格-->
    <Modal title="从数据库的表定义选择" v-model="isShowSelectTable" ok-text="保存" cancel-text="" width="900">
      <table-selector :dsid="1" :is-cross-db="dataSource.crossDb" :data-source-id="dataSource.id"></table-selector>
    </Modal>

    <!--               <Modal v-model="showFields" title="选择字段" cancel-text="" width="370">
                    <ul class="showFieldsList">
                      <li v-for="(v, k) in fields" :key="k">
                        <label>
                          <div class="fieldsComments">{{table.fieldsComments[k]}}</div>
                          <a @click="table.fieldsMapping[toField] = k; showFields = false;">{{k}}</a>
                        </label>
                      </li>
                    </ul>
                  </Modal> -->
  </div>

</template>

<script lang="ts" src="../data-service/data-service.ts"></script>

<style lang="less" scoped>
.data-service {
  height: 100%;
}

.split-pane-right {
  padding-left: 5px;
}

.content {
  border-top: 1px solid lightgray;
  padding: 1%;
}

.status-bar {
  position: absolute;
  bottom: 0;
  left: 0;
}

table.layout {
  border-collapse: collapse;
  border-spacing: 0;

  td {
    border-collapse: collapse;
    border-spacing: 0;
  }
}

.table-selector .ivu-table-body table {
  font-size: 10.5pt;
}

.table-selector {

  .ivu-page-next,
  .ivu-page-prev {
    padding-top: 7px;
  }
}
</style>
<style lang="less">
.http-method {
  padding: 0px 2px;
  margin-right: 5px;
  border-radius: 3px;
  font-size: 8px;
  vertical-align: middle;

  &.get {
    color: #3175fe;
    border: 1px solid #3175fe;
  }

  &.post {
    color: green;
    border: 1px solid green;
  }

  &.put {
    color: rgb(224, 60, 254);
    border: 1px solid rgb(224, 60, 254);
  }

  &.delete {
    color: red;
    border: 1px solid red;
  }
}
</style>