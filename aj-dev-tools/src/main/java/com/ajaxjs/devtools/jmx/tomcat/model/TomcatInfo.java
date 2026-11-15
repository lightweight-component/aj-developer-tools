package com.ajaxjs.devtools.jmx.tomcat.model;


import java.util.List;

public class TomcatInfo {
    public List<Session> session;

//    public List<ThreadPool> threadPool;

    public ThreadPool threadPool;
    public SystemInfo systemInfo;

    public JvmInfo jvmInfo;
}
