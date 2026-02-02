package com.sunny.datapillar.admin.module.user.dto;

import java.util.List;
import lombok.Data;

/**
 * 菜单 DTO
 *
 * @author sunny
 */
public class MenuDto {

    @Data
    public static class Response {
        private Long id;
        private Long parentId;
        private String type;
        private String name;
        private String path;
        private String location;
        private Integer sort;
        private Integer status;
        private Long categoryId;
        private String categoryName;
        private List<Response> children;
    }
}
