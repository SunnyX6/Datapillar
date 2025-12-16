package com.sunny.job.core.serializer;

import com.sunny.job.core.message.ExecutorMessage;
import com.sunny.job.core.message.proto.ExecutorMessageProto;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.typed.ActorRefResolver;
import org.apache.pekko.serialization.SerializerWithStringManifest;

/**
 * ExecutorMessage Protobuf 序列化器
 * <p>
 * 将 ExecutorMessage 序列化为 Protobuf 二进制格式
 * <p>
 * 特殊处理：
 * - ActorRef 序列化为路径字符串，反序列化时还原
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class ExecutorMessageSerializer extends SerializerWithStringManifest {

    private static final String MANIFEST = "ExecutorMessage";

    private final ExtendedActorSystem system;
    private final ActorRefResolver resolver;

    public ExecutorMessageSerializer(ExtendedActorSystem system) {
        this.system = system;
        this.resolver = ActorRefResolver.get(org.apache.pekko.actor.typed.ActorSystem.wrap(system));
    }

    @Override
    public int identifier() {
        return 10002;
    }

    @Override
    public String manifest(Object o) {
        return MANIFEST;
    }

    @Override
    public byte[] toBinary(Object o) {
        if (!(o instanceof ExecutorMessage msg)) {
            throw new IllegalArgumentException("无法序列化类型: " + o.getClass().getName());
        }
        ExecutorMessageProto proto = MessageConverter.toProto(msg, resolver);
        return proto.toByteArray();
    }

    @Override
    public Object fromBinary(byte[] bytes, String manifest) {
        if (!MANIFEST.equals(manifest)) {
            throw new IllegalArgumentException("未知的 manifest: " + manifest);
        }
        try {
            ExecutorMessageProto proto = ExecutorMessageProto.parseFrom(bytes);
            return MessageConverter.fromProto(proto, resolver);
        } catch (Exception e) {
            throw new RuntimeException("ExecutorMessage 反序列化失败", e);
        }
    }
}
