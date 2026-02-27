package com.sunny.datapillar.auth.dto.login.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthMenuInfo")
public class MenuItem {

    private Long id;

    private String name;

    private String path;

    private String permissionCode;

    private String location;

    private Long categoryId;

    private String categoryName;

    private List<MenuItem> children;
}
