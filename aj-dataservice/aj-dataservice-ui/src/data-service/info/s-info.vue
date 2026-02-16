<!-- Single 服务的编辑页面 -->
<template>
    <div class="s-info">
        <ul class="line-layout">
            <!--       <li>
            <i-Select v-model="datasource.id" placeholder="请选择数据源">
            <Option v-for="item in datasource.list" :key="item.id" :value="item.id">{{
                                                item.name }}</Option>
            </i-Select>
        </li> -->
            <li class="top_namespace" v-if="data.name.indexOf('/') != -1">
                {{ parentDir() }}/
            </li>

            <li>
                <i-input placeholder="相当于接口的 URL 目录，必填的" v-model="currentData.namespace" size="small">
                    <span slot="prepend">命名空间</span>
                </i-input>
            </li>
            <li>
                <i-input placeholder="接口的说明" v-model="currentData.name" size="small">
                    <span slot="prepend">说明</span>
                </i-input>
            </li>
            <!-- <li style="padding-top: 5px;">数据源： {{data.datasourceName}}</li> -->
        </ul>

        <br />

        <!-- SQL 源码编辑器-->
        <codemirror class="code-editor" ref="cm" v-model="currentData.sql" :options="cmOption"></codemirror>
        <div style="float:right">
            <a @click="formatSql">格式化</a> | <a @click="copySql">复制</a> | <label><input type="checkbox" /> 启用</label>
        </div>
        <br />
        <br />
        <div style="margin:20px 0">
            <Divider style="color:gray" size="small">API 接口</Divider>
            <api :api-url="getUrl()" />

            <div class="api-notes">
                请求方式可以是下列之中的任意一种：
                <ul>
                    <li>
                        获取单笔记录：<code>curl -X GET {{ getUrl() }}</code>
                    </li>
                    <li>
                        获取列表记录：<code>curl -X GET {{ getUrl() }}/list</code>
                    </li>
                    <li>
                        获取分页列表记录：<code>curl -X GET {{ getUrl() }}/page</code>
                    </li>
                    <li>
                        创建记录：<code>curl -X POST U{{ getApiPrefix() }} -F "key1=value1" -F "key2=value2"</code>
                    </li>
                    <li>
                        修改记录：<code>curl -X PUT  {{ getUrl() }} -F "key1=value1" -F "key2=value2"</code>
                    </li>
                    <li>
                        删除记录：<code>curl -X DELETE {{ getUrl() }}/{id}</code>
                    </li>
                </ul>
            </div>
        </div>
    </div>
</template>

<script src="./s-info.ts" lang="ts"></script>

<style lang="less" scoped>
.s-info {
    height: 100%;

    .top_namespace {
        padding-top: 5px;
        text-align: right;
        margin-right: 5px !important;
    }

    ul.line-layout {
        text-align: center;

        li {
            display: inline-block;
            width: 230px;
            vertical-align: top;
            line-height: 100%;
            margin-right: 20px;
        }
    }

    .code-editor {
        border: 1px solid #e3e3e3;
        margin-bottom: 20px;
    }
}

.api-notes {
    padding: 15px 0;

    ul {
        margin-top: 10px;
    }

    ul li {
        margin-left: 20px;
        list-style: disc;
        color: gray;
        margin-top: 8px;
    }
}

code {
    background-color: #eee;
    border: 1px solid lightgray;
    border-radius: 3px;
    padding: 0 5px;
    font-family: "Courier New", Courier, monospace;
    font-size: 0.95rem;
    color: black;
}
</style>