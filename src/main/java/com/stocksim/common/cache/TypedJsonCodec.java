package com.stocksim.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.InputStream;

/**
 * 面向 record 的薄 Jackson codec：直接用外部 ObjectMapper 编解码到指定类型。
 * Redisson 3.25.0 的 JsonJacksonCodec（默认 typing NON_FINAL 不给 final record 写 @class）
 * 与 TypedJsonJacksonCodec（重配 mapper 破坏 record 构造器识别）均无法回序列化 record，
 * 故自定义 BaseCodec 复用纯净 mapper（Q1 实现级修正，已实证 round-trip）。
 */
public class TypedJsonCodec extends BaseCodec {

    private final Class<?> type;
    private final ObjectMapper mapper;

    public TypedJsonCodec(Class<?> type, ObjectMapper mapper) {
        this.type = type;
        this.mapper = mapper;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return (ByteBuf buf, State state) -> {
            try (InputStream in = new ByteBufInputStream(buf)) {
                return mapper.readValue(in, type);
            }
        };
    }

    @Override
    public Encoder getValueEncoder() {
        return value -> Unpooled.wrappedBuffer(mapper.writeValueAsBytes(value));
    }
}