
package com.mogujie.tt.packet.biz;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

import com.mogujie.tt.packet.base.DataBuffer;
import com.mogujie.tt.packet.base.Packet;

/**
 * @Description: 数据编码器，把要发送的数据按照一定格式拼接成二进制的流
 * @author ziye - ziye[at]mogujie.com
 * @date 2013-7-21 下午4:02:07
 * @modify yugui
 */
public class PacketEncoder extends SimpleChannelDownstreamHandler {

    /**
     * @Description: 把要发送的数据编码成二进制数据并发送
     */
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {

        Packet request = (Packet) e.getMessage();
        DataBuffer buffer = null;
        try {
            buffer = request.encode();
        } catch (Exception e2) {

        }
        if (null != buffer) {
            Channels.write(ctx, e.getFuture(), buffer.getOrignalBuffer());
        }
    }

}
