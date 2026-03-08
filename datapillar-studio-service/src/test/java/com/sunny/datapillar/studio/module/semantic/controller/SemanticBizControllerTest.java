package com.sunny.datapillar.studio.module.semantic.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sunny.datapillar.studio.dto.semantic.response.PageResponse;
import com.sunny.datapillar.studio.dto.semantic.response.WordRootSummaryResponse;
import com.sunny.datapillar.studio.module.semantic.service.SemanticBizService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SemanticBizControllerTest {

  @Mock private SemanticBizService semanticBizService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SemanticBizController(semanticBizService)).build();
  }

  @Test
  void listWordRoots_shouldReturnPagedResult() throws Exception {
    WordRootSummaryResponse item = new WordRootSummaryResponse();
    item.setCode("gmv");
    PageResponse<WordRootSummaryResponse> page = new PageResponse<>();
    page.setItems(List.of(item));
    page.setOffset(0);
    page.setLimit(20);
    page.setTotal(1L);
    when(semanticBizService.listWordRoots(0, 20)).thenReturn(page);

    mockMvc
        .perform(get("/biz/semantic/wordroots"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data[0].code").value("gmv"))
        .andExpect(jsonPath("$.limit").value(20))
        .andExpect(jsonPath("$.offset").value(0))
        .andExpect(jsonPath("$.total").value(1));
  }
}
