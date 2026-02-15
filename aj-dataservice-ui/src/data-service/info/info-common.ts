import { copyToClipboard, isDev } from '@ajaxjs/util/dist/util/utils';
import { formatSql } from './format-sql.js';
import { xhr_post, xhr_put, xhr_del } from '@ajaxjs/util/dist/util/xhr';

export default {
    props: {
        data: { type: Object, required: true },
        main: { type: Object }
    },
    // data() {
    //     return {
    //         cmOption: {
    //             tabSize: 4,
    //             styleActiveLine: true,
    //             lineNumbers: true,
    //             mode: "text/x-mysql",
    //             autoRefresh: true, // 重点是这句，为true
    //             // theme: "monokai"
    //         },
    //     };
    // },
    mounted(): void {
        setTimeout(() => this.$refs.cm && this.$refs.cm.refresh(), 500);// 加载codemirror编辑器必须点击一下代码才能正常显示并且代码向左偏移
        this.data.tabCmp = this;
    },
    methods: {
        copySql(): void {
            copyToClipboard(this.editorData.sql);
            this.$Message.success("复制 SQL 代码成功");
        },
        formatSql(): void {
            this.editorData.sql = formatSql(this.editorData.sql, null);
        },
        getApiPrefix(): string {
            let url: string = this.data.apiPrefix;

            if (!url.endsWith("/"))
                url += "/";
            url += "common_api/";

            return url;
        },

        getUrl(isPage: boolean): string {
            // debugger;
            // if (data.parentNode.apiPrefixDev) {
            //   // project node
            // } else url = data.parentNode.data.namespace + "/" + url;
            let url = this.getApiPrefix() + this.data.data.namespace;

            if (isPage) url += "/page";
            else {
                if (this.$parent.editorData) {
                    switch (this.$parent.editorData.type) {
                        case "info":
                        case "delete":
                            url += "/{id}";
                            break;
                        case "list":
                            url += "/list";
                            break;
                        case "create":
                        case "update":
                    }
                } else {
                }
            }

            return url;
        },
        parentDir(): string {
            let dir: string = this.data.name.split('/')[0];

            if (dir.indexOf(':') != -1) {
                let arr = dir.split(':');
                dir = arr.pop();
            }

            return dir;
        },

        /**
         * 保存服务
         */
        save(): void {
            let dml = Object.assign({}, this.currentData);

            delete dml.createDate;
            delete dml.children;
            delete dml.updateDate;

            for (let i in dml) {
                let v = dml[i];
                if (v === null)
                    delete dml[i];

                if (v === true)
                    dml[i] = 1;
                else if (v === false)
                    dml[i] = 0;
            }

            // let prefix: string = this.getCurrentApiPrefix();
            let prefix: string;
            debugger

            if (this.data.isCreate) {
                xhr_post(`${prefix}/common_api/common_api`, (j: RepsonseResult) => {
                    if (j.status) {
                        this.$Message.success('创建命令成功');
                        // this.$refs.leftTreeCmp.refreshTree();

                        let newlyId: number = j.data;
                        this.currentData.id = newlyId;
                        this.data.isCreate = false;

                        let parentNode = this.project.parentNode.data;
                        let hasParent: boolean = !!parentNode; // 是否有父节点
                        let projectName: string = this.project.name;

                        // if (hasParent) {
                        //     current.id = projectName + ":" + parentNode.namespace + "/" + dml.namespace;
                        // } else
                        //     current.id = projectName + ":" + dml.namespace;

                        // // 获取 tab
                        // for (let i = 0; i < this.mainTabs.length; i++) {
                        //     let tab: any = this.mainTabs[i];

                        //     if (tab.name === this.activeTab) {

                        //         tab.name = current.id;
                        //         // this.$set(tab, 'label', name);
                        //         this.activeTab = current.id;
                        //         break;
                        //     }
                        // }

                        // iview tab 改名会导致 tab 内容消失，于是改为关闭了再打开
                        // this.onTabClose(this.activeTab);
                    } else
                        this.$Message.error(j.message);
                }, dml);
            } else
                xhr_put(`${prefix}/common_api/common_api`, (j: RepsonseResult) => {
                    if (j.status)
                        this.$Message.success('修改命令成功');
                    else
                        this.$Message.error(j.message);
                }, dml);
        },
        /**
         * 删除服务
         */
        del(e: MouseEvent): void {
            let li: HTMLElement = e.currentTarget as HTMLElement;

            if (li.classList.contains('disabled'))
                return;

            let current = this.activeTabData;
            let prefix: string | null = this.getCurrentApiPrefix();

            if (prefix) {
                this.$Modal.confirm({
                    title: '删除服务',
                    content: '<p>确定删除 ' + current.data.name + ' 这个服务吗？</p>',
                    onOk: () => {
                        xhr_del(`${prefix}/common_api/common_api/${current.data.id}`, (j: RepsonseResult) => {
                            if (j.status) {
                                this.$Message.success('删除成功');
                                this.onTabClose(this.activeTab);
                                this.activeTab = 'index';
                                this.activeTabData = null;
                                this.refreshTree();
                            } else
                                this.$Message.warning(j.message || '获取数据失败');
                        });
                    }
                });
            }
        }


    }
};