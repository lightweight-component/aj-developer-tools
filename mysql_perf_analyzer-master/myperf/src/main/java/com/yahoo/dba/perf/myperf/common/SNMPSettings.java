/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import javax.json.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

/**
 * Store SNMP settings for community, version, etc.
 */
@Slf4j
public class SNMPSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    public static class SNMPSetting {
        public static final String DEFAULT_COMMUNITY = "public";
        public static final String DEFAULT_VERSION = "2c";
        public static final String COMMUNITY = "community";
        public static final String VERSION = "version";
        public static final String ENABLEED = "enabled";
        public static final String V3_USERNAME = "username";
        public static final String V3_PASSWORD = "password";
        public static final String V3_AUTHPROTOCOL = "authprotocol";
        public static final String V3_PRIVACYPASSPHRASE = "privacypassphrase";
        public static final String V3_PRIVACYPROTOCOL = "privacyprotocol";
        public static final String V3_CONTEXT = "context";

        @Getter
        @Setter
        private String community;
        @Getter
        @Setter
        private String version;
        @Getter
        @Setter
        private String username;//v3
        @Getter
        @Setter
        private String password;//v3
        @Getter
        @Setter
        private String authProtocol;//v3
        @Getter
        @Setter
        private String privacyPassphrase;//v3
        @Getter
        @Setter
        private String privacyProtocol;//v3
        @Getter
        @Setter
        private String context;//v3
        @Getter
        @Setter
        private String enabled = "yes";

        public SNMPSetting() {

        }

        public SNMPSetting(String community, String version) {
            this.community = community;
            this.version = version;
        }

