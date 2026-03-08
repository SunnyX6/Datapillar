package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ValueDomainCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ValueDomainUpdateCommand;

public interface GravitinoValueDomainService {

  GravitinoPageResult<GravitinoValueDomainSummaryResponse> listValueDomains(int offset, int limit);

  GravitinoValueDomainResponse createValueDomain(ValueDomainCreateCommand command);

  GravitinoValueDomainResponse loadValueDomain(String code);

  GravitinoValueDomainResponse updateValueDomain(String code, ValueDomainUpdateCommand command);

  boolean deleteValueDomain(String code);
}
