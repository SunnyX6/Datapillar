package com.sunny.datapillar.studio.dto.setup.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "SetupInitializeRequest")
public class SetupInitializeRequest {

  @NotBlank(message = "enterprise/Organization name cannot be empty")
  @Size(max = 128, message = "enterprise/The organization name cannot be longer than128characters")
  private String organizationName;

  @NotBlank(message = "Administrator name cannot be empty")
  @Size(max = 64, message = "Administrator name cannot be longer than64characters")
  private String adminName;

  @NotBlank(message = "Administrator username cannot be empty")
  @Size(max = 64, message = "Administrator user name cannot be longer than64characters")
  @Pattern(
      regexp = "^[a-zA-Z0-9_.-]+$",
      message = "Admin username only supports letters,numbers,Underline,Dots and dashes")
  private String username;

  @NotBlank(message = "Administrator email cannot be empty")
  @Size(max = 128, message = "The length of the administrators email cannot exceed128characters")
  @Email(message = "Administrator email format is incorrect")
  private String email;

  @NotBlank(message = "Administrator password cannot be empty")
  @Size(
      min = 8,
      max = 128,
      message = "Administrator password length must be within8Arrive128between characters")
  private String password;
}
