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

import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.QuietWriter;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;

import java.io.*;

// Contibutors: Jens Uwe Pipka <jens.pipka@gmx.de>
//              Ben Sandee

/**
 * WriterAppender appends log events to a {@link java.io.Writer} or an
 * {@link java.io.OutputStream} depending on the user's choice.
 *
 * 基于输出流的输出器
 *
 * @author Ceki G&uuml;lc&uuml;
 * @since 1.1
 */
public class WriterAppender extends AppenderSkeleton {


    /**
     * Immediate flush means that the underlying writer or output stream
     * will be flushed at the end of each append operation unless shouldFlush()
     * is overridden. Immediate
     * flush is slower but ensures that each append request is actually
     * written. If <code>immediateFlush</code> is set to
     * <code>false</code>, then there is a good chance that the last few
     * logs events are not actually written to persistent media if and
     * when the application crashes.
     *
     * <p>The <code>immediateFlush</code> variable is set to
     * <code>true</code> by default.
     *
     * 立刻刷新标记，默认true-立即刷新
     *
     */
    protected boolean immediateFlush = true;

    /**
     * The encoding to use when writing.  <p>The
     * <code>encoding</code> variable is set to <code>null</null> by
     * default which results in the utilization of the system's default
     * encoding.
     *
     * 编码
     *
     */
    protected String encoding;

    /**
     * This is the {@link QuietWriter quietWriter} where we will write
     * to.
     *
     * 输出流引用
     *
     */
    protected QuietWriter qw;


    /**
     * This default constructor does nothing.
     */
    public WriterAppender() {
    }

    /**
     * Instantiate a WriterAppender and set the output destination to a
     * new {@link OutputStreamWriter} initialized with <code>os</code>
     * as its {@link OutputStream}.
     */
    public WriterAppender(Layout layout, OutputStream os) {
        this(layout, new OutputStreamWriter(os));
    }

    /**
     * Instantiate a WriterAppender and set the output destination to
     * <code>writer</code>.
     *
     * <p>The <code>writer</code> must have been previously opened by
     * the user.
     */
    public WriterAppender(Layout layout, Writer writer) {
        this.layout = layout;
        this.setWriter(writer);
    }

    /**
     * If the <b>ImmediateFlush</b> option is set to
     * <code>true</code>, the appender will flush at the end of each
     * write. This is the default behavior. If the option is set to
     * <code>false</code>, then the underlying stream can defer writing
     * to physical medium to a later time.
     *
     * <p>Avoiding the flush operation at the end of each append results in
     * a performance gain of 10 to 20 percent. However, there is safety
     * tradeoff involved in skipping flushing. Indeed, when flushing is
     * skipped, then it is likely that the last few log events will not
     * be recorded on disk when the application exits. This is a high
     * price to pay even for a 20% performance gain.
     */
    public void setImmediateFlush(boolean value) {
        immediateFlush = value;
    }

    /**
     * Returns value of the <b>ImmediateFlush</b> option.
     */
    public boolean getImmediateFlush() {
        return immediateFlush;
    }

    /**
     * Does nothing.
     */
    public void activateOptions() {
    }


    /**
     * This method is called by the {@link AppenderSkeleton#doAppend}
     * method.
     *
     * <p>If the output stream exists and is writable then write a log
     * statement to the output stream. Otherwise, write a single warning
     * message to <code>System.err</code>.
     *
     * <p>The format of the output will depend on this appender's
     * layout.
     */
    public void append(LoggingEvent event) {

        // Reminder: the nesting of calls is:
        //
        //    doAppend()
        //      - check threshold
        //      - filter
        //      - append();
        //        - checkEntryConditions();
        //        - subAppend();

        // 检查关键条件，主要是输出流是否准备
        if (!checkEntryConditions()) {
            return;
        }

        // 输出
        subAppend(event);
    }

    /**
     * This method determines if there is a sense in attempting to append.
     *
     * <p>It checks whether there is a set output target and also if
     * there is a set layout. If these checks fail, then the boolean
     * value <code>false</code> is returned.
     */
    protected boolean checkEntryConditions() {
        if (this.closed) {
            LogLog.warn("Not allowed to write to a closed appender.");
            return false;
        }

        if (this.qw == null) {
            errorHandler.error("No output stream or file set for the appender named [" +
                    name + "].");
            return false;
        }

        if (this.layout == null) {
            errorHandler.error("No layout set for the appender named [" + name + "].");
            return false;
        }
        return true;
    }


