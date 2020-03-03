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

package org.apache.log4j.spi;

import org.apache.log4j.Appender;
import org.apache.log4j.Category;

/**
 * Listen to events occuring within a {@link
 * org.apache.log4j.Hierarchy Hierarchy}.
 *
 * 日志容器事件监听器
 * 主要监听2个事件：添加appender和删除appender
 *
 * @author Ceki G&uuml;lc&uuml;
 * @since 1.2
 */
public interface HierarchyEventListener {


    //public
    //void categoryCreationEvent(Category cat);

    /**
     * 添加appender事件
     * @param cat
     * @param appender
     */
    public void addAppenderEvent(Category cat, Appender appender);


    /**
     * 移出appender事件
     * @param cat
     * @param appender
     */
    public void removeAppenderEvent(Category cat, Appender appender);


}
