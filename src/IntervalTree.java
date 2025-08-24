import java.util.*;

public class IntervalTree {

    private IntervalNode root;
    private int size;

    public IntervalTree() {
        this.root = null;
        this.size = 0;
    }


    private static class IntervalNode {
        BinRange range;
        long max;
        IntervalNode left;
        IntervalNode right;
        int height;

        IntervalNode(BinRange range) {
            this.range = range;
            this.max = range.getEndBin();
            this.left = null;
            this.right = null;
            this.height = 1;
        }


        void updateMax() {
            max = range.getEndBin();
            if (left != null) {
                max = Math.max(max, left.max);
            }
            if (right != null) {
                max = Math.max(max, right.max);
            }
        }


        void updateHeight() {
            int leftHeight = (left != null) ? left.height : 0;
            int rightHeight = (right != null) ? right.height : 0;
            height = Math.max(leftHeight, rightHeight) + 1;
        }


        int getBalance() {
            int leftHeight = (left != null) ? left.height : 0;
            int rightHeight = (right != null) ? right.height : 0;
            return leftHeight - rightHeight;
        }
    }


    public void insert(BinRange range) throws OverlappingRangeException {

        if (findOverlapping(range) != null) {
            throw new OverlappingRangeException(findOverlapping(range), range);
        }

        root = insertRecursive(root, range);
        size++;
    }

    private IntervalNode insertRecursive(IntervalNode node, BinRange range) {

        if (node == null) {
            return new IntervalNode(range);
        }


        if (range.getStartBin() < node.range.getStartBin()) {
            node.left = insertRecursive(node.left, range);
        } else if (range.getStartBin() > node.range.getStartBin()) {
            node.right = insertRecursive(node.right, range);
        } else {

            if (range.getEndBin() < node.range.getEndBin()) {
                node.left = insertRecursive(node.left, range);
            } else {
                node.right = insertRecursive(node.right, range);
            }
        }


        node.updateHeight();
        node.updateMax();


        return balance(node);
    }


    public BinRange findInterval(long point) {
        return findIntervalRecursive(root, point);
    }

    private BinRange findIntervalRecursive(IntervalNode node, long point) {
        if (node == null) {
            return null;
        }


        if (node.range.contains(point)) {
            return node.range;
        }


        if (node.left != null && node.left.max >= point) {
            BinRange leftResult = findIntervalRecursive(node.left, point);
            if (leftResult != null) {
                return leftResult;
            }
        }


        return findIntervalRecursive(node.right, point);
    }


    public BinRange findOverlapping(BinRange queryRange) {
        return findOverlappingRecursive(root, queryRange);
    }

    private BinRange findOverlappingRecursive(IntervalNode node, BinRange queryRange) {
        if (node == null) {
            return null;
        }


        if (node.range.overlaps(queryRange)) {
            return node.range;
        }


        if (node.left != null && node.left.max >= queryRange.getStartBin()) {
            BinRange leftResult = findOverlappingRecursive(node.left, queryRange);
            if (leftResult != null) {
                return leftResult;
            }
        }


        return findOverlappingRecursive(node.right, queryRange);
    }


    public List<BinRange> getAllIntervals() {
        List<BinRange> result = new ArrayList<>();
        inOrderTraversal(root, result);
        return result;
    }

    private void inOrderTraversal(IntervalNode node, List<BinRange> result) {
        if (node != null) {
            inOrderTraversal(node.left, result);
            result.add(node.range);
            inOrderTraversal(node.right, result);
        }
    }


    public List<BinRange> findAllOverlapping(BinRange queryRange) {
        List<BinRange> result = new ArrayList<>();
        findAllOverlappingRecursive(root, queryRange, result);
        return result;
    }

    private void findAllOverlappingRecursive(IntervalNode node, BinRange queryRange, List<BinRange> result) {
        if (node == null) {
            return;
        }
        
        if (node.range.overlaps(queryRange)) {
            result.add(node.range);
        }


        if (node.left != null && node.left.max >= queryRange.getStartBin()) {
            findAllOverlappingRecursive(node.left, queryRange, result);
        }


        if (node.right != null && node.range.getStartBin() <= queryRange.getEndBin()) {
            findAllOverlappingRecursive(node.right, queryRange, result);
        }
    }


    private IntervalNode balance(IntervalNode node) {
        if (node == null) {
            return null;
        }

        int balance = node.getBalance();


        if (balance > 1) {
            
            if (node.left.getBalance() < 0) {
                node.left = rotateLeft(node.left);
            }
            
            return rotateRight(node);
        }


        if (balance < -1) {
            
            if (node.right.getBalance() > 0) {
                node.right = rotateRight(node.right);
            }

            return rotateLeft(node);
        }

        return node;
    }

    private IntervalNode rotateRight(IntervalNode y) {
        IntervalNode x = y.left;
        IntervalNode T2 = x.right;


        x.right = y;
        y.left = T2;

        
        y.updateHeight();
        y.updateMax();
        x.updateHeight();
        x.updateMax();

        return x;
    }

    private IntervalNode rotateLeft(IntervalNode x) {
        IntervalNode y = x.right;
        IntervalNode T2 = y.left;


        y.left = x;
        x.right = T2;


        x.updateHeight();
        x.updateMax();
        y.updateHeight();
        y.updateMax();

        return y;
    }


    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return root == null;
    }

    public int getHeight() {
        return (root != null) ? root.height : 0;
    }

    public int getMaxDepth() {
        return calculateMaxDepth(root);
    }

    private int calculateMaxDepth(IntervalNode node) {
        if (node == null) {
            return 0;
        }

        int leftDepth = calculateMaxDepth(node.left);
        int rightDepth = calculateMaxDepth(node.right);

        return Math.max(leftDepth, rightDepth) + 1;
    }


    public boolean isBalanced() {
        return isBalancedRecursive(root);
    }

    private boolean isBalancedRecursive(IntervalNode node) {
        if (node == null) {
            return true;
        }

        int balance = Math.abs(node.getBalance());
        if (balance > 1) {
            return false;
        }

        return isBalancedRecursive(node.left) && isBalancedRecursive(node.right);
    }


    public long estimateMemoryUsage() {

        return size * 48L;
    }


    public void printTree() {
        printTreeRecursive(root, "", true);
    }

    private void printTreeRecursive(IntervalNode node, String prefix, boolean isLast) {
        if (node != null) {
            System.out.println(prefix + (isLast ? "└── " : "├── ") +
                    node.range + " (max: " + node.max + ")");

            if (node.left != null || node.right != null) {
                if (node.left != null) {
                    printTreeRecursive(node.left, prefix + (isLast ? "    " : "│   "), node.right == null);
                }
                if (node.right != null) {
                    printTreeRecursive(node.right, prefix + (isLast ? "    " : "│   "), true);
                }
            }
        }
    }
}