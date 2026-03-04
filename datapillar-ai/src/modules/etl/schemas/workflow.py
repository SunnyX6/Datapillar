# @author Sunny
# @date 2026-01-27

"""
Workflow data structure(Unify the three ends:AI/front end/Scheduling)

hierarchical relationship:- Workflow:Workflow,by multiple Job composed of DAG
- Job:Homework/Task,each Job is an independent execution unit(A node on the front end)
- Stage:SQL execution unit,one Job Can contain multiple Stage(architect planning)

Terminology:- type:Job Type(hive/shell/datax/flink Wait)
- depends:Job dependencies between
"""

import json
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


def _try_parse_json(value: object) -> object:
    if not isinstance(value, str):
        return value
    text = value.strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return value


# ==================== LLM Output only Schema ====================
# Contains only LLM Fields to be output,Other fields are populated by code


class StageOutput(BaseModel):
    """Stage output(LLM generate,used for structured output)"""

    stage_id: int = Field(
        ..., description="Stage serial number,Job the only one,from 1 Start incrementing"
    )
    name: str = Field(..., description="Stage Name,Briefly describe what this stage does")
    description: str = Field(
        ..., description="Stage Detailed description,Explain data processing logic"
    )
    input_tables: list[str] = Field(
        default_factory=list,
        description="List of tables read,The persistent table is catalog.schema.table,Temporary tables allowed temp.xxx",
    )
    output_table: str = Field(
        ...,
        description="Output table name,The persistent table is catalog.schema.table,Temporary tables allowed temp.xxx",
    )
    is_temp_table: bool = Field(
        default=True,
        description="Is it a temporary table?, Temporary tables are only currently Job Valid within",
    )
    sql: str | None = Field(default=None, description="SQL statement(Development stage supplement)")

    @field_validator("input_tables", mode="before")
    @classmethod
    def _parse_input_tables(cls, v: object) -> object:
        return _try_parse_json(v)


class JobOutput(BaseModel):
    """Job output(LLM generate,used for structured output)"""

    id: str = Field(..., description="Job unique identifier,Recommended job_1,job_2 Format")
    name: str = Field(..., description="Job Name,Briefly describe what this assignment does")
    description: str | None = Field(None, description="Job Detailed description")
    depends: list[str] = Field(
        default_factory=list,
        description="Dependent upstream Job ID list,Used for scheduling dependencies,if Job B The table to be read is Job A written,Then fill in Job A of ID",
    )
    job_ids: list[str] = Field(
        default_factory=list,
        description="Related business Job ID list,from AnalysisResult.pipelines[].jobs",
    )
    stages: list[StageOutput] = Field(
        default_factory=list, description="Execution phase list,Arranged in order of execution"
    )
    input_tables: list[str] = Field(
        default_factory=list, description="Job List of persistent tables read"
    )
    output_table: str | None = Field(None, description="Job final target table to write to")

    @field_validator("depends", "job_ids", "input_tables", mode="before")
    @classmethod
    def _parse_list_fields(cls, v: object) -> object:
        """fault tolerance:
        null -> empty list,stringification JSON -> parse"""
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v

    @field_validator("stages", mode="before")
    @classmethod
    def _parse_stages(cls, v: object) -> object:
        return _try_parse_json(v)


class WorkflowOutput(BaseModel):
    """
    Workflow output(LLM generate,used for structured output)

    Does not contain id/env Wait for runtime fields,populated by code."""

    name: str = Field(..., description="Workflow name,succinctly describe the entire ETL process")
    description: str | None = Field(None, description="Detailed description of workflow")
    schedule: str | None = Field(
        ..., description="Scheduling cycle cron expression,If not specified,it is null"
    )
    jobs: list[JobOutput] = Field(
        default_factory=list, description="Job list,press DAG Topological ordering"
    )
    risks: list[str] = Field(
        default_factory=list,
        description="List of architectural risk points,Such as performance bottleneck,Data skew etc.",
    )
    confidence: float = Field(
        default=0.8,
        ge=0.0,
        le=1.0,
        description="Architectural solution confidence,Complex scenes should < 0.8",
    )

    @field_validator("jobs", mode="before")
    @classmethod
    def _parse_jobs(cls, v: object) -> object:
        return _try_parse_json(v)

    @field_validator("risks", mode="before")
    @classmethod
    def _parse_risks(cls, v: object) -> object:
        """fault tolerance:
        null -> empty list,stringification JSON -> parse"""
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


