/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.extern.slf4j.Slf4j;

import java.io.OutputStreamWriter;
import java.io.Writer;

@Slf4j
public class MailUtil {

    /**
     * A simple mail client to use shell command to send mail
     *
     * @param receiver
     * @param subject
     * @param msg
     * @return
     */
    public static boolean sendMail(String receiver, String subject, String msg) {
        String mailCommand = "mailx";//or mail, which send long body as attachment

        String os = System.getProperty("os.name");
        if (os != null && os.toUpperCase().contains("WIN"))
            return false;
        log.info("Sending email to " + receiver + " regarding " + subject);
        String[] cmd = {mailCommand, "-s", subject, receiver};
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            try (Writer w = new OutputStreamWriter(p.getOutputStream())) {
                w.append(msg);
                w.flush();
            }
            p.waitFor();
            log.info("Mail exitValue=" + p.exitValue());
            return true;
        } catch (Exception ex) {
            log.error("Error when send mail", ex);
        }
        return false;
    }

}
