package com.sunny.job.core.dag;

import java.io.Serializable;
import java.util.Objects;

/**
 * DAG 边元数据
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class DagEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long fromNodeId;
    private final long toNodeId;
    private final String conditionExpr;

    public DagEdge(long fromNodeId, long toNodeId) {
        this(fromNodeId, toNodeId, null);
    }

    public DagEdge(long fromNodeId, long toNodeId, String conditionExpr) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.conditionExpr = conditionExpr;
    }

    public long getFromNodeId() {
        return fromNodeId;
    }

    public long getToNodeId() {
        return toNodeId;
    }

    public String getConditionExpr() {
        return conditionExpr;
    }

    public boolean hasCondition() {
        return conditionExpr != null && !conditionExpr.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DagEdge dagEdge = (DagEdge) o;
        return fromNodeId == dagEdge.fromNodeId && toNodeId == dagEdge.toNodeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromNodeId, toNodeId);
    }

    @Override
    public String toString() {
        return fromNodeId + " -> " + toNodeId + (hasCondition() ? " [" + conditionExpr + "]" : "");
    }
}
