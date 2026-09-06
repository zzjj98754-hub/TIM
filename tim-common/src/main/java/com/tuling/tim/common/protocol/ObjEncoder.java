package com.tuling.tim.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ObjEncoder extends MessageToByteEncoder {

    /** Four bytes make accidental protocol mismatches fail fast. */
    public static final int MAGIC = 0x54494D31; // TIM1
    public static final byte VERSION = 1;

    private Class<?> genericClass;

    public ObjEncoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) {
        if (genericClass.isInstance(in)) {
            byte[] data = ProtostuffUtil.serialize(in);
            TIMReqMsg message = (TIMReqMsg) in;
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(message.getType() == null ? 0 : message.getType());
            out.writeLong(message.getRequestId() == null ? 0L : message.getRequestId());
            // LengthFieldBasedFrameDecoder uses this body length to resolve sticky/half packets.
            out.writeInt(data.length);
            out.writeBytes(data);
        }
    }

}
