package com.cosmo.rpc.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * @ClassName MyRPCTest
 * @Description
 * @Author lvyifan
 * @Date 2021/8/10 23:10
 **/
public class MyRPCTest {


    @Test
    public void startServer() {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = boss;

        ServerBootstrap sbs = new ServerBootstrap();
        ChannelFuture bind = sbs.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        System.out.println("server accept client port:" + ch.remoteAddress().getPort());
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new ServerRequestHandler());
                    }
                }).bind(new InetSocketAddress("localhost", 9090));
        try {
            bind.sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 模拟客户端
     */
    @Test
    public void get(){

        new Thread(() -> {
            startServer();
        }).start();

        System.out.println("server started");

        Car car = proxyGet(Car.class);
        car.ooxx("hello");

//        Fly fly = proxyGet(Fly.class);
//        fly.xxoo("hello");

    }


    public static <T> T proxyGet(Class<T> interfaceInfo) {
        //实现各个版本的动态代理

        ClassLoader loader = interfaceInfo.getClassLoader();
        Class<?>[] methodInfo = {interfaceInfo};


        return (T)Proxy.newProxyInstance(loader, methodInfo, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                //如何设计我们的消费者对于提供方的调用过程

                //1。调用服务，方法，参数 -》 封装为message [content]
                String name = interfaceInfo.getName();
                String methodName = method.getName();
                Class<?>[] parameterTypes = method.getParameterTypes();

                MyContent content = new MyContent();
                content.setArgs(args);
                content.setMethodName(methodName);
                content.setName(name);
                content.setParameterTypes(parameterTypes);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(out);
                oout.writeObject(content);
                byte[] msgBody = out.toByteArray();

                //2。request id + message -》 本地缓存
                //协议：【header】【msgBody】

                MyHeader header = createHeader(msgBody);

                out.reset();
                oout = new ObjectOutputStream(out);
                oout.writeObject(header);
                byte[] msgHeader = out.toByteArray();
                //3。连接池 -》 取得连接

                ClientFactory factory = ClientFactory.factory;
                NioSocketChannel clientChannel = factory.getClient(new InetSocketAddress("localhost", 9090));

                //4。发送 -》 io
                CountDownLatch countDownLatch = new CountDownLatch(1);
                long requestID = header.getRequestID();
                ResponseHandler.addCallBack(requestID, new Runnable() {
                    @Override
                    public void run() {
                        countDownLatch.countDown();
                    }
                });


                ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(msgHeader.length + msgBody.length);

                byteBuf.writeBytes(msgHeader);
                byteBuf.writeBytes(msgBody);
                ChannelFuture channelFuture = clientChannel.writeAndFlush(byteBuf);
                channelFuture.sync();



                countDownLatch.await();

                //5。提供方返回数据

                return null;
            }
        });
    }

    public static MyHeader createHeader(byte[] msgBody) {
        MyHeader myHeader = new MyHeader();
        int size = msgBody.length;
        int f = 0x14141414;
        long requestID = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        myHeader.setFlag(f);
        myHeader.setDataLen(size);
        myHeader.setRequestID(requestID);

        return myHeader;
    }

}

class ServerRequestHandler extends ChannelInboundHandlerAdapter{

