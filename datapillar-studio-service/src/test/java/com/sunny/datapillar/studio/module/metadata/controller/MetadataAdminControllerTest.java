package com.sunny.datapillar.studio.module.metadata.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.module.metadata.service.MetadataBizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MetadataAdminControllerTest {

  @Mock private MetadataBizService metadataBizService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new MetadataAdminController(metadataBizService)).build();
  }

  @Test
  void testCatalogConnection_shouldDelegateService() throws Exception {
    mockMvc
        .perform(
            post("/admin/metadata/catalogs/testConnection")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        java.util.Map.of(
                            "name", "ods",
                            "type", "DATASET",
                            "provider", "dataset"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(metadataBizService).testCatalogConnection(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deleteCatalog_shouldPassForceFlag() throws Exception {
    when(metadataBizService.deleteCatalog("analytics", true)).thenReturn(true);

    mockMvc
        .perform(delete("/admin/metadata/catalogs/analytics").param("force", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value(true));

    verify(metadataBizService).deleteCatalog("analytics", true);
  }
}
