package net.client.replay.session;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.SocketAddress;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;

/**
 * A completely inert ClientConnection. Packets sent by the client while a replay
 * is loaded (settings updates, chat, etc.) are swallowed instead of reaching a
 * server, and no Netty channel is ever opened.
 */
public class ReplayClientConnection extends net.minecraft.network.ClientConnection {
    public ReplayClientConnection() {
        super(net.minecraft.network.NetworkSide.CLIENTBOUND);
    }

    @Override
    public void send(net.minecraft.network.Packet packet) {
    }

    @Override
    public void send(net.minecraft.network.Packet packet, GenericFutureListener<? extends Future<? super Void>> listener,
                     GenericFutureListener<? extends Future<? super Void>>... listeners) {
    }

    @Override
    public void tick() {
    }

    @Override
    public void disconnect(net.minecraft.text.Text reason) {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean hasChannel() {
        return false;
    }

    @Override
    public SocketAddress getAddress() {
        return null;
    }
}
