package com.sunny.datapillar.studio.module.semantic.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.module.semantic.service.SemanticBizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SemanticAdminControllerTest {

  @Mock private SemanticBizService semanticBizService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SemanticAdminController(semanticBizService)).build();
  }

  @Test
  void createWordRoot_shouldDelegateService() throws Exception {
    mockMvc
        .perform(
            post("/admin/semantic/wordroots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        java.util.Map.of("code", "gmv", "name", "GMV"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(semanticBizService).createWordRoot(org.mockito.ArgumentMatchers.any());
  }
}
