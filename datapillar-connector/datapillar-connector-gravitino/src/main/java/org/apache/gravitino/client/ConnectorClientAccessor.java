package org.apache.gravitino.client;

/** Package-access bridge for connector module to access Gravitino client internals. */
public final class ConnectorClientAccessor {

  private ConnectorClientAccessor() {}

  public static RESTClient restClient(GravitinoClientBase clientBase) {
    return clientBase.restClient();
  }
}
