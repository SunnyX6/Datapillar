-- ====================================================
-- Datapillar Job Scheduler DDL (MySQL 8.0+)
-- ====================================================
--
-- 【架构设计】
--
--   采用 事件驱动 + Pull + Bucket 去中心化模式
--   核心原则：Worker 完全自治，无单点，无限水平扩展
--
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │                        System Architecture                              │
--   ├─────────────────────────────────────────────────────────────────────────┤
--   │                                                                         │
--   │   ┌──────────────────┐                                                 │
--   │   │  Server (API)    │ ◄── 只做 CRUD，不与 Worker 通信                  │
--   │   │  Spring Boot     │     写完 DB 就结束，不通知任何人                  │
--   │   └────────┬─────────┘                                                 │
--   │            │ CRUD (只写)                                               │
--   │            ▼                                                           │
--   │   ┌──────────────────┐                                                 │
--   │   │      MySQL       │ ◄── 唯一共享状态                                 │
--   │   │  job_run 按      │     Worker 通过 bucket_id 分片查询               │
--   │   │  bucket_id 分片  │                                                 │
--   │   └────────┬─────────┘                                                 │
--   │            │                                                           │
--   │   ┌────────┴─────────────────────────────────────────────────────────┐ │
--   │   │                   Worker Cluster（完全对等）                      │ │
--   │   │                                                                   │ │
--   │   │   ┌─────────────────────────────────────────────────────────┐    │ │
--   │   │   │                 CRDT: Bucket 所有权                      │    │ │
--   │   │   │                                                          │    │ │
--   │   │   │   Bucket-0 → Worker-A    Bucket-3 → Worker-A            │    │ │
--   │   │   │   Bucket-1 → Worker-B    Bucket-4 → Worker-B            │    │ │
--   │   │   │   Bucket-2 → Worker-C    Bucket-5 → Worker-C            │    │ │
--   │   │   │   ...                                                    │    │ │
--   │   │   │                                                          │    │ │
--   │   │   │   Gossip 同步，无中心化存储                              │    │ │
--   │   │   │   Worker 定期续租，过期自动转移                          │    │ │
--   │   │   └─────────────────────────────────────────────────────────┘    │ │
--   │   │                                                                   │ │
--   │   │   ┌───────────────────────────────────────────────────────────┐  │ │
--   │   │   │  Worker-A                 Worker-B                Worker-N│  │ │
--   │   │   │  [Bucket 0,3,6...]        [Bucket 1,4,7...]       [...]   │  │ │
--   │   │   │                                                           │  │ │
--   │   │   │  ┌─────────────┐          ┌─────────────┐                │  │ │
--   │   │   │  │ TimerWheel  │          │ TimerWheel  │                │  │ │
--   │   │   │  │ (本地内存)  │          │ (本地内存)  │                │  │ │
--   │   │   │  └──────┬──────┘          └──────┬──────┘                │  │ │
--   │   │   │         │ 触发                    │ 触发                  │  │ │
--   │   │   │         ▼                         ▼                       │  │ │
--   │   │   │  ┌─────────────┐          ┌─────────────┐                │  │ │
--   │   │   │  │ 本地执行    │          │ 本地执行    │                │  │ │
--   │   │   │  │ (线程池)    │          │ (线程池)    │                │  │ │
--   │   │   │  └─────────────┘          └─────────────┘                │  │ │
--   │   │   │                                                           │  │ │
--   │   │   │  每个 Worker 完全自治：                                   │  │ │
--   │   │   │  - 启动时加载自己 Bucket 的任务                           │  │ │
--   │   │   │  - TimerWheel 精确触发                                    │  │ │
--   │   │   │  - 本地线程池执行                                         │  │ │
--   │   │   │  - 执行完自己生成下一次触发                               │  │ │
--   │   │   │  - 不依赖任何中心节点                                     │  │ │
--   │   │   └───────────────────────────────────────────────────────────┘  │ │
--   │   │                                                                   │ │
--   │   └───────────────────────────────────────────────────────────────────┘ │
--   │                                                                         │
--   └─────────────────────────────────────────────────────────────────────────┘
--
--   核心角色与边界（铁律）：
--   | 角色               | 职责                          | 绝对不做               |
--   |--------------------|------------------------------|------------------------|
--   | Server (API)       | 工作流/任务 CRUD              | 不触发、不调度、不执行  |
--   |                    | 首次上线时创建 job_run        | 不与 Worker 通信       |
--   | Worker             | 认领 Bucket，调度+执行        | 不依赖中心节点         |
--   |                    | 生成下一个 job_run            | 不轮询 DB              |
--   | CRDT               | Bucket 所有权管理             | 不存储任务数据         |
--   | DB                 | 唯一任务数据源                | 不参与调度决策         |
--
-- 【核心设计原则：调度本地化 vs 执行分布式】
--
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │  关键区分：调度决策在本地，任务执行可跨 Worker                           │
--   ├─────────────────────────────────────────────────────────────────────────┤
--   │                                                                         │
--   │  ✓ 调度本地化（去中心化）                                               │
--   │    - 每个 Worker 有自己的 JobScheduler                                  │
--   │    - JobScheduler 只调度自己 Bucket 的任务                              │
--   │    - 调度决策不依赖中心节点                                             │
--   │                                                                         │
--   │  ✓ 执行分布式（可跨 Worker）                                            │
--   │    - 普通任务：根据路由策略选择一个 Worker 执行                         │
--   │    - 分片任务：调度者发现 Task → Worker Pull 模式执行                   │
--   │    - 执行位置由路由策略决定，不限于调度者本地                           │
--   │                                                                         │
--   └─────────────────────────────────────────────────────────────────────────┘
--
-- 【Task 分片机制（Worker 自治模型）】
--
--   核心思想：Worker 根据自己的能力自主拆分 Task，框架不干预
--
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │  设计理念：借鉴 Hadoop MapReduce 的 Split 机制                          │
--   ├─────────────────────────────────────────────────────────────────────────┤
--   │                                                                         │
--   │  Hadoop Split 核心参数：                                                │
--   │    - minSplitSize: 最小分片大小                                         │
--   │    - maxSplitSize: 最大分片大小                                         │
--   │    - splitSize = max(minSize, min(maxSize, goalSize))                  │
--   │                                                                         │
--   │  本框架同理：                                                           │
--   │    - Worker 有自己的 minSplitSize、maxSplitSize（内部配置）             │
--   │    - Worker 根据自身资源（CPU、内存、负载）计算 splitSize               │
--   │    - 能力强的 Worker 拆得多，能力弱的 Worker 拆得少                     │
--   │                                                                         │
--   └─────────────────────────────────────────────────────────────────────────┘
--
--   Task 分片流程（Worker 自治）：
--
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │                                                                         │
--   │   Job 触发（route_strategy = SHARDING）                                 │
--   │       │                                                                 │
--   │       │ Job 信息写入 CRDT，所有 Worker 可见                             │
--   │       ▼                                                                 │
--   │   ┌─────────────────────────────────────────────────────────────────┐   │
--   │   │  CRDT（集群共享，Gossip 同步）                                  │   │
--   │   │                                                                 │   │
--   │   │  jobRunId=123:                                                 │   │
--   │   │    completed:  [0-99, 100-149, 200-249]                        │   │
--   │   │    processing: {150-199: Worker-A, 250-259: Worker-B}          │   │
--   │   │                                                                 │   │
--   │   └─────────────────────────────────────────────────────────────────┘   │
--   │       ▲                                                                 │
--   │       │ Worker 自己查、自己拆、自己标记                                 │
--   │       │                                                                 │
--   │   ┌─────────────────────────────────────────────────────────────────┐   │
--   │   │                                                                 │   │
--   │   │  Worker-A（能力强）              Worker-B（能力弱）             │   │
--   │   │  ┌─────────────────────┐        ┌─────────────────────┐        │   │
--   │   │  │ 1. 计算 splitSize   │        │ 1. 计算 splitSize   │        │   │
--   │   │  │    = 50（能力强）   │        │    = 10（能力弱）   │        │   │
--   │   │  │ 2. 查 CRDT 找起点   │        │ 2. 查 CRDT 找起点   │        │   │
--   │   │  │ 3. 标记 150-199     │        │ 3. 标记 250-259     │        │   │
--   │   │  │ 4. 执行             │        │ 4. 执行             │        │   │
--   │   │  │ 5. 标记完成         │        │ 5. 标记完成         │        │   │
--   │   │  │ 6. 继续拆下一批     │        │ 6. 继续拆下一批     │        │   │
--   │   │  └─────────────────────┘        └─────────────────────┘        │   │
--   │   │                                                                 │   │
--   │   └─────────────────────────────────────────────────────────────────┘   │
--   │                                                                         │
--   │   全部范围处理完成 → Job 完成                                           │
--   │                                                                         │
--   └─────────────────────────────────────────────────────────────────────────┘
--
--   splitSize 计算算法（Worker 内部）：
--
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │                                                                         │
--   │  Worker 配置（内部参数，非 Job 配置）：                                  │
--   │    - minSplitSize: 最小分片大小（默认 1）                               │
--   │    - maxSplitSize: 最大分片大小（默认 100）                             │
--   │                                                                         │
--   │  算法：                                                                 │
--   │    goalSize = 根据 Worker 资源计算（CPU 核数、可用内存、当前负载）      │
--   │    splitSize = max(minSplitSize, min(maxSplitSize, goalSize))          │
--   │                                                                         │
--   │  示例：                                                                 │
--   │    Worker-A: 8核16G，负载低 → goalSize=80 → splitSize=80               │
--   │    Worker-B: 2核4G，负载高  → goalSize=5  → splitSize=5                │
--   │                                                                         │
--   └─────────────────────────────────────────────────────────────────────────┘
--
--   关键设计：
--
--   | 机制           | 说明                                                    |
--   |----------------|--------------------------------------------------------|
--   | Worker 自治    | Worker 自己拆分，不依赖调度者分配                        |
--   | 能力自适应     | splitSize 根据 Worker 资源动态计算                       |
--   | CRDT 协调      | 已处理范围通过 CRDT 共享，避免重复                       |
--   | 状态机         | PENDING → PROCESSING → COMPLETED/FAILED                 |
--   | 故障恢复       | Worker 挂了 → 其他 Worker 从 CRDT 恢复 → 继续处理        |
--   | 幂等性         | 业务代码保证：重复执行同一范围结果一致                   |
--
--   不重复保证：
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │  Worker 拆分前先标记（CAS 语义）：                                      │
--   │    1. Worker 查 CRDT 找到下一个未处理的起点                             │
--   │    2. Worker 尝试标记该范围为 PROCESSING（带自己的 workerAddress）      │
--   │    3. CRDT LWW 保证：并发标记时，只有一个 Worker 成功                   │
--   │    4. 标记成功 → 执行；标记失败 → 重新找起点                            │
--   └─────────────────────────────────────────────────────────────────────────┘
--
--   故障恢复：
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │  场景：Worker-A 标记了范围 150-199 后挂了                               │
--   │                                                                         │
--   │  1. CRDT 中 150-199 状态为 PROCESSING，worker=Worker-A                  │
--   │  2. 其他 Worker 检测到 Worker-A 离线（Pekko Cluster 事件）              │
--   │  3. 任意 Worker 可以重置该范围为 PENDING                                │
--   │  4. 下一个 Worker 重新标记并执行                                        │
--   │  5. 业务代码保证幂等性                                                  │
--   └─────────────────────────────────────────────────────────────────────────┘
--
--   与旧分片策略对比：
--
--   | 维度           | 旧策略（已废弃）              | 新策略（Worker 自治）         |
--   |----------------|------------------------------|------------------------------|
--   | 分片数         | Worker 数量（固定）           | Worker 自己决定（动态）       |
--   | 分片方式       | 调度者广播分配                | Worker 自主拆分               |
--   | 负载均衡       | 每个 Worker 固定一个分片      | 能力强多拆，能力弱少拆        |
--   | 扩缩容         | 分片数随 Worker 数变化        | 无影响，新 Worker 继续拆      |
--   | 状态共享       | 无                            | CRDT Gossip 同步              |
--   | 故障恢复       | 状态丢失，需重新执行          | 从 CRDT 恢复，继续执行        |
--   | 调度者依赖     | 强依赖                        | 无依赖，Worker 完全自治       |
--
--   Server 边界（关键）：
--   - Server 只在"工作流首次上线"时创建 job_run，之后再也不创建
--   - 后续所有 job_run 由 Worker 完成后创建（同一事务）
--   - Server 不知道 Worker 在哪，不与 Worker 通信
--
--   优势：
--   - 去中心化：无调度者单点，Worker 完全自治
--   - 自适应：Worker 根据自己能力决定拆分粒度
--   - 弹性伸缩：Worker 数量变化不影响分片逻辑
--   - 故障容错：CRDT 共享状态，任意 Worker 可接管
--   - 负载均衡：能力强的 Worker 自然处理更多
--
-- 【Bucket 分片机制】
--
--   核心思想：任务按 bucket_id 分片，Worker 按 Bucket 认领
--
--   ┌─────────────────────────────────────────────────────────────────┐
--   │  bucket_id = hash(job_id) % BUCKET_COUNT (默认 1024)           │
--   │                                                                 │
--   │  job_run 写入时自动计算 bucket_id                               │
--   │  Worker 只查询自己负责的 bucket_id                              │
--   │                                                                 │
--   │  无锁设计：每个 Bucket 只有一个 Owner，不存在竞争               │
--   └─────────────────────────────────────────────────────────────────┘
--
--   Bucket 所有权管理（CRDT）：
--   ┌─────────────────────────────────────────────────────────────────┐
--   │  LWWMap<BucketId, BucketLease>                                  │
--   │                                                                 │
--   │  BucketLease {                                                  │
--   │      workerAddress: String   // 当前 Owner                      │
--   │      leaseTime: Long         // 最后续租时间                    │
--   │  }                                                              │
--   │                                                                 │
--   └─────────────────────────────────────────────────────────────────┘
--
--   Bucket 转移流程：
--   ┌─────────────────────────────────────────────────────────────────┐
--   │                                                                 │
--   │  场景 A：主动下线（立即转移）                                   │
--   │  ────────────────────────────                                   │
--   │  1. Worker-A 收到 shutdown 信号                                 │
--   │  2. Worker-A 主动释放所有 Bucket（CRDT 删除）                   │
--   │  3. 其他 Worker 立即检测到空闲 Bucket，抢占认领                 │
--   │                                                                 │
--   │  场景 B：故障下线（Gossip 检测，秒级转移）                      │
--   │  ────────────────────────────                                   │
--   │  1. Worker-A 崩溃/网络断开                                      │
--   │  2. Pekko Gossip 检测到节点 Unreachable → Down（几秒）          │
--   │  3. 集群广播 MemberRemoved 事件                                 │
--   │  4. 其他 Worker 收到事件 → 清理 Worker-A 的所有 Bucket          │
--   │  5. 立即抢占认领                                                │
--   │                                                                 │
--   │  场景 C：脑裂兜底（30秒超时）                                   │
--   │  ────────────────────────────                                   │
--   │  1. 极端情况：Gossip 未检测到故障，但 Worker 实际已不可用       │
--   │  2. 30 秒未续租 → lease 过期 → 其他 Worker 可抢占               │
--   │  3. 这是最后的兜底机制                                          │
--   │                                                                 │
--   └─────────────────────────────────────────────────────────────────┘
--
-- 【核心概念：设计阶段 vs 执行阶段】
--
--   ┌─────────────────────────────────────────────────────────────┐
--   │                     设计阶段（蓝图）                          │
--   │  job_workflow + job_info + job_dependency                   │
--   │  Server CRUD 管理，定义"做什么、怎么依赖、什么时候触发"         │
--   └─────────────────────────────────────────────────────────────┘
--                               │
--                               │ Workflow 上线时实例化
--                               ▼
--   ┌─────────────────────────────────────────────────────────────┐
--   │                     执行阶段（实例）                          │
--   │  job_workflow_run + job_run + job_run_dependency            │
--   │  调度器查询 job_run，分发给 Sharding Entity 执行             │
--   └─────────────────────────────────────────────────────────────┘
--
-- 【核心流程】
--
--   ┌────────────────────────────────────────────────────────────────┐
--   │                        两种 job_run 创建场景                    │
--   ├────────────────────────────────────────────────────────────────┤
--   │                                                                 │
--   │   场景 A：首次上线（Server 创建）                               │
--   │   ────────────────────────────────                              │
--   │   Server 上线工作流 → 写 DB（job_run + bucket_id）→ 完成        │
--   │   Server 不通知任何人，只管写 DB                                │
--   │                                                                 │
--   │   场景 B：后续循环（Worker 创建）                               │
--   │   ────────────────────────────────                              │
--   │   Worker 完成任务 → 写 DB（下一个 job_run）→ 直接加入 TimerWheel│
--   │   形成闭环，无需 Server 参与，无需通知任何人                    │
--   │                                                                 │
--   └────────────────────────────────────────────────────────────────┘
--
--   1. Workflow 首次上线（Server 唯一职责）
--      Server 计算下次触发时间，把设计阶段的"蓝图"转换成执行阶段的"实例"：
--        - 1 条 job_workflow_run (status=WAITING)
--        - N 条 job_run (status=WAITING，bucket_id=hash(job_id)%1024)
--        - M 条 job_run_dependency (根据 job_dependency 映射生成)
--      Server 写完 DB 后结束，不通知任何人
--
--   2. Worker 启动与任务发现
--      a. 从 CRDT 认领空闲 Bucket
--      b. 一次性加载自己 Bucket 的 WAITING 任务：
--         SELECT * FROM job_run
--         WHERE bucket_id IN (#{myBuckets}) AND status = 0;
--      c. 全部加入 TimerWheel
--      d. 启动完成，进入事件循环
--
--   3. TimerWheel 触发任务（事件驱动）
--      trigger_time 到达时：
--        a. 检查依赖是否满足（所有 parent.status = SUCCESS）
--        b. 满足 → 本地线程池执行
--        c. 不满足 → 等待依赖完成事件
--
--   4. Worker 执行任务
--      - 更新 job_run.status = RUNNING
--      - 执行任务（Shell/Spark/Flink/HTTP 等）
--      - 更新 job_run.status = SUCCESS/FAIL/TIMEOUT
--
--   5. 任务完成，触发下游（事件驱动，无需通知）
--      Worker 本地检查下游任务：
--        - 下游任务在同一 Bucket → 直接检查依赖，满足则触发
--        - 下游任务在其他 Bucket → 依赖数据在 DB，其他 Worker 自己检查
--
--   6. Cron 任务自驱动
--      任务完成后，Worker 自己计算下次触发时间：
--        a. 创建新的 job_run（bucket_id 相同，保持在同一 Worker）
--        b. 直接加入本地 TimerWheel（不等待加载）
--        c. 形成闭环，无需任何通知
--
-- 【事件驱动调度详解】
--
--   核心原则：启动加载 + 任务自驱动，不轮询 DB
--
--   Worker 内部事件：
--   | 事件              | 来源           | 处理逻辑                          |
--   |-------------------|---------------|-----------------------------------|
--   | TimerTick         | TimerWheel    | 检查依赖 → 执行任务               |
--   | JobCompleted      | 本地执行完成   | 触发下游 + Cron 生成下一次        |
--   | BucketAcquired    | CRDT          | 加载该 Bucket 的任务              |
--   | BucketLost        | CRDT          | 清理该 Bucket 的任务              |
--
--   Worker 查 DB 时机（不轮询）：
--   | 时机                    | 查询内容                          |
--   |------------------------|-----------------------------------|
--   | 启动时                  | 自己 Bucket 的 WAITING 任务       |
--   | 获得新 Bucket 时         | 新 Bucket 的 WAITING 任务         |
--   | 故障恢复时               | 自己 Bucket 的 WAITING 任务       |
--
--   延迟分析：
--   | 场景                    | 延迟                              |
--   |------------------------|-----------------------------------|
--   | 首次上线                | 等待 Worker 获得 Bucket 并加载    |
--   | 后续循环                | 零延迟（Worker 自己生成并加载）   |
--   | 下游任务触发            | 零延迟（本地事件直接触发）        |
--   | 主动下线 Bucket 转移    | 立即（主动释放）                  |
--   | 故障下线 Bucket 转移    | 秒级（Gossip 检测）               |
--
--   Worker 故障恢复：
--   - Worker 故障 → Gossip 检测到节点 Down → 广播 MemberRemoved
--   - 其他 Worker 收到事件 → 清理故障节点的 Bucket → 立即抢占
--   - 加载该 Bucket 的 WAITING + RUNNING 任务
--   - RUNNING 状态的任务：检查超时，超时则重置为 WAITING
--   - 继续调度执行
--
--   CRDT 在集群内同步 Bucket 所有权：
--   - 存储：Bucket → Worker 映射 + lease 时间
--   - 作用：去中心化的 Bucket 分配，无需协调者
--   - 更新时机：认领/续租/释放 Bucket 时
--
-- 【实例化映射示例】
--
--   设计阶段（蓝图）                    执行阶段（实例）
--   ─────────────────                  ─────────────────
--   job_info:                          job_run:
--     id=1, name=任务A                   id=101, job_id=1
--     id=2, name=任务B                   id=102, job_id=2
--     id=3, name=任务C                   id=103, job_id=3
--
--   job_dependency:                    job_run_dependency:
--     job_id=2, parent_job_id=1   →      job_run_id=102, parent_run_id=101
--     job_id=3, parent_job_id=2   →      job_run_id=103, parent_run_id=102
--
--   映射关系：job_info.id → job_run.id (1→101, 2→102, 3→103)
--
-- 【状态流转】
--
--   状态值: 0-WAITING 1-RUNNING 2-SUCCESS 3-FAIL 4-CANCEL 5-TIMEOUT
--
--   job_run 状态流转：
--     WAITING ──调度器分发──→ RUNNING ──执行成功──→ SUCCESS
--                               │
--                               ├──执行失败──→ FAIL ──可重试──→ WAITING（原地重试）
--                               │               └──不可重试──→ FAIL（终态）
--                               │
--                               └──执行超时──→ TIMEOUT（终态，不重试）
--
--   workflow_run 状态流转：
--     WAITING ──首个job_run开始──→ RUNNING ──全部SUCCESS──→ SUCCESS
--                                    │
--                                    └──有FAIL/TIMEOUT且无RUNNING──→ FAIL
--
--   workflow_run 完成判断（原子性：全部成功才成功，否则失败）：
--     每个 job_run 完成后检查：
--       1. 还有 RUNNING 的 job_run？→ 继续等待
--       2. 全部 SUCCESS？→ workflow_run = SUCCESS，生成下一次
--       3. 否则 → workflow_run = FAIL
--
--   DAG 失败处理：
--     若某个 job_run 失败/超时，其下游 job_run 保持 WAITING
--     因为依赖不满足（parent.status != SUCCESS），不会被分发
--
-- 【失败与重试】
--
--   重试条件：status = FAIL 且 retry_count < max_retry_times
--
--   重试行为（原地重试，不生成新记录）：
--     1. Entity 检测到失败，判断是否可重试
--     2. 可重试：
--        - retry_count += 1，status 重置为 WAITING
--        - 通知 Dispatcher: Registerjob(job_run_id, now + retry_interval)
--        - Dispatcher 注册定时事件，到期后重新分发
--     3. 不可重试：status = FAIL（终态），通知 Dispatcher: JobCompleted
--
--   不重试的情况：
--     - TIMEOUT：超时不重试，直接终态
--     - retry_count >= max_retry_times：达到最大重试次数
--
-- 【超时检测】
--
--   由 JobExecutorEntity 自己负责，基于 Pekko Actor + Timers：
--
--   JobExecutorEntity 执行任务时：
--     1. 启动任务执行
--     2. 同时启动 Timer（timeout_seconds 秒后触发）
--     3. 任务先完成 → 取消 Timer → 更新状态 SUCCESS/FAIL
--     4. Timer 先到期 → 中断执行 → 更新状态 TIMEOUT
--
--   中断执行方式（按任务类型）：
--     - Shell/Python: 杀进程（Process.destroy）
--     - Spark/Flink: 调用 YARN/K8s API 取消
--     - HTTP: 取消 HTTP 请求
--     - HiveSQL: 取消查询
--
-- 【重跑机制】
--
--   核心原则：重跑不生成新的 run_id，原地重置状态
--
--   触发方式：
--     - NEW（新触发）：生成新的 workflow_run + job_run
--     - RERUN（重跑）：原地重置，不生成新记录
--
--   重跑行为：
--     workflow_run (id=100) FAIL
--       ├── job_run_A1 SUCCESS  ← 保持不动
--       ├── job_run_A2 FAIL     ← 重置为 WAITING
--       └── job_run_A3 WAITING  ← 保持 WAITING
--
--     重跑后：
--       - Server 更新 DB：workflow_run 状态 → RUNNING
--       - Server 更新 DB：FAIL 的 job_run → WAITING
--       - Server 完成，不通知任何人（遵循 Server 边界）
--       - Dispatcher 下一次定时器触发时发现状态变化（id > last_max_id 不适用）
--       - 注意：重跑的 job_run id 不变，但 status 变了
--       - Dispatcher 需要检查内存中 FAIL 状态的 job_run 是否变成 WAITING
--
--   跨工作流依赖与重跑：
--     1. B 绑定 A 的 workflow_run_id = 100
--     2. A(100) 失败 → B 等待
--     3. A(100) 重跑成功（还是 id=100）→ B 检查通过，继续
--     绑定关系不变，无需特殊处理
--
-- 【路由策略】
--
--   Dispatcher 分发任务时，根据路由策略选择 Worker
--
--   | 策略            | 值 | 说明                                      |
--   |-----------------|---|-------------------------------------------|
--   | FIRST           | 1 | 第一个可用的 Worker                        |
--   | ROUND_ROBIN     | 2 | 轮询，依次分发                             |
--   | RANDOM          | 3 | 随机选择                                   |
--   | CONSISTENT_HASH | 4 | 一致性哈希，相同参数发到同一个 Worker       |
--   | LEAST_BUSY      | 5 | 最空闲的 Worker                            |
--   | FAILOVER        | 6 | 故障转移，按顺序尝试，失败则换下一个        |
--   | SHARDING        | 7 | Task 分片，Worker 自治拆分执行             |
--
--   SHARDING 策略（Worker 自治模型）：
--
--   ┌─────────────────────────────────────────────────────────────────────────┐
--   │  1. Job 触发，信息写入 CRDT，所有 Worker 可见                            │
--   │  2. Worker 根据自身能力计算 splitSize                                   │
--   │  3. Worker 查 CRDT 找未处理范围，标记为 PROCESSING                      │
--   │  4. Worker 执行该范围的 Task                                            │
--   │  5. Worker 完成后标记为 COMPLETED，继续拆下一批                         │
--   │  6. 全部范围处理完成 → Job 完成                                         │
--   └─────────────────────────────────────────────────────────────────────────┘
--
--   JobContext（Worker 执行时获取）：
--     - jobId: 任务ID
--     - jobRunId: 执行实例ID
--     - splitStart: 分片起点
--     - splitEnd: 分片终点
--     - splitSize: 分片大小
--     - 其他上下文信息...
--
-- 【Pekko 集群特性】
--
--   | 特性              | 说明                                    |
--   |-------------------|----------------------------------------|
--   | CRDT (ddata)      | Bucket 所有权管理，Gossip 同步          |
--   | Cluster Sharding  | 可选，用于跨 Bucket 的任务执行          |
--   | Gossip            | 节点状态感知，故障快速检测               |
--   | Supervision       | Actor 崩溃自动重启                      |
--
--   故障转移：
--   - Worker 节点挂了 → Bucket lease 过期 → 其他 Worker 接管
--   - 任务执行超时 → 重置 job_run 状态为 WAITING → 重新调度
--   - 无单点故障，完全去中心化
--
-- ====================================================


-- =====================================================
-- 第一部分：设计阶段表（蓝图）
-- =====================================================

-- ---------------------------------------------------
-- 1. 命名空间表（多租户隔离）
-- ---------------------------------------------------
CREATE TABLE `job_namespace` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_name` VARCHAR(64) NOT NULL COMMENT '命名空间名称',
    `namespace_code` VARCHAR(64) NOT NULL COMMENT '命名空间编码（唯一标识）',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_namespace_code` (`namespace_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命名空间';

-- ---------------------------------------------------
-- 1.5. 组件定义表
--
-- 说明：
--   - 定义调度平台支持的任务类型（SHELL、PYTHON、SPARK...）
--   - job_params 存储参数模板，前端根据模板渲染表单
--   - status 控制组件是否上线可用
-- ---------------------------------------------------
CREATE TABLE `job_component` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `component_code` VARCHAR(50) NOT NULL COMMENT '组件编码（SHELL、PYTHON、SPARK等）',
    `component_name` VARCHAR(100) NOT NULL COMMENT '组件名称',
    `component_type` VARCHAR(20) DEFAULT NULL COMMENT '组件分类（脚本、数据同步、计算引擎等）',
    `job_params` JSON NOT NULL COMMENT '参数模板（JSON格式，定义参数key和默认值）',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '组件描述',
    `icon` VARCHAR(256) DEFAULT NULL COMMENT '图标URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-下线 1-上线',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_component_code` (`component_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务组件定义';

-- 初始化内置组件
INSERT INTO `job_component` (`component_code`, `component_name`, `component_type`, `job_params`, `description`, `sort_order`) VALUES
('SHELL', 'Shell 脚本', '脚本', '{"script": "", "timeout": 60}', '执行 Shell 脚本命令', 1),
('PYTHON', 'Python 脚本', '脚本', '{"script": "", "pythonPath": "/usr/bin/python3", "timeout": 60}', '执行 Python 脚本', 2),
('HTTP', 'HTTP 请求', '接口', '{"url": "", "method": "GET", "headers": {}, "body": "", "timeout": 30}', '发送 HTTP 请求', 3);

-- ---------------------------------------------------
-- 2. 工作流定义表
--
-- 说明：
--   - 工作流是调度的最小单位，定义触发策略
--   - 独立任务 = 只包含一个 job 的工作流
--   - workflow_status 上线时，Server 生成执行实例
-- ---------------------------------------------------
CREATE TABLE `job_workflow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_id` BIGINT NOT NULL COMMENT '命名空间ID',
    `workflow_name` VARCHAR(64) NOT NULL COMMENT '工作流名称',
    `workflow_status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-下线 1-上线',
    `trigger_type` TINYINT NOT NULL DEFAULT 1 COMMENT '触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API',
    `trigger_value` VARCHAR(128) DEFAULT NULL COMMENT '触发配置（CRON表达式或秒数）',
    `timeout_seconds` INT NOT NULL DEFAULT 0 COMMENT '整体超时（秒）0-不限制',
    `max_retry_times` INT NOT NULL DEFAULT 0 COMMENT '失败重试次数',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级: 数字越大越优先',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_namespace_workflow` (`namespace_id`, `workflow_name`),
    KEY `idx_status` (`workflow_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义';

-- ---------------------------------------------------
-- 3. 任务定义表
--
-- 说明：
--   - 任务必须属于某个工作流
--   - job_params 根据 job_type 存储不同结构的 JSON
-- ---------------------------------------------------
CREATE TABLE `job_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_id` BIGINT NOT NULL COMMENT '命名空间ID',
    `workflow_id` BIGINT NOT NULL COMMENT '所属工作流ID',
    `job_name` VARCHAR(64) NOT NULL COMMENT '任务名称',
    `job_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `job_type` VARCHAR(32) NOT NULL COMMENT '任务类型: SHELL/PYTHON/SPARK/FLINK/HIVE_SQL/DATAX/HTTP 或自定义类型',
    `job_params` JSON DEFAULT NULL COMMENT '任务配置（JSON格式，不同类型结构不同）',
    `route_strategy` TINYINT NOT NULL DEFAULT 1 COMMENT '路由策略: 1-FIRST 2-ROUND_ROBIN 3-RANDOM 4-HASH 5-LEAST_BUSY 6-FAILOVER 7-SHARDING',
    `block_strategy` TINYINT NOT NULL DEFAULT 3 COMMENT '阻塞策略: 1-丢弃后续 2-覆盖之前 3-并行执行',
    `timeout_seconds` INT NOT NULL DEFAULT 0 COMMENT '执行超时（秒）0-不限制',
    `max_retry_times` INT NOT NULL DEFAULT 0 COMMENT '失败重试次数',
    `retry_interval` INT NOT NULL DEFAULT 0 COMMENT '重试间隔（秒）',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级: 数字越大越优先',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workflow_job` (`workflow_id`, `job_name`),
    KEY `idx_namespace` (`namespace_id`),
    KEY `idx_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务定义';

-- job_params JSON 结构示例:
-- Shell:   {"script": "#!/bin/bash\necho hello", "scriptPath": "/opt/scripts/etl.sh"}
-- Python:  {"script": "print('hello')", "scriptPath": "/opt/scripts/etl.py", "pythonPath": "/usr/bin/python3"}
-- Spark:   {"jarPath": "hdfs:///jars/etl.jar", "mainClass": "com.example.Job", "args": ["--date", "${bizDate}"], "driverMemory": "2g", "executorMemory": "4g", "numExecutors": 10}
-- Flink:   {"jarPath": "hdfs:///jars/etl.jar", "entryClass": "com.example.Job", "parallelism": 4}
-- HiveSQL: {"sql": "INSERT OVERWRITE TABLE ...", "engine": "spark"}
-- DataX:   {"jobJson": {...}}
-- HTTP:    {"url": "https://api.example.com", "method": "POST", "headers": {}, "body": "{}"}

-- ---------------------------------------------------
-- 4. 任务依赖关系表（设计阶段，工作流内 DAG）
--
-- 说明：
--   - 定义 job_info 之间的依赖关系
--   - Workflow 实例化时，会复制到 job_run_dependency
-- ---------------------------------------------------
CREATE TABLE `job_dependency` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id` BIGINT NOT NULL COMMENT '所属工作流ID',
    `job_id` BIGINT NOT NULL COMMENT '当前任务ID',
    `parent_job_id` BIGINT NOT NULL COMMENT '上游任务ID（依赖）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dependency` (`workflow_id`, `job_id`, `parent_job_id`),
    KEY `idx_job` (`job_id`),
    KEY `idx_parent` (`parent_job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务依赖关系（设计阶段）';

-- ---------------------------------------------------
-- 5. 工作流依赖关系表（跨工作流，同周期依赖）
--
-- 说明：
--   - 当前工作流依赖另一个工作流中的某个 job 在同一调度周期内成功
--   - trigger_time 相同视为同一调度周期
-- ---------------------------------------------------
CREATE TABLE `job_workflow_dependency` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_id` BIGINT NOT NULL COMMENT '当前工作流ID',
    `depend_workflow_id` BIGINT NOT NULL COMMENT '依赖的工作流ID',
    `depend_job_id` BIGINT NOT NULL COMMENT '依赖的具体任务ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dependency` (`workflow_id`, `depend_workflow_id`, `depend_job_id`),
    KEY `idx_depend` (`depend_workflow_id`, `depend_job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流依赖关系（跨工作流）';


-- =====================================================
-- 第二部分：执行阶段表（实例）
-- =====================================================

-- ---------------------------------------------------
-- 6. 工作流执行实例表
--
-- 说明：
--   - Workflow 上线时生成，代表一次调度周期的执行
--   - 包含多个 job_run（对应 workflow 内的所有 job_info）
-- ---------------------------------------------------
CREATE TABLE `job_workflow_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_id` BIGINT NOT NULL COMMENT '命名空间ID',
    `workflow_id` BIGINT NOT NULL COMMENT '工作流ID',
    `trigger_type` TINYINT NOT NULL COMMENT '触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API',
    `trigger_time` BIGINT NOT NULL COMMENT '计划触发时间（毫秒）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-等待 1-运行中 2-成功 3-失败 4-取消 5-超时',
    `start_time` BIGINT DEFAULT NULL COMMENT '实际开始时间（毫秒）',
    `end_time` BIGINT DEFAULT NULL COMMENT '结束时间（毫秒）',
    `result_message` TEXT DEFAULT NULL COMMENT '执行结果/错误信息',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workflow_trigger` (`workflow_id`, `trigger_time`),
    KEY `idx_namespace` (`namespace_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行实例';

-- ---------------------------------------------------
-- 7. 任务执行实例表
--
-- 说明：
--   - Worker Pull 的核心对象
--   - 每条记录代表一个待执行/执行中/已完成的任务实例
--   - bucket_id 用于分片，Worker 只查询自己负责的 Bucket
--   - bucket_id = hash(job_id) % 1024，保证同一 job 的所有 run 在同一 Bucket
-- ---------------------------------------------------
CREATE TABLE `job_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_id` BIGINT NOT NULL COMMENT '命名空间ID',
    `workflow_run_id` BIGINT NOT NULL COMMENT '所属工作流执行实例ID',
    `job_id` BIGINT NOT NULL COMMENT '任务定义ID',
    `bucket_id` INT NOT NULL COMMENT '分片ID: hash(job_id) % 1024，用于 Worker 分片查询',
    `trigger_type` TINYINT NOT NULL COMMENT '触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API 6-重试',
    `trigger_time` BIGINT NOT NULL COMMENT '计划触发时间（毫秒）',
    `job_params` JSON DEFAULT NULL COMMENT '任务参数（JSON）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-等待 1-运行中 2-成功 3-失败 4-取消 5-超时',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级: 数字越大越优先',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `worker_address` VARCHAR(64) DEFAULT NULL COMMENT '执行者（Worker Address）',
    `start_time` BIGINT DEFAULT NULL COMMENT '实际开始时间（毫秒）',
    `end_time` BIGINT DEFAULT NULL COMMENT '结束时间（毫秒）',
    `result_message` TEXT DEFAULT NULL COMMENT '执行结果/错误信息',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_workflow_run` (`workflow_run_id`),
    KEY `idx_job` (`job_id`),
    KEY `idx_namespace` (`namespace_id`),
    KEY `idx_bucket_status` (`bucket_id`, `status`, `trigger_time`),
    KEY `idx_trigger_time` (`job_id`, `trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行实例';

-- ---------------------------------------------------
-- 8. 任务执行依赖关系表（统一模型：工作流内 + 跨工作流）
--
-- 说明：
--   - 统一存储 job_run 之间的依赖关系
--   - 工作流内依赖：parent_run_id 在同一个 workflow_run 内
--   - 跨工作流依赖：parent_run_id 在另一个 workflow_run 内
--   - Worker 检查依赖时只需查这张表，逻辑统一
--
-- 依赖来源：
--   - 工作流内：根据 job_dependency 映射生成
--   - 跨工作流：根据 job_workflow_dependency 绑定生成
--     B 实例化时，找到 A 的最新成功 workflow_run，绑定其 job_run
--
-- 依赖检查 SQL（工作流内和跨工作流统一）：
--   SELECT 1 FROM job_run_dependency d
--   JOIN job_run parent ON d.parent_run_id = parent.id
--   WHERE d.job_run_id = ? AND parent.status != 2  -- 2=SUCCESS
-- ---------------------------------------------------
CREATE TABLE `job_run_dependency` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_run_id` BIGINT NOT NULL COMMENT '当前任务所属的工作流执行实例ID',
    `job_run_id` BIGINT NOT NULL COMMENT '当前任务实例ID',
    `parent_run_id` BIGINT NOT NULL COMMENT '依赖的任务实例ID（可同一workflow，可跨workflow）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dependency` (`job_run_id`, `parent_run_id`),
    KEY `idx_workflow_run` (`workflow_run_id`),
    KEY `idx_parent` (`parent_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行依赖关系（工作流内+跨工作流统一）';


-- =====================================================
-- 第三部分：告警配置
-- =====================================================

-- ---------------------------------------------------
-- 9. 告警渠道表
-- ---------------------------------------------------
CREATE TABLE `job_alarm_channel` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_id` BIGINT NOT NULL COMMENT '命名空间ID',
    `channel_name` VARCHAR(64) NOT NULL COMMENT '渠道名称',
    `channel_type` TINYINT NOT NULL COMMENT '渠道类型: 1-钉钉 2-企微 3-飞书 4-Webhook 5-邮件',
    `channel_config` JSON NOT NULL COMMENT '渠道配置（webhook地址、密钥等）',
    `channel_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_namespace` (`namespace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警渠道';

-- channel_config JSON 示例:
-- 钉钉:    {"webhook": "https://oapi.dingtalk.com/robot/send?access_token=xxx", "secret": "SECxxx"}
-- 企微:    {"webhook": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"}
-- 飞书:    {"webhook": "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", "secret": "xxx"}
-- Webhook: {"url": "https://example.com/alert", "method": "POST", "headers": {"Authorization": "Bearer xxx"}}
-- 邮件:    {"smtp": "smtp.example.com", "port": 465, "username": "xxx", "password": "xxx", "to": ["a@b.com"]}

-- ---------------------------------------------------
-- 10. 告警规则表
-- ---------------------------------------------------
CREATE TABLE `job_alarm_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_id` BIGINT NOT NULL COMMENT '命名空间ID',
    `rule_name` VARCHAR(64) NOT NULL COMMENT '规则名称',
    `job_id` BIGINT DEFAULT NULL COMMENT '关联的任务ID（与workflow_id互斥）',
    `workflow_id` BIGINT DEFAULT NULL COMMENT '关联的工作流ID（与job_id互斥）',
    `trigger_event` TINYINT NOT NULL DEFAULT 1 COMMENT '触发事件: 1-失败 2-超时 3-成功',
    `fail_threshold` INT NOT NULL DEFAULT 1 COMMENT '连续失败N次触发告警',
    `notify_on_recover` TINYINT NOT NULL DEFAULT 0 COMMENT '恢复时是否通知: 0-否 1-是',
    `channel_id` BIGINT NOT NULL COMMENT '告警渠道ID',
    `consecutive_fails` INT NOT NULL DEFAULT 0 COMMENT '当前连续失败次数',
    `alarm_status` TINYINT NOT NULL DEFAULT 0 COMMENT '告警状态: 0-正常 1-已触发',
    `last_trigger_time` BIGINT DEFAULT NULL COMMENT '上次触发时间（毫秒）',
    `rule_status` TINYINT NOT NULL DEFAULT 1 COMMENT '规则状态: 0-禁用 1-启用',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_job` (`job_id`),
    KEY `idx_workflow` (`workflow_id`),
    KEY `idx_namespace` (`namespace_id`),
    KEY `idx_channel` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则';

-- ---------------------------------------------------
-- 11. 告警发送记录表
-- ---------------------------------------------------
CREATE TABLE `job_alarm_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `namespace_id` BIGINT NOT NULL COMMENT '命名空间ID',
    `rule_id` BIGINT NOT NULL COMMENT '告警规则ID',
    `channel_id` BIGINT NOT NULL COMMENT '告警渠道ID',
    `job_run_id` BIGINT DEFAULT NULL COMMENT '任务执行实例ID',
    `workflow_run_id` BIGINT DEFAULT NULL COMMENT '工作流执行实例ID',
    `alarm_type` TINYINT NOT NULL DEFAULT 1 COMMENT '告警类型: 1-告警 2-恢复',
    `alarm_title` VARCHAR(200) NOT NULL COMMENT '告警标题',
    `alarm_content` TEXT NOT NULL COMMENT '告警内容',
    `send_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态: 0-待发送 1-成功 2-失败',
    `send_result` VARCHAR(500) DEFAULT NULL COMMENT '发送结果/错误信息',
    `send_time` DATETIME(3) DEFAULT NULL COMMENT '发送时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_rule` (`rule_id`),
    KEY `idx_job_run` (`job_run_id`),
    KEY `idx_workflow_run` (`workflow_run_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警发送记录';


-- =====================================================
-- 第四部分：Worker 状态
-- =====================================================

-- ---------------------------------------------------
-- 12. Bucket 租约持久化表
--
-- 说明：
--   - 仅用于 Worker 重启时恢复 Bucket 所有权
--   - 运行时状态由 CRDT 管理，此表为持久化备份
--   - Worker 认领 Bucket 后异步写入，释放时删除
-- ---------------------------------------------------
CREATE TABLE `job_bucket_lease` (
    `bucket_id` INT NOT NULL COMMENT 'Bucket ID (0 ~ 1023)',
    `worker_address` VARCHAR(64) NOT NULL COMMENT '持有者 Worker 地址',
    `lease_time` BIGINT NOT NULL COMMENT '最后更新时间（毫秒）',
    PRIMARY KEY (`bucket_id`),
    KEY `idx_worker` (`worker_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bucket 租约持久化（仅用于恢复）';


-- =====================================================
-- 初始化数据
-- =====================================================
INSERT INTO `job_namespace` (`namespace_name`, `namespace_code`, `description`)
VALUES ('默认命名空间', 'default', '系统默认命名空间');
