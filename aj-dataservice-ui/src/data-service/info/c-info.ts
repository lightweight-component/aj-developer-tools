import { codemirror } from "vue-codemirror";
import 'codemirror/lib/codemirror.css';
import 'codemirror/addon/display/autorefresh';
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/mode/sql/sql.js'
import 'codemirror/addon/selection/active-line.js'
import 'codemirror/addon/edit/closebrackets.js'
import 'codemirror/theme/monokai.css'// 编辑的主题文件
import 'codemirror/theme/base16-light.css'
import './code-prettify';
import tips from "@ajaxjs/ui/dist/iView-ext/tips.vue";
import api from "./api.vue";
import infoCommon from "./info-common";

export default {
    components: { codemirror, tips, api },
    mixins: [infoCommon],
    props: {
        data: {
            type: Object,
            required: true,
            default() {
                return {
                    data: {}
                };
            },
        },
        type: {
            type: String,
            required: false
        },
    },
    data() {
        debugger
        let data = this.data.data;
        let isSignle = data.type && data.type === 'SINGLE';

        return {
            isSignle: isSignle,
            currentData: data,
            editorData: {
                // 当前编辑器数据，根据不同类型的
                type: isSignle ? "sql" : "infoSql", isCustomSql: isSignle ? true : !!data.infoSql, sql: isSignle ? data.sql : data.infoSql
            },
            cmOption: {
                tabSize: 4,
                styleActiveLine: true,
                lineNumbers: true,
                mode: "text/x-mysql",
                autoRefresh: true, // 重点是这句，为true
                // theme: "monokai"
            },
        };
    },

    methods: {
        togglePanel(): void {
            let config = this.$el.querySelector(".config");

            if (config.style.height == "300px")
                config.style.height = "0";
            else config.style.height = "300px";
        },

        /**
         * 切换编辑器不同的类型
         * 
         * @param {String} type 
         */
        setEditorData(type: string): void {
            this.editorData.type = type;
            let sql = this.currentData[type];

            if (sql) {
                this.editorData.sql = sql;
                this.editorData.isCustomSql = true;
            } else
                this.editorData.sql = "";
        }
    },
    watch: {
        "editorData.isCustomSql"(v): void {
            if (v) {

            } else {
                this.editorData.sql = "";
                if (this.currentData[this.editorData.type])
                    this.currentData[this.editorData.type] = '__NULL_STRING__';
            }
        },
        // 同步到 data
        "editorData.sql"(v): void {
            this.currentData[this.editorData.type] = this.editorData.sql;
        }
    },
};

/**
 * 转义
 * @param s 
 * @returns 
 */
function to(s: string): string {
    if (s) {
        s = s.replace(/&/g, '&amp;');
        s = s.replace(/</g, '&lt;');
        s = s.replace(/>/g, '&gt;');
        s = s.replace(/ /g, '&nbsp;');
    }

    return s;
}
