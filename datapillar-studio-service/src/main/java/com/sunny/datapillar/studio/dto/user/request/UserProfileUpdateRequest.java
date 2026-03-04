package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "UserUpdateProfile")
public class UserProfileUpdateRequest {

  @Size(max = 50, message = "Nickname length cannot exceed50characters")
  private String nickname;

  @Email(message = "Email format is incorrect")
  @Size(max = 100, message = "The length of the email cannot exceed100characters")
  private String email;

  @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Mobile phone number format is incorrect")
  private String phone;
}