# ==================== Complete data structure ====================


class Stage(BaseModel):
    """
    Stage - SQL execution unit

    planned by architect,one Stage Corresponds to one SQL,Produce a table.one Job Can contain multiple Stage,Execute in order.
    """

    stage_id: int = Field(..., description="Stage serial number(Job the only one)")
    name: str = Field(..., description="Stage Name")
    description: str = Field(..., description="this Stage what to do")

    input_tables: list[str] = Field(
        default_factory=list, description="table to read(Persistent or temporary table)"
    )
    output_table: str = Field(..., description="Output table(Persistent or temporary table)")
    is_temp_table: bool = Field(default=True, description="Is it a temporary table?")

    sql: str | None = Field(None, description="SQL statement(by DeveloperAgent generate)")


class Job(BaseModel):
    """
    job definition(Unify the naming of the three terminals:Job)

    each Job is an independent execution unit(A node on the front end).one Job Can contain multiple businesses Job(job_ids)and multiple execution stages(stages).
    """

    # Basic information
    id: str = Field(..., description="Job unique identifier")
    name: str = Field(..., description="Job Name(Chinese)")
    description: str | None = Field(None, description="Job Description")

    # Job type(Unified fields:type)
    type: str = Field(..., description="Job Type:HIVE/SHELL/DATAX/SPARK_SQL/PYTHON")
    type_id: int | None = Field(
        None, description="Component numbers ID(Correspond job_component.id)"
    )

    # Dependencies(Unified fields:depends)
    depends: list[str] = Field(default_factory=list, description="Dependent upstream Job ID list")

    # Related business Job(from AnalystAgent)
    job_ids: list[str] = Field(
        default_factory=list,
        description="Included business Job ID list(from AnalysisResult.pipelines[].jobs)",
    )

    # Execution phase(by ArchitectAgent planning)
    stages: list[Stage] = Field(default_factory=list, description="Execution phase list")

    # Data read and write statement(Pass data through shared storage)
    input_tables: list[str] = Field(default_factory=list, description="List of tables read")
    output_table: str | None = Field(None, description="target table to write to")

    # Component configuration(by DeveloperAgent generate)
    config: dict[str, Any] = Field(default_factory=dict, description="Component configuration")

    # Runtime configuration
    priority: int = Field(default=0, description="priority")
    timeout: int = Field(default=3600, description="timeout(seconds)")
    retry_times: int = Field(default=3, description="Number of failed retries")
    retry_interval: int = Field(default=60, description="Retry interval(seconds)")

    # status flag
    config_generated: bool = Field(
        default=False, description="Whether the configuration has been generated"
    )
    config_validated: bool = Field(default=False, description="Is the configuration verified?")

    def get_ordered_stages(self) -> list[Stage]:
        """Return in execution order Stage"""
        return sorted(self.stages, key=lambda s: s.stage_id)


