package com.sunny.job.worker.pekko.ddata;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.job.core.message.JobRunBroadcast;
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
 * 任务级广播状态管理
 * <p>
 * 监听 Server 通过 CRDT 广播的任务级事件，同时也可以广播 ACK 回 Server
 * <p>
 * 使用 LWWMap[String, String] 订阅：
 * - Key: eventId
 * - Value: JSON 序列化的 JobRunBroadcast
 * <p>
 * 与 WorkflowBroadcastState 分开，使用不同的 CRDT Key，数据隔离
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class JobRunBroadcastState {

    private static final Logger log = LoggerFactory.getLogger(JobRunBroadcastState.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String BROADCAST_KEY = "jobrun-broadcast";

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
    private volatile Consumer<JobRunBroadcast> eventListener;

    /**
     * 内部 Actor 引用
     */
    private ActorRef<InternalCommand> internalActor;

    public JobRunBroadcastState(ActorSystem<?> system) {
        this.system = system;
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();
        this.dataKey = LWWMapKey.create(BROADCAST_KEY);

        // 创建内部 Actor 处理 CRDT 订阅和广播
        this.internalActor = system.systemActorOf(
                InternalActor.create(this),
                "jobrun-broadcast-listener",
                Props.empty()
        );

        log.info("JobRunBroadcastState 初始化完成");
    }

    /**
     * 设置事件监听器
     *
     * @param listener 监听器，参数为 JobRunBroadcast 事件
     */
    public void subscribe(Consumer<JobRunBroadcast> listener) {
        this.eventListener = listener;
        log.info("已设置任务级广播事件监听器");
    }

    /**
     * 广播事件（用于发送 ACK）
     *
     * @param event 任务级广播事件
     */
    public void broadcast(JobRunBroadcast event) {
        log.info("广播任务级事件: eventId={}, op={}", event.getEventId(), event.getOp());

        // 标记为已处理，避免自己收到后再次处理
        processedEventIds.add(event.getEventId());

        // 序列化事件为 JSON
        String eventJson;
        try {
            eventJson = MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            log.error("序列化任务级广播事件失败: eventId={}", event.getEventId(), e);
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
        // Pekko 1.2.1 的 getEntries() 返回 Java Map
        java.util.Map<String, String> entries = (java.util.Map<String, String>) data.getEntries();

        for (java.util.Map.Entry<String, String> entry : entries.entrySet()) {
            String eventId = entry.getKey();
            String eventJson = entry.getValue();

            // 去重：已处理的事件跳过
            if (processedEventIds.contains(eventId)) {
                continue;
            }

            // 标记为已处理
            processedEventIds.add(eventId);

            // 解析事件
            try {
                JobRunBroadcast event = MAPPER.readValue(eventJson, JobRunBroadcast.class);
                if (event != null) {
                    log.info("收到任务级广播事件: eventId={}, op={}", event.getEventId(), event.getOp());

                    // 回调监听器
                    if (eventListener != null) {
                        eventListener.accept(event);
                    }
                }
            } catch (Exception e) {
                log.error("解析任务级广播事件失败: eventId={}", eventId, e);
            }
        }

        // 清理过期的事件 ID（保留最近 10000 个）
        if (processedEventIds.size() > 10000) {
            log.debug("清理过期的事件 ID，当前数量: {}", processedEventIds.size());
            int toRemove = processedEventIds.size() / 2;
            var iterator = processedEventIds.iterator();
            while (toRemove > 0 && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
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

        private final JobRunBroadcastState state;
        private final ReplicatorMessageAdapter<InternalCommand, LWWMap<String, String>> replicatorAdapter;
        private final SelfUniqueAddress selfUniqueAddress;

        static Behavior<InternalCommand> create(JobRunBroadcastState state) {
            return Behaviors.setup(context -> new InternalActor(context, state));
        }

        private InternalActor(ActorContext<InternalCommand> context, JobRunBroadcastState state) {
            super(context);
            this.state = state;
            this.selfUniqueAddress = DistributedData.get(context.getSystem()).selfUniqueAddress();

            // 创建 Replicator 消息适配器
            ActorRef<Replicator.Command> replicator = DistributedData.get(context.getSystem()).replicator();
            this.replicatorAdapter = new ReplicatorMessageAdapter<>(context, replicator, java.time.Duration.ofSeconds(5));

            // 订阅 CRDT 数据变化
            replicatorAdapter.subscribe(state.dataKey, InternalSubscribeResponse::new);

            log.info("JobRunBroadcastState 内部 Actor 初始化完成，已订阅 CRDT 数据变化");
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
            if (response instanceof Replicator.Changed) {
                @SuppressWarnings("unchecked")
                Replicator.Changed<LWWMap<String, String>> changed = (Replicator.Changed<LWWMap<String, String>>) response;
                state.onDataChanged(changed.get(state.dataKey));
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