    /**
     * Close this appender instance. The underlying stream or writer is
     * also closed.
     *
     * <p>Closed appenders cannot be reused.
     *
     * 关闭appender
     *
     * @see #setWriter
     * @since 0.8.4
     */
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        // 一旦关闭，则不能再次打开
        this.closed = true;
        // 输出样式页脚
        writeFooter();
        // 关闭流
        reset();
    }

    /**
     * Close the underlying {@link java.io.Writer}.
     */
    protected void closeWriter() {
        if (qw != null) {
            try {
                qw.close();
            } catch (IOException e) {
                if (e instanceof InterruptedIOException) {
                    Thread.currentThread().interrupt();
                }
                // There is do need to invoke an error handler at this late
                // stage.
                LogLog.error("Could not close " + qw, e);
            }
        }
    }

    /**
     * Returns an OutputStreamWriter when passed an OutputStream.  The
     * encoding used will depend on the value of the
     * <code>encoding</code> property.  If the encoding value is
     * specified incorrectly the writer will be opened using the default
     * system encoding (an error message will be printed to the loglog.
     *
     * 创建输出流
     *
     */
    protected OutputStreamWriter createWriter(OutputStream os) {
        OutputStreamWriter retval = null;

        String enc = getEncoding();
        if (enc != null) {
            try {
                // 指定编码
                retval = new OutputStreamWriter(os, enc);
            } catch (IOException e) {
                if (e instanceof InterruptedIOException) {
                    Thread.currentThread().interrupt();
                }
                LogLog.warn("Error initializing output writer.");
                LogLog.warn("Unsupported encoding?");
            }
        }
        if (retval == null) {
            retval = new OutputStreamWriter(os);
        }
        return retval;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String value) {
        encoding = value;
    }


    /**
     * Set the {@link ErrorHandler} for this WriterAppender and also the
     * underlying {@link QuietWriter} if any.
     */
    public synchronized void setErrorHandler(ErrorHandler eh) {
        if (eh == null) {
            LogLog.warn("You have tried to set a null error-handler.");
        } else {
            this.errorHandler = eh;
            if (this.qw != null) {
                this.qw.setErrorHandler(eh);
            }
        }
    }

    /**
     * <p>Sets the Writer where the log output will go. The
     * specified Writer must be opened by the user and be
     * writable.
     *
     * <p>The <code>java.io.Writer</code> will be closed when the
     * appender instance is closed.
     *
     *
     * <p><b>WARNING:</b> Logging to an unopened Writer will fail.
     * <p>
     *
     * @param writer An already opened Writer.
     */
    public synchronized void setWriter(Writer writer) {
        // 关闭原来的流，准备设置新的流
        reset();
        // 此处的异常处理器是一次失败处理器
        this.qw = new QuietWriter(writer, errorHandler);
        //this.tp = new TracerPrintWriter(qw);
        writeHeader();
    }


    /**
     * Actual writing occurs here.
     *
     * <p>Most subclasses of <code>WriterAppender</code> will need to
     * override this method.
     *
     * @since 0.9.0
     */
    protected void subAppend(LoggingEvent event) {
        // 使用样式处理消息
        // qw写出消息
        this.qw.write(this.layout.format(event));

        // 输出异常栈
        if (layout.ignoresThrowable()) {
            String[] s = event.getThrowableStrRep();
            if (s != null) {
                int len = s.length;
                for (int i = 0; i < len; i++) {
                    this.qw.write(s[i]);
                    this.qw.write(Layout.LINE_SEP);
                }
            }
        }

        // flush输出
        if (shouldFlush(event)) {
            this.qw.flush();
        }
    }


    /**
     * The WriterAppender requires a layout. Hence, this method returns
     * <code>true</code>.
     */
    public boolean requiresLayout() {
        return true;
    }

    /**
     * Clear internal references to the writer and other variables.
     * <p>
     * Subclasses can override this method for an alternate closing
     * behavior.
     */
    protected void reset() {
        // 关闭流
        closeWriter();
        this.qw = null;
        //this.tp = null;
    }


    /**
     * Write a footer as produced by the embedded layout's {@link
     * Layout#getFooter} method.
     */
    protected void writeFooter() {
        if (layout != null) {
            String f = layout.getFooter();
            if (f != null && this.qw != null) {
                this.qw.write(f);
                this.qw.flush();
            }
        }
    }

    /**
     * Write a header as produced by the embedded layout's {@link
     * Layout#getHeader} method.
     */
    protected void writeHeader() {
        // 从样式中获取头并输出
        if (layout != null) {
            String h = layout.getHeader();
            if (h != null && this.qw != null) {
                this.qw.write(h);
            }
        }
    }

    /**
     * Determines whether the writer should be flushed after
     * this event is written.
     *
     * @since 1.2.16
     */
    protected boolean shouldFlush(final LoggingEvent event) {
        return immediateFlush;
    }
}
