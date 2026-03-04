package com.sunny.datapillar.studio.dto.tenant.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "InvitationRegisterRequest")
public class InvitationRegisterRequest {

  @NotBlank(message = "Invitation code cannot be empty")
  @Size(max = 64, message = "The length of the invitation code cannot exceed64characters")
  private String inviteCode;

  @NotBlank(message = "Username cannot be empty")
  @Size(max = 64, message = "Username length cannot exceed64characters")
  private String username;

  @NotBlank(message = "Email cannot be empty")
  @Email(message = "Email format is incorrect")
  @Size(max = 128, message = "The length of the email cannot exceed128characters")
  private String email;

  @NotBlank(message = "Password cannot be empty")
  @Size(min = 6, max = 255, message = "Password length must be within6-255between characters")
  private String password;
}
