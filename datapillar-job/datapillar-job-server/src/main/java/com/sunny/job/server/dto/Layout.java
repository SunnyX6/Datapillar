package com.sunny.job.server.dto;

import java.util.List;

/**
 * 布局保存 DTO
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
public class Layout {

    private List<Position> positions;

    public static class Position {

        private Long jobId;

        private Double x;

        private Double y;

        public Long getJobId() {
            return jobId;
        }

        public void setJobId(Long jobId) {
            this.jobId = jobId;
        }

        public Double getX() {
            return x;
        }

        public void setX(Double x) {
            this.x = x;
        }

        public Double getY() {
            return y;
        }

        public void setY(Double y) {
            this.y = y;
        }
    }

    public List<Position> getPositions() {
        return positions;
    }

    public void setPositions(List<Position> positions) {
        this.positions = positions;
    }
}
