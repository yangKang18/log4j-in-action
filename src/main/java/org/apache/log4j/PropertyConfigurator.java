/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


// Contributors: "Luke Blanshard" <Luke@quiq.com>
//              "Mark DONSZELMANN" <Mark.Donszelmann@cern.ch>
//               Anders Kristensen <akristensen@dynamicsoft.com>

package org.apache.log4j;

import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.helpers.FileWatchdog;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.spi.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URLConnection;
import java.util.*;

/**
 * Allows the configuration of log4j from an external file.  See
 * <b>{@link #doConfigure(String, LoggerRepository)}</b> for the
 * expected format.
 *
 * <p>It is sometimes useful to see how log4j is reading configuration
 * files. You can enable log4j internal logging by defining the
 * <b>log4j.debug</b> variable.
 *
 * <P>As of log4j version 0.8.5, at class initialization time class,
 * the file <b>log4j.properties</b> will be searched from the search
 * path used to load classes. If the file can be found, then it will
 * be fed to the {@link PropertyConfigurator#configure(java.net.URL)}
 * method.
 *
 * <p>The <code>PropertyConfigurator</code> does not handle the
 * advanced configuration features supported by the {@link
 * org.apache.log4j.xml.DOMConfigurator DOMConfigurator} such as
 * support custom {@link org.apache.log4j.spi.ErrorHandler ErrorHandlers},
 * nested appenders such as the {@link org.apache.log4j.AsyncAppender
 * AsyncAppender}, etc.
 *
 * <p>All option <em>values</em> admit variable substitution. The
 * syntax of variable substitution is similar to that of Unix
 * shells. The string between an opening <b>&quot;${&quot;</b> and
 * closing <b>&quot;}&quot;</b> is interpreted as a key. The value of
 * the substituted variable can be defined as a system property or in
 * the configuration file itself. The value of the key is first
 * searched in the system properties, and if not found there, it is
 * then searched in the configuration file being parsed.  The
 * corresponding value replaces the ${variableName} sequence. For
 * example, if <code>java.home</code> system property is set to
 * <code>/home/xyz</code>, then every occurrence of the sequence
 * <code>${java.home}</code> will be interpreted as
 * <code>/home/xyz</code>.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author Anders Kristensen
 * @since 0.8.1
 */
public class PropertyConfigurator implements Configurator {

    /**
     * Used internally to keep track of configured appenders.
     */
    protected Hashtable registry = new Hashtable(11);
    private LoggerRepository repository;
    protected LoggerFactory loggerFactory = new DefaultCategoryFactory();

    static final String CATEGORY_PREFIX = "log4j.category.";
    static final String LOGGER_PREFIX = "log4j.logger.";
    static final String FACTORY_PREFIX = "log4j.factory";
    static final String ADDITIVITY_PREFIX = "log4j.additivity.";
    static final String ROOT_CATEGORY_PREFIX = "log4j.rootCategory";
    static final String ROOT_LOGGER_PREFIX = "log4j.rootLogger";
    static final String APPENDER_PREFIX = "log4j.appender.";
    static final String RENDERER_PREFIX = "log4j.renderer.";
    static final String THRESHOLD_PREFIX = "log4j.threshold";
    private static final String THROWABLE_RENDERER_PREFIX = "log4j.throwableRenderer";
    private static final String LOGGER_REF = "logger-ref";
    private static final String ROOT_REF = "root-ref";
    private static final String APPENDER_REF_TAG = "appender-ref";


    /**
     * Key for specifying the {@link org.apache.log4j.spi.LoggerFactory
     * LoggerFactory}.  Currently set to "<code>log4j.loggerFactory</code>".
     */
    public static final String LOGGER_FACTORY_KEY = "log4j.loggerFactory";

    /**
     * If property set to true, then hierarchy will be reset before configuration.
     */
    private static final String RESET_KEY = "log4j.reset";

    static final private String INTERNAL_ROOT_NAME = "root";

