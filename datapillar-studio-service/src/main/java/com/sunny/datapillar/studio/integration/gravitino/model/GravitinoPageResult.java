package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.List;
import lombok.Data;

@Data
public class GravitinoPageResult<T> {

  private List<T> items;

  private long total;

  private int offset;

  private int limit;
}
