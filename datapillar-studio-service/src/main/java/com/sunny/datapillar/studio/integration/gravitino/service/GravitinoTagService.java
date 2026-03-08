package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.request.ObjectTagAlterCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TagCreateCommand;
import com.sunny.datapillar.studio.integration.gravitino.model.request.TagUpdateCommand;
import java.util.List;

public interface GravitinoTagService {

  List<GravitinoTagSummaryResponse> listTags();

  GravitinoTagResponse createTag(TagCreateCommand command);

  GravitinoTagResponse loadTag(String tagName);

  GravitinoTagResponse updateTag(String tagName, TagUpdateCommand command);

  boolean deleteTag(String tagName);

  List<GravitinoTagSummaryResponse> listObjectTags(
      String domain, String objectType, String fullName);

  List<GravitinoTagSummaryResponse> alterObjectTags(
      String domain, String objectType, String fullName, ObjectTagAlterCommand command);
}
