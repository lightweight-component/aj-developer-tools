import { xhr_get, xhr_post, xhr_put, xhr_del } from '@ajaxjs/util/dist/util/xhr';

// 声明 window.config 并为其指定类型
declare const window: Window & {
    config: ConfigInterface;
};

export default {
    data() {
        return {
            loading: true,
            isShow: false,
            isCreate: true,
            rules: {
                name: [
                    { required: true, message: '项目名称不能为空', trigger: 'blur' }
                ],
                apiPrefixDev: [
                    { required: true, message: 'API 前缀-开发阶段 不能为空', trigger: 'blur' }
                ],
                apiPrefixProd: [
                    { required: true, message: 'API 前缀-生产环境 不能为空', trigger: 'blur' }
                ],
            },
            data: {} as DataService_Porject
        };
    },
    methods: {
        saveProject(): void {
            this.loading = false;

            this.$refs['editForm'].validate((valid: boolean) => {
                if (valid) {
                    if (this.isCreate) {
                        xhr_post(`${window.config.dsApiRoot}/common_api/project`, (j: RepsonseResult) => {
                            if (j.status) {
                                this.$Message.success('创建项目成功');
                                this.$parent.refreshTree();
                            } else
                                this.$Message.error(j.message);

                            this.loading = true;
                        }, this.data);
                    } else {
                        xhr_put(`${window.config.dsApiRoot}/common_api/project`, (j: RepsonseResult) => {
                            if (j.status) {
                                this.$Message.success('修改项目成功');
                                this.$parent.refreshTree();
                            }
                            else
                                this.$Message.error(j.message);

                            this.loading = true;
                        }, this.data);
                    }
                } 
                else
                    this.loading = false;
            });
        },
        update(): void {
            this.isCreate = false;
            this.isShow = true;
        },

        create(): void {
            this.isCreate = true;
            this.isShow = true;
            this.data = {};
        },

        deletePorject(): void {
            this.$Modal.confirm({
                title: '确定删除吗？',
                content: `<p>删除<b>工程 ${this.data.name}</b> 以及其所有的<b>服务</b>？</p>`,
                onOk: () => {
                    xhr_del(`/api/common_api/project/${this.data.id}`, (j: RepsonseResult) => {
                        if (j.status) {
                            this.$Message.info('删除成功');
                            this.$parent.refreshTree();
                        }
                    });
                }
            });
        },
    }
}