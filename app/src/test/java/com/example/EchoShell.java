package com.example;

import java.io.InputStream;
import java.io.OutputStream;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

/** Test-only loopback shell: echoes input exactly and acknowledges CR. */
public final class EchoShell implements Command {
    private InputStream input;
    private OutputStream output;
    private ExitCallback callback;
    private Thread worker;
    private final java.io.ByteArrayOutputStream received = new java.io.ByteArrayOutputStream();
    public String receivedText() {
        synchronized (received) { return new String(received.toByteArray(), java.nio.charset.StandardCharsets.UTF_8); }
    }
    public void setInputStream(InputStream in) { input = in; }
    public void setOutputStream(OutputStream out) { output = out; }
    public void setErrorStream(OutputStream err) { }
    public void setExitCallback(ExitCallback cb) { callback = cb; }
    public void start(ChannelSession channel, Environment environment) {
        worker = new Thread(() -> {
            try {
                output.write("ready> ".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.flush();
                int b;
                while ((b = input.read()) != -1) {
                    synchronized (received) { received.write(b); }
                    if (b == 13) output.write("\r\nACK\r\nready> ".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    else output.write(b);
                    output.flush();
                }
                callback.onExit(0);
            } catch (Exception ignored) { callback.onExit(1); }
        }, "test-ssh-shell");
        worker.setDaemon(true);
        worker.start();
    }
    public void destroy(ChannelSession channel) throws Exception {
        if (input != null) input.close();
        if (worker != null) worker.interrupt();
    }
}
