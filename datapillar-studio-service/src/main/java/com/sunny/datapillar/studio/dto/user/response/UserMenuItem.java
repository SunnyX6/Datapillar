package com.sunny.datapillar.studio.dto.user.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "UserMenuItem")
public class UserMenuItem {

  private Long id;

  private String name;

  private String path;

  private String permissionCode;

  private String location;

  private Long categoryId;

  private String categoryName;

  private List<UserMenuItem> children;
}
