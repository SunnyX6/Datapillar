package io.datapillar.openlineage.transform;

import io.openlineage.client.OpenLineage.RunEvent;
import io.openlineage.client.OpenLineage.RunEvent.EventType;

/**
 * 只保留 COMPLETE 事件的过滤器
 *
 * 过滤掉 START、RUNNING 等事件，避免重复处理
 */
public class CompleteOnlyFilter implements EventFilter {

    @Override
    public boolean shouldEmit(RunEvent event) {
        if (event == null || event.getEventType() == null) {
            return false;
        }
        return event.getEventType() == EventType.COMPLETE;
    }
}
