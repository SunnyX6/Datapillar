package com.sunny.datapillar.studio.module.metadata.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sunny.datapillar.studio.dto.metadata.response.CatalogSummaryResponse;
import com.sunny.datapillar.studio.module.metadata.service.MetadataBizService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MetadataBizControllerTest {

  @Mock private MetadataBizService metadataBizService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new MetadataBizController(metadataBizService)).build();
  }

  @Test
  void listCatalogs_shouldReturnWrappedResult() throws Exception {
    CatalogSummaryResponse item = new CatalogSummaryResponse();
    item.setName("analytics");
    when(metadataBizService.listCatalogs()).thenReturn(List.of(item));

    mockMvc
        .perform(get("/biz/metadata/catalogs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data[0].name").value("analytics"));
  }
}
