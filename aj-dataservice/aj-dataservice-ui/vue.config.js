const path = require('path');

module.exports = {
    publicPath: './', // vue_cli 打包后的项目，默认使用的是绝对路径。改为相对路径
    runtimeCompiler: true,
    indexPath: "index.html",
    pluginOptions: {
        'style-resources-loader': {
            preProcessor: 'less',
            patterns: [path.resolve(__dirname, '.\\node_modules\\@ajaxjs\\util\\dist\\style\\common-functions.less')]
        }
    },
    lintOnSave: true,
    devServer: {
        proxy: {
            '/login': {
                target: 'http://local.ajaxjs.com', // 后端服务的实际地址
                changeOrigin: true,                       // 如果目标是一个域名而不是IP，请设置为true
                pathRewrite: {
                    '^/login': ''                             // 重写路径，比如将 /api/v1/test -> /v1/test
                }
            },
            '/api': {
                target: 'http://local.ajaxjs.com/dataservice_api', // 后端服务的实际地址
                changeOrigin: true,                       // 如果目标是一个域名而不是IP，请设置为true
                pathRewrite: {
                    '^/api': ''                             // 重写路径，比如将 /api/v1/test -> /v1/test
                }
            },
            '/iam_api': {
                target: 'http://local.ajaxjs.com/iam_api', // 后端服务的实际地址
                changeOrigin: true,                       // 如果目标是一个域名而不是IP，请设置为true
                pathRewrite: {
                    '^/iam_api': ''                             // 重写路径，比如将 /api/v1/test -> /v1/test
                }
            }
        },
        overlay: {
            warnings: true,
            error: true
        }
    },
    chainWebpack: config => {
        config.module.rule('images').set('parser', {
            dataUrlCondition: {
                maxSize: 15 * 1024 // 4KiB 内联文件的大小限制
            }
        });
    }
};