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
import api from "./api.vue";
import infoCommon from "./info-common";

export default {
    components: { codemirror, api },
    mixins: [infoCommon],
    data() {
        return {
            currentData: this.data.data
        };
    },
    methods: {

    }
}