    /**
     * Read configuration from a file. <b>The existing configuration is
     * not cleared nor reset.</b> If you require a different behavior,
     * then call {@link  LogManager#resetConfiguration
     * resetConfiguration} method before calling
     * <code>doConfigure</code>.
     *
     * <p>The configuration file consists of statements in the format
     * <code>key=value</code>. The syntax of different configuration
     * elements are discussed below.
     *
     * <h3>Repository-wide threshold</h3>
     *
     * <p>The repository-wide threshold filters logging requests by level
     * regardless of logger. The syntax is:
     *
     * <pre>
     * log4j.threshold=[level]
     * </pre>
     *
     * <p>The level value can consist of the string values OFF, FATAL,
     * ERROR, WARN, INFO, DEBUG, ALL or a <em>custom level</em> value. A
     * custom level value can be specified in the form
     * level#classname. By default the repository-wide threshold is set
     * to the lowest possible value, namely the level <code>ALL</code>.
     * </p>
     *
     *
     * <h3>Appender configuration</h3>
     *
     * <p>Appender configuration syntax is:
     * <pre>
     * # For appender named <i>appenderName</i>, set its class.
     * # Note: The appender name can contain dots.
     * log4j.appender.appenderName=fully.qualified.name.of.appender.class
     *
     * # Set appender specific options.
     * log4j.appender.appenderName.option1=value1
     * ...
     * log4j.appender.appenderName.optionN=valueN
     * </pre>
     * <p>
     * For each named appender you can configure its {@link Layout}. The
     * syntax for configuring an appender's layout is:
     * <pre>
     * log4j.appender.appenderName.layout=fully.qualified.name.of.layout.class
     * log4j.appender.appenderName.layout.option1=value1
     * ....
     * log4j.appender.appenderName.layout.optionN=valueN
     * </pre>
     * <p>
     * The syntax for adding {@link Filter}s to an appender is:
     * <pre>
     * log4j.appender.appenderName.filter.ID=fully.qualified.name.of.filter.class
     * log4j.appender.appenderName.filter.ID.option1=value1
     * ...
     * log4j.appender.appenderName.filter.ID.optionN=valueN
     * </pre>
     * The first line defines the class name of the filter identified by ID;
     * subsequent lines with the same ID specify filter option - value
     * pairs. Multiple filters are added to the appender in the lexicographic
     * order of IDs.
     * <p>
     * The syntax for adding an {@link ErrorHandler} to an appender is:
     * <pre>
     * log4j.appender.appenderName.errorhandler=fully.qualified.name.of.filter.class
     * log4j.appender.appenderName.errorhandler.root-ref={true|false}
     * log4j.appender.appenderName.errorhandler.logger-ref=loggerName
     * log4j.appender.appenderName.errorhandler.appender-ref=appenderName
     * log4j.appender.appenderName.errorhandler.option1=value1
     * ...
     * log4j.appender.appenderName.errorhandler.optionN=valueN
     * </pre>
     *
     * <h3>Configuring loggers</h3>
     *
     * <p>The syntax for configuring the root logger is:
     * <pre>
     * log4j.rootLogger=[level], appenderName, appenderName, ...
     * </pre>
     *
     * <p>This syntax means that an optional <em>level</em> can be
     * supplied followed by appender names separated by commas.
     *
     * <p>The level value can consist of the string values OFF, FATAL,
     * ERROR, WARN, INFO, DEBUG, ALL or a <em>custom level</em> value. A
     * custom level value can be specified in the form
     * <code>level#classname</code>.
     *
     * <p>If a level value is specified, then the root level is set
     * to the corresponding level.  If no level value is specified,
     * then the root level remains untouched.
     *
     * <p>The root logger can be assigned multiple appenders.
     *
     * <p>Each <i>appenderName</i> (separated by commas) will be added to
     * the root logger. The named appender is defined using the
     * appender syntax defined above.
     *
     * <p>For non-root categories the syntax is almost the same:
     * <pre>
     * log4j.logger.logger_name=[level|INHERITED|NULL], appenderName, appenderName, ...
     * </pre>
     *
     * <p>The meaning of the optional level value is discussed above
     * in relation to the root logger. In addition however, the value
     * INHERITED can be specified meaning that the named logger should
     * inherit its level from the logger hierarchy.
     *
     * <p>If no level value is supplied, then the level of the
     * named logger remains untouched.
     *
     * <p>By default categories inherit their level from the
     * hierarchy. However, if you set the level of a logger and later
     * decide that that logger should inherit its level, then you should
     * specify INHERITED as the value for the level value. NULL is a
     * synonym for INHERITED.
     *
     * <p>Similar to the root logger syntax, each <i>appenderName</i>
     * (separated by commas) will be attached to the named logger.
     *
     * <p>See the <a href="../../../../manual.html#additivity">appender
     * additivity rule</a> in the user manual for the meaning of the
     * <code>additivity</code> flag.
     *
     * <h3>ObjectRenderers</h3>
     * <p>
     * You can customize the way message objects of a given type are
     * converted to String before being logged. This is done by
     * specifying an {@link org.apache.log4j.or.ObjectRenderer ObjectRenderer}
     * for the object type would like to customize.
     *
     * <p>The syntax is:
     *
     * <pre>
     * log4j.renderer.fully.qualified.name.of.rendered.class=fully.qualified.name.of.rendering.class
     * </pre>
     * <p>
     * As in,
     * <pre>
     * log4j.renderer.my.Fruit=my.FruitRenderer
     * </pre>
     *
     * <h3>ThrowableRenderer</h3>
     * <p>
     * You can customize the way an instance of Throwable is
     * converted to String before being logged. This is done by
     * specifying an {@link org.apache.log4j.spi.ThrowableRenderer ThrowableRenderer}.
     *
     * <p>The syntax is:
     *
     * <pre>
     * log4j.throwableRenderer=fully.qualified.name.of.rendering.class
     * log4j.throwableRenderer.paramName=paramValue
     * </pre>
     * <p>
     * As in,
     * <pre>
     * log4j.throwableRenderer=org.apache.log4j.EnhancedThrowableRenderer
     * </pre>
     *
     * <h3>Logger Factories</h3>
     * <p>
     * The usage of custom logger factories is discouraged and no longer
     * documented.
     *
     * <h3>Resetting Hierarchy</h3>
     * <p>
     * The hierarchy will be reset before configuration when
     * log4j.reset=true is present in the properties file.
     *
     * <h3>Example</h3>
     *
     * <p>An example configuration is given below. Other configuration
     * file examples are given in the <code>examples</code> folder.
     *
     * <pre>
     *
     * # Set options for appender named "A1".
     * # Appender "A1" will be a SyslogAppender
     * log4j.appender.A1=org.apache.log4j.net.SyslogAppender
     *
     * # The syslog daemon resides on www.abc.net
     * log4j.appender.A1.SyslogHost=www.abc.net
     *
     * # A1's layout is a PatternLayout, using the conversion pattern
     * # <b>%r %-5p %c{2} %M.%L %x - %m\n</b>. Thus, the log output will
     * # include # the relative time since the start of the application in
     * # milliseconds, followed by the level of the log request,
     * # followed by the two rightmost components of the logger name,
     * # followed by the callers method name, followed by the line number,
     * # the nested diagnostic context and finally the message itself.
     * # Refer to the documentation of {@link PatternLayout} for further information
     * # on the syntax of the ConversionPattern key.
     * log4j.appender.A1.layout=org.apache.log4j.PatternLayout
     * log4j.appender.A1.layout.ConversionPattern=%-4r %-5p %c{2} %M.%L %x - %m\n
     *
     * # Set options for appender named "A2"
     * # A2 should be a RollingFileAppender, with maximum file size of 10 MB
     * # using at most one backup file. A2's layout is TTCC, using the
     * # ISO8061 date format with context printing enabled.
     * log4j.appender.A2=org.apache.log4j.RollingFileAppender
     * log4j.appender.A2.MaxFileSize=10MB
     * log4j.appender.A2.MaxBackupIndex=1
     * log4j.appender.A2.layout=org.apache.log4j.TTCCLayout
     * log4j.appender.A2.layout.ContextPrinting=enabled
     * log4j.appender.A2.layout.DateFormat=ISO8601
     *
     * # Root logger set to DEBUG using the A2 appender defined above.
     * log4j.rootLogger=DEBUG, A2
     *
     * # Logger definitions:
     * # The SECURITY logger inherits is level from root. However, it's output
     * # will go to A1 appender defined above. It's additivity is non-cumulative.
     * log4j.logger.SECURITY=INHERIT, A1
     * log4j.additivity.SECURITY=false
     *
     * # Only warnings or above will be logged for the logger "SECURITY.access".
     * # Output will go to A1.
     * log4j.logger.SECURITY.access=WARN
     *
     *
     * # The logger "class.of.the.day" inherits its level from the
     * # logger hierarchy.  Output will go to the appender's of the root
     * # logger, A2 in this case.
     * log4j.logger.class.of.the.day=INHERIT
     * </pre>
     *
     * <p>Refer to the <b>setOption</b> method in each Appender and
     * Layout for class specific options.
     *
     * <p>Use the <code>#</code> or <code>!</code> characters at the
     * beginning of a line for comments.
     *
     * <p>
     *
     * @param configFileName The name of the configuration file where the
     *                       configuration information is stored.
     */
    public void doConfigure(String configFileName, LoggerRepository hierarchy) {
        Properties props = new Properties();
        FileInputStream istream = null;
        try {
            istream = new FileInputStream(configFileName);
            props.load(istream);
            istream.close();
        } catch (Exception e) {
            if (e instanceof InterruptedIOException || e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LogLog.error("Could not read configuration file [" + configFileName + "].", e);
            LogLog.error("Ignoring configuration file [" + configFileName + "].");
            return;
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (InterruptedIOException ignore) {
                    Thread.currentThread().interrupt();
                } catch (Throwable ignore) {
                }

            }
        }
        // If we reach here, then the config file is alright.
        doConfigure(props, hierarchy);
    }

