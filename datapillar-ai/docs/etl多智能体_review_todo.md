# ETL 多智能体 Review TODO（面向“生成 ETL 工作流”交付）

目标：不改变产品交互主线（仍以“表收敛”为锚点），在不做过度优化的前提下提升稳定性、可解释性与用户体验。

## P0（优先落地）

1. **human 抢占优先**
   - 现状：`pending_requests` 队首如果是 `delegate`，可能阻塞后续 `human` 请求，导致用户迟迟看不到需要回答的问题。
   - 目标：只要队列里存在待处理 `human` 请求，优先进入 `human_in_the_loop`。

2. **可恢复错误不直接 finalize**
   - 现状：出现错误后容易提前走 `finalize`，在用户看来像“结束了但没交付”。
   - 目标：对可恢复错误先触发一次 `human` 补充/简化输入的恢复流程；无法恢复再结束。

## P1（重要但不阻塞）

3. **no-hit 引导更产品化**
   - 目标：当知识检索 no-hit 或表无法定位时，优先给出“可选入口/推荐导航”（tag/catalog），减少用户贴 SQL、猜表名的成本。

4. **请求审计沉淀统一**
   - 目标：将 human 与 delegate 的关键审计信息以统一结构落盘到状态里（request_id、created_by、completed_by、耗时、摘要），便于复盘与问题定位。

## P2（体验/运维型优化）

5. **关键阈值参数配置化**
   - 目标：将 `max_iterations/max_human_requests/min_score/max_pointers` 等关键阈值收敛到配置，便于按租户/场景调参（不改变默认行为）。

## 当前状态

- P0-1 已完成：human 抢占优先（router + human_in_the_loop）
- P0-2 已完成：error 可恢复时先走一次人机补充
- P1-3 已完成：在需澄清场景附带 tag/catalog 导航数据（不新增额外打断）
- P1-4 已完成：human 与 delegate 的完成信息统一沉淀到 `state.request_results`
- P2-5 已完成：新增 dynaconf 配置项 `etl_orchestrator_*` 并在编排器初始化/初始 state 注入
