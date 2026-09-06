package com.tuling.tim.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class ObjDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024;
    private Class<?> genericClass;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 18) {
            return;
        }
        int magic = in.readInt();
        byte version = in.readByte();
        byte type = in.readByte();
        long requestId = in.readLong();
        int dataLength = in.readInt();
        if (magic != ObjEncoder.MAGIC || version != ObjEncoder.VERSION || dataLength < 0 || dataLength > MAX_FRAME_LENGTH || in.readableBytes() != dataLength) {
            ctx.close();
            return;
        }
        byte[] data = new byte[dataLength];
        in.readBytes(data);
        TIMReqMsg message = (TIMReqMsg) ProtostuffUtil.deserialize(data, genericClass);
        // Header is authoritative; it lets the frame be inspected without deserializing it.
        message.setType((int) type);
        message.setRequestId(requestId);
        out.add(message);
    }

    public ObjDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

}
