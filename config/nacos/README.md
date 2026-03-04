# Nacos Configuration template usage instructions

This catalog provides **Datapillar of each service Nacos Configuration template**.These files do not participate in the operation,Only for initialization Nacos in
DataId content.Directory structure convention:- `dev/DATAPILLAR/`:Development environment configuration template(unified grouping)
- `prod/DATAPILLAR/`:Production environment configuration template(unified grouping)
- `dev/.metadata.yml`,`prod/.metadata.yml`:Nacos 3.x Import required metadata

## Usage
1) enter Nacos console,Cut to the corresponding **namespace**(dev/prod).2) Select the corresponding directory(`dev/` or `prod/`)template.3) Create for each service DataId(See table below),Group fixed to `DATAPILLAR`.4) Copy the template content to Nacos Configuring,Replace sensitive items and addresses according to the actual environment.5) Can also be used"Import configuration"Function:- Nacos 3.x:Packaging includes the corresponding environment `.metadata.yml` + `DATAPILLAR/` Directory
 - DataId = file name,Configuration format selection **YAML**

## HTTPS/TLS Strategy(Unified edge layer)
By default,this project only supports **edge layer termination TLS**(Nginx/Ingress/SLB),Gateway Both the back-end and back-end services use the intranet.HTTP.If required HTTPS,For local or production environments,please configure the certificate and reverse proxy at the edge layer..

## DataId Checklist(force)
| service | DataId |
| --- | --- |
| gateway | `datapillar-api-gateway.yaml` |
| Certification | `datapillar-auth.yaml` |
| Studio | `datapillar-studio-service.yaml` |
| AI | `datapillar-ai.yaml` |

## Things to note
- The template has been filled with local default values,Production environments must be replaced with real safe values.- Nacos Only as a sole configuration source,Apply local `application.yml` Business configuration is no longer included.
