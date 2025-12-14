package com.sunny.job.worker.pekko.serializer;

import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.core.message.proto.JobRunInfoProto;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.serialization.SerializerWithStringManifest;

/**
 * JobRunInfo Protobuf 序列化器
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class JobRunInfoSerializer extends SerializerWithStringManifest {

    private static final String MANIFEST = "JobRunInfo";

    public JobRunInfoSerializer(ExtendedActorSystem system) {
        // system 参数由 Pekko 框架注入
    }

    @Override
    public int identifier() {
        return 10003;
    }

    @Override
    public String manifest(Object o) {
        return MANIFEST;
    }

    @Override
    public byte[] toBinary(Object o) {
        if (!(o instanceof JobRunInfo info)) {
            throw new IllegalArgumentException("无法序列化类型: " + o.getClass().getName());
        }
        JobRunInfoProto proto = MessageConverter.toProto(info);
        return proto.toByteArray();
    }

    @Override
    public Object fromBinary(byte[] bytes, String manifest) {
        if (!MANIFEST.equals(manifest)) {
            throw new IllegalArgumentException("未知的 manifest: " + manifest);
        }
        try {
            JobRunInfoProto proto = JobRunInfoProto.parseFrom(bytes);
            return MessageConverter.fromProto(proto);
        } catch (Exception e) {
            throw new RuntimeException("JobRunInfo 反序列化失败", e);
        }
    }
}
