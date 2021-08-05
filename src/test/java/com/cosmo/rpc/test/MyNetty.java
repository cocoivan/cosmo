package com.cosmo.rpc.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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

    @Test
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

    @Test
    public void nettyClient() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bs = new Bootstrap();
        ChannelFuture channelFuture = bs.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new MyInHandler());
            }
        }).connect(new InetSocketAddress("127.0.0.1", 8000));
        Channel channel = channelFuture.sync().channel();

        ByteBuf byteBuf = Unpooled.copiedBuffer("hello server".getBytes());

        ChannelFuture send = channel.writeAndFlush(byteBuf);
        send.sync();

        channel.closeFuture().sync();
    }


    @Test
    public void serverMode() throws InterruptedException {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);
        NioServerSocketChannel server = new NioServerSocketChannel();

        thread.register(server);
        ChannelPipeline p = server.pipeline();
        p.addLast(new MyAcceptHandler());
        ChannelFuture channelFuture = server.bind(new InetSocketAddress("127.0.0.1", 8000));
        channelFuture.sync().channel().closeFuture().sync();

    }

    @Test
    public void nettyServer() throws InterruptedException {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup(2);

        ServerBootstrap bs = new ServerBootstrap();

        ChannelFuture bind = bs.group(boss, worker).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel serverSocketChannel) throws Exception {
                ChannelPipeline pipeline = serverSocketChannel.pipeline();
                pipeline.addLast(new MyInHandler());
            }
        }).bind(new InetSocketAddress("127.0.0.1", 8000));

        bind.sync().channel().closeFuture().sync();

    }

}
class MyAcceptHandler extends  ChannelInboundHandlerAdapter{
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("server registered...");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SocketChannel client = (SocketChannel) msg;


    }
}

class MyInHandler extends ChannelInboundHandlerAdapter{

}