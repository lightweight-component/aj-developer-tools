/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.util.*;


/**
 * Records of (group, host, alert)
 *
 * @author xrao
 */
@Slf4j
public class AlertSubscribers {

    /**
     * 表示subscription相关的类型。
     */
    public static class Subscription {
        //lazy, not getter/setter
        /**
         * 保存group信息。
         */
        public String group; //server group
        /**
         * 保存host信息。
         */
        public String host;// server host, can be null
        /**
         * 保存alert name信息。
         */
        public String alertName; //alert name
        /**
         * 保存threshold信息。
         */
        public Float threshold; //if required. Null use default
        /**
         * 保存params集合。
         */
        public Map<String, String> params;

        /**
         * 创建Subscription对象。
         */
        public Subscription() {
            params = new HashMap<>();
        }

        //parse and set params

        /**
         * 设置params。
         *
         * @param paramString param string参数。
         */
        public void setParams(String paramString) {

            if (paramString == null || paramString.isEmpty())
                return;

            try (JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(paramString.getBytes()))) {
                JsonObject jsonObject = jsonReader.readObject();

                String thresholdString = jsonObject.getString("threshold", null);

                if (thresholdString != null && !thresholdString.isEmpty())
                    try {
                        threshold = Float.parseFloat(thresholdString);
                    } catch (Exception ex) {
                    }

                JsonArray paramList = jsonObject.getJsonArray("params");

                if (paramList != null) {
                    int mlen = paramList.size();

                    for (int i = 0; i < mlen; i++) {
                        JsonObject mobj = paramList.getJsonObject(i);
                        params.put(mobj.getString("name"),
                                mobj.getString("value"));
                    }
                }

            } catch (Exception ex) {
                log.warn("Error to parse alert subscription params: " + paramString, ex);
            }

        }

