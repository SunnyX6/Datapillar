# Nacos 配置模板使用说明

本目录提供 **Datapillar 各服务的 Nacos 配置模板**。这些文件不参与运行，仅用于初始化 Nacos 中的
DataId 内容。

目录结构约定：
- `dev/DATAPILLAR/`：开发环境配置模板（统一分组）
- `prod/DATAPILLAR/`：生产环境配置模板（统一分组）
- `dev/.metadata.yml`、`prod/.metadata.yml`：Nacos 3.x 导入所需元数据

## 使用方式
1) 进入 Nacos 控制台，切到对应的 **namespace**（dev/prod）。
2) 选择对应目录（`dev/` 或 `prod/`）的模板。
3) 为每个服务创建 DataId（见下面表格），Group 固定为 `DATAPILLAR`。
4) 将模板内容复制到 Nacos 配置中，按实际环境替换敏感项与地址。
5) 也可以使用「导入配置」功能：
   - Nacos 3.x：打包时包含对应环境的 `.metadata.yml` + `DATAPILLAR/` 目录
   - DataId = 文件名，配置格式选择 **YAML**

## HTTPS/TLS 策略（统一采用边缘层）
本项目默认只支持 **边缘层终止 TLS**（Nginx/Ingress/SLB），Gateway 与后端服务均使用内网 HTTP。
如需 HTTPS，本地或生产环境请在边缘层配置证书与反向代理。

## DataId 清单（强制）
| 服务 | DataId |
| --- | --- |
| 网关 | `datapillar-api-gateway.yaml` |
| 认证 | `datapillar-auth.yaml` |
| Studio | `datapillar-studio-service.yaml` |
| AI | `datapillar-ai.yaml` |

## 注意事项
- 模板已填本地默认值，生产环境必须替换为真实安全值。
- Nacos 仅作为唯一配置来源，应用本地 `application.yml` 不再包含业务配置。
