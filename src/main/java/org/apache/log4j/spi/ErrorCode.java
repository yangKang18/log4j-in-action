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


/**
 * This interface defines commonly encoutered error codes.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @since 0.9.0
 */
public interface ErrorCode {

    // 通用错误
    public final int GENERIC_FAILURE = 0;
    // 写出错误，输出流输出时报的错
    public final int WRITE_FAILURE = 1;
    // 刷新错误，需要刷新输出流时可能会报的错误
    public final int FLUSH_FAILURE = 2;
    // 关闭错误
    public final int CLOSE_FAILURE = 3;
    // 文件打开错误，文件输出器开发文件时报的错
    public final int FILE_OPEN_FAILURE = 4;
    // 样式丢失错误
    public final int MISSING_LAYOUT = 5;
    // 地址转换错误
    public final int ADDRESS_PARSE_FAILURE = 6;
}