    /**
     */
    static
    public void configure(String configFilename) {
        new PropertyConfigurator().doConfigure(configFilename,
                LogManager.getLoggerRepository());
    }

    /**
     * Read configuration options from url <code>configURL</code>.
     *
     * @since 0.8.2
     */
    public
    static void configure(java.net.URL configURL) {
        new PropertyConfigurator().doConfigure(configURL,
                LogManager.getLoggerRepository());
    }

    /**
     * Reads configuration options from an InputStream.
     *
     * @since 1.2.17
     */
    public
    static void configure(InputStream inputStream) {
        new PropertyConfigurator().doConfigure(inputStream,
                LogManager.getLoggerRepository());
    }


    /**
     * Read configuration options from <code>properties</code>.
     * <p>
     * See {@link #doConfigure(String, LoggerRepository)} for the expected format.
     */
    static
    public void configure(Properties properties) {
        new PropertyConfigurator().doConfigure(properties,
                LogManager.getLoggerRepository());
    }

    /**
     * Like {@link #configureAndWatch(String, long)} except that the
     * default delay as defined by {@link FileWatchdog#DEFAULT_DELAY} is
     * used.
     *
     * @param configFilename A file in key=value format.
     */
    static
    public void configureAndWatch(String configFilename) {
        configureAndWatch(configFilename, FileWatchdog.DEFAULT_DELAY);
    }


