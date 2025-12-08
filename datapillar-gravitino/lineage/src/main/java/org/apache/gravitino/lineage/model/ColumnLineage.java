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
package org.apache.gravitino.lineage.model;

/** Represents column-level lineage */
public class ColumnLineage {
  private String sourceDataset;
  private String sourceColumn;
  private String targetDataset;
  private String targetColumn;
  private String transformation;
  private String direction; // "upstream" or "downstream"

  public ColumnLineage() {}

  public String getSourceDataset() {
    return sourceDataset;
  }

  public void setSourceDataset(String sourceDataset) {
    this.sourceDataset = sourceDataset;
  }

  public String getSourceColumn() {
    return sourceColumn;
  }

  public void setSourceColumn(String sourceColumn) {
    this.sourceColumn = sourceColumn;
  }

  public String getTargetDataset() {
    return targetDataset;
  }

  public void setTargetDataset(String targetDataset) {
    this.targetDataset = targetDataset;
  }

  public String getTargetColumn() {
    return targetColumn;
  }

  public void setTargetColumn(String targetColumn) {
    this.targetColumn = targetColumn;
  }

  public String getTransformation() {
    return transformation;
  }

  public void setTransformation(String transformation) {
    this.transformation = transformation;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }
}
