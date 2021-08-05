package com.cosmo.rpc.test;

import io.netty.buffer.*;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * @ClassName MyNetty
 * @Description
 * @Author lvyifan
 * @Date 2021/8/4 23:08
 **/
public class MyNetty {

    @Test
    public void myByteBuf() {
        //默认写法
        //ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(8, 20);
        //unPool
        //ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.heapBuffer(8,20);

        //Pool
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer(8, 20);

        print(buf);

        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
    }

    public static void print(ByteBuf buf) {
        System.out.println("buf.isReadable:" + buf.isReadable());
        System.out.println("buf.readerIndex:" + buf.readerIndex());
        System.out.println("buf.readableBytes:" + buf.readableBytes());
        System.out.println("buf.isWritable:" + buf.isWritable());
        System.out.println("buf.writerIndex:" + buf.writerIndex());
        System.out.println("buf.writableBytes:" + buf.writableBytes());
        System.out.println("buf.capacity:" + buf.capacity());
        System.out.println("buf.maxCapacity:" + buf.maxCapacity());
        System.out.println("buf.isDirect:" + buf.isDirect());

        System.out.println("-------------------");
    }


    @Test
    public void loopExecutor(){
        NioEventLoopGroup selector = new NioEventLoopGroup(1);
        selector.execute(() ->{
            System.out.println("hello world");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void clientMode() throws InterruptedException {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);

        NioSocketChannel client = new NioSocketChannel();
        thread.register(client);

        //响应式
        ChannelPipeline p = client.pipeline();
        p.addLast(new MyInHandler());

        ChannelFuture connect = client.connect(new InetSocketAddress("127.0.0.1", 8000));
        ChannelFuture sync = connect.sync();

        ByteBuf buf = Unpooled.copiedBuffer("hello world".getBytes(StandardCharsets.UTF_8));
        ChannelFuture send = client.writeAndFlush(buf);
        send.sync();


        sync.channel().closeFuture().sync();

        System.out.println("client over....");


    }
}
class MyInHandler extends ChannelInboundHandlerAdapter{

}