        /**
         * 执行param to json操作。
         *
         * @return 处理结果。
         */
        public String paramToJSON() {

            if ((this.params == null || this.params.size() == 0) && this.threshold == null)
                return null;

            StringBuilder mtrStr = new StringBuilder();
            mtrStr.append("{\r\n");

            if (this.threshold != null)
                mtrStr.append("\"threshold\": \"").append(threshold).append("\",\r\n");

            mtrStr.append("\"params\":[");
            int cnt = 0;

            for (Map.Entry<String, String> e : this.params.entrySet()) {

                if (cnt > 0)
                    mtrStr.append(",\r\n");

                mtrStr.append("{\"name\": \"").append(e.getKey()).append("\",\"value\":\"")
                        .append(e.getValue())
                        .append("\"}");
            }

            mtrStr.append("]}");
            return mtrStr.toString();
        }
    }

    /**
     * 表示host subscriptions相关的类型。
     */
    public static class HostSubscriptions {
        /**
         * 保存subscriptions集合。
         */
        @Getter
        private List<Subscription> subscriptions;//host level subscription
        /**
         * 保存subscriptions map集合。
         */
        private Map<String, Subscription> subscriptionsMap; //internal index

        /**
         * 创建HostSubscriptions对象。
         */
        public HostSubscriptions() {
            subscriptions = new ArrayList<>();
            subscriptionsMap = new HashMap<>();
        }

        /**
         * 添加subscription。
         *
         * @param sub sub参数。
         */
        public void addSubscription(Subscription sub) {
            this.subscriptions.add(sub);
            this.subscriptionsMap.put(sub.alertName, sub);
        }

        /**
         * 删除subscription。
         *
         * @param sub sub参数。
         */
        public void deleteSubscription(Subscription sub) {

            if (sub == null)
                return;

            for (int i = this.subscriptions.size() - 1; i >= 0; i--) {
                Subscription sub2 = this.subscriptions.get(i);

                if (sub2.alertName.equals(sub.alertName)) {
                    this.subscriptions.remove(i);
                    this.subscriptionsMap.remove(sub.alertName);
                    break;
                }
            }
        }

        /**
         * 获取subscription。
         *
         * @param alertName alert name参数。
         * @return 处理结果。
         */
        public Subscription getSubscription(String alertName) {
            return this.subscriptionsMap.get(alertName);
        }

    }

    /**
     * 表示group subscriptions相关的类型。
     */
    public static class GroupSubscriptions {
        /**
         * 保存subscriptions集合。
         */
        private List<Subscription> subscriptions;//group level subscription
        /**
         * 保存hosts subscriptions集合。
         */
        private Map<String, HostSubscriptions> hostsSubscriptions;//key is the hostname
        /**
         * 保存subscriptions map集合。
         */
        private Map<String, Subscription> subscriptionsMap; //internal index

        /**
         * 创建GroupSubscriptions对象。
         */
        public GroupSubscriptions() {
            subscriptions = new ArrayList<>();
            hostsSubscriptions = new HashMap<>();
            subscriptionsMap = new HashMap<>();
        }

        /**
         * 添加subscription。
         *
         * @param sub sub参数。
         */
        public void addSubscription(Subscription sub) {

            if (sub == null)
                return;

            if (sub.host == null || sub.host.isEmpty())
                this.subscriptions.add(sub);
            else {

                if (!this.hostsSubscriptions.containsKey(sub.host))
                    this.hostsSubscriptions.put(sub.host, new HostSubscriptions());

                this.hostsSubscriptions.get(sub.host).addSubscription(sub);
                this.subscriptionsMap.put(sub.alertName, sub);
            }
        }

        /**
         * 删除subscription。
         *
         * @param sub sub参数。
         */
        public void deleteSubscription(Subscription sub) {

            if (sub == null)
                return;

            if (sub.host == null || sub.host.isEmpty())
                for (int i = this.subscriptions.size() - 1; i >= 0; i--) {
                    Subscription sub2 = this.subscriptions.get(i);

                    if (sub2.alertName.equals(sub.alertName)) {
                        this.subscriptions.remove(i);
                        this.subscriptionsMap.remove(sub.alertName);
                        break;
                    }
                }

            else if (this.hostsSubscriptions.containsKey(sub.host))
                this.hostsSubscriptions.get(sub.host).deleteSubscription(sub);

        }

        /**
         * 获取subscription。
         *
         * @param host      host参数。
         * @param alertName alert name参数。
         * @return 处理结果。
         */
        public Subscription getSubscription(String host, String alertName) {
            Subscription sub = null;

            if (this.hostsSubscriptions.containsKey(host))
                sub = this.hostsSubscriptions.get(host).getSubscription(alertName);

            if (sub == null)
                sub = this.subscriptionsMap.get(alertName);

            return sub;
        }

        /**
         * 获取subscription。
         *
         * @param host host参数。
         * @return 处理结果。
         */
        public List<Subscription> getSubscription(String host) {
            HashSet<String> alertnames = new HashSet<>();
            List<Subscription> mysubs = new ArrayList<>();

            if (this.hostsSubscriptions.containsKey(host))
                for (Subscription sub : this.hostsSubscriptions.get(host).getSubscriptions()) {
                    mysubs.add(sub);
                    alertnames.add(sub.alertName);
                }

            //group level
            for (Subscription sub : subscriptions)
                if (!alertnames.contains(sub.alertName)) {
                    mysubs.add(sub);
                    alertnames.add(sub.alertName);
                }

            return mysubs;
        }
    }

    /**
     * 保存group subscriptions集合。
     */
    private Map<String, GroupSubscriptions> groupSubscriptions;//all subscriptions, grouped by server group
    /**
     * 保存context信息。
     */
    @Getter
    @Setter
    private MyPerfContext context; //framework context to access metrics db

    /**
     * 创建AlertSubscribers对象。
     */
    public AlertSubscribers() {
        groupSubscriptions = new HashMap<>();
    }

    /**
     * Load subscriptions from DB. Return false if failed.
     *
     * @return
     */
    public boolean load() {

        if (this.context.getMetricDb() != null)
            try {
                List<Subscription> subs = this.context.getMetricDb().loadAlertSubscriptions();
                synchronized (this.groupSubscriptions) {
                    this.groupSubscriptions.clear();//remove old data

                    for (Subscription sub : subs) {
                        String group = sub.group;

                        if (!this.groupSubscriptions.containsKey(group))
                            this.groupSubscriptions.put(group, new GroupSubscriptions());

                        this.groupSubscriptions.get(group).addSubscription(sub);
                    }
                }
                return true;
            } catch (Exception ex) {
                log.warn("Failed to load alert subscriptions", ex);
                return false;
            }

        return false;
    }

    /**
     * 添加subscription。
     *
     * @param sub sub参数。
     * @return 是否满足条件。
     */
    public boolean addSubscription(Subscription sub) {

        if (sub == null || sub.group == null || sub.group.isEmpty()
                || sub.alertName == null || sub.alertName.isEmpty()) return false;

        boolean status = this.context.getMetricDb() != null ?
                this.context.getMetricDb().upsertAlertSubscription(sub) : false;

        if (!status)
            return false;

        synchronized (this.groupSubscriptions) {
            String group = sub.group;

            if (!this.groupSubscriptions.containsKey(group))
                this.groupSubscriptions.put(group, new GroupSubscriptions());

            this.groupSubscriptions.get(group).addSubscription(sub);
        }
        return true;
    }

    /**
     * 删除subscription。
     *
     * @param sub sub参数。
     * @return 是否满足条件。
     */
    public boolean deleteSubscription(Subscription sub) {
        boolean status = this.context.getMetricDb() != null ?
                this.context.getMetricDb().deleteAlertSubscription(sub) : false;

        if (!status)
            return false;

        synchronized (this.groupSubscriptions) {
            String group = sub.group;

            if (this.groupSubscriptions.containsKey(group))
                this.groupSubscriptions.get(group).deleteSubscription(sub);
        }
        return true;
    }

    /**
     * Check if a server has subscribed an alert
     *
     * @param group
     * @param host
     * @param alertName
     * @return
     */
    public Subscription getSubscription(String group, String host, String alertName) {
        synchronized (this.groupSubscriptions) {

            if (!this.groupSubscriptions.containsKey(group))
                return null;

            return this.groupSubscriptions.get(group).getSubscription(host, alertName);
        }
    }

    /**
     * 获取subscriptions。
     *
     * @param group group参数。
     * @param host  host参数。
     * @return 处理结果。
     */
    public List<Subscription> getSubscriptions(String group, String host) {
        synchronized (this.groupSubscriptions) {

            if (!this.groupSubscriptions.containsKey(group))
                return null;

            return this.groupSubscriptions.get(group).getSubscription(host);
        }
    }
}
