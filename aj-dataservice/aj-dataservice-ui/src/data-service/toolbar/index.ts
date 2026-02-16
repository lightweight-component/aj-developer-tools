import { xhr_get, xhr_post, xhr_put, xhr_del } from '@ajaxjs/util/dist/util/xhr';
import { isDebug } from '@ajaxjs/util/dist/util/utils';

export default {
    props: {
        activeTab: String
    },
    data() {
        return {};
    },
    methods: {
        createService(): void {
            let prefix = this.getSelectedProject();

            if (prefix)
                this.$parent.$parent.createSelect = true;
        },
        refreshConfig(): void {
            let prefix = this.getSelectedProject();

            if (prefix)
                xhr_get(`${prefix}/common_api/reload_config`, (j: RepsonseResult) => {
                    if (j.status)
                        this.$Message.success('刷新成功');
                });
        },

        /**
         * 获取当前树选中的工程
         */
        getSelectedProject(): string {
            let selectedNodes: any[] = this.$parent.$parent.$refs.leftTreeCmp.$refs.treeCmp.getSelectedNodes();

            if (selectedNodes.length === 0) {
                this.$Message.warning('请先选择一个树节点，<br >选择一个项目或者一个服务。');
                return null;
            }

            let selectedNode = selectedNodes[0];
            let parentNode = selectedNode.parentNode;

            let project = this.$parent.$parent.project;
            project.parentServiceName = '';
            project.parentId = -1;

            if (parentNode) {
                if (parentNode.parentNode) { // sub node
                    project.parentServiceName = parentNode.data.name;
                    project.parentId = parentNode.data.id;
                    project.parentNode = parentNode.data;
                    parentNode = parentNode.parentNode;
                } else { // level 1 node
                    project.parentServiceName = selectedNode.data.name;
                    project.parentId = selectedNode.data.id;
                    project.parentNode = selectedNode;
                }
            } else { // project node
                parentNode = selectedNode.projectData;
                project.parentNode = parentNode;
            }

            project.name = parentNode.name;

            return isDebug() ? parentNode.apiPrefixDev : parentNode.apiPrefixProd;
        },
        refreshTree(): void {
            this.$parent.$parent.$refs.leftTreeCmp.refreshTree();
        },
        save(): void {
            let tabCmp = this.$parent.$parent.activeTabData.tabCmp;
            tabCmp.save();
        }
    }
};