package com.ajaxjs.devtools.sysmonitor;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class TestOshiSystem {
    public static String prettyJson(String json) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine scriptEngine = manager.getEngineByName("JavaScript");
        scriptEngine.put("jsonString", json);

        try {
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)");
        } catch (ScriptException e) {
            e.printStackTrace();
        }

        return (String) scriptEngine.get("result");
    }
}
