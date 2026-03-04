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

/** Indicator version change interface */
@Evolving
public interface MetricVersionChange {

  /**
   * Create changes that update comments
   *
   * @param newComment new annotation
   * @return Change object
   */
  static MetricVersionChange updateComment(String newComment) {
    return new UpdateComment(newComment);
  }

  /**
   * Create changes to settings properties
   *
   * @param property attribute name
   * @param value attribute value
   * @return Change object
   */
  static MetricVersionChange setProperty(String property, String value) {
    return new SetProperty(property, value);
  }

  /**
   * Create changes that delete attributes
   *
   * @param property attribute name
   * @return Change object
   */
  static MetricVersionChange removeProperty(String property) {
    return new RemoveProperty(property);
  }

  /** Update note changes */
  final class UpdateComment implements MetricVersionChange {
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

  /** Set property changes */
  final class SetProperty implements MetricVersionChange {
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

  /** Delete attribute changes */
  final class RemoveProperty implements MetricVersionChange {
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
