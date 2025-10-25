package io.fluxgate.core.policy;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Minimal radix trie for IPv4 prefixes. Nodes are allocated during compilation and never
 * mutated afterwards which keeps policy evaluation thread-safe without synchronization.
 */
final class RadixTrie {

    private final Node root = new Node();

    void insert(String cidr) {
        Prefix prefix = Prefix.parse(cidr);
        Node node = root;
        for (int i = 31; i >= 32 - prefix.length; i--) {
            int bit = (prefix.address >>> i) & 1;
            node = node.child(bit);
        }
        node.terminal = true;
    }

    boolean matches(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        int address;
        try {
            address = Prefix.toAddress(ip);
        } catch (UnknownHostException e) {
            return false;
        }
        Node node = root;
        if (node.terminal) {
            return true;
        }
        for (int i = 31; i >= 0; i--) {
            node = node.next((address >>> i) & 1);
            if (node == null) {
                return false;
            }
            if (node.terminal) {
                return true;
            }
        }
        return false;
    }

    private static final class Node {
        private Node zero;
        private Node one;
        private boolean terminal;

        Node child(int bit) {
            if (bit == 0) {
                if (zero == null) {
                    zero = new Node();
                }
                return zero;
            }
            if (one == null) {
                one = new Node();
            }
            return one;
        }

        Node next(int bit) {
            return bit == 0 ? zero : one;
        }
    }

    private record Prefix(int address, int length) {

        private static Prefix parse(String cidr) {
            String trimmed = cidr.trim();
            int slashIndex = trimmed.indexOf('/');
            if (slashIndex < 0) {
                return new Prefix(toAddressUnchecked(trimmed), 32);
            }
            int address = toAddressUnchecked(trimmed.substring(0, slashIndex));
            int length = Integer.parseInt(trimmed.substring(slashIndex + 1));
            return new Prefix(address, length);
        }

        private static int toAddressUnchecked(String ip) {
            try {
                return toAddress(ip);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP address: " + ip, e);
            }
        }

        private static int toAddress(String ip) throws UnknownHostException {
            byte[] address = InetAddress.getByName(ip).getAddress();
            if (address.length != 4) {
                throw new UnknownHostException("Only IPv4 addresses supported: " + ip);
            }
            int result = 0;
            for (byte b : address) {
                result = (result << 8) | (b & 0xff);
            }
            return result;
        }
    }
}
