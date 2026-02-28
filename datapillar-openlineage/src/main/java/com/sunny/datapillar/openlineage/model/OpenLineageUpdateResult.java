package com.sunny.datapillar.openlineage.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图谱更新结果。
 */
public class OpenLineageUpdateResult {

    private final List<AsyncTaskCandidate> taskCandidates;

    public OpenLineageUpdateResult(List<AsyncTaskCandidate> taskCandidates) {
        this.taskCandidates = taskCandidates == null ? Collections.emptyList() : List.copyOf(taskCandidates);
    }

    public List<AsyncTaskCandidate> taskCandidates() {
        return taskCandidates;
    }

    public static OpenLineageUpdateResult empty() {
        return new OpenLineageUpdateResult(Collections.emptyList());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<AsyncTaskCandidate> candidates = new ArrayList<>();

        public Builder addCandidate(AsyncTaskCandidate candidate) {
            if (candidate != null) {
                candidates.add(candidate);
            }
            return this;
        }

        public OpenLineageUpdateResult build() {
            return new OpenLineageUpdateResult(candidates);
        }
    }
}
