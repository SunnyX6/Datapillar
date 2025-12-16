package com.sunny.job.server.broadcast;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/**
 * 任务级广播器
 * <p>
 * 使用 Pekko Distributed Data (CRDT) 广播任务级事件给所有 Worker
 * <p>
 * 数据结构: LWWMap[String, String]
 * - Key: eventId（事件唯一 ID）
 * - Value: JSON 序列化的 JobRunBroadcast
 * <p>
 * 与 WorkflowBroadcaster 分开，使用不同的 CRDT Key，数据隔离
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class JobRunBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(JobRunBroadcaster.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BROADCAST_KEY = "jobrun-broadcast";

    private final ActorSystem<?> system;
    private final SelfUniqueAddress selfUniqueAddress;
    private final Key<LWWMap<String, String>> dataKey;

    /**
     * 已处理的事件 ID（用于去重）
     */
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * 内部 Actor 引用
     */
    private ActorRef<InternalCommand> internalActor;

    public JobRunBroadcaster(ActorSystem<?> system) {
        this.system = system;
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();
        this.dataKey = LWWMapKey.create(BROADCAST_KEY);

        // 创建内部 Actor 处理 CRDT 交互
        this.internalActor = system.systemActorOf(
                InternalActor.create(this),
                "jobrun-broadcaster",
                Props.empty()
        );

        log.info("JobRunBroadcaster 初始化完成");
    }

    /**
     * 广播任务级事件
     *
     * @param event 任务级广播事件
     */
    public void broadcast(JobRunBroadcast event) {
        log.info("广播任务级事件: eventId={}, op={}, payload={}",
                event.getEventId(), event.getOp(), event.getPayload());

        // 标记为已处理，避免自己收到后再次处理
        processedEventIds.add(event.getEventId());

        // 序列化事件为 JSON
        String eventJson;
        try {
            eventJson = MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("序列化任务级广播事件失败: eventId={}", event.getEventId(), e);
            return;
        }

        // 发送广播命令到内部 Actor
        internalActor.tell(new BroadcastCommand(event.getEventId(), eventJson));
    }

    /**
     * 获取 CRDT 数据 Key（供 Worker 订阅使用）
     */
    public static Key<LWWMap<String, String>> getDataKey() {
        return LWWMapKey.create(BROADCAST_KEY);
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

    private static class InternalUpdateResponse implements InternalCommand {
        final Replicator.UpdateResponse<LWWMap<String, String>> response;

        InternalUpdateResponse(Replicator.UpdateResponse<LWWMap<String, String>> response) {
            this.response = response;
        }
    }

    // ============ 内部 Actor ============

    private static class InternalActor extends AbstractBehavior<InternalCommand> {

        private final JobRunBroadcaster broadcaster;
        private final ReplicatorMessageAdapter<InternalCommand, LWWMap<String, String>> replicatorAdapter;
        private final SelfUniqueAddress selfUniqueAddress;

        static Behavior<InternalCommand> create(JobRunBroadcaster broadcaster) {
            return Behaviors.setup(context -> new InternalActor(context, broadcaster));
        }

        private InternalActor(ActorContext<InternalCommand> context, JobRunBroadcaster broadcaster) {
            super(context);
            this.broadcaster = broadcaster;
            this.selfUniqueAddress = DistributedData.get(context.getSystem()).selfUniqueAddress();

            // 创建 Replicator 消息适配器
            ActorRef<Replicator.Command> replicator = DistributedData.get(context.getSystem()).replicator();
            this.replicatorAdapter = new ReplicatorMessageAdapter<>(context, replicator, java.time.Duration.ofSeconds(5));

            log.info("JobRunBroadcaster 内部 Actor 初始化完成");
        }

        @Override
        public Receive<InternalCommand> createReceive() {
            return newReceiveBuilder()
                    .onMessage(BroadcastCommand.class, this::onBroadcast)
                    .onMessage(InternalUpdateResponse.class, this::onUpdateResponse)
                    .build();
        }

        private Behavior<InternalCommand> onBroadcast(BroadcastCommand cmd) {
            log.debug("处理广播命令: eventId={}", cmd.eventId);

            replicatorAdapter.askUpdate(
                    askReplyTo -> new Replicator.Update<>(
                            broadcaster.dataKey,
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