        public SNMPSetting copy() {
            return new SNMPSetting(this.community, this.version);
        }

    }

    @Getter
    @Setter
    private SNMPSetting siteSetting;//default site setting, if provided
    //group level setting, if any. The key is dbGroupName
    private Map<String, SNMPSetting> groupSettings = new HashMap<>();
    //host level settings, if any. The key is dbGroupName+"|"+hostName
    @Getter
    private Map<String, SNMPSetting> hostSettings = new HashMap<>();
    private String myperfSnmpConfigPath;
    @Getter
    @Setter
    private String rootPath = "myperf_config";
    private String SNMPPATH = "snmp";
    @Getter
    @Setter
    private MyPerfContext frameworkContext;

    public SNMPSettings() {

    }

    public void setGroupSetting(String dbGroup, SNMPSetting groupSetting) {
        this.groupSettings.put(dbGroup, groupSetting);
    }

    public void setHostSetting(String dbGroupName, String hostName, SNMPSetting hostSetting) {
        this.hostSettings.put(dbGroupName + "|" + hostName, hostSetting);
    }

    /**
     * @param dbGroupHostName dbGroupName+"|"+hostName
     * @param hostSetting
     */
    public void setHostSetting(String dbGroupHostName, SNMPSetting hostSetting) {
        this.hostSettings.put(dbGroupHostName, hostSetting);
    }

    public SNMPSetting getGroupSetting(String dbGroupName) {

        if (this.groupSettings.containsKey(dbGroupName))
            return this.groupSettings.get(dbGroupName);

        return this.siteSetting;
    }

    public SNMPSetting getHostSetting(String dbgroup, String host) {

        if ((dbgroup == null || dbgroup.isEmpty()) && (host == null || host.isEmpty()))
            return this.siteSetting;

        if (this.hostSettings.containsKey(dbgroup + "|" + host))
            return this.hostSettings.get(dbgroup + "|" + host);
        else
            return getGroupSetting(dbgroup);
    }

    public boolean updateSnmpSetting(String dbgroup, String host, String community, String ver,
                                     String username, String password, String authprotocol, String privacypassphrase,
                                     String privacyprotocol, String context, String enabled
    ) {
        SNMPSetting setting = new SNMPSetting(community, ver);

        if ("3".equals(ver)) {
            setting.setUsername(username);
            setting.setPassword(password);
            setting.setAuthProtocol(authprotocol);
            setting.setPrivacyPassphrase(privacypassphrase);
            setting.setPrivacyProtocol(privacyprotocol);
            setting.setContext(context);
        }

        setting.setEnabled("no".equalsIgnoreCase(enabled) ? "no" : "yes");

        if ((dbgroup == null || dbgroup.isEmpty()) && (host == null || host.isEmpty())) {
            this.siteSetting = setting;
            log.info("Add snmp setting for site, version " + setting.getVersion());
        } else if (host == null || host.isEmpty()) {
            this.setGroupSetting(dbgroup, setting);
            log.info("Add snmp setting for group" + dbgroup + ", version " + setting.getVersion());
        } else {
            this.setHostSetting(dbgroup, host, setting);
            log.info("Add snmp setting for host (" + dbgroup + ", " + host + ") version " + setting.getVersion());
        }

        return this.store();
    }

    public void init(MyPerfContext ctx) {
        log.info("Read customized SNMP configuration ...");
        this.frameworkContext = ctx;
        File snmproot = new File(new File(rootPath), SNMPPATH);

        if (!snmproot.exists())
            snmproot.mkdirs();

        myperfSnmpConfigPath = snmproot.getAbsolutePath() + File.separatorChar + "snmp.json";
        this.readConfig();
    }

    synchronized public boolean store() {
        File cfgFile = new File(this.myperfSnmpConfigPath);

        if (!cfgFile.exists())
            log.info("There is no customized snmp configuration, create one.");

        try (Writer pw = new FileWriter(cfgFile)) {
            return this.writeToJson(pw);
        } catch (Exception ex) {
            log.error("Failed to store configurations to file " + this.myperfSnmpConfigPath, ex);
        }
        return false;
    }

    synchronized private boolean readConfig() {
        File cfgFile = new File(this.myperfSnmpConfigPath);

        if (!cfgFile.exists()) {
            log.info("There is no customized snmp configuration file");
            return true;
        }

        try (FileInputStream in = new FileInputStream(cfgFile);
             JsonReader jr = Json.createReader(in)) {
            JsonObject jsonObject = jr.readObject();
            this.setSiteSetting(readFromJson(jsonObject.getJsonObject("site")));
            JsonArray groups = jsonObject.getJsonArray("group");

            if (groups != null) {
                int len = groups.size();

                for (int i = 0; i < len; i++) {
                    JsonObject grp = groups.getJsonObject(i);
                    SNMPSetting grpSetting = readFromJson(grp);
                    String grpName = grp.getString("dbgroup", null);

                    if (grpName != null && grpSetting != null)
                        this.groupSettings.put(grpName, grpSetting);
                }
            }

            JsonArray hosts = jsonObject.getJsonArray("host");

            if (hosts != null) {
                int len = hosts.size();

                for (int i = 0; i < len; i++) {
                    JsonObject host = hosts.getJsonObject(i);
                    SNMPSetting hostSetting = readFromJson(host);
                    String hostName = host.getString("dbhost", null);

                    if (hostName != null && hostSetting != null)
                        this.hostSettings.put(hostName, hostSetting);
                }
            }

            return true;
        } catch (Exception ex) {
            log.error("Failed to read SNMP configuration file " + cfgFile.getAbsolutePath(), ex);
        }
        return false;
    }

    private SNMPSetting readFromJson(JsonObject jobj) {
        SNMPSetting snmp = null;

        if (jobj != null) {
            String version = jobj.getString(SNMPSetting.VERSION, null);
            String community = jobj.getString(SNMPSetting.COMMUNITY, null);
            String enabled = jobj.getString(SNMPSetting.ENABLEED, "yes");

            if ((version != null && !version.isEmpty()) || (community != null && !community.isEmpty())) {
                snmp = new SNMPSetting();
                snmp.setVersion(version);
                snmp.setCommunity(community);
                snmp.setEnabled(enabled);

                if ("3".equals(version)) {
                    snmp.setUsername(jobj.getString(SNMPSetting.V3_USERNAME, null));
                    String pwd = jobj.getString(SNMPSetting.V3_PASSWORD, null);

                    if (pwd != null)
                        pwd = frameworkContext.getMetaDb().dec(pwd);

                    snmp.setPassword(pwd);
                    snmp.setAuthProtocol(jobj.getString(SNMPSetting.V3_AUTHPROTOCOL, null));
                    pwd = jobj.getString(SNMPSetting.V3_PRIVACYPASSPHRASE, null);

                    if (pwd != null)
                        pwd = frameworkContext.getMetaDb().dec(pwd);

                    snmp.setPrivacyPassphrase(pwd);
                    snmp.setPrivacyProtocol(jobj.getString(SNMPSetting.V3_PRIVACYPROTOCOL, null));
                    snmp.setContext(jobj.getString(SNMPSetting.V3_CONTEXT, null));
                }
            }
        }

        return snmp;
    }

    private boolean writeToJson(Writer writer) {
        try {
            JsonObjectBuilder settingBuilder = Json.createObjectBuilder();
            settingBuilder.add("site", toJson(this.siteSetting));

            JsonArrayBuilder groupBuilder = Json.createArrayBuilder();

            for (Map.Entry<String, SNMPSetting> e : this.groupSettings.entrySet()) {
                JsonObjectBuilder grp = toJson(e.getValue());
                grp.add("dbgroup", e.getKey());
                groupBuilder.add(grp);
            }

            settingBuilder.add("group", groupBuilder);

            JsonArrayBuilder hostBuilder = Json.createArrayBuilder();

            for (Map.Entry<String, SNMPSetting> e : this.hostSettings.entrySet()) {
                JsonObjectBuilder host = toJson(e.getValue());
                host.add("dbhost", e.getKey());
                hostBuilder.add(host);
            }

            settingBuilder.add("host", hostBuilder);

            try (JsonWriter wr = Json.createWriter(writer)) {
                JsonObject jsonObj = settingBuilder.build();
                wr.writeObject(jsonObj);
                return true;
            }
        } catch (Exception ex) {
            log.info("Failed to store snmp json", ex);
        }
        return false;
    }

    private JsonObjectBuilder toJson(SNMPSettings.SNMPSetting setting) {
        JsonObjectBuilder sb = Json.createObjectBuilder();

        if (setting != null) {

            if (setting.getVersion() != null && !setting.getVersion().isEmpty())
                sb.add(SNMPSetting.VERSION, setting.getVersion());

            if (setting.getCommunity() != null && !setting.getCommunity().isEmpty())
                sb.add(SNMPSetting.COMMUNITY, setting.getCommunity());

            if ("3".equals(setting.getVersion())) {

                if (setting.getUsername() != null && !setting.getUsername().isEmpty())
                    sb.add(SNMPSetting.V3_USERNAME, setting.getUsername());

                if (setting.getPassword() != null && !setting.getPassword().isEmpty())
                    sb.add(SNMPSetting.V3_PASSWORD, frameworkContext.getMetaDb().enc(setting.getPassword()));

                if (setting.getAuthProtocol() != null && !setting.getAuthProtocol().isEmpty())
                    sb.add(SNMPSetting.V3_AUTHPROTOCOL, setting.getAuthProtocol());

                if (setting.getPrivacyPassphrase() != null && !setting.getPrivacyPassphrase().isEmpty())
                    sb.add(SNMPSetting.V3_PRIVACYPASSPHRASE, frameworkContext.getMetaDb().enc(setting.getPrivacyPassphrase()));

                if (setting.getPrivacyProtocol() != null && !setting.getPrivacyProtocol().isEmpty())
                    sb.add(SNMPSetting.V3_PRIVACYPROTOCOL, setting.getPrivacyProtocol());

                if (setting.getContext() != null && !setting.getContext().isEmpty())
                    sb.add(SNMPSetting.V3_CONTEXT, setting.getContext());
            }

            sb.add(SNMPSetting.ENABLEED, setting.getEnabled());

        }

        return sb;
    }

}