    /**
     * provider
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        ByteBuf sendBuf = buf.copy();

        if (buf.readableBytes() >= 110) {
            byte[] bytes = new byte[110];
            buf.readBytes(bytes);

            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            ObjectInputStream oin = new ObjectInputStream(in);
            MyHeader header = (MyHeader) oin.readObject();
            System.out.println(header.getDataLen());
            System.out.println(header.getRequestID());


            if (buf.readableBytes() >= header.getDataLen()) {
                byte[] data = new byte[(int) header.getDataLen()];
                ByteArrayInputStream bin = new ByteArrayInputStream(data);
                ObjectInputStream obin = new ObjectInputStream(bin);
                MyContent content = (MyContent) obin.readObject();
                System.out.println(content.getMethodName());

            }

        }


        ChannelFuture channelFuture = ctx.writeAndFlush(sendBuf);
        channelFuture.sync();
    }
}

class ClientFactory{

    int poolSize = 1;

    Random random = new Random();

    private ClientFactory(){

    }

    public static final ClientFactory factory;

    NioEventLoopGroup clientWorker;

    static {
        factory = new ClientFactory();
    }
    //一个消费者可以连接很多服务提供者，每一个服务提供者都有自己的pool

    ConcurrentHashMap<InetSocketAddress, ClientPool> outboxs = new ConcurrentHashMap<>();

    public synchronized NioSocketChannel getClient(InetSocketAddress address){
        ClientPool clientPool = outboxs.get(address);
        if (clientPool == null) {
            outboxs.putIfAbsent(address, new ClientPool(poolSize));
            clientPool = outboxs.get(address);
        }
        int i = random.nextInt(poolSize);

        if (clientPool.clients[i] != null && clientPool.clients[i].isActive()) {
            return clientPool.clients[i];
        }

        synchronized (clientPool.lock[i]){
            return clientPool.clients[i] = create(address);
        }
    }

    private NioSocketChannel create(InetSocketAddress address){
        //基于netty 的客户端创建方式

        clientWorker  = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(clientWorker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) throws Exception {
                        ChannelPipeline p = channel.pipeline();
                        p.addLast(new ClientResponse()); //解决给谁的
                    }
                }).connect(address);

        try {
            NioSocketChannel client = (NioSocketChannel)connect.sync().channel();
            return client;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

}

class ResponseHandler{
    static ConcurrentHashMap<Long, Runnable> mapping = new ConcurrentHashMap<>();

    public static void addCallBack(long requestID, Runnable cb) {
        mapping.putIfAbsent(requestID, cb);
    }

    public static void runCallBack(long requestID) {
        Runnable runnable = mapping.get(requestID);
        runnable.run();
        removeCallBack(requestID);
    }

    private static void removeCallBack(long requestID) {
        mapping.remove(requestID);
    }

}

class ClientResponse extends ChannelInboundHandlerAdapter{

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        if (buf.readableBytes() >= 110) {
            byte[] bytes = new byte[110];
            buf.readBytes(bytes);

            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            ObjectInputStream oin = new ObjectInputStream(in);
            MyHeader header = (MyHeader) oin.readObject();
            System.out.println(header.getDataLen());
            System.out.println(header.getRequestID());

            ResponseHandler.runCallBack(header.getRequestID());

            if (buf.readableBytes() >= header.getDataLen()) {
                byte[] data = new byte[(int) header.getDataLen()];
                ByteArrayInputStream bin = new ByteArrayInputStream(data);
                ObjectInputStream obin = new ObjectInputStream(bin);
                MyContent content = (MyContent) obin.readObject();
                System.out.println(content.getMethodName());

            }

        }



        super.channelRead(ctx, msg);
    }
}

class ClientPool{
    NioSocketChannel[] clients;

    Object[] lock;

    ClientPool(int size) {
        clients = new NioSocketChannel[size];
        lock = new Object[size];

        for (int i = 0 ; i < size ; i++) {
            lock[i] = new Object();
        }
    }

}


class MyHeader implements Serializable{

    int flag; //32 bit可以设置很多信息

    long requestID;

    long dataLen;

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public long getRequestID() {
        return requestID;
    }

    public void setRequestID(long requestID) {
        this.requestID = requestID;
    }

    public long getDataLen() {
        return dataLen;
    }

    public void setDataLen(long dataLen) {
        this.dataLen = dataLen;
    }
}

class  MyContent implements Serializable {

    String name;

    String methodName;

    Class<?>[] parameterTypes;

    Object[] args;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}

interface Car{
    void ooxx(String msg);
}

interface Fly{
    void xxoo(String msg);
}