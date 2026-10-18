/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.Map;
import java.util.TreeMap;


@Slf4j
public class StatDefManager implements java.io.Serializable {

    private static final long serialVersionUID = 9087706748220206476L;
    @SuppressWarnings("restriction")
    static final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
    @Setter
    private Map<String, String> statMap = new TreeMap<String, String>();
    @Getter
    @Setter
    private String sourcePath = "stats.xml";

    public StatDefManager() {

    }

    public String getStatDescription(String category, String name) {
        if (category == null || category.isEmpty())
            return getStatDescription(name);
        if ("thead_state".equalsIgnoreCase(category)) {

        }
        return null;
    }

    public String getStatDescription(String name) {
        if (name == null) return null;
        name = name.trim().toLowerCase();
        if (this.statMap.containsKey(name))
            return this.statMap.get(name);
        if (name.startsWith("mysql_status_com_stmt"))
            return this.statMap.get("mysql_status_com_stmt_xxx");
        if (name.startsWith("mysql_status_com_"))
            return this.statMap.get("mysql_status_com_xxx");
        if (name.startsWith("mysql_status_performance_schema"))
            return this.statMap.get("mysql_status_performance_schema_xxx");
        return null;
    }

    public String[] getStatNames() {
        return this.statMap.keySet().toArray(new String[0]);
    }

    /**
     * Load all predefined sqls
     *
     * @param in
     * @return
     * @throws XMLStreamException
     */
    @SuppressWarnings("restriction")
    public boolean load(java.io.InputStream in) throws XMLStreamException {
        XMLStreamReader reader = null;
        try {
            reader = inputFactory.createXMLStreamReader(new java.io.InputStreamReader(in));
            while (reader.hasNext()) {
                //loop till one sql tag is found
                int evtType = reader.next();
                if (evtType != XMLStreamConstants.START_ELEMENT) continue;
                String tagName = reader.getLocalName();
                if (!"stat".equals(tagName)) continue;
                String name = reader.getAttributeValue(null, "name");
                if (name != null && name.trim().length() > 0) {
                    this.statMap.put(name.toLowerCase(), reader.getElementText());
                }
            }
        } finally {
            if (reader != null) try {
                reader.close();
                reader = null;
            } catch (Exception iex) {
            }
        }

        return false;
    }

    public boolean init() {
        java.io.InputStream in = null;

        try {
            in = this.getClass().getClassLoader().getResourceAsStream(this.sourcePath);
            this.load(in);
            log.info("Load preloaded stat def: " + this.statMap.size() + ", input " + (in == null) + ", source path=" + this.sourcePath);
            return true;
        } catch (Exception ex) {
            log.error("Exception", ex);
        } finally {
            if (in != null) try {
                in.close();
            } catch (Exception ex) {
            }
        }
        return false;
    }


}
