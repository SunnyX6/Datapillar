package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "LlmProviderUpdateRequest")
public class LlmProviderUpdateRequest {

  @Size(min = 1, max = 64, message = "name The length range must be within 1-64")
  private String name;

  @Size(max = 255, message = "base_url The length cannot exceed 255")
  private String baseUrl;

  @Size(max = 200, message = "add_model_ids The quantity cannot exceed 200")
  private List<
          @NotBlank(message = "add_model_ids Null value exists")
          @Size(max = 128, message = "add_model_ids The element length cannot exceed 128") String>
      addModelIds;

  @Size(max = 200, message = "remove_model_ids The quantity cannot exceed 200")
  private List<
          @NotBlank(message = "remove_model_ids Null value exists")
          @Size(max = 128, message = "remove_model_ids The element length cannot exceed 128")
          String>
      removeModelIds;
}
