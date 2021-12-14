/**
 * Copyright (c) 2009-2017, Perforce Software, Inc.  All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * 1.  Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p>
 * 2.  Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL PERFORCE SOFTWARE, INC. BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.perforce.p4java.impl.mapbased.rpc.stream.helper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.Properties;

import com.perforce.p4java.Log;
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs;
import com.perforce.p4java.impl.mapbased.rpc.stream.RpcSSLSocketFactory;

/**
 * Helper class for creating and configuring sockets.
 */
public class RpcSocketHelper {
    public static String httpProxyHost;
    public static int httpProxyPort;

    /**
     * Configure a socket with specified properties.
     */
    public static void configureSocket(Socket socket, Properties properties) {
        if (socket == null || properties == null) {
            return;
        }

        try {
            // Enable/disable TCP_NODELAY (disable/enable Nagle's algorithm).
            boolean tcpNoDelay = RpcPropertyDefs.getPropertyAsBoolean(properties,
                    RpcPropertyDefs.RPC_SOCKET_TCP_NO_DELAY_NICK,
                    RpcPropertyDefs.RPC_SOCKET_TCP_NO_DELAY_DEFAULT);
            socket.setTcpNoDelay(tcpNoDelay);

            String keepAlive = RpcPropertyDefs.getProperty(properties,
                    RpcPropertyDefs.RPC_SOCKET_USE_KEEPALIVE_NICK);

            int timeouts = RpcPropertyDefs.getPropertyAsInt(properties,
                    RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_NICK,
                    RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_DEFAULT);

            int[] perfPrefs = RpcPropertyDefs.getPropertyAsIntArray(properties,
                    RpcPropertyDefs.RPC_SOCKET_PERFORMANCE_PREFERENCES_NICK,
                    RpcPropertyDefs.RPC_DEFAULT_PROPERTY_DELIMITER,
                    RpcPropertyDefs.RPC_SOCKET_PERFORMANCE_PREFERENCES_DEFAULT);

            // Setting the socket performance preferences, described by three
            // integers whose values indicate the relative importance of short
            // connection time, low latency, and high bandwidth.
            // Socket.setPerformancePreferences(int connectionTime, int latency, int bandwidth)
            // The default values is (1, 2, 0), assume no one changes them.
            // This gives the highest importance to low latency, followed by
            // short connection time, and least importance to high bandwidth.
            if (perfPrefs != null && perfPrefs.length == 3) {
                socket.setPerformancePreferences(
                        perfPrefs[0],
                        perfPrefs[1],
                        perfPrefs[2]);
            }

            socket.setSoTimeout(timeouts);

            if ((keepAlive != null)
                    && (keepAlive.startsWith("n") || keepAlive.startsWith("N"))) {
                socket.setKeepAlive(false);
            } else {
                socket.setKeepAlive(true);
            }

            int sockRecvBufSize = RpcPropertyDefs.getPropertyAsInt(properties,
                    RpcPropertyDefs.RPC_SOCKET_RECV_BUF_SIZE_NICK, 0);
            int sockSendBufSize = RpcPropertyDefs.getPropertyAsInt(properties,
                    RpcPropertyDefs.RPC_SOCKET_SEND_BUF_SIZE_NICK, 0);

            if (sockRecvBufSize != 0) {
                socket.setReceiveBufferSize(sockRecvBufSize);
            }

            if (sockSendBufSize != 0) {
                socket.setSendBufferSize(sockSendBufSize);
            }
        } catch (Throwable exc) {
            Log
                    .warn("Unexpected exception while setting Perforce RPC socket options: "
                            + exc.getLocalizedMessage());
            Log.exception(exc);
        }
    }

    /**
     * Create a socket with the specified properties and connect to the specified host and port.
     */
    public static Socket createSocket(String host, int port, Properties properties, boolean secure) throws IOException {
        Socket socket = null;

        if (secure) {
            if (httpProxyHost != null) {
                Socket proxySocket = new Socket(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpProxyHost, httpProxyPort)));
                configureSocket(proxySocket, properties);

                proxySocket.bind(new InetSocketAddress(0));
                proxySocket.connect(new InetSocketAddress(host, port));
                return RpcSSLSocketFactory.getInstance(properties).createSocket(proxySocket, host, port, false);
            } else {
                socket = RpcSSLSocketFactory.getInstance(properties).createSocket();
            }
        } else {
            if (httpProxyHost != null) {
                socket = new Socket(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpProxyHost, httpProxyPort)));
            } else {
                socket = new Socket();
            }
        }

        configureSocket(socket, properties);

        socket.bind(new InetSocketAddress(0));
        socket.connect(new InetSocketAddress(host, port));

        return socket;
    }
}
