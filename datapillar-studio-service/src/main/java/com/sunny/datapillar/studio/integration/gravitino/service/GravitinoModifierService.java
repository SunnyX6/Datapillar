package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ModifierCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ModifierUpdateCommand;

public interface GravitinoModifierService {

  GravitinoPageResult<GravitinoModifierSummaryResponse> listModifiers(int offset, int limit);

  GravitinoModifierResponse createModifier(ModifierCreateCommand command);

  GravitinoModifierResponse loadModifier(String code);

  GravitinoModifierResponse updateModifier(String code, ModifierUpdateCommand command);

  boolean deleteModifier(String code);
}
