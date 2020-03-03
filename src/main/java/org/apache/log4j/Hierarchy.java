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

// WARNING This class MUST not have references to the Category or
// WARNING RootCategory classes in its static initiliazation neither
// WARNING directly nor indirectly.

// Contributors:
//                Luke Blanshard <luke@quiq.com>
//                Mario Schomburg - IBM Global Services/Germany
//                Anders Kristensen
//                Igor Poteryaev

package org.apache.log4j;


import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.or.ObjectRenderer;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.spi.*;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class is specialized in retrieving loggers by name and also
 * maintaining the logger hierarchy.
 *
 * <p><em>The casual user does not have to deal with this class
 * directly.</em>
 *
 * <p>The structure of the logger hierarchy is maintained by the
 * {@link #getLogger} method. The hierarchy is such that children link
 * to their parent but parents do not have any pointers to their
 * children. Moreover, loggers can be instantiated in any order, in
 * particular descendant before ancestor.
 *
 * <p>In case a descendant is created before a particular ancestor,
 * then it creates a provision node for the ancestor and adds itself
 * to the provision node. Other descendants of the same ancestor add
 * themselves to the previously created provision node.
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class Hierarchy implements LoggerRepository, RendererSupport, ThrowableRendererSupport {

    /**
     * 默认日志工厂，创建logger实例
     */
    private LoggerFactory defaultFactory;

    /**
     * 监听器集合
     */
    private Vector listeners;

    /**
     * 日志容器，存放所有注册的logger
     */
    Hashtable ht;
    /** 根日志对象引用 */
    Logger root;
    /** 渲染器集合 */
    RendererMap rendererMap;

    /** 日志容器阈值，在容器级别上限制日志输出级别 */
    int thresholdInt;
    Level threshold;

    /** 无appender警告信号 */
    boolean emittedNoAppenderWarning = false;
    /** 无绑定文件警号信号 */
    boolean emittedNoResourceBundleWarning = false;

    /** 异常渲染器 */
    private ThrowableRenderer throwableRenderer = null;

    /**
     * Create a new logger hierarchy.
     *
     * @param root The root of the new hierarchy.
     */
    public Hierarchy(Logger root) {
        // 初始化日志容器
        ht = new Hashtable();
        // 初始化监听器集合
        listeners = new Vector(1);
        // 指定根日志logger
        this.root = root;
        // Enable all level levels by default.
        // 设置容器阈值，默认所有级别都通行
        setThreshold(Level.ALL);
        // 对根日志暴露容器的引用
        this.root.setHierarchy(this);
        // 初始化渲染器几个
        rendererMap = new RendererMap();
        // 指定日志工厂为默认的工厂类，直接new Logger()
        defaultFactory = new DefaultCategoryFactory();
    }


    /**
     * =================================================================================================
     *  日志容器类支持
     * =================================================================================================
     *
     * 添加监听器
     *
     * @param listener
     */
    public void addHierarchyEventListener(HierarchyEventListener listener) {
        if (listeners.contains(listener)) {
            LogLog.warn("Ignoring attempt to add an existent listener.");
        } else {
            listeners.addElement(listener);
        }
    }

    /**
     * This call will clear all logger definitions from the internal
     * hashtable. Invoking this method will irrevocably mess up the
     * logger hierarchy.
     *
     * <p>You should <em>really</em> know what you are doing before
     * invoking this method.
     *
     * 清空日志容器中所有的logger
     *
     * @since 0.9.0
     */
    public void clear() {
        //System.out.println("\n\nAbout to clear internal hash table.");
        ht.clear();
    }


    /**
     * 如果没有appender是，会使用内部日志打印警告，且只会打印一次，
     * @param cat
     */
    public void emitNoAppenderWarning(Category cat) {
        // No appenders in hierarchy, warn user only once.
        if (!this.emittedNoAppenderWarning) {
            LogLog.warn("No appenders could be found for logger (" +
                    cat.getName() + ").");
            LogLog.warn("Please initialize the log4j system properly.");
            LogLog.warn("See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.");
            this.emittedNoAppenderWarning = true;
        }
    }


    /**
     * Check if the named logger exists in the hierarchy. If so return
     * its reference, otherwise returns <code>null</code>.
     *
     * 检查日志容器中是否包含指定名字的logger，没有返回null
     *
     * @param name The name of the logger to search for.
     */
    public Logger exists(String name) {
        Object o = ht.get(new CategoryKey(name));
        if (o instanceof Logger) {
            return (Logger) o;
        } else {
            return null;
        }
    }

    /**
     * 设置日志容器的阈值，根据字符串转化为级别对象
     *
     * The string form of {@link #setThreshold(Level)}.
     */
    public void setThreshold(String levelStr) {
        Level l = Level.toLevel(levelStr, null);
        if (l != null) {
            setThreshold(l);
        } else {
            LogLog.warn("Could not convert [" + levelStr + "] to Level.");
        }
    }


    /**
     * Enable logging for logging requests with level <code>l</code> or
     * higher. By default all levels are enabled.
     *
     * 设置日志容器的阈值
     * threshold和thresholdInt是一组，设置是一同变更的
     *
     * @param l The minimum level for which logging requests are sent to
     *          their appenders.
     */
    public void setThreshold(Level l) {
        if (l != null) {
            thresholdInt = l.level;
            threshold = l;
        }
    }


    /**
     * 触发添加appender事件
     *
     * @param logger
     * @param appender
     */
    public void fireAddAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            int size = listeners.size();
            HierarchyEventListener listener;
            // 遍历监听器集合，处理添加事件
            for (int i = 0; i < size; i++) {
                listener = (HierarchyEventListener) listeners.elementAt(i);
                listener.addAppenderEvent(logger, appender);
            }
        }
    }

    /**
     * 触发移出appender事件
     *
     * @param logger
     * @param appender
     */
    void fireRemoveAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            int size = listeners.size();
            HierarchyEventListener listener;
            // 遍历监听器集合，处理移出事件
            for (int i = 0; i < size; i++) {
                listener = (HierarchyEventListener) listeners.elementAt(i);
                listener.removeAppenderEvent(logger, appender);
            }
        }
    }

    /**
     * Returns a {@link Level} representation of the <code>enable</code>
     * state.
     *
     * @since 1.2
     */
    public Level getThreshold() {
        return threshold;
    }

    /**
     Returns an integer representation of the this repository's
     threshold.

     @since 1.2 */
    //public
    //int getThresholdInt() {
    //  return thresholdInt;
    //}


    /**
     * Return a new logger instance named as the first parameter using
     * the default factory.
     *
     * <p>If a logger of that name already exists, then it will be
     * returned.  Otherwise, a new logger will be instantiated and
     * then linked with its existing ancestors as well as children.
     *
     * @param name The name of the logger to retrieve.
     */
    public Logger getLogger(String name) {
        return getLogger(name, defaultFactory);
    }

    /**
     * Return a new logger instance named as the first parameter using
     * <code>factory</code>.
     *
     * <p>If a logger of that name already exists, then it will be
     * returned.  Otherwise, a new logger will be instantiated by the
     * <code>factory</code> parameter and linked with its existing
     * ancestors as well as children.
     *
     * @param name    The name of the logger to retrieve.
     * @param factory The factory that will make the new logger instance.
     */
    public Logger getLogger(String name, LoggerFactory factory) {
        //System.out.println("getInstance("+name+") called.");
        // 该对象是的核心功能是提升在ht遍历查询中的效率
        CategoryKey key = new CategoryKey(name);
        // Synchronize to prevent write conflicts. Read conflicts (in
        // getChainedLevel method) are possible only if variable
        // assignments are non-atomic.
        Logger logger;

        synchronized (ht) {
            Object o = ht.get(key);
            if (o == null) {
                // 通过key查询logger，容器没有，则使用工厂创建Logger实例，设置日志容器引用
                logger = factory.makeNewLoggerInstance(name);
                logger.setHierarchy(this);
                ht.put(key, logger);
                // 更新父级日志，此处涉及logger是以何种方式存在于容器中的
                updateParents(logger);
                return logger;
            } else if (o instanceof Logger) {
                // 如果直接找到该日志则直接返回
                return (Logger) o;
            } else if (o instanceof ProvisionNode) {
                // 如果是临时备用节点，则把该节点实例化，由临时节点变成logger
                //System.out.println("("+name+") ht.get(this) returned ProvisionNode");
                logger = factory.makeNewLoggerInstance(name);
                logger.setHierarchy(this);
                ht.put(key, logger);
                updateChildren((ProvisionNode) o, logger);
                updateParents(logger);
                return logger;
            } else {
                // It should be impossible to arrive here
                // 从ht存储结构可以看出来，要么不存在，要么是logger，要么是备用节点。
                // 所以不可能到这一步，作者英文很俏皮：but let's keep the compiler happy -- 让编译器爽爽
                return null;  // but let's keep the compiler happy.
            }
        }
    }

    /**
     * Returns all the currently defined categories in this hierarchy as
     * an {@link java.util.Enumeration Enumeration}.
     *
     * <p>The root logger is <em>not</em> included in the returned
     * {@link Enumeration}.
     */
    public Enumeration getCurrentLoggers() {
        // The accumlation in v is necessary because not all elements in
        // ht are Logger objects as there might be some ProvisionNodes
        // as well.
        Vector v = new Vector(ht.size());
        // 获取所有logger实例，不包含备用节点
        Enumeration elems = ht.elements();
        while (elems.hasMoreElements()) {
            Object o = elems.nextElement();
            if (o instanceof Logger) {
                v.addElement(o);
            }
        }
        return v.elements();
    }

    /**
     * @deprecated Please use {@link #getCurrentLoggers} instead.
     */
    public Enumeration getCurrentCategories() {
        return getCurrentLoggers();
    }



    /**
     * Get the root of this hierarchy.
     *
     * @since 0.9.0
     */
    public Logger getRootLogger() {
        return root;
    }

    /**
     * This method will return <code>true</code> if this repository is
     * disabled for <code>level</code> object passed as parameter and
     * <code>false</code> otherwise. See also the {@link
     * #setThreshold(Level) threshold} emthod.
     *
     * 这个是日志容器阈值使用的主要方式，默认的日志容器阈值为Level.ALL，即thresholdInt = Integer.MIN_VALUE
     * 所以默认这个方法返回为false，即容器阈值不生效
     *
     * 假如日志容器设置为info级别，则info级别以下的日志打印行为在容器看来是不可行的，
     *
     */
    public boolean isDisabled(int level) {
        return thresholdInt > level;
    }

    /**
     * @deprecated Deprecated with no replacement.
     */
    public void overrideAsNeeded(String override) {
        LogLog.warn("The Hiearchy.overrideAsNeeded method has been deprecated.");
    }

    /**
     * Reset all values contained in this hierarchy instance to their
     * default.  This removes all appenders from all categories, sets
     * the level of all non-root categories to <code>null</code>,
     * sets their additivity flag to <code>true</code> and sets the level
     * of the root logger to {@link Level#DEBUG DEBUG}.  Moreover,
     * message disabling is set its default "off" value.
     *
     * <p>Existing categories are not removed. They are just reset.
     *
     * <p>This method should be used sparingly and with care as it will
     * block all logging until it is completed.</p>
     *
     * 重置配置方法
     *
     * @since 0.8.5
     */
    public void resetConfiguration() {

        // 根日志级别设置为debug
        getRootLogger().setLevel(Level.DEBUG);
        // 移出绑定资源
        root.setResourceBundle(null);
        // 设置日志容器级别为最低
        setThreshold(Level.ALL);

        // the synchronization is needed to prevent JDK 1.2.x hashtable
        // surprises
        synchronized (ht) {
            // 关闭日志
            shutdown(); // nested locks are OK

            // 容器中所有的logger实例都重置级别，继承属性和绑定资源
            Enumeration cats = getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.setLevel(null);
                c.setAdditivity(true);
                c.setResourceBundle(null);
            }
        }
        // 清空渲染器集合和异常渲染器
        rendererMap.clear();
        throwableRenderer = null;
    }

    /**
     * Does nothing.
     *
     * @deprecated Deprecated with no replacement.
     */
    public void setDisableOverride(String override) {
        LogLog.warn("The Hiearchy.setDisableOverride method has been deprecated.");
    }


    /**
     * Used by subclasses to add a renderer to the hierarchy passed as parameter.
     */
    public void setRenderer(Class renderedClass, ObjectRenderer renderer) {
        rendererMap.put(renderedClass, renderer);
    }


    /**
     * Shutting down a hierarchy will <em>safely</em> close and remove
     * all appenders in all categories including the root logger.
     *
     * <p>Some appenders such as {@link org.apache.log4j.net.SocketAppender}
     * and {@link AsyncAppender} need to be closed before the
     * application exists. Otherwise, pending logging events might be
     * lost.
     *
     * <p>The <code>shutdown</code> method is careful to close nested
     * appenders before closing regular appenders. This is allows
     * configurations where a regular appender is attached to a logger
     * and again to a nested appender.
     *
     * 关闭logger的所有appender，然后移出所有的appender
     * 因为有些appender涉及IO操作，这些操作在appender生成时就存在了，在移除appender时，对他们进行关闭
     *
     * @since 1.0
     */
    public void shutdown() {
        Logger root = getRootLogger();

        // begin by closing nested appenders
        // 关闭根日志的所有的appender
        root.closeNestedAppenders();

        synchronized (ht) {
            // 关闭其他所有非根日志的appender
            Enumeration cats = this.getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.closeNestedAppenders();
            }

            // then, remove all appenders
            // 移除日志的所有appender
            root.removeAllAppenders();
            cats = this.getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.removeAllAppenders();
            }
        }
    }


    /**
     * This method loops through all the *potential* parents of
     * 'cat'. There 3 possible cases:
     * <p>
     * 1) No entry for the potential parent of 'cat' exists
     * <p>
     * We create a ProvisionNode for this potential parent and insert
     * 'cat' in that provision node.
     * <p>
     * 2) There entry is of type Logger for the potential parent.
     * <p>
     * The entry is 'cat's nearest existing parent. We update cat's
     * parent field with this entry. We also break from the loop
     * because updating our parent's parent is our parent's
     * responsibility.
     * <p>
     * 3) There entry is of type ProvisionNode for this potential parent.
     * <p>
     * We add 'cat' to the list of children for this potential parent.
     *
     * 更新日志的父级：
     * 默认情况下，root是所有logger的根父级，也是唯一父级，但是在设计中，logger的名称一般以全限定类名来命名，于是产生
     * 了类似：a.b.c.d.e这样的命名，在log4j里面，a.b.c.d.e为生成logger对象，同时会保留4个临时备用节点：
     * a
     * a.b
     * a.b.c
     * a.b.c.d
     * 所有的节点是单链条关系，由最下面的节点中的parent指向上一个节点
     *
     * 这里可以看出在保存logger的ht容器里面，有两种对象结构：
     * 1. logger对象，且每个logger有parent，最终的parent是root
     * 2. ProvisionNode备用节点，且每个备用节点没有parent，但是维护了一个集合，集合中都是具体的logger对象，也就是说备用节点是这个logger集合的潜在parent
     *
     */
    final
    private void updateParents(Logger cat) {
        String name = cat.name;
        int length = name.length();
        boolean parentFound = false;

        //System.out.println("UpdateParents called for " + name);

        // if name = "w.x.y.z", loop thourgh "w.x.y", "w.x" and "w", but not "w.x.y.z"
        // 检查日志名称是否包含“.”，如果没有，则直接设置cat的parent指向root日志，如果有：a.b.c.d.e
        for (int i = name.lastIndexOf('.', length - 1); i >= 0;i = name.lastIndexOf('.', i - 1)) {
            // 截取至最后一个“.”之前：a.b.c.d
            String substr = name.substring(0, i);

            //System.out.println("Updating parent : " + substr);
            // 包装key，并检查日志容器中是否有该logger实例
            CategoryKey key = new CategoryKey(substr); // simple constructor
            Object o = ht.get(key);
            // Create a provision node for a future parent.
            if (o == null) {
                //System.out.println("No parent "+substr+" found. Creating ProvisionNode.");
                // 如果没有该节点实例，则把该key设置为临时备用节点
                ProvisionNode pn = new ProvisionNode(cat);
                ht.put(key, pn);
            } else if (o instanceof Category) {
                // 如果有该节点，说明节点对象完整，直接把cat挂靠在该节点上，因为logger一定是有parent并且后续的节点处理完了，所以到此就可以跳出后续逻辑了
                parentFound = true;
                cat.parent = (Category) o;
                //System.out.println("Linking " + cat.name + " -> " + ((Category) o).name);
                break; // no need to update the ancestors of the closest ancestor
            } else if (o instanceof ProvisionNode) {
                // 如果也是该节点，则在该节点下保存cat的引用
                ((ProvisionNode) o).addElement(cat);
            } else {
                Exception e = new IllegalStateException("unexpected object type " +
                        o.getClass() + " in ht.");
                e.printStackTrace();
            }
        }
        // If we could not find any existing parents, then link with root.
        if (!parentFound) {
            cat.parent = root;
        }
    }

    /**
     * We update the links for all the children that placed themselves
     * in the provision node 'pn'. The second argument 'cat' is a
     * reference for the newly created Logger, parent of all the
     * children in 'pn'
     * <p>
     * We loop on all the children 'c' in 'pn':
     * <p>
     * If the child 'c' has been already linked to a child of
     * 'cat' then there is no need to update 'c'.
     * <p>
     * Otherwise, we set cat's parent field to c's parent and set
     * c's parent field to cat.
     */
    final
    private void updateChildren(ProvisionNode pn, Logger logger) {
        //System.out.println("updateChildren called for " + logger.name);
        final int last = pn.size();

        // 遍历备用节点中的logger集合，因为logger的结构是单链的，备用节点可能就是这些logger的直接parent了
        for (int i = 0; i < last; i++) {
            Logger l = (Logger) pn.elementAt(i);
            //System.out.println("Updating child " +p.name);

            // Unless this child already points to a correct (lower) parent,
            // make cat.parent point to l.parent and l.parent to cat.
            // logger集合中的元素的父级logger不是已现logger名字开头的，就调整parent关联
            // 比如：临时节点a.b，含有a.b.c和a.b.d两个logger
            // 其中a.b.c的parent为root，其名称root和临时节点的名称a.b不匹配
            // 此时，设置a.b的parent指向a.b.c的parent root，a.b.c的parent指向该logger
            // 最终链式关系为root  -->  a.b  --> a.b.c，相当于把备用临时节点添加到root和a.b.c这条链式关系中

            // 因为临时节点本身a.b也是含有“.”的命名结构，所以理论上也会存在名称为a的备用节点，
            // 所以该方法结束后，还需要调用updateParents方法，已更新所有的备用节点
            if (!l.parent.name.startsWith(logger.name)) {
                logger.parent = l.parent;
                l.parent = logger;
            }
        }
    }



    /**
     *  ==========================================================================================
     *  渲染器接口支持方法
     *  ==========================================================================================
     *
     *  添加渲染器
     *
     */
    public void addRenderer(Class classToRender, ObjectRenderer or) {
        rendererMap.put(classToRender, or);
    }

    /**
     * Get the renderer map for this hierarchy.
     */
    public RendererMap getRendererMap() {
        return rendererMap;
    }


    /**
     *  ==========================================================================================
     *  异常渲染器接口支持方法
     *  ==========================================================================================
     *
     * 设置异常渲染器
     *
     * {@inheritDoc}
     */
    public void setThrowableRenderer(final ThrowableRenderer renderer) {
        throwableRenderer = renderer;
    }

    /**
     * {@inheritDoc}
     */
    public ThrowableRenderer getThrowableRenderer() {
        return throwableRenderer;
    }


}


