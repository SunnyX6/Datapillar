package com.sunny.datapillar.openlineage.web;

import com.sunny.datapillar.openlineage.web.dto.request.DlqReplayRequest;
import com.sunny.datapillar.openlineage.web.dto.request.EventIngestRequest;
import com.sunny.datapillar.openlineage.web.dto.request.RebuildRequest;
import com.sunny.datapillar.openlineage.web.dto.request.SearchRequest;
import com.sunny.datapillar.openlineage.web.dto.request.SetEmbeddingRequest;
import com.sunny.datapillar.openlineage.web.dto.request.Text2CypherRequest;
import com.sunny.datapillar.openlineage.web.dto.response.DlqReplayResponse;
import com.sunny.datapillar.openlineage.web.dto.response.EventIngestAckResponse;
import com.sunny.datapillar.openlineage.web.dto.response.InitialGraphResponse;
import com.sunny.datapillar.openlineage.web.dto.response.RebuildResponse;
import com.sunny.datapillar.openlineage.web.dto.response.SearchResponse;
import com.sunny.datapillar.openlineage.web.dto.response.SetEmbeddingResponse;
import com.sunny.datapillar.openlineage.web.dto.response.Text2CypherResponse;
import com.sunny.datapillar.openlineage.web.service.EmbeddingService;
import com.sunny.datapillar.openlineage.web.service.EventService;
import com.sunny.datapillar.openlineage.web.service.QueryService;
import com.sunny.datapillar.openlineage.web.service.RebuildService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OpenLineage API controller. */
@RestController
@RequestMapping
public class OpenLineageController {

  private final EventService eventService;
  private final EmbeddingService embeddingService;
  private final RebuildService rebuildService;
  private final QueryService queryService;

  public OpenLineageController(
      EventService eventService,
      EmbeddingService embeddingService,
      RebuildService rebuildService,
      QueryService queryService) {
    this.eventService = eventService;
    this.embeddingService = embeddingService;
    this.rebuildService = rebuildService;
    this.queryService = queryService;
  }

  @PostMapping("/events")
  public ResponseEntity<EventIngestAckResponse> events(
      @Valid @RequestBody EventIngestRequest request) {
    EventIngestAckResponse response = eventService.ingest(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @PostMapping("/setEmbedding")
  public SetEmbeddingResponse setEmbedding(@Valid @RequestBody SetEmbeddingRequest request) {
    return embeddingService.setEmbedding(request);
  }

  @PostMapping("/rebuild")
  public RebuildResponse rebuild(@Valid @RequestBody RebuildRequest request) {
    return rebuildService.rebuild(request);
  }

  @PostMapping("/search")
  public SearchResponse search(@Valid @RequestBody SearchRequest request) {
    return queryService.search(request);
  }

  @GetMapping("/initial")
  public InitialGraphResponse initial(
      @RequestParam(value = "limit", required = false) Integer limit) {
    return queryService.initial(limit);
  }

  @PostMapping("/text2cypher")
  public Text2CypherResponse text2cypher(@Valid @RequestBody Text2CypherRequest request) {
    return queryService.text2cypher(request);
  }

  @PostMapping("/dlq/replay/events")
  public DlqReplayResponse replayEventsDlq(@Valid @RequestBody DlqReplayRequest request) {
    return eventService.replayEventsDlq(request);
  }

  @PostMapping("/dlq/replay/embedding")
  public DlqReplayResponse replayEmbeddingDlq(@Valid @RequestBody DlqReplayRequest request) {
    return embeddingService.replayEmbeddingDlq(request);
  }
}
