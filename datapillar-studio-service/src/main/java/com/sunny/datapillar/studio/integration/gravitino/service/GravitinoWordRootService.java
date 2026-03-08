package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.WordRootCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.WordRootUpdateCommand;

public interface GravitinoWordRootService {

  GravitinoPageResult<GravitinoWordRootSummaryResponse> listWordRoots(int offset, int limit);

  GravitinoWordRootResponse createWordRoot(WordRootCreateCommand command);

  GravitinoWordRootResponse loadWordRoot(String code);

  GravitinoWordRootResponse updateWordRoot(String code, WordRootUpdateCommand command);

  boolean deleteWordRoot(String code);
}
