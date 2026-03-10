package com.sunny.datapillar.openlineage.source.event;

/** RocketMQ header keys and schema constants for OpenLineage topics. */
public final class EventHeaders {

  public static final String MESSAGE_ID = "x-message-id";
  public static final String TENANT_ID = "x-tenant-id";
  public static final String TENANT_CODE = "x-tenant-code";
  public static final String SOURCE = "x-source";
  public static final String TRIGGER = "x-trigger";
  public static final String REBUILD_ID = "x-rebuild-id";
  public static final String ATTEMPT = "x-attempt";
  public static final String ENQUEUED_AT = "x-enqueued-at";
  public static final String SCHEMA_VERSION = "x-schema-version";

  public static final String EVENTS_SCHEMA = "events";
  public static final String EMBEDDING_SCHEMA = "embedding";
  public static final String REBUILD_COMMAND_SCHEMA = "rebuild-command";

  private EventHeaders() {}
}
