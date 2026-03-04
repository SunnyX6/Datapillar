package com.sunny.datapillar.studio.dto.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "UserUpdate")
public class UserUpdateRequest {

  @Size(min = 3, max = 64, message = "Username length must be within3-64between characters")
  private String username;

  @Size(min = 6, max = 255, message = "Password length must be within6-255between characters")
  private String password;

  @Size(max = 64, message = "Nickname length cannot exceed64characters")
  private String nickname;

  @Email(message = "Email format is incorrect")
  @Size(max = 128, message = "The length of the email cannot exceed128characters")
  private String email;

  @Size(max = 32, message = "The length of the mobile phone number cannot exceed32characters")
  private String phone;

  private Integer status;

  private List<Long> roleIds;
}
