package com.tuling.tim.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TIMFrameCodecTest {
    @Test
    void keepsProtocolHeaderAndReassemblesSplitFrame() {
        TIMReqMsg sent = new TIMReqMsg(42L, "hello", 4);
        EmbeddedChannel encoder = new EmbeddedChannel(new ObjEncoder(TIMReqMsg.class));
        encoder.writeOutbound(sent);
        ByteBuf frame = encoder.readOutbound();

        EmbeddedChannel decoder = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(1024 * 1024, 14, 4, 0, 0), new ObjDecoder(TIMReqMsg.class));
        ByteBuf first = frame.readRetainedSlice(7);
        ByteBuf second = frame.readRetainedSlice(frame.readableBytes());
        decoder.writeInbound(first);
        decoder.writeInbound(second);

        TIMReqMsg actual = decoder.readInbound();
        assertEquals(42L, actual.getRequestId());
        assertEquals(4, actual.getType());
        assertEquals("hello", actual.getReqMsg());
        encoder.finishAndReleaseAll();
        decoder.finishAndReleaseAll();
    }

    @Test
    void closesChannelForInvalidMagicVersionOrEmptyBody() {
        for (int invalidMagic : new int[]{0, ObjEncoder.MAGIC}) {
            EmbeddedChannel decoder = new EmbeddedChannel(new ObjDecoder(TIMReqMsg.class));
            ByteBuf frame = decoder.alloc().buffer(18);
            frame.writeInt(invalidMagic).writeByte(invalidMagic == 0 ? ObjEncoder.VERSION : 99)
                    .writeByte(1).writeLong(1L).writeInt(0);
            decoder.writeInbound(frame);
            assertFalse(decoder.isActive());
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsNegativeAndOversizedBodyLengths() {
        for (int length : new int[]{-1, 1024 * 1024 + 1}) {
            EmbeddedChannel decoder = new EmbeddedChannel(new ObjDecoder(TIMReqMsg.class));
            ByteBuf frame = decoder.alloc().buffer(18);
            frame.writeInt(ObjEncoder.MAGIC).writeByte(ObjEncoder.VERSION).writeByte(1)
                    .writeLong(1L).writeInt(length);
            decoder.writeInbound(frame);
            assertFalse(decoder.isActive());
            decoder.finishAndReleaseAll();
        }
    }
}
