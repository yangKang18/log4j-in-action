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

package org.apache.log4j.helpers;

import org.apache.log4j.spi.LoggingEvent;

/**
 * <p>PatternConverter is an abtract class that provides the
 * formatting functionality that derived classes need.
 *
 * <p>Conversion specifiers in a conversion patterns are parsed to
 * individual PatternConverters. Each of which is responsible for
 * converting a logging event in a converter specific manner.
 *
 * 样式转化器
 *
 * @author <a href="mailto:cakalijp@Maritz.com">James P. Cakalic</a>
 * @author Ceki G&uuml;lc&uuml;
 * @since 0.8.2
 */
public abstract class PatternConverter {
    // 下一个转化器的引用
    public PatternConverter next;
    // 最小值
    int min = -1;
    // 最大值
    int max = 0x7FFFFFFF;
    // 左对齐标记
    boolean leftAlign = false;

    protected PatternConverter() {
    }

    protected PatternConverter(FormattingInfo fi) {
        min = fi.min;
        max = fi.max;
        leftAlign = fi.leftAlign;
    }

    /**
     * Derived pattern converters must override this method in order to
     * convert conversion specifiers in the correct way.
     */
    abstract
    protected String convert(LoggingEvent event);

    /**
     * A template method for formatting in a converter specific way.
     */
    public void format(StringBuffer sbuf, LoggingEvent e) {
        // 转化消息
        String s = convert(e);

        // 没有字符的化
        if (s == null) {
            // 看最小空格，大于0 ，追加空格
            if (0 < min) {
                spacePad(sbuf, min);
            }
            return;
        }

        int len = s.length();

        if (len > max) {
            // 大于最大数，则保留后面部分，前面超出的截取掉
            sbuf.append(s.substring(len - max));
        } else if (len < min) {
            // 如果小于最小数，看是否左对齐，左对齐则先追加消息再补空格
            if (leftAlign) {
                sbuf.append(s);
                spacePad(sbuf, min - len);
            } else {
                // 先补空格再追加消息
                spacePad(sbuf, min - len);
                sbuf.append(s);
            }
        } else {
            sbuf.append(s);
        }
    }

    static String[] SPACES = {" ", "  ", "    ", "        ", //1,2,4,8 spaces
            "                ", // 16 spaces
            "                                "}; // 32 spaces

    /**
     * Fast space padding method.
     */
    public void spacePad(StringBuffer sbuf, int length) {
        // 超过32个字符长度的只补32个空格
        while (length >= 32) {
            sbuf.append(SPACES[5]);
            length -= 32;
        }

        // 1-3 - 1，4-7 - 2， 5-8 - 4， 9-15 - 8， 16-31 - 16
        for (int i = 4; i >= 0; i--) {
            if ((length & (1 << i)) != 0) {
                sbuf.append(SPACES[i]);
            }
        }
    }
}
