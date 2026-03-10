package com.sunny.datapillar.openlineage.pipeline;

import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import java.util.List;
import lombok.Getter;

/** Graph rebuild execution result. */
@Getter
public class GraphRebuildStats {

  private final int graphUpserts;
  private final List<EmbeddingTaskPayload> embeddingTasks;

  public GraphRebuildStats(int graphUpserts, List<EmbeddingTaskPayload> embeddingTasks) {
    this.graphUpserts = graphUpserts;
    this.embeddingTasks = embeddingTasks;
  }

  public int graphUpserts() {
    return graphUpserts;
  }

  public List<EmbeddingTaskPayload> embeddingTasks() {
    return embeddingTasks;
  }
}
