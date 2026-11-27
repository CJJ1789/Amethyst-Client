package net.client.util;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Reimplementation of the useful part of fast-ip-ping for 1.8.9 / Legacy Fabric.
 *
 * <p>For a server address that is already a literal IPv4 / IPv6 address, the
 * {@link InetAddress} returned by {@code getByName} keeps a null hostname. The
 * first later call to {@code getHostName()} would then perform a reverse DNS
 * lookup, which can stall the server-list ping or the connect screen for
 * seconds. Rebuilding the address with the literal text as its hostname avoids
 * that lookup, while normal domain names are left untouched.</p>
 */
public final class FastIpPing {
   private FastIpPing() {
   }

   public static InetAddress getByName(String address) throws UnknownHostException {
      InetAddress resolved = InetAddress.getByName(address);
      if (address != null && InetAddresses.isInetAddress(address)) {
         return InetAddress.getByAddress(resolved.getHostAddress(), resolved.getAddress());
      }
      return resolved;
   }
}
