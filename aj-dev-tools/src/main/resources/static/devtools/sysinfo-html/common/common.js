/* Local compatibility helpers used by the sysinfo pages. */
(function (global) {
    'use strict';
    const aj = global.aj = global.aj || {};
    const context = location.pathname.split('/devtools/')[0];
    function request(method, url, body, callback, config) {
        config = config || {};
        const xhr = new XMLHttpRequest();
        const target = url.startsWith('/') && !url.startsWith('//') ? context + url : url;
        xhr.open(method, target, true);
        Object.entries(config.header || {}).forEach(([name, value]) => xhr.setRequestHeader(name, value));
        function fail(error) {
            if (config.onError) config.onError(error, xhr);
            else global.alert(error.message);
        }
        xhr.onload = function () {
            if (xhr.status < 200 || xhr.status >= 300) {
                fail(new Error('请求失败：HTTP ' + xhr.status + ' (' + url + ')'));
                return;
            }
            let data;
            try {
                if (config.parseContentType === 'text') data = xhr.responseText;
                else if (config.parseContentType === 'xml') data = xhr.responseXML;
                else data = xhr.responseText.trim() ? JSON.parse(xhr.responseText) : null;
            } catch (error) { fail(new Error('响应解析失败：' + error.message)); return; }
            if (data && (data.status === false || data.success === false)) {
                fail(new Error(data.message || '操作失败'));
                return;
            }
            if (callback) callback(data, xhr);
        };
        xhr.onerror = () => fail(new Error('网络请求失败：' + url));
        xhr.ontimeout = () => fail(new Error('请求超时：' + url));
        xhr.timeout = config.timeout || 30000;
        xhr.send(body == null ? null : body);
        return xhr;
    }
    function form(method, url, params, callback, config) {
        const multipart = params instanceof FormData;
        config = Object.assign({}, config);
        config.header = Object.assign({}, config.header);
        if (!multipart) config.header['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
        return request(method, url, multipart || typeof params === 'string' ? params : new URLSearchParams(params || {}).toString(), callback, config);
    }
    function json(method, url, params, callback, config) {
        config = Object.assign({}, config);
        config.header = Object.assign({}, config.header, {'Content-Type': 'application/json;charset=UTF-8'});
        return request(method, url, typeof params === 'string' ? params : JSON.stringify(params), callback, config);
    }
    aj.xhr = {
        get: (url, cb, cfg) => request('GET', url, null, cb, cfg),
        postForm: (url, data, cb, cfg) => form('POST', url, data, cb, cfg),
        putForm: (url, data, cb, cfg) => form('PUT', url, data, cb, cfg),
        postJson: (url, data, cb, cfg) => json('POST', url, data, cb, cfg),
        putJson: (url, data, cb, cfg) => json('PUT', url, data, cb, cfg),
        del: (url, cb, cfg) => request('DELETE', url, null, cb, cfg),
        formData: form => Object.fromEntries(new FormData(typeof form === 'string' ? document.querySelector(form) : form))
    };
    aj.copyToClipboard = text => navigator.clipboard.writeText(String(text));
})(window);
