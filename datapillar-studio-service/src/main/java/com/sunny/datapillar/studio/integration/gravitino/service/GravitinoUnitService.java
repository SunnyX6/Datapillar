package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.UnitCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.UnitUpdateCommand;

public interface GravitinoUnitService {

  GravitinoPageResult<GravitinoUnitSummaryResponse> listUnits(int offset, int limit);

  GravitinoUnitResponse createUnit(UnitCreateCommand command);

  GravitinoUnitResponse loadUnit(String code);

  GravitinoUnitResponse updateUnit(String code, UnitUpdateCommand command);

  boolean deleteUnit(String code);
}