class Workflow(BaseModel):
    """
    Workflow definition(Unify the naming of the three terminals:Workflow)

    fully described ETL Workflow,by multiple Job composed of DAG.by ArchitectAgent Planning based on business analysis results.
    """

    # Basic information
    id: str | None = Field(None, description="Workflow unique identifier")
    name: str = Field(..., description="Workflow name")
    description: str | None = Field(None, description="Workflow description")

    # Scheduling configuration
    schedule: str | None = Field(None, description="Scheduling cycle cron expression")
    env: Literal["dev", "stg", "prod"] = Field(default="dev", description="Operating environment")

    # Job list
    jobs: list[Job] = Field(default_factory=list, description="Job list")

    # Risk warning
    risks: list[str] = Field(default_factory=list, description="Architecture risk points")

    # decision point(User confirmation required)
    decision_points: list[dict[str, Any]] = Field(default_factory=list)

    # Confidence
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)

    @classmethod
    def from_output(
        cls,
        output: "WorkflowOutput",
        selected_component: str,
        selected_component_id: int | None = None,
    ) -> "Workflow":
        """
        from LLM Output build complete Workflow

        Args:output:LLM generated WorkflowOutput
        selected_component:User-selected component type
        selected_component_id:components ID
        """
        jobs = []
        for job_output in output.jobs:
            stages = [
                Stage(
                    stage_id=s.stage_id,
                    name=s.name,
                    description=s.description,
                    input_tables=s.input_tables,
                    output_table=s.output_table,
                    is_temp_table=s.is_temp_table,
                    sql=s.sql,
                )
                for s in job_output.stages
            ]
            job = Job(
                id=job_output.id,
                name=job_output.name,
                description=job_output.description,
                type=selected_component,
                type_id=selected_component_id,
                depends=job_output.depends,
                job_ids=job_output.job_ids,
                stages=stages,
                input_tables=job_output.input_tables,
                output_table=job_output.output_table,
            )
            jobs.append(job)

        return cls(
            name=output.name,
            description=output.description,
            schedule=output.schedule,
            jobs=jobs,
            risks=output.risks,
            confidence=output.confidence,
        )

    def get_job(self, job_id: str) -> Job | None:
        """Get a job"""
        for job in self.jobs:
            if job.id == job_id:
                return job
        return None

    def get_upstream_jobs(self, job_id: str) -> list[Job]:
        """Get upstream job"""
        job = self.get_job(job_id)
        if not job:
            return []
        return [j for j in self.jobs if j.id in job.depends]

    def get_downstream_jobs(self, job_id: str) -> list[Job]:
        """Get downstream jobs"""
        return [j for j in self.jobs if job_id in j.depends]

    def get_root_jobs(self) -> list[Job]:
        """Get root job(Dependency-free jobs)"""
        return [j for j in self.jobs if not j.depends]

    def get_leaf_jobs(self) -> list[Job]:
        """Get leaf jobs(No downstream operations)"""
        all_deps = set()
        for job in self.jobs:
            all_deps.update(job.depends)
        return [j for j in self.jobs if j.id not in all_deps]

    def topological_sort(self) -> list[Job]:
        """topological sort(in order of execution)"""
        visited = set()
        result = []

        def dfs(job_id: str):
            if job_id in visited:
                return
            visited.add(job_id)
            job = self.get_job(job_id)
            if job:
                for dep_id in job.depends:
                    dfs(dep_id)
                result.append(job)

        for job in self.jobs:
            dfs(job.id)

        return result

    def topological_layers(self) -> list[list[Job]]:
        """
        Topological layering(Group by dependency level)

        Returns a list of layers in order of execution,within the same layer Job Can be executed in parallel.For example:[[job_1,job_2],[job_3],[job_4,job_5]]
        express job_1 and job_2 Can be parallelized,After completion job_3 execute,finally job_4 and job_5 Parallel.algorithm:Kahn's algorithm(BFS topological sort)
        """
        if not self.jobs:
            return []

        # Construct in-degree table and adjacency table
        in_degree: dict[str, int] = {job.id: 0 for job in self.jobs}
        adjacency: dict[str, list[str]] = {job.id: [] for job in self.jobs}

        for job in self.jobs:
            for dep_id in job.depends:
                if dep_id in adjacency:
                    adjacency[dep_id].append(job.id)
                    in_degree[job.id] += 1

        layers: list[list[Job]] = []

        # BFS layered
        while True:
            # Find the current in-degree as 0 all nodes of(current layer)
            current_layer_ids = [job_id for job_id, degree in in_degree.items() if degree == 0]
            if not current_layer_ids:
                break

            # Get the current layer Job object
            current_layer = [job for job in self.jobs if job.id in current_layer_ids]
            if current_layer:
                layers.append(current_layer)

            # Remove the current layer node,Update the in-degree of downstream nodes
            for job_id in current_layer_ids:
                del in_degree[job_id]
                for downstream_id in adjacency.get(job_id, []):
                    if downstream_id in in_degree:
                        in_degree[downstream_id] -= 1

        return layers

    def validate_dag(self) -> list[str]:
        """Verify DAG Is it legal?"""
        errors = []

        # Check Job ID uniqueness
        ids = [j.id for j in self.jobs]
        if len(ids) != len(set(ids)):
            errors.append("There are duplicates Job ID")

        # Check if dependencies exist
        id_set = set(ids)
        for job in self.jobs:
            for dep in job.depends:
                if dep not in id_set:
                    errors.append(f"Job {job.id} dependent {dep} does not exist")

        # Check for circular dependencies
        def has_cycle(job_id: str, path: set) -> bool:
            if job_id in path:
                return True
            path.add(job_id)
            job = self.get_job(job_id)
            if job:
                for dep in job.depends:
                    if has_cycle(dep, path.copy()):
                        return True
            return False

        for job in self.jobs:
            if has_cycle(job.id, set()):
                errors.append(f"There is a circular dependency,involving Job {job.id}")
                break

        return errors

    def validate_data_dependencies(self) -> tuple[list[str], list[str]]:
        """
        Verify that data dependencies are correctly declared

        check logic:- if Job B of input_tables contains Job A of output_table
        - rule Job B Dependencies must be declared Job A(Job B.depends contains Job A.id)

        Returns:(errors,warnings):Error list and warning list
        - errors:Missing critical dependencies(Will cause scheduling failure)
        - warnings:possible problems(Requires manual confirmation)
        """
        errors = []
        warnings = []

        # Build output_table -> job_id mapping
        output_to_job: dict[str, str] = {}
        for job in self.jobs:
            if job.output_table:
                output_to_job[job.output_table] = job.id

        # Check each Job of input_tables
        for job in self.jobs:
            if not job.input_tables:
                continue

            for input_table in job.input_tables:
                # Check if this input table is used by other Job output
                producer_job_id = output_to_job.get(input_table)
                if (
                    producer_job_id
                    and producer_job_id != job.id
                    and producer_job_id not in job.depends
                ):
                    errors.append(
                        f"Job '{job.id}' Read table '{input_table}',"
                        f"The table consists of Job '{producer_job_id}' output,"
                        f"but no dependencies declared"
                    )

        return errors, warnings

    def fix_missing_dependencies(self) -> list[str]:
        """
        Automatically fix missing data dependencies

        Returns:Repair record list
        """
        fixes = []

        # Build output_table -> job_id mapping
        output_to_job: dict[str, str] = {}
        for job in self.jobs:
            if job.output_table:
                output_to_job[job.output_table] = job.id

        # Check and fix each Job
        for job in self.jobs:
            if not job.input_tables:
                continue

            for input_table in job.input_tables:
                producer_job_id = output_to_job.get(input_table)
                if (
                    producer_job_id
                    and producer_job_id != job.id
                    and producer_job_id not in job.depends
                ):
                    job.depends.append(producer_job_id)
                    fixes.append(
                        f"Automatically add dependencies:Job '{job.id}' -> Job '{producer_job_id}' "
                        f"(Because reading the table '{input_table}')"
                    )

        return fixes

    def validate_temp_scope(self) -> list[str]:
        """
        Verify temporary table scope

        rules:Stage Output temporary table(is_temp_table=True)only at present Job Internal use,cannot be used by other Job Quote.Returns:error list
        """
        errors = []

        # collect each Job Internal temporary table
        job_temp_tables: dict[str, set] = {}  # job_id -> {temp_table1, temp_table2}

        for job in self.jobs:
            temp_tables = set()
            for stage in job.stages:
                if stage.is_temp_table and stage.output_table:
                    temp_tables.add(stage.output_table)
            job_temp_tables[job.id] = temp_tables

        # Merge all temporary tables
        all_temp_tables: dict[str, str] = {}  # temp_table -> owner_job_id
        for job_id, temp_tables in job_temp_tables.items():
            for temp_table in temp_tables:
                all_temp_tables[temp_table] = job_id

        # Check each Job Does it cite other Job temporary table
        for job in self.jobs:
            # Check Job level input_tables
            for input_table in job.input_tables:
                if input_table in all_temp_tables:
                    owner_job_id = all_temp_tables[input_table]
                    if owner_job_id != job.id:
                        errors.append(
                            f"Job '{job.id}' quoted Job '{owner_job_id}' temporary table '{input_table}',"
                            f"A temporary table can only be defined in Job Internal use"
                        )

            # Check Stage level input_tables
            for stage in job.stages:
                for input_table in stage.input_tables:
                    if input_table in all_temp_tables:
                        owner_job_id = all_temp_tables[input_table]
                        if owner_job_id != job.id:
                            errors.append(
                                f"Job '{job.id}' of Stage '{stage.name}' quoted "
                                f"Job '{owner_job_id}' temporary table '{input_table}',"
                                f"A temporary table can only be defined in Job Internal use"
                            )

        return errors
