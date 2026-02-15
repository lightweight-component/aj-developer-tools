import topToolabar from "./toolbar/top-toolbar.vue";
import toolbar from "./toolbar/toolbar.vue";
import leftTree from "./tree/tree.vue";
import tips from "@ajaxjs/ui/dist/iView-ext/tips.vue";
import tableSelector from "@ajaxjs/ui/dist/table-selector/table-selector.vue";
import info from "./info/info.vue";
import sInfo from "./info/s-info.vue";
import project from "./project/project.vue";
import Datasource from "@ajaxjs/ui/dist/data-source/data-source.vue";
import { isDev, isDebug } from '@ajaxjs/util/dist/util/utils';

// 新建 tab 的 index
let NEW_TAB: number = 1;

export default {
    components: { topToolabar, info, tips, tableSelector, Datasource, project, toolbar, leftTree, sInfo },
    props: {
        enableDatasource: { type: Boolean, default: false }
    },
    data() {
        return {
            isShowSelectTable: false,
            createSelect: false, // 是否显示创建选择的类型
            split1: 0.2,
            dataSource: {
                isShowDataSource: false,
                isMulti: true, // 是否多个数据源
                id: 1,
                name: null,
                crossDb: false
            },
            activeTab: "index",
            activeTabData: null as TabCmp,// the tab cmp that actives
            mainTabs: [
                // {
                //     label: "数据服务",
                //     name: "tab1",
                //     closable: true,
                //     index: 0
                // },
            ] as TabCmp[],
            table: {
                createRule: {},
                fieldsMapping: {
                }
            },
            showFields: false,
            fields: [],
            dmlSelected: null, // 命令选中高亮显示
            project: {
                name: '',
                parentServiceName: '', // 如果选择了父服务节点，显示其名字
                parentId: -1,
                parentNode: null,
                treeData: []
            }
        }
    },
    methods: {
        /**
         * 打开 tab
         * 
         * @param a 
         * @param _data 
         * @returns 
         */
        openTab(a: any, _data: any): void {
            if (_data.projectData)
                return;

            let data: DS_TreeNode_Service = <DS_TreeNode_Service>_data;
            let name: string = data.title + ' ' + data.id;
            let hasTab: TabCmp | null = null; // looks for if exists
            let mainTabs: TabCmp[] = this.mainTabs;

            mainTabs.forEach((tab: TabCmp) => {
                if (tab.name === name)
                    hasTab = tab;
            });
            let label: string = name; // tab label 太长

            // if (label.length > 10)
            //     label = label.substring(0, 25) + '...';

            if (!hasTab) {
                hasTab = { tabId: NEW_TAB++, label, name, closable: true, data: data.data, apiPrefix: this._getApiPrefix(name, data), isCreate: false };
                mainTabs.push(hasTab);
            }

            setTimeout(() => {
                this.activeTab = name;
                this.activeTabData = hasTab;
            }, 100);
        },

        _getApiPrefix(name: string, treeData: DS_TreeNode_Service): string {
            let obj;

            if (name.indexOf('/') != -1)
                obj = treeData.parentNode.parentNode;
            else
                obj = treeData.parentNode;

            return isDev() ? obj.apiPrefixDev : obj.apiPrefixProd;
        },

        /**
         * 新建
         * 
         * @param isCustomSQL 是否自定义 SQL
         */
        addNew(isCustomSQL: boolean) {
            let parentNode = this.project.parentNode;
            let hasParent: boolean = !!parentNode.parentNode; // 是否有父节点

            if (hasParent)
                parentNode = parentNode.parentNode;

            let data = {
                type: isCustomSQL ? 'SINGLE' : "CRUD",
                namespace: '',
            };

            let name: string = '新建服务-' + NEW_TAB++;
            let newTab: TabCmp = {
                tabId: NEW_TAB++, label: name, name, closable: true, data: data,
                apiPrefix: isDev() ? parentNode.apiPrefixDev : parentNode.apiPrefixProd, isCreate: true
            };
   
            this.mainTabs.push(newTab);

            this.activeTab = name;
            this.activeTabData = newTab;
            this.createSelect = false;
        },

        addNewByTable(): void {
            this.isShowSelectTable = true;
            this.createSelect = false;
        },

        /**
         * Toggole when tab clicks
         * 
         * @param tabName 
         */
        onTabClick(tabName: string): void {
            this.activeTab = tabName;

            for (let i = 0; i < this.mainTabs.length; i++) {
                if (this.mainTabs[i].name === tabName) {
                    this.activeTabData = this.mainTabs[i];

                    break;
                }
            }
        },
        onTabClose(tabName: string): void {
            let index = -1;

            for (let i = 0; i < this.mainTabs.length; i++) {
                if (this.mainTabs[i].name === tabName) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                this.$delete(this.mainTabs, index);

                if (this.mainTabs[0])
                    this.activeTab = this.mainTabs[0].name;
                else
                    this.activeTab = 'index';
            }
        },
        changeDatasource(ds: any): void {
            this.dataSource.id = ds.id;
            this.dataSource.name = ds.name;
            this.dataSource.crossDb = ds.crossDb;
            this.dataSource.isShowDataSource = false;
        },

        /**
         * 获取当前工程的 API 前缀
         */
        getCurrentApiPrefix(): string {
            let current = this.activeTabData;

            if (!current) {
                this.$Message.warning('请先选择一个 tab');
                return null;
            }

            let project;

            if (current.id.indexOf('/') != -1)
                project = current.parentNode.parentNode;
            else
                project = current.parentNode;

            let prefix: string = isDebug() ? project.apiPrefixDev : project.apiPrefixProd;

            return prefix;
        },

        /**
         * 删除服务
         */
        del(e: MouseEvent): void {
        }
    }
};