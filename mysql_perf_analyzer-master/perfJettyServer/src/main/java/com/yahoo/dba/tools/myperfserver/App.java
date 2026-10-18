/*
 *  Copyright 2015, Yahoo Inc.
 *  Copyrights licensed under the Apache License.
 *  See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.tools.myperfserver;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.cli.*;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.servlet.ServletContainerInitializer;
import java.io.File;
import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;

public class App {
    public static String PID_FILE = "framework.pid";
    private static int pid = -1;

    @Getter
    @Setter
    private String configFilePath;
    @Getter
    @Setter
    private int port = 9090;
    @Getter
    @Setter
    private String contextPath = "/";
    @Getter
    @Setter
    private String logDirectoryPath = "logs";
    @Getter
    @Setter
    private String warFile;
    @Getter
    @Setter
    private String workDirectoryPath = "work";
    @Getter
    @Setter
    private String jettyHome; // it should have directory webapps which has our
    // warFile
    @Getter
    @Setter
    private boolean useHttps = false;
    @Getter
    @Setter
    private String certKeyStorePath;
    @Getter
    @Setter
    private String certKeyStorePassword;

    private Server jettyServer; // jetty server
    private URI serverURI;
    private String shutdownFile = "myserver.shutdown";

    /* -p --port 9090
     * -c --webcontextroot webapps
     * -l --logpath logpath
     * -w --war webapp war file name
     * -f --config config file path, if provided
     *     configuration file contains name=value pairs (java property file). If any configuration parameter is specified in command line,
     *     it will not be overwritten by the value in file. The main purpose to use file is to avoid ssl cert store password
     *     to be exposed in command line. The following list the configurations:
     *     jettyHome
     *     useHttps: value yes/y/no/n/true/false
     *     port
     *     webcontextroot
     *     workdir
     *     logpath
     *     warfile
     *     certKeyStorePath
     *     certKeyStorePassword
     */
    public static void main(String[] args) throws Exception {
        App myServer = new App();
        CommandLineParser parser = new GnuParser();
        Options options = getAvaliableCommandLineOptions();
        System.out.println(new Date()
                + " Usage: java -classpath ... com.yahoo.dba.tools.myperfserver.App -f config_file_path -j jettyhome "
                + "-p port -c webcontextroot -k workingDir -l logpath -w war_file");
        readOptionsFromCommandLine(args, parser, options, myServer);
        System.setProperty("logPath", myServer.getLogDirectoryPath());
        PID_FILE = myServer.getWarFile().substring(0, myServer.getWarFile().indexOf('.')) + ".pid";
        checksRunningOfAnotherServerInstance();
        //for https, we have to use https for jQuery
        System.setProperty("url_protocl", myServer.useHttps ? "https" : "http");
        runServer(myServer);
    }

    private static void runServer(App myServer) throws Exception{
        if (myServer.startServer()) {
            pid = getPid();
            writePid();
            myServer.waitForInterrupt();
            removePid();
        } else
            System.out.println("Server not started.");
    }

    private static void checksRunningOfAnotherServerInstance() {
        int historyPid = getHistoryPid();
        if (historyPid >= 0) {
            System.out
                    .println(new Date()
                            + " *************************** WARNING *********************");
            System.out
                    .println(PID_FILE
                            + " exists. Possibly another instance is still running. PID = "
                            + historyPid);
            System.out
                    .println(new Date()
                            + " *************************** WARNING *********************");
        }
    }

    private static void readOptionsFromCommandLine(String[] args,
                                                   CommandLineParser parser, Options options, App myServer) {
        try {
            CommandLine commandLine = parser.parse(options, args);
            checkCommandLineContainAvaliableOptions(myServer, commandLine);
        } catch (ParseException exp) {
            System.out.println("Unexpected exception:" + exp.getMessage());
        }
    }

    private static void checkCommandLineContainAvaliableOptions(App myServer,
                                                                CommandLine line) {
        if (line.hasOption("f")) {

            String argument = line.getOptionValue("f");
            if (argument != null && !argument.trim().isEmpty()) {
                System.out.println("Use configuration file: " + argument);
                //if we have configuration file, load it first
                //and allow command line to overwrite any specified
                myServer.setConfigFilePath(argument.trim());
                myServer.parseConfigurationFile();
            }
        }

        if (line.hasOption("p")) {
            try {
                myServer.setPort(Short.parseShort(line.getOptionValue("p")));
            } catch (Exception ex) {
            }
        }
        if (line.hasOption("c")) {
            String argument = line.getOptionValue("c");
            if (isOptionArgumentValid(argument))
                myServer.setContextPath(argument);
        }
        if (line.hasOption("l")) {
            String argument = line.getOptionValue("l");
            if (isOptionArgumentValid(argument))
                myServer.setLogDirectoryPath(argument);
        }
        if (line.hasOption("w")) {
            String argument = line.getOptionValue("w");
            if (isOptionArgumentValid(argument))
                myServer.setWarFile(argument);
        }
        if (line.hasOption("k")) {
            String argument = line.getOptionValue("k");
            myServer.setWorkDirectoryPath(argument);
        }
        if (line.hasOption("j")) {
            String argument = line.getOptionValue("j");
            if (isOptionArgumentValid(argument))
                myServer.setJettyHome(argument);
        }
    }

    private static boolean isOptionArgumentValid(String argument) {
        return (argument != null) && !(argument.isEmpty());
    }

    private static Options getAvaliableCommandLineOptions() {
        Options options = new Options();
        options.addOption("f", "config", true, "Configuration file path, no default.");
        options.addOption(
                "j",
                "jettyHome",
                true,
                "Jetty home, if not set, check system property jetty.home, then default to current Dir");
        options.addOption("p", "port", true,
                "http server port, default to 9090.");
        options.addOption("c", "webcontextroot", true,
                "web app url root context, defaul to /");
        options.addOption("l", "logpath", true,
                "log path, default to current directory.");
        options.addOption("w", "warfile", true,
                "war file name, default to myperf.war.");
        options.addOption("k", "workdir", true,
                "work directory for jetty, default to current dir.");
        return options;
    }

    public App() {
        this.jettyHome = System.getProperty("jetty.home", "."); // set default jetty.home
    }

    // This should be invoked before using command line options
    public void parseConfigurationFile() throws RuntimeException {
        String path = this.getConfigFilePath();
        if (path == null || path.isEmpty()) {
            System.out.println(new Date() + ": No configuration file specified, use command line only.");
            return;
        }

        try {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(path)) {
                props.load(in);
            }
            String useHttpsStr = props.getProperty("useHttps", "no");
            useHttpsStr = useHttpsStr.trim().toLowerCase();
            if (useHttpsStr.equals("yes") || useHttpsStr.equals("y") || useHttpsStr.equals("true"))
                this.setUseHttps(true);
            else
                this.setUseHttps(false);
            if (this.useHttps) {
                this.setCertKeyStorePath(props.getProperty("certKeyStorePath", null));
                this.setCertKeyStorePassword(props.getProperty("certKeyStorePassword", null));
            }
            String prop = props.getProperty("jettyHome", null);
            if (prop != null && !prop.isEmpty())
                this.setJettyHome(prop.trim());
            prop = props.getProperty("port", null);
            if (prop != null && !prop.isEmpty()) {
                try {
                    this.setPort(Integer.parseInt(prop.trim()));
                } catch (Exception ex) {
                }
            }
            prop = props.getProperty("webcontextroot", null);
            if (prop != null && !prop.isEmpty()) {
                this.setContextPath(prop.trim());
            }

            prop = props.getProperty("workdir", null);
            if (prop != null && !prop.isEmpty()) {
                this.setWorkDirectoryPath(prop.trim());
            }

            prop = props.getProperty("warfile", null);
            if (prop != null && !prop.isEmpty()) {
                this.setWarFile(prop.trim());
            }

            prop = props.getProperty("logpath", null);
            if (prop != null && !prop.isEmpty()) {
                this.setLogDirectoryPath(prop.trim());
            }
        } catch (Exception ex) {
            System.out.println(new Date() + ": Failed to load configuration file " + path);
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public boolean startServer() throws Exception {
        removeShutdownFile();
        File workDirectory = new File(this.getWorkDirectoryPath());
        File logDirectory = new File(this.getLogDirectoryPath());
        String deployedApplicationPath = this.getJettyHome()
                + File.separatorChar + "webapps" + File.separatorChar
                + this.getWarFile();
        if (!(isMeetingRequirementsToRunServer(workDirectory, logDirectory,
                deployedApplicationPath)))
            return false;
        WebAppContext deployedApplication = createDeployedApplicationInstance(
                workDirectory, deployedApplicationPath);

        // server = new Server(port);
        jettyServer = new Server();
        ServerConnector connector = this.isUseHttps() ? this.sslConnector() : connector();
        jettyServer.addConnector(connector);
        jettyServer.setHandler(deployedApplication);
        jettyServer.start();
        // server.join();

        // dump server state
        System.out.println(jettyServer.dump());
        this.serverURI = getServerUri(connector);
        return true;
    }

    private WebAppContext createDeployedApplicationInstance(File workDirectory,
                                                            String deployedApplicationPath) {
        WebAppContext deployedApplication = new WebAppContext();
        deployedApplication.setContextPath(this.getContextPath());
        deployedApplication.setWar(deployedApplicationPath);
        deployedApplication.setAttribute("javax.servlet.context.tempdir",
                workDirectory.getAbsolutePath());
        deployedApplication
                .setAttribute(
                        "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                        ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");
        deployedApplication.setAttribute(
                "org.eclipse.jetty.containerInitializers", jspInitializers());
        deployedApplication.setAttribute(InstanceManager.class.getName(),
                new SimpleInstanceManager());
        deployedApplication.addBean(new ServletContainerInitializersStarter(
                deployedApplication), true);
        // webapp.setClassLoader(new URLClassLoader(new
        // URL[0],App.class.getClassLoader()));
        deployedApplication.addServlet(jspServletHolder(), "*.jsp");
        return deployedApplication;
    }

    private boolean isMeetingRequirementsToRunServer(File workDirectory,
                                                     File logDirectory, String deployedApplicationPath) {
        if (!(createValidWorkDirectory(workDirectory)))
            return false;
        if (!(createValidLogDirectory(logDirectory)))
            return false;
        System.out.println(new Date() + " Use jetty home "
                + this.getJettyHome());
        if (!(new File(deployedApplicationPath).exists())) {
            System.out.println(new Date()
                    + " Cannot find war file at location "
                    + deployedApplicationPath);
            return false;
        }
        System.out.println(new Date() + " Use war file "
                + deployedApplicationPath);
        System.out.println(new Date() + " Use port " + this.getPort()
                + ", context " + this.getContextPath() + ".");
        return true;
    }

    private boolean createValidLogDirectory(File logDirectory) {
        if (!logDirectory.exists()) {
            System.out.println(new Date() + " Create log directory at "
                    + logDirectory.getAbsolutePath());
            logDirectory.mkdir();
        }
        if (!logDirectory.exists()) {
            System.out.println(new Date() + " Invalid log directory "
                    + logDirectory.getAbsolutePath());
            return false;
        }
        System.out.println(new Date() + " Use log directory "
                + logDirectory.getAbsolutePath());
        return true;
    }

    private boolean createValidWorkDirectory(File workDirectory) {
        if (!workDirectory.exists()) {
            System.out.println(new Date() + " Create working dir at "
                    + workDirectory.getAbsolutePath());
            workDirectory.mkdir();
        }
        if (!workDirectory.exists()) {
            System.out.println(new Date() + " Invalid working directory "
                    + workDirectory.getAbsolutePath());
            return false;
        }
        System.out.println(new Date() + " Use working directory "
                + workDirectory.getAbsolutePath());
        return true;
    }

    private ServerConnector connector() {
        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setPort(port);
        return connector;
    }

    /**
     * Create ssl connector if https is used
     *
     * @return
     */
    private ServerConnector sslConnector() {
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(this.getPort());

        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());

        SslContextFactory sslContextFactory = new SslContextFactory(this.getCertKeyStorePath());
        sslContextFactory.setKeyStorePassword(this.getCertKeyStorePassword());
        //exclude weak ciphers
        sslContextFactory.setExcludeCipherSuites("^.*_(MD5|SHA|SHA1)$");
        //only support tlsv1.2
        sslContextFactory.addExcludeProtocols("SSL", "SSLv2", "SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1");

        ServerConnector connector = new ServerConnector(jettyServer,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(https_config));
        connector.setPort(this.getPort());
        connector.setIdleTimeout(50000);
        return connector;
    }

    private URI getServerUri(ServerConnector connector)
            throws URISyntaxException {
        String scheme = "http";
        for (ConnectionFactory connectFactory : connector
                .getConnectionFactories()) {
            if (connectFactory.getProtocol().startsWith("SSL-http")) {
                scheme = "https";
            }
        }
        String host = connector.getHost();
        if (host == null) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception ex) {
            }
        }
        if (host == null) {
            host = "localhost";
        }
        int myport = connector.getLocalPort();
        serverURI = new URI(String.format("%s://%s:%d", scheme, host, myport));
        System.out.println(new Date() + " Server URI: " + serverURI + this.contextPath);
        return serverURI;
    }

    public void stop() throws Exception {
        if (jettyServer != null)
            jettyServer.stop();
    }

    /**
     * Get process id of current running starloader
     *
     * @return OS process id
     */
    public static int getPid() {
        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            String jvmName = runtime.getName();
            String ss = jvmName.substring(0, jvmName.indexOf('@'));
            return Integer.parseInt(ss);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;// no valid number
    }

    private static void writePid() {
        if (pid < 0)
            return;// not supported
        try {
            try (FileWriter fw = new FileWriter(PID_FILE)) {
                fw.write(String.valueOf(pid));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static int getHistoryPid() {
        try {
            try (BufferedReader fr = new BufferedReader(new FileReader(PID_FILE))) {
                String line = fr.readLine();
                return Integer.parseInt(line);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    private static void removePid() {
        try {
            new File(PID_FILE).delete();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void removeShutdownFile() {
        try {
            new File(this.shutdownFile).delete();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private List<ContainerInitializer> jspInitializers() {
        JettyJasperInitializer sci = new JettyJasperInitializer();
        ContainerInitializer initializer = new ContainerInitializer(
                (ServletContainerInitializer) sci, null);
        List<ContainerInitializer> initializers = new ArrayList<ContainerInitializer>();
        initializers.add(initializer);
        return initializers;
    }

    private ServletHolder jspServletHolder() {
        ServletHolder holderJsp = new ServletHolder("jsp",
                JettyJspServlet.class);
        holderJsp.setInitOrder(0);
        holderJsp.setInitParameter("logVerbosityLevel", "DEBUG");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.8");
        holderJsp.setInitParameter("compilerSourceVM", "1.8");
        holderJsp.setInitParameter("keepgenerated", "true");
        return holderJsp;
    }

    public void waitForInterrupt() throws InterruptedException {
        waitToShutdownSignal();
        shutdown();
        removeShutdownFile();
        System.out.println(new Date() + " Wait for shutdown to complete");
        if (jettyServer != null)
            jettyServer.join(); // wait for it to complete
        System.out.println(new Date() + " Shutdown is completed.");
    }

    private void waitToShutdownSignal() {
        while (true) {
            // sleep 5 seconds, then check shutdown file
            try {
                Thread.sleep(5000);
                if (new File(this.shutdownFile).exists()) {
                    // we are supposed to shut down
                    System.out.println(new Date() + " Receive soft shutdown signal.");
                    break;
                }
            } catch (Throwable th) {
                System.out.println(new Date() + " Receive shutdown signal.");
                break;
            }
        }
    }

    private void shutdown() {
        try { // shutdown
            System.out.println(new Date() + " Shutdown server");
            stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
