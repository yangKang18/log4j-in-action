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
package org.apache.log4j;

import org.apache.log4j.spi.ThrowableRenderer;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;

/**
 * Default implementation of ThrowableRenderer using
 * Throwable.printStackTrace.
 *
 * 默认的异常渲染器
 * 处理异常栈
 *
 * @since 1.2.16
 */
public final class DefaultThrowableRenderer implements ThrowableRenderer {
    /**
     * Construct new instance.
     */
    public DefaultThrowableRenderer() {

    }


    /**
     * {@inheritDoc}
     */
    public String[] doRender(final Throwable throwable) {
        return render(throwable);
    }

    /**
     * Render throwable using Throwable.printStackTrace.
     *
     * 有个疑问，这里使用了流操作，操作完了为什么不关闭流？
     *
     * @param throwable throwable, may not be null.
     * @return string representation.
     */
    public static String[] render(final Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            // 异常信息输出到打印流
            throwable.printStackTrace(pw);
        } catch (RuntimeException ex) {
        }
        pw.flush();
        // 按行读取栈异常信息
        LineNumberReader reader = new LineNumberReader(
                new StringReader(sw.toString()));
        ArrayList lines = new ArrayList();
        try {
            String line = reader.readLine();
            while (line != null) {
                // 读出的信息添加至字符串数组
                lines.add(line);
                line = reader.readLine();
            }
        } catch (IOException ex) {
            if (ex instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            lines.add(ex.toString());
        }
        String[] tempRep = new String[lines.size()];
        lines.toArray(tempRep);
        return tempRep;
    }
}
