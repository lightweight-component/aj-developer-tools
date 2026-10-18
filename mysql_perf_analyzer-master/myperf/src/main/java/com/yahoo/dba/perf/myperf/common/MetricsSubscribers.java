/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;


/**
 * Records of (group, host, metrics group, metrics subsgroup)
 *
 * @author xrao
 */
@Slf4j
public class MetricsSubscribers {

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
         * 保存m group信息。
         */
        public String mGroup; //metrics group name
        /**
         * 保存m sub group信息。
         */
        public String mSubGroup; //metrics subgroup name

        /**
         * 创建Subscription对象。
         */
        public Subscription() {
        }
    }

    /**
     * 表示host subscriptions相关的类型。
     */
    public static class HostSubscriptions {
        /**
         * 保存subscriptions集合。
         */
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
            String key = sub.mSubGroup != null ? sub.mGroup + "." + sub.mSubGroup : sub.mGroup;
            this.subscriptionsMap.put(key, sub);
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

                if (sub2.mGroup.equals(sub.mGroup)
                        && ((sub2.mSubGroup == null && sub.mSubGroup == null)
                        || (sub2.mSubGroup != null && sub2.mSubGroup.equals(sub.mSubGroup)))
                ) {
                    this.subscriptions.remove(i);
                    String key = sub.mSubGroup != null ? sub.mGroup + "." + sub.mSubGroup : sub.mGroup;
                    this.subscriptionsMap.remove(key);
                    break;
                }
            }

        }

        /**
         * 获取subscription。
         *
         * @param mGroup    m group参数。
         * @param mSubGroup m sub group参数。
         * @return 处理结果。
         */
        public Subscription getSubscription(String mGroup, String mSubGroup) {
            String key = mSubGroup != null ? mGroup + "." + mSubGroup : mGroup;
            return this.subscriptionsMap.get(key);
        }

        /**
         * 获取subscribed udms。
         *
         * @return 处理结果。
         */
        public List<Subscription> getSubscribedUDMs() {
            List<Subscription> subs = new ArrayList<>();

            for (Subscription sub : this.subscriptions)
                if ("UDM".equals(sub.mGroup))
                    subs.add(sub);

            return subs;
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

            if (sub.host == null || sub.host.isEmpty()) {
                this.subscriptions.add(sub);
                String key = sub.mSubGroup != null ? sub.mGroup + "." + sub.mSubGroup : sub.mGroup;
                this.subscriptionsMap.put(key, sub);
            } else {

                if (!this.hostsSubscriptions.containsKey(sub.host))
                    this.hostsSubscriptions.put(sub.host, new HostSubscriptions());

                this.hostsSubscriptions.get(sub.host).addSubscription(sub);
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

                    if (sub2.mGroup.equals(sub.mGroup)
                            && ((sub2.mSubGroup == null && sub.mSubGroup == null)
                            || (sub2.mSubGroup != null && sub2.mSubGroup.equals(sub.mSubGroup)))
                    ) {
                        this.subscriptions.remove(i);
                        String key = sub.mSubGroup != null ? sub.mGroup + "." + sub.mSubGroup : sub.mGroup;
                        this.subscriptionsMap.remove(key);
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
         * @param mGroup    m group参数。
         * @param mSubGroup m sub group参数。
         * @return 处理结果。
         */
        public Subscription getSubscription(String host, String mGroup, String mSubGroup) {
            Subscription sub = null;

            if (this.hostsSubscriptions.containsKey(host))
                sub = this.hostsSubscriptions.get(host).getSubscription(mGroup, mSubGroup);

            if (sub == null) {
                String key = mSubGroup != null ? mGroup + "." + mSubGroup : mGroup;
                sub = this.subscriptionsMap.get(key);
            }

            return sub;
        }

        /**
         * 获取subscribed udms。
         *
         * @param host host参数。
         * @return 处理结果。
         */
        public List<String> getSubscribedUDMs(String host) {
            List<String> subs = new ArrayList<>();
            //for now, it is either at host level, or group level
            Set<String> udmSet = new HashSet<>();

            for (Subscription sub : this.subscriptions)
                if ("UDM".equals(sub.mGroup))
                    udmSet.add(sub.mSubGroup);


            if (this.hostsSubscriptions.containsKey(host))
                for (Subscription sub : this.hostsSubscriptions.get(host).getSubscribedUDMs())
                    udmSet.add(sub.mSubGroup);

            for (String subGrpName : udmSet)
                subs.add(subGrpName);

            return subs;
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
     * 创建MetricsSubscribers对象。
     */
    public MetricsSubscribers() {
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
                List<Subscription> subs = this.context.getMetricDb().loadMetricsSubscriptions();
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

        if (sub == null || sub.group == null || sub.group.isEmpty())
            return false;

        boolean status = this.context.getMetricDb() != null ?
                this.context.getMetricDb().addMetricsSubscription(sub) : false;

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
                this.context.getMetricDb().deleteMetricsSubscription(sub) : false;

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
     * 获取subscription。
     *
     * @param group     group参数。
     * @param host      host参数。
     * @param mGroup    m group参数。
     * @param mSubGroup m sub group参数。
     * @return 处理结果。
     */
    public Subscription getSubscription(String group, String host, String mGroup, String mSubGroup) {
        synchronized (this.groupSubscriptions) {

            if (!this.groupSubscriptions.containsKey(group))
                return null;

            return this.groupSubscriptions.get(group).getSubscription(host, mGroup, mSubGroup);
        }
    }

    /**
     * 获取subscribed udms。
     *
     * @param group group参数。
     * @param host  host参数。
     * @return 处理结果。
     */
    public List<String> getSubscribedUDMs(String group, String host) {
        synchronized (this.groupSubscriptions) {

            if (this.groupSubscriptions.containsKey(group))
                return this.groupSubscriptions.get(group).getSubscribedUDMs(host);

            return new ArrayList<>();
        }
    }
}

