package com.sunny.datapillar.studio.dto.semantic.response;

import java.util.List;
import lombok.Data;

@Data
public class PageResponse<T> {

  private List<T> items;

  private long total;

  private int offset;

  private int limit;
}