    /**
     * Read the configuration file <code>configFilename</code> if it
     * exists. Moreover, a thread will be created that will periodically
     * check if <code>configFilename</code> has been created or
     * modified. The period is determined by the <code>delay</code>
     * argument. If a change or file creation is detected, then
     * <code>configFilename</code> is read to configure log4j.
     *
     * @param configFilename A file in key=value format.
     * @param delay          The delay in milliseconds to wait between each check.
     */
    static
    public void configureAndWatch(String configFilename, long delay) {
        PropertyWatchdog pdog = new PropertyWatchdog(configFilename);
        pdog.setDelay(delay);
        pdog.start();
    }


    /**
     * Read configuration options from <code>properties</code>.
     * <p>
     * See {@link #doConfigure(String, LoggerRepository)} for the expected format.
     */
    public void doConfigure(Properties properties, LoggerRepository hierarchy) {
        repository = hierarchy;

        // 配置中获取log4j的内部日志打印的开关，如果配置了log4j.debug或者log4j.configDebug的值为false，则关闭内部debug日志打印
        // 也就是Loglog.debug的打印结果没有了。但是warn的级别还是会打印的
        String value = properties.getProperty(LogLog.DEBUG_KEY);
        if (value == null) {
            value = properties.getProperty("log4j.configDebug");
            if (value != null) {
                LogLog.warn("[log4j.configDebug] is deprecated. Use [log4j.debug] instead.");
            }
        }

        if (value != null) {
            LogLog.setInternalDebugging(OptionConverter.toBoolean(value, true));
        }

        //
        //   if log4j.reset=true then
        //        reset hierarchy
        // 日志容器重置配置确认
        String reset = properties.getProperty(RESET_KEY);
        if (reset != null && OptionConverter.toBoolean(reset, false)) {
            hierarchy.resetConfiguration();
        }

        // 确认容器的阈值配置，log4j.threshold
        String thresholdStr = OptionConverter.findAndSubst(THRESHOLD_PREFIX,
                properties);
        if (thresholdStr != null) {
            hierarchy.setThreshold(OptionConverter.toLevel(thresholdStr,
                    Level.ALL));
            LogLog.debug("Hierarchy threshold set to [" + hierarchy.getThreshold() + "].");
        }

        // 配置根日志
        configureRootCategory(properties, hierarchy);
        // 配置日志工厂
        configureLoggerFactory(properties);
        // 配置其他日志和渲染器
        parseCatsAndRenderers(properties, hierarchy);

        LogLog.debug("Finished configuring.");
        // We don't want to hold references to appenders preventing their
        // garbage collection.
        // appender做了缓存处理，配置加载完成，及时清理内存
        registry.clear();
    }

    /**
     * Read configuration options from an InputStream.
     *
     * @since 1.2.17
     */
    public void doConfigure(InputStream inputStream, LoggerRepository hierarchy) {
        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            if (e instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            LogLog.error("Could not read configuration file from InputStream [" + inputStream
                    + "].", e);
            LogLog.error("Ignoring configuration InputStream [" + inputStream + "].");
            return;
        }
        this.doConfigure(props, hierarchy);
    }

