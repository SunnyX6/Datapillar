/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.exceptions;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** 当指定版本的指标不存在时抛出的异常 */
public class NoSuchMetricVersionException extends NotFoundException {

  /**
   * 使用指定的详细消息构造新异常
   *
   * @param message 详细消息
   * @param args 消息的参数
   */
  @FormatMethod
  public NoSuchMetricVersionException(@FormatString String message, Object... args) {
    super(message, args);
  }

  /**
   * 使用指定的详细消息和原因构造新异常
   *
   * @param cause 原因
   * @param message 详细消息
   * @param args 消息的参数
   */
  @FormatMethod
  public NoSuchMetricVersionException(
      Throwable cause, @FormatString String message, Object... args) {
    super(cause, message, args);
  }
}
