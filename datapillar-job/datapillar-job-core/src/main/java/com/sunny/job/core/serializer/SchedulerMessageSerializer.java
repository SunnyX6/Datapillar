package com.sunny.job.core.serializer;

import com.sunny.job.core.message.SchedulerMessage;
import com.sunny.job.core.message.proto.SchedulerMessageProto;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.serialization.SerializerWithStringManifest;

/**
 * SchedulerMessage Protobuf 序列化器
 * <p>
 * 将 SchedulerMessage 序列化为 Protobuf 二进制格式
 * <p>
 * 性能对比：
 * - JSON: ~500 bytes, 序列化 ~50μs
 * - Protobuf: ~100 bytes, 序列化 ~5μs
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class SchedulerMessageSerializer extends SerializerWithStringManifest {

    private static final String MANIFEST = "SchedulerMessage";

    private final ExtendedActorSystem system;

    public SchedulerMessageSerializer(ExtendedActorSystem system) {
        this.system = system;
    }

    @Override
    public int identifier() {
        // 序列化器唯一标识（必须在 [0, 40] 之外的范围）
        return 10001;
    }

    @Override
    public String manifest(Object o) {
        return MANIFEST;
    }

    @Override
    public byte[] toBinary(Object o) {
        if (!(o instanceof SchedulerMessage msg)) {
            throw new IllegalArgumentException("无法序列化类型: " + o.getClass().getName());
        }
        SchedulerMessageProto proto = MessageConverter.toProto(msg);
        return proto.toByteArray();
    }

    @Override
    public Object fromBinary(byte[] bytes, String manifest) {
        if (!MANIFEST.equals(manifest)) {
            throw new IllegalArgumentException("未知的 manifest: " + manifest);
        }
        try {
            SchedulerMessageProto proto = SchedulerMessageProto.parseFrom(bytes);
            return MessageConverter.fromProto(proto);
        } catch (Exception e) {
            throw new RuntimeException("SchedulerMessage 反序列化失败", e);
        }
    }
}
