package com.sunny.job.worker.pekko.ddata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.job.core.message.WorkflowBroadcast;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.ddata.Key;
import org.apache.pekko.cluster.ddata.LWWMap;
import org.apache.pekko.cluster.ddata.LWWMapKey;
import org.apache.pekko.cluster.ddata.SelfUniqueAddress;
import org.apache.pekko.cluster.ddata.typed.javadsl.DistributedData;
import org.apache.pekko.cluster.ddata.typed.javadsl.Replicator;
import org.apache.pekko.cluster.ddata.typed.javadsl.ReplicatorMessageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 工作流广播状态管理
 * <p>
 * 监听 Server 通过 CRDT 广播的工作流事件，同时也可以广播 ACK 回 Server
 * <p>
 * 使用 LWWMap[String, String] 订阅：
 * - Key: eventId
 * - Value: JSON 序列化的 WorkflowBroadcast
 * <p>
 * 收到新事件后回调 listener 处理
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class WorkflowBroadcastState {

    private static final Logger log = LoggerFactory.getLogger(WorkflowBroadcastState.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BROADCAST_KEY = "workflow-broadcast";

    private final ActorSystem<?> system;
    private final SelfUniqueAddress selfUniqueAddress;
    private final Key<LWWMap<String, String>> dataKey;

    /**
     * 已处理的事件 ID（用于去重）
     */
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * 事件监听器
     */
    private volatile Consumer<WorkflowBroadcast> eventListener;

    /**
     * 内部 Actor 引用
     */
    private ActorRef<InternalCommand> internalActor;

    public WorkflowBroadcastState(ActorSystem<?> system) {
        this.system = system;
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();
        this.dataKey = LWWMapKey.create(BROADCAST_KEY);

        // 创建内部 Actor 处理 CRDT 订阅和广播
        this.internalActor = system.systemActorOf(
                InternalActor.create(this),
                "workflow-broadcast-listener",
                Props.empty()
        );

        log.info("WorkflowBroadcastState 初始化完成");
    }

    /**
     * 设置事件监听器
     *
     * @param listener 监听器，参数为 WorkflowBroadcast 事件
     */
    public void subscribe(Consumer<WorkflowBroadcast> listener) {
        this.eventListener = listener;
        log.info("已设置工作流广播事件监听器");
    }

    /**
     * 广播事件（用于发送 ACK）
     *
     * @param event 工作流广播事件
     */
    public void broadcast(WorkflowBroadcast event) {
        log.info("广播工作流事件: eventId={}, op={}", event.getEventId(), event.getOp());

        // 标记为已处理，避免自己收到后再次处理
        processedEventIds.add(event.getEventId());

        // 序列化事件为 JSON
        String eventJson;
        try {
            eventJson = MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            log.error("序列化工作流广播事件失败: eventId={}", event.getEventId(), e);
            return;
        }

        // 发送广播命令到内部 Actor
        internalActor.tell(new BroadcastCommand(event.getEventId(), eventJson));
    }

    /**
     * 处理 CRDT 数据变化（由内部 Actor 调用）
     */
    @SuppressWarnings("unchecked")
    void onDataChanged(LWWMap<String, String> data) {
        Object entriesObj = data.getEntries();

        // 处理 Scala Map 类型
        if (entriesObj instanceof scala.collection.Map) {
            scala.collection.Map<String, String> scalaMap = (scala.collection.Map<String, String>) entriesObj;
            scala.collection.Iterator<scala.Tuple2<String, String>> iterator = scalaMap.iterator();

            while (iterator.hasNext()) {
                scala.Tuple2<String, String> tuple = iterator.next();
                processEvent(tuple._1(), tuple._2());
            }
        }
        // 处理 Java Map 类型（兼容不同版本）
        else if (entriesObj instanceof java.util.Map) {
            java.util.Map<String, String> javaMap = (java.util.Map<String, String>) entriesObj;
            for (java.util.Map.Entry<String, String> entry : javaMap.entrySet()) {
                processEvent(entry.getKey(), entry.getValue());
            }
        } else {
            log.warn("CRDT 数据格式不正确，类型: {}", entriesObj.getClass().getName());
            return;
        }

        // 清理过期的事件 ID（保留最近 10000 个）
        if (processedEventIds.size() > 10000) {
            log.debug("清理过期的事件 ID，当前数量: {}", processedEventIds.size());
            int toRemove = processedEventIds.size() / 2;
            var cleanupIterator = processedEventIds.iterator();
            while (toRemove > 0 && cleanupIterator.hasNext()) {
                cleanupIterator.next();
                cleanupIterator.remove();
                toRemove--;
            }
        }
    }

    /**
     * 处理单个事件
     */
    private void processEvent(String eventId, String eventJson) {
        // 去重：已处理的事件跳过
        if (processedEventIds.contains(eventId)) {
            return;
        }

        // 标记为已处理
        processedEventIds.add(eventId);

        // 解析事件
        try {
            WorkflowBroadcast event = MAPPER.readValue(eventJson, WorkflowBroadcast.class);
            if (event != null) {
                log.info("收到工作流广播事件: eventId={}, op={}", event.getEventId(), event.getOp());

                // 回调监听器
                if (eventListener != null) {
                    eventListener.accept(event);
                }
            }
        } catch (Exception e) {
            log.error("解析工作流广播事件失败: eventId={}", eventId, e);
        }
    }

    /**
     * 获取已处理的事件数量（用于监控）
     */
    public int getProcessedEventCount() {
        return processedEventIds.size();
    }

    // ============ 内部命令接口 ============

    interface InternalCommand {}

    static class BroadcastCommand implements InternalCommand {
        final String eventId;
        final String eventJson;

        BroadcastCommand(String eventId, String eventJson) {
            this.eventId = eventId;
            this.eventJson = eventJson;
        }
    }

    private static class InternalSubscribeResponse implements InternalCommand {
        final Replicator.SubscribeResponse<LWWMap<String, String>> response;

        InternalSubscribeResponse(Replicator.SubscribeResponse<LWWMap<String, String>> response) {
            this.response = response;
        }
    }

    private static class InternalUpdateResponse implements InternalCommand {
        final Replicator.UpdateResponse<LWWMap<String, String>> response;

        InternalUpdateResponse(Replicator.UpdateResponse<LWWMap<String, String>> response) {
            this.response = response;
        }
    }

    // ============ 内部 Actor ============

    private static class InternalActor extends AbstractBehavior<InternalCommand> {

        private final WorkflowBroadcastState state;
        private final ReplicatorMessageAdapter<InternalCommand, LWWMap<String, String>> replicatorAdapter;
        private final SelfUniqueAddress selfUniqueAddress;

        static Behavior<InternalCommand> create(WorkflowBroadcastState state) {
            return Behaviors.setup(context -> new InternalActor(context, state));
        }

        private InternalActor(ActorContext<InternalCommand> context, WorkflowBroadcastState state) {
            super(context);
            this.state = state;
            this.selfUniqueAddress = DistributedData.get(context.getSystem()).selfUniqueAddress();

            // 创建 Replicator 消息适配器
            ActorRef<Replicator.Command> replicator = DistributedData.get(context.getSystem()).replicator();
            this.replicatorAdapter = new ReplicatorMessageAdapter<>(context, replicator, java.time.Duration.ofSeconds(5));

            // 订阅 CRDT 数据变化
            replicatorAdapter.subscribe(state.dataKey, InternalSubscribeResponse::new);

            log.info("WorkflowBroadcastState 内部 Actor 初始化完成，已订阅 CRDT 数据变化");
        }

        @Override
        public Receive<InternalCommand> createReceive() {
            return newReceiveBuilder()
                    .onMessage(InternalSubscribeResponse.class, this::onSubscribeResponse)
                    .onMessage(BroadcastCommand.class, this::onBroadcast)
                    .onMessage(InternalUpdateResponse.class, this::onUpdateResponse)
                    .build();
        }

        private Behavior<InternalCommand> onSubscribeResponse(InternalSubscribeResponse wrapper) {
            Replicator.SubscribeResponse<LWWMap<String, String>> response = wrapper.response;
            log.info("收到 CRDT 订阅响应: type={}", response.getClass().getSimpleName());
            if (response instanceof Replicator.Changed) {
                @SuppressWarnings("unchecked")
                Replicator.Changed<LWWMap<String, String>> changed = (Replicator.Changed<LWWMap<String, String>>) response;
                LWWMap<String, String> data = changed.get(state.dataKey);
                log.info("CRDT 数据变化: size={}", data.size());
                state.onDataChanged(data);
            } else if (response instanceof Replicator.Deleted) {
                log.warn("CRDT 数据被删除");
            }
            return this;
        }

        private Behavior<InternalCommand> onBroadcast(BroadcastCommand cmd) {
            log.debug("处理广播命令: eventId={}", cmd.eventId);

            replicatorAdapter.askUpdate(
                    askReplyTo -> new Replicator.Update<>(
                            state.dataKey,
                            LWWMap.empty(),
                            new Replicator.WriteMajority(java.time.Duration.ofSeconds(3)),
                            askReplyTo,
                            existing -> existing.put(selfUniqueAddress, cmd.eventId, cmd.eventJson)
                    ),
                    InternalUpdateResponse::new
            );
            return this;
        }

        private Behavior<InternalCommand> onUpdateResponse(InternalUpdateResponse wrapper) {
            Replicator.UpdateResponse<LWWMap<String, String>> response = wrapper.response;
            if (response instanceof Replicator.UpdateSuccess) {
                log.debug("CRDT 广播成功");
            } else if (response instanceof Replicator.UpdateFailure) {
                log.warn("CRDT 广播失败: {}", response);
            }
            return this;
        }
    }
}
