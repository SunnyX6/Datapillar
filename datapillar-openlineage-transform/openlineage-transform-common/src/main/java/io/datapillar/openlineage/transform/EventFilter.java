package io.datapillar.openlineage.transform;

import io.openlineage.client.OpenLineage.RunEvent;

/**
 * 事件过滤器接口
 */
public interface EventFilter {

    /**
     * 判断事件是否应该被发送
     *
     * @param event OpenLineage RunEvent
     * @return true 保留事件，false 过滤掉
     */
    boolean shouldEmit(RunEvent event);
}
