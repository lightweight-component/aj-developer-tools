import { xhr_get, xhr_del } from '@ajaxjs/util/dist/util/xhr';
import { isDebug } from '@ajaxjs/util/dist/util/utils';

declare const window: Window & {
    config: ConfigInterface;
};

export default {
    data() {
        return {
            treeData: [],
            isProjectNode: false, // 鼠标右键菜单用的
        };
    },

    created(): void {
        this.refreshTree();
    },
    methods: {
        handleContextMenu(data: any): void {
            if (!data.parentNode) {// it's a project
                this.isProjectNode = true;
                let { id, name, content, apiPrefixDev, apiPrefixProd, defaultConfig } = data.projectData;

                let project = this.$parent.$parent.$refs.project;
                project.$refs['editForm'].resetFields();
                project.data = { id, name, content, apiPrefixDev, apiPrefixProd, defaultConfig};
            } else
                this.isProjectNode = false;
        },
        /**
         * 更新根节点的数据
         */
        refreshTree(): void {
            this.loadTreeData(null, data => this.treeData = data);
        },

        // 异步加载树数据
        loadTreeData(item: null, callback: Function): void {
            xhr_get(`/api/common_api/project/list`, (j: RepsonseResult) => {
                if (j.status) {
                    let data: DS_TreeNode_Project[] = [];

                    j.data.forEach((project: DataService_Porject) => {
                        let projectTreeNode: DS_TreeNode_Project = {
                            title: project.name,
                            loading: false,
                            expand: true,
                            children: [],
                            contextmenu: true,
                            projectData: project,
                            render: renderProjectTreeNode
                        };

                        this.loadTreeProejct(isDebug() ? project.apiPrefixDev : project.apiPrefixProd, projectTreeNode)

                        data.push(projectTreeNode);
                    });

                    callback(data);
                }
            });
        },

        /**
         * 加载服务列表
         * 
         * @param apiPrefix 
         * @param projectTreeNode 
         */
        loadTreeProejct(apiPrefix: string, projectTreeNode: DS_TreeNode_Project): void {
            xhr_get(`/api/common_api/common_api/list`, (j: RepsonseResult) => {
                if (j.status) {
                    let data: DS_TreeNode_Service[] = [];

                    // 添加 iView 树节点的字段
                    j.data.forEach((item) => {
                        let title: string = item.name || item.namespace;
                        let id: string = projectTreeNode.title + ':' + item.namespace;
                        let node: DS_TreeNode_Service = {
                            title: title,
                            contextmenu: true,
                            data: item,
                            id: id,
                            isCreate: false,
                            parentNode: projectTreeNode.projectData,
                            render: renderCrudTreeNode
                        };

                        if (item.children) {
                            node.expand = true;
                            node.children = [];

                            item.children.forEach(item2 => {
                                let title: string = item2.name || item2.namespace;

                                node.children.push({
                                    title: title,
                                    contextmenu: true,
                                    data: item2,
                                    id: id + '/' + item2.namespace,
                                    isCreate: false,
                                    parentNode: node,
                                    render: renderCrudTreeNode
                                });
                            });
                        }

                        data.push(node);
                    });

                    projectTreeNode.children = data;
                }
            });
        }
    }
};

const renderProjectTreeNode = (h: Function, { root, node, data }) => {
    return [
        h("span", { class: "http-method get" }, "P"),
        h("span", { style: 'font-weight:bold' }, data.title),
    ];
};

const renderCrudTreeNode = (h: Function, { root, node, data }) => {
    if (data.data.type && 'SINGLE' === data.data.type)
        return [
            h("span", { class: "http-method put" }, 'S'),
            h("span", data.title),
        ];
    else
        return [
            h("span", { class: "http-method post" }, 'C'),
            h("span", data.title),
        ];
};