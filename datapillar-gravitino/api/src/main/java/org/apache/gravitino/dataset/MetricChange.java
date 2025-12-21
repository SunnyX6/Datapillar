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
package org.apache.gravitino.dataset;

import java.util.Objects;
import org.apache.gravitino.annotation.Evolving;

/** 指标变更接口 */
@Evolving
public interface MetricChange {

  /**
   * 创建指标重命名变更
   *
   * @param newName 新的指标名称
   * @return 指标变更对象
   */
  static MetricChange rename(String newName) {
    return new RenameMetric(newName);
  }

  /**
   * 创建更新指标注释的变更
   *
   * @param newComment 新的注释
   * @return 指标变更对象
   */
  static MetricChange updateComment(String newComment) {
    return new UpdateComment(newComment);
  }

  /**
   * 创建设置属性的变更
   *
   * @param property 属性名
   * @param value 属性值
   * @return 指标变更对象
   */
  static MetricChange setProperty(String property, String value) {
    return new SetProperty(property, value);
  }

  /**
   * 创建删除属性的变更
   *
   * @param property 属性名
   * @return 指标变更对象
   */
  static MetricChange removeProperty(String property) {
    return new RemoveProperty(property);
  }

  /** 指标重命名变更 */
  final class RenameMetric implements MetricChange {
    private final String newName;

    private RenameMetric(String newName) {
      this.newName = newName;
    }

    public String newName() {
      return newName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof RenameMetric)) return false;
      RenameMetric that = (RenameMetric) o;
      return Objects.equals(newName, that.newName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(newName);
    }

    @Override
    public String toString() {
      return "RENAMEMETRIC " + newName;
    }
  }

  /** 更新指标注释变更 */
  final class UpdateComment implements MetricChange {
    private final String newComment;

    private UpdateComment(String newComment) {
      this.newComment = newComment;
    }

    public String newComment() {
      return newComment;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UpdateComment)) return false;
      UpdateComment that = (UpdateComment) o;
      return Objects.equals(newComment, that.newComment);
    }

    @Override
    public int hashCode() {
      return Objects.hash(newComment);
    }

    @Override
    public String toString() {
      return "UPDATECOMMENT " + newComment;
    }
  }

  /** 设置属性变更 */
  final class SetProperty implements MetricChange {
    private final String property;
    private final String value;

    private SetProperty(String property, String value) {
      this.property = property;
      this.value = value;
    }

    public String property() {
      return property;
    }

    public String value() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SetProperty)) return false;
      SetProperty that = (SetProperty) o;
      return Objects.equals(property, that.property) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(property, value);
    }

    @Override
    public String toString() {
      return "SETPROPERTY " + property + " " + value;
    }
  }

  /** 删除属性变更 */
  final class RemoveProperty implements MetricChange {
    private final String property;

    private RemoveProperty(String property) {
      this.property = property;
    }

    public String property() {
      return property;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof RemoveProperty)) return false;
      RemoveProperty that = (RemoveProperty) o;
      return Objects.equals(property, that.property);
    }

    @Override
    public int hashCode() {
      return Objects.hash(property);
    }

    @Override
    public String toString() {
      return "REMOVEPROPERTY " + property;
    }
  }
}
