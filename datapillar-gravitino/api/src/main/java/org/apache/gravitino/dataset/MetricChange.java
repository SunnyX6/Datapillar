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

/** Indicator change interface */
@Evolving
public interface MetricChange {

  /**
   * Create indicator rename changes
   *
   * @param newName New indicator name
   * @return Indicator change object
   */
  static MetricChange rename(String newName) {
    return new RenameMetric(newName);
  }

  /**
   * Create changes that update indicator annotations
   *
   * @param newComment new annotation
   * @return Indicator change object
   */
  static MetricChange updateComment(String newComment) {
    return new UpdateComment(newComment);
  }

  /**
   * Create changes that update data types
   *
   * @param newDataType new data type
   * @return Indicator change object
   */
  static MetricChange updateDataType(String newDataType) {
    return new UpdateDataType(newDataType);
  }

  /**
   * Create changes to settings properties
   *
   * @param property attribute name
   * @param value attribute value
   * @return Indicator change object
   */
  static MetricChange setProperty(String property, String value) {
    return new SetProperty(property, value);
  }

  /**
   * Create changes that delete attributes
   *
   * @param property attribute name
   * @return Indicator change object
   */
  static MetricChange removeProperty(String property) {
    return new RemoveProperty(property);
  }

  /** Indicator rename changes */
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

  /** Update indicator annotation changes */
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

  /** Update data type changes */
  final class UpdateDataType implements MetricChange {
    private final String newDataType;

    private UpdateDataType(String newDataType) {
      this.newDataType = newDataType;
    }

    public String newDataType() {
      return newDataType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UpdateDataType)) return false;
      UpdateDataType that = (UpdateDataType) o;
      return Objects.equals(newDataType, that.newDataType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(newDataType);
    }

    @Override
    public String toString() {
      return "UPDATEDATATYPE " + newDataType;
    }
  }

  /** Set property changes */
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

  /** Delete attribute changes */
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
