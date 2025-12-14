package com.sunny.job.core.strategy.route;

import java.util.Collections;
import java.util.List;

/**
 * 路由结果
 * <p>
 * 包含选中的 Worker 地址（支持单选和多选）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class RouteResult {

    private final boolean success;
    private final String message;
    private final List<WorkerInfo> selectedWorkers;

    private RouteResult(boolean success, String message, List<WorkerInfo> selectedWorkers) {
        this.success = success;
        this.message = message;
        this.selectedWorkers = selectedWorkers;
    }

    /**
     * 路由成功（单个 Worker）
     */
    public static RouteResult success(WorkerInfo worker) {
        return new RouteResult(true, null, Collections.singletonList(worker));
    }

    /**
     * 路由成功（多个 Worker，用于分片广播）
     */
    public static RouteResult success(List<WorkerInfo> workers) {
        return new RouteResult(true, null, workers);
    }

    /**
     * 路由失败
     */
    public static RouteResult fail(String message) {
        return new RouteResult(false, message, Collections.emptyList());
    }

    /**
     * 无可用 Worker
     */
    public static RouteResult noWorker() {
        return new RouteResult(false, "无可用 Worker", Collections.emptyList());
    }

    /**
     * 是否路由成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取错误消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取选中的 Worker 列表
     */
    public List<WorkerInfo> getSelectedWorkers() {
        return selectedWorkers;
    }

    /**
     * 获取第一个选中的 Worker
     */
    public WorkerInfo getFirstWorker() {
        if (selectedWorkers == null || selectedWorkers.isEmpty()) {
            return null;
        }
        return selectedWorkers.getFirst();
    }

    /**
     * 获取第一个选中的 Worker 地址
     */
    public String getFirstAddress() {
        WorkerInfo worker = getFirstWorker();
        return worker == null ? null : worker.address();
    }

    /**
     * 是否为广播模式（多个 Worker）
     */
    public boolean isBroadcast() {
        return success && selectedWorkers != null && selectedWorkers.size() > 1;
    }

    /**
     * 获取选中的 Worker 数量
     */
    public int selectedCount() {
        return selectedWorkers == null ? 0 : selectedWorkers.size();
    }

    @Override
    public String toString() {
        if (success) {
            return "RouteResult{success=true, workers=" + selectedCount() + "}";
        }
        return "RouteResult{success=false, message='" + message + "'}";
    }
}
