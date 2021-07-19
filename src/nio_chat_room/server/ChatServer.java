package nio_chat_room.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Set;

public class ChatServer {

    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;

    private ServerSocketChannel server;
    private Selector selector;
    private ByteBuffer rBuffer = ByteBuffer.allocate(BUFFER);
    private ByteBuffer wBuffer = ByteBuffer.allocate(BUFFER);
    private Charset charset = Charset.forName("UTF-8");
    private int port;

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public ChatServer(int port) {
        this.port = port;
    }

    private void start() {
        try {
            //绑定服务器端口，设置服务器端口为非阻塞
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.socket().bind(new InetSocketAddress(port));
            //打开selector，并把服务器端注册到selector
            selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("启动服务器， 监听端口：" + port + "...");

            while (true) {
                //循环获取selector
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey key : selectionKeys) {
                    // 处理被触发的事件
                    handles(key);
                }
                selectionKeys.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(selector);
        }

    }

    private void handles(SelectionKey key) throws IOException {
        // ACCEPT事件 - 和客户端建立了连接
        if (key.isAcceptable()) {
            //获取客户端的channel
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            //将客户端channle注册到selecotor上
            client.register(selector, SelectionKey.OP_READ);
            System.out.println(getClientName(client) + "已连接");
        }
        // READ事件 - 客户端发送了消息
        else if (key.isReadable()) {
            SocketChannel client = (SocketChannel) key.channel();
            String fwdMsg = receive(client);
            if (fwdMsg.isEmpty()) {
                // 客户端异常，清除当前key，并且通知selector
                key.cancel();
                selector.wakeup();
            } else {
                System.out.println(getClientName(client) + ":" + fwdMsg);
                //转发消息
                forwardMessage(client, fwdMsg);
                // 检查用户是否退出
                if (readyToQuit(fwdMsg)) {
                    key.cancel();
                    selector.wakeup();
                    System.out.println(getClientName(client) + "已断开");
                }
            }

        }
    }

    private void forwardMessage(SocketChannel client, String fwdMsg) throws IOException {
        for (SelectionKey key : selector.keys()) {
            Channel connectedClient = key.channel();
            //如果当前channle是 服务器端channel则不处理
            if (connectedClient instanceof ServerSocketChannel) {
                continue;
            }
            ///如果key存在，并且是客户端的channel
            if (key.isValid() && !client.equals(connectedClient)) {
                //清空写的buffer
                wBuffer.clear();
                wBuffer.put(charset.encode(getClientName(client) + ":" + fwdMsg));
                //buffer反转
                wBuffer.flip();
                //当buffer还存在时，向socketchannel写数据
                while (wBuffer.hasRemaining()) {
                    ((SocketChannel) connectedClient).write(wBuffer);
                }
            }
        }
    }

    private String receive(SocketChannel client) throws IOException {
        //清空buffer
        rBuffer.clear();
        //持续向通道读取，当通道数据大于0时，读取数据
        while (client.read(rBuffer) > 0) ;
        //buffer反转
        rBuffer.flip();
        //返回UTF-8格式的编码
        return String.valueOf(charset.decode(rBuffer));
    }

    private String getClientName(SocketChannel client) {
        return "客户端[" + client.socket().getPort() + "]";
    }

    private boolean readyToQuit(String msg) {
        return QUIT.equals(msg);
    }

    private void close(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer(7777);
        chatServer.start();
    }
}