    /**
     * Read configuration options from url <code>configURL</code>.
     */
    public void doConfigure(java.net.URL configURL, LoggerRepository hierarchy) {
        // 根据配置文件路径，把配置转为Properties对象，常用的加载配置文件的方式
        // Properties文件中的会转成K-V的方式
        Properties props = new Properties();
        LogLog.debug("Reading configuration from URL " + configURL);
        InputStream istream = null;
        URLConnection uConn = null;
        try {
            uConn = configURL.openConnection();
            uConn.setUseCaches(false);
            istream = uConn.getInputStream();
            props.load(istream);
        } catch (Exception e) {
            if (e instanceof InterruptedIOException || e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LogLog.error("Could not read configuration file from URL [" + configURL
                    + "].", e);
            LogLog.error("Ignoring configuration file [" + configURL + "].");
            return;
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (InterruptedIOException ignore) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignore) {
                } catch (RuntimeException ignore) {
                }
            }
        }
        doConfigure(props, hierarchy);
    }


    // --------------------------------------------------------------------------
    // Internal stuff
    // --------------------------------------------------------------------------

    /**
     * Check the provided <code>Properties</code> object for a
     * {@link org.apache.log4j.spi.LoggerFactory LoggerFactory}
     * entry specified by {@link #LOGGER_FACTORY_KEY}.  If such an entry
     * exists, an attempt is made to create an instance using the default
     * constructor.  This instance is used for subsequent Category creations
     * within this configurator.
     *
     * @see #parseCatsAndRenderers
     */
    protected void configureLoggerFactory(Properties props) {
        // 配置日志创建工厂类
        String factoryClassName = OptionConverter.findAndSubst(LOGGER_FACTORY_KEY,
                props);
        if (factoryClassName != null) {
            LogLog.debug("Setting category factory to [" + factoryClassName + "].");
            loggerFactory = (LoggerFactory)
                    OptionConverter.instantiateByClassName(factoryClassName,
                            LoggerFactory.class,
                            loggerFactory);
            PropertySetter.setProperties(loggerFactory, props, FACTORY_PREFIX + ".");
        }
    }

  /*
  void configureOptionHandler(OptionHandler oh, String prefix,
			      Properties props) {
    String[] options = oh.getOptionStrings();
    if(options == null)
      return;

    String value;
    for(int i = 0; i < options.length; i++) {
      value =  OptionConverter.findAndSubst(prefix + options[i], props);
      LogLog.debug(
         "Option " + options[i] + "=[" + (value == null? "N/A" : value)+"].");
      // Some option handlers assume that null value are not passed to them.
      // So don't remove this check
      if(value != null) {
	oh.setOption(options[i], value);
      }
    }
    oh.activateOptions();
  }
  */


    void configureRootCategory(Properties props, LoggerRepository hierarchy) {
        // 获取根日志前缀：log4j.rootLogger或者log4j.rootCategory
        String effectiveFrefix = ROOT_LOGGER_PREFIX;
        String value = OptionConverter.findAndSubst(ROOT_LOGGER_PREFIX, props);

        if (value == null) {
            value = OptionConverter.findAndSubst(ROOT_CATEGORY_PREFIX, props);
            effectiveFrefix = ROOT_CATEGORY_PREFIX;
        }

        if (value == null) {
            LogLog.debug("Could not find root logger information. Is this OK?");
        } else {
            Logger root = hierarchy.getRootLogger();
            synchronized (root) {
                // 解析根日志
                parseCategory(props, root, effectiveFrefix, INTERNAL_ROOT_NAME, value);
            }
        }
    }


    /**
     * Parse non-root elements, such non-root categories and renderers.
     */
    protected void parseCatsAndRenderers(Properties props, LoggerRepository hierarchy) {
        Enumeration enumeration = props.propertyNames();
        while (enumeration.hasMoreElements()) {
            // 查获所有配置key
            String key = (String) enumeration.nextElement();
            // 日志前缀开头的解析
            if (key.startsWith(CATEGORY_PREFIX) || key.startsWith(LOGGER_PREFIX)) {
                String loggerName = null;
                if (key.startsWith(CATEGORY_PREFIX)) {
                    loggerName = key.substring(CATEGORY_PREFIX.length());
                } else if (key.startsWith(LOGGER_PREFIX)) {
                    loggerName = key.substring(LOGGER_PREFIX.length());
                }
                String value = OptionConverter.findAndSubst(key, props);

                // 从日志容器中获取日志器，如果没有，由loggerFactory创建新的，此处可以学习日志容器是如何管理所有的logger的
                Logger logger = hierarchy.getLogger(loggerName, loggerFactory);
                synchronized (logger) {
                    // 解析一般logger，同rootLogger解析步骤一样
                    parseCategory(props, logger, key, loggerName, value);
                    // 解析日志打印传播设置
                    parseAdditivityForLogger(props, logger, loggerName);
                }

            } else if (key.startsWith(RENDERER_PREFIX)) {
                // 渲染器前缀开头的解析：log4j.renderer.
                String renderedClass = key.substring(RENDERER_PREFIX.length());
                String renderingClass = OptionConverter.findAndSubst(key, props);
                // hierarchy本身的设计就是RendererSupport的实现类
                if (hierarchy instanceof RendererSupport) {
                    RendererMap.addRenderer((RendererSupport) hierarchy, renderedClass,
                            renderingClass);
                }

            } else if (key.equals(THROWABLE_RENDERER_PREFIX)) {
                // 报错渲染支持解析
                if (hierarchy instanceof ThrowableRendererSupport) {
                    ThrowableRenderer tr = (ThrowableRenderer)
                            OptionConverter.instantiateByKey(props,
                                    THROWABLE_RENDERER_PREFIX,
                                    org.apache.log4j.spi.ThrowableRenderer.class,
                                    null);
                    if (tr == null) {
                        LogLog.error(
                                "Could not instantiate throwableRenderer.");
                    } else {
                        PropertySetter setter = new PropertySetter(tr);
                        setter.setProperties(props, THROWABLE_RENDERER_PREFIX + ".");
                        ((ThrowableRendererSupport) hierarchy).setThrowableRenderer(tr);

                    }
                }
            }
        }
    }

    /**
     * Parse the additivity option for a non-root category.
     */
    void parseAdditivityForLogger(Properties props, Logger cat,
                                  String loggerName) {

        // 检查log4j.additivity.设置，该设置表示不允许父级logger打印，默认允许。在appender打印触发时讲解
        String value = OptionConverter.findAndSubst(ADDITIVITY_PREFIX + loggerName,
                props);
        LogLog.debug("Handling " + ADDITIVITY_PREFIX + loggerName + "=[" + value + "]");
        // touch additivity only if necessary
        if ((value != null) && (!value.equals(""))) {
            boolean additivity = OptionConverter.toBoolean(value, true);
            LogLog.debug("Setting additivity for \"" + loggerName + "\" to " +
                    additivity);
            cat.setAdditivity(additivity);
        }
    }

    /**
     * This method must work for the root category as well.
     */
    void parseCategory(Properties props, Logger logger, String optionKey,
                       String loggerName, String value) {

        LogLog.debug("Parsing for [" + loggerName + "] with value=[" + value + "].");
        // We must skip over ',' but not white space
        // 对日志字符串使用“,”进行分割，一般第一块是日志的级别设置，也可以不设置或者设置继承，后面的则是输出器append的名称定义
        StringTokenizer st = new StringTokenizer(value, ",");

        // If value is not in the form ", appender.." or "", then we should set
        // the level of the loggeregory.

        if (!(value.startsWith(",") || value.equals(""))) {

            // just to be on the safe side...
            if (!st.hasMoreTokens()) {
                return;
            }

            // 获取第一块值：级别字符串
            String levelStr = st.nextToken();
            LogLog.debug("Level token is [" + levelStr + "].");

            // If the level value is inherited, set category level value to
            // null. We also check that the user has not specified inherited for the
            // root category.
            // 如果没有设置第一块的值或者设置为继承，root日志则警告没有级别设置，其他则设置为null
            if (INHERITED.equalsIgnoreCase(levelStr) ||
                    NULL.equalsIgnoreCase(levelStr)) {
                if (loggerName.equals(INTERNAL_ROOT_NAME)) {
                    LogLog.warn("The root logger cannot be set to null.");
                } else {
                    logger.setLevel(null);
                }
            } else {
                // 设置日志级别
                logger.setLevel(OptionConverter.toLevel(levelStr, Level.DEBUG));
            }
            LogLog.debug("Category " + loggerName + " set to " + logger.getLevel());
        }

        // Begin by removing all existing appenders.
        // 先移出日志器上所有的appender
        logger.removeAllAppenders();

        Appender appender;
        String appenderName;
        while (st.hasMoreTokens()) {
            appenderName = st.nextToken().trim();
            if (appenderName == null || appenderName.equals(",")) {
                continue;
            }
            LogLog.debug("Parsing appender named \"" + appenderName + "\".");
            // 解析appender
            appender = parseAppender(props, appenderName);

            // 一个logger持有多个appender
            if (appender != null) {
                logger.addAppender(appender);
            }
        }
    }

    Appender parseAppender(Properties props, String appenderName) {
        // appender缓存，根据名字查看是解析过，有则直接返回，这里说明一个appender也可以被其他logger复用
        Appender appender = registryGet(appenderName);
        if ((appender != null)) {
            LogLog.debug("Appender \"" + appenderName + "\" was already parsed.");
            return appender;
        }
        // Appender was not previously initialized.
        // 构建appender的前缀名：log4j.appender. + 名称
        String prefix = APPENDER_PREFIX + appenderName;
        // 构建样式的前缀名：log4j.appender. + 名称 + .layout
        String layoutPrefix = prefix + ".layout";

        // 根据前缀名的配置value，反射获取appender的实例对象
        appender = (Appender) OptionConverter.instantiateByKey(props, prefix,
                org.apache.log4j.Appender.class,
                null);
        if (appender == null) {
            LogLog.error(
                    "Could not instantiate appender named \"" + appenderName + "\".");
            return null;
        }
        appender.setName(appenderName);

        // 扩展接口来激活其他选项设置
        if (appender instanceof OptionHandler) {
            // 检查appender是否需要样式要求
            if (appender.requiresLayout()) {
                Layout layout = (Layout) OptionConverter.instantiateByKey(props,
                        layoutPrefix,
                        Layout.class,
                        null);
                if (layout != null) {
                    appender.setLayout(layout);
                    LogLog.debug("Parsing layout options for \"" + appenderName + "\".");
                    //configureOptionHandler(layout, layoutPrefix + ".", props);
                    // 配置以样式前缀开头的样式对象属性值，使用内省的方式实现
                    PropertySetter.setProperties(layout, props, layoutPrefix + ".");
                    LogLog.debug("End of parsing for \"" + appenderName + "\".");
                }
            }

            // 检查appender错误处理器的配置
            final String errorHandlerPrefix = prefix + ".errorhandler";
            String errorHandlerClass = OptionConverter.findAndSubst(errorHandlerPrefix, props);
            if (errorHandlerClass != null) {
                ErrorHandler eh = (ErrorHandler) OptionConverter.instantiateByKey(props,
                        errorHandlerPrefix,
                        ErrorHandler.class,
                        null);
                if (eh != null) {
                    appender.setErrorHandler(eh);
                    LogLog.debug("Parsing errorhandler options for \"" + appenderName + "\".");
                    // 解析错误处理器
                    parseErrorHandler(eh, errorHandlerPrefix, props, repository);
                    final Properties edited = new Properties();
                    final String[] keys = new String[]{
                            errorHandlerPrefix + "." + ROOT_REF,
                            errorHandlerPrefix + "." + LOGGER_REF,
                            errorHandlerPrefix + "." + APPENDER_REF_TAG
                    };
                    for (Iterator iter = props.entrySet().iterator(); iter.hasNext(); ) {
                        Map.Entry entry = (Map.Entry) iter.next();
                        int i = 0;
                        for (; i < keys.length; i++) {
                            if (keys[i].equals(entry.getKey())) {
                                break;
                            }
                        }
                        if (i == keys.length) {
                            edited.put(entry.getKey(), entry.getValue());
                        }
                    }
                    PropertySetter.setProperties(eh, edited, errorHandlerPrefix + ".");
                    LogLog.debug("End of errorhandler parsing for \"" + appenderName + "\".");
                }

            }
            //configureOptionHandler((OptionHandler) appender, prefix + ".", props);
            // 设置appender的其他属性值
            PropertySetter.setProperties(appender, props, prefix + ".");
            LogLog.debug("Parsed \"" + appenderName + "\" options.");
        }

        // 解析appender的过滤器
        parseAppenderFilters(props, appenderName, appender);
        // 解析完成的appender加入缓存中
        registryPut(appender);
        return appender;
    }

    private void parseErrorHandler(
            final ErrorHandler eh,
            final String errorHandlerPrefix,
            final Properties props,
            final LoggerRepository hierarchy) {

        // 检查错误处理器根日志引用，默认不设置
        boolean rootRef = OptionConverter.toBoolean(
                OptionConverter.findAndSubst(errorHandlerPrefix + ROOT_REF, props), false);
        if (rootRef) {
            eh.setLogger(hierarchy.getRootLogger());
        }

        // 检查错误处理器的日志器名称，根据名称在日志容器中查找到logger，设置到错误处理器中
        String loggerName = OptionConverter.findAndSubst(errorHandlerPrefix + LOGGER_REF, props);
        if (loggerName != null) {
            Logger logger = (loggerFactory == null) ? hierarchy.getLogger(loggerName)
                    : hierarchy.getLogger(loggerName, loggerFactory);
            eh.setLogger(logger);
        }

        // 检查错误处理器的appender，有的话，设置错误处理器的后备appender
        String appenderName = OptionConverter.findAndSubst(errorHandlerPrefix + APPENDER_REF_TAG, props);
        if (appenderName != null) {
            Appender backup = parseAppender(props, appenderName);
            if (backup != null) {
                eh.setBackupAppender(backup);
            }
        }
    }


    void parseAppenderFilters(Properties props, String appenderName, Appender appender) {
        // extract filters and filter options from props into a hashtable mapping
        // the property name defining the filter class to a list of pre-parsed
        // name-value pairs associated to that filter
        final String filterPrefix = APPENDER_PREFIX + appenderName + ".filter.";
        int fIdx = filterPrefix.length();
        Hashtable filters = new Hashtable();
        Enumeration e = props.keys();
        String name = "";
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            // 遍历所有配置项，处理以过滤器前缀开头的配置，用K-V暂存下来，key为过滤器前缀+名称，value为名称-过滤值的数组
            if (key.startsWith(filterPrefix)) {
                int dotIdx = key.indexOf('.', fIdx);
                String filterKey = key;
                if (dotIdx != -1) {
                    filterKey = key.substring(0, dotIdx);
                    name = key.substring(dotIdx + 1);
                }
                Vector filterOpts = (Vector) filters.get(filterKey);
                if (filterOpts == null) {
                    filterOpts = new Vector();
                    filters.put(filterKey, filterOpts);
                }
                if (dotIdx != -1) {
                    String value = OptionConverter.findAndSubst(key, props);
                    filterOpts.add(new NameValue(name, value));
                }
            }
        }

        // sort filters by IDs, insantiate filters, set filter options,
        // add filters to the appender
        // 对过滤器按自然排序
        Enumeration g = new SortedKeyEnumeration(filters);
        while (g.hasMoreElements()) {
            String key = (String) g.nextElement();
            // 获取过滤去的全限定类名
            String clazz = props.getProperty(key);
            if (clazz != null) {
                LogLog.debug("Filter key: [" + key + "] class: [" + props.getProperty(key) + "] props: " + filters.get(key));
                // 反射获取过滤器实例
                Filter filter = (Filter) OptionConverter.instantiateByClassName(clazz, Filter.class, null);
                if (filter != null) {
                    // 设置过滤器属性
                    PropertySetter propSetter = new PropertySetter(filter);
                    Vector v = (Vector) filters.get(key);
                    Enumeration filterProps = v.elements();
                    while (filterProps.hasMoreElements()) {
                        NameValue kv = (NameValue) filterProps.nextElement();
                        propSetter.setProperty(kv.key, kv.value);
                    }
                    // 激活过滤器的选项设置
                    propSetter.activate();
                    LogLog.debug("Adding filter of type [" + filter.getClass()
                            + "] to appender named [" + appender.getName() + "].");
                    // 将过滤器添加至appender
                    appender.addFilter(filter);
                }
            } else {
                LogLog.warn("Missing class definition for filter: [" + key + "]");
            }
        }
    }


    void registryPut(Appender appender) {
        registry.put(appender.getName(), appender);
    }

    Appender registryGet(String name) {
        return (Appender) registry.get(name);
    }
}

class PropertyWatchdog extends FileWatchdog {

    PropertyWatchdog(String filename) {
        super(filename);
    }

    /**
     * Call {@link PropertyConfigurator#configure(String)} with the
     * <code>filename</code> to reconfigure log4j.
     */
    public void doOnChange() {
        new PropertyConfigurator().doConfigure(filename,
                LogManager.getLoggerRepository());
    }
}

class NameValue {
    String key, value;

    public NameValue(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String toString() {
        return key + "=" + value;
    }
}

class SortedKeyEnumeration implements Enumeration {

    private Enumeration e;

    public SortedKeyEnumeration(Hashtable ht) {
        Enumeration f = ht.keys();
        Vector keys = new Vector(ht.size());
        for (int i, last = 0; f.hasMoreElements(); ++last) {
            String key = (String) f.nextElement();
            for (i = 0; i < last; ++i) {
                String s = (String) keys.get(i);
                if (key.compareTo(s) <= 0) {
                    break;
                }
            }
            keys.add(i, key);
        }
        e = keys.elements();
    }

    public boolean hasMoreElements() {
        return e.hasMoreElements();
    }

    public Object nextElement() {
        return e.nextElement();
    }
}
