package io.fluxgate.core.policy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact Patricia trie for IPv4 CIDR prefixes. The trie is built during policy compilation
 * and flattened into cache-friendly arrays for lookups.
 */
final class PatriciaTrie {

    private final Node root = new Node(0, 0, false);
    private boolean frozen;

    private int[] prefixes;
    private byte[] lengths;
    private int[] children;
    private byte[] terminal;
    private int[] masks;

    void insert(String cidr) {
        ensureNotFrozen();
        Prefix prefix = Prefix.parse(cidr);
        insert(root, prefix.address(), prefix.length());
    }

    void freeze() {
        if (frozen) {
            return;
        }
        List<Node> ordered = new ArrayList<>();
        assignIndices(root, ordered);
        prefixes = new int[ordered.size()];
        lengths = new byte[ordered.size()];
        children = new int[ordered.size() * 2];
        terminal = new byte[ordered.size()];
        masks = new int[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            Node node = ordered.get(i);
            prefixes[i] = node.prefix;
            lengths[i] = (byte) node.length;
            terminal[i] = (byte) (node.terminal ? 1 : 0);
            masks[i] = mask(node.length);
            children[i * 2] = node.zero != null ? node.zero.index : -1;
            children[i * 2 + 1] = node.one != null ? node.one.index : -1;
        }
        frozen = true;
    }

    boolean matches(String ip) {
        if (!frozen) {
            throw new IllegalStateException("Trie must be frozen before lookups");
        }
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        int address;
        try {
            address = Prefix.toAddress(ip);
        } catch (UnknownHostException e) {
            return false;
        }
        int index = 0;
        if (terminal[index] == 1) {
            return true;
        }
        while (true) {
            int nodeLength = lengths[index] & 0xff;
            if (nodeLength == 32) {
                return terminal[index] == 1;
            }
            int bit = (address >>> (31 - nodeLength)) & 1;
            int childIndex = children[index * 2 + bit];
            if (childIndex < 0) {
                return false;
            }
            if ((address & masks[childIndex]) != prefixes[childIndex]) {
                return false;
            }
            if (terminal[childIndex] == 1) {
                return true;
            }
            index = childIndex;
        }
    }

    private void insert(Node node, int prefix, int length) {
        if (length == node.length && node.prefix == (prefix & mask(length))) {
            node.terminal = true;
            return;
        }
        int bit = bitAt(prefix, node.length);
        Node child = bit == 0 ? node.zero : node.one;
        if (child == null) {
            Node newChild = new Node(prefix & mask(length), length, true);
            if (bit == 0) {
                node.zero = newChild;
            } else {
                node.one = newChild;
            }
            return;
        }
        int common = commonPrefixLength(prefix, length, child.prefix, child.length);
        if (common == child.length) {
            if (common == length) {
                child.terminal = true;
            } else {
                insert(child, prefix, length);
            }
            return;
        }
        if (common == length) {
            Node newNode = new Node(prefix & mask(length), length, true);
            int childBit = bitAt(child.prefix, length);
            if (childBit == 0) {
                newNode.zero = child;
            } else {
                newNode.one = child;
            }
            if (bit == 0) {
                node.zero = newNode;
            } else {
                node.one = newNode;
            }
            return;
        }
        Node split = new Node(child.prefix & mask(common), common, common == length);
        int childBit = bitAt(child.prefix, common);
        if (common < length) {
            int newBit = bitAt(prefix, common);
            Node newNode = new Node(prefix & mask(length), length, true);
            if (newBit == 0) {
                split.zero = newNode;
                split.one = child;
            } else {
                split.one = newNode;
                split.zero = child;
            }
        } else {
            if (childBit == 0) {
                split.zero = child;
            } else {
                split.one = child;
            }
        }
        if (bit == 0) {
            node.zero = split;
        } else {
            node.one = split;
        }
    }

    private static int mask(int length) {
        if (length == 0) {
            return 0;
        }
        return -1 << (32 - length);
    }

    private static int bitAt(int value, int index) {
        if (index >= 32) {
            return 0;
        }
        return (value >>> (31 - index)) & 1;
    }

    private static int commonPrefixLength(int aPrefix, int aLength, int bPrefix, int bLength) {
        int max = Math.min(aLength, bLength);
        if (max == 0) {
            return 0;
        }
        int xor = (aPrefix ^ bPrefix) & mask(max);
        if (xor == 0) {
            return max;
        }
        return Integer.numberOfLeadingZeros(xor);
    }

    private void assignIndices(Node node, List<Node> ordered) {
        node.index = ordered.size();
        ordered.add(node);
        if (node.zero != null) {
            assignIndices(node.zero, ordered);
        }
        if (node.one != null) {
            assignIndices(node.one, ordered);
        }
    }

    private void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Trie already frozen");
        }
    }

    private static final class Node {
        private final int prefix;
        private final int length;
        private Node zero;
        private Node one;
        private boolean terminal;
        private int index;

        private Node(int prefix, int length, boolean terminal) {
            this.prefix = prefix;
            this.length = length;
            this.terminal = terminal;
        }
    }

    record Prefix(int address, int length) {

        static Prefix parse(String cidr) {
            String trimmed = cidr.trim();
            int slashIndex = trimmed.indexOf('/');
            if (slashIndex < 0) {
                return new Prefix(toAddressUnchecked(trimmed), 32);
            }
            int address = toAddressUnchecked(trimmed.substring(0, slashIndex));
            int length = Integer.parseInt(trimmed.substring(slashIndex + 1));
            if (length < 0 || length > 32) {
                throw new IllegalArgumentException("CIDR prefix length must be between 0 and 32: " + cidr);
            }
            return new Prefix(address, length);
        }

        static int toAddressUnchecked(String ip) {
            try {
                return toAddress(ip);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP address: " + ip, e);
            }
        }

        static int toAddress(String ip) throws UnknownHostException {
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
