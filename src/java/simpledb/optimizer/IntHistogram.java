package simpledb.optimizer;

import simpledb.execution.Predicate;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {
    private final int bucketCount;
    private final int min;
    private final int max;
    private final int[] buckets;
    private final double width;
    private int totalValues;

    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
        int range = Math.max(1, max - min + 1);
        this.bucketCount = Math.max(1, Math.min(buckets, range));
        this.min = min;
        this.max = max;
        this.buckets = new int[this.bucketCount];
        this.width = ((double) (max - min + 1)) / this.bucketCount;
        this.totalValues = 0;
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
        if (v < min || v > max) {
            return;
        }
        buckets[bucketIndex(v)]++;
        totalValues++;
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
        if (totalValues == 0) {
            return 0.0;
        }

        switch (op) {
            case EQUALS:
                if (v < min || v > max) {
                    return 0.0;
                }
                return bucketHeight(v) / bucketWidth(bucketIndex(v)) / totalValues;
            case NOT_EQUALS:
                return 1.0 - estimateSelectivity(Predicate.Op.EQUALS, v);
            case GREATER_THAN:
                if (v < min) {
                    return 1.0;
                }
                if (v >= max) {
                    return 0.0;
                }
                return greaterThanSelectivity(v);
            case LESS_THAN:
                if (v <= min) {
                    return 0.0;
                }
                if (v > max) {
                    return 1.0;
                }
                return lessThanSelectivity(v);
            case GREATER_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.GREATER_THAN, v)
                        + estimateSelectivity(Predicate.Op.EQUALS, v);
            case LESS_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.LESS_THAN, v)
                        + estimateSelectivity(Predicate.Op.EQUALS, v);
            case LIKE:
                return estimateSelectivity(Predicate.Op.EQUALS, v);
            default:
                return 0.0;
        }
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        if (totalValues == 0) {
            return 0.0;
        }
        return 1.0 / Math.max(1, max - min + 1);
    }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        return "IntHistogram[min=" + min + ", max=" + max
                + ", buckets=" + bucketCount + ", total=" + totalValues + "]";
    }

    private int bucketIndex(int v) {
        int idx = (int) ((v - min) / width);
        if (idx < 0) {
            return 0;
        }
        if (idx >= bucketCount) {
            return bucketCount - 1;
        }
        return idx;
    }

    private int bucketHeight(int v) {
        return buckets[bucketIndex(v)];
    }

    private double bucketLeft(int idx) {
        return min + idx * width;
    }

    private double bucketRightExclusive(int idx) {
        return Math.min(max + 1.0, bucketLeft(idx) + width);
    }

    private double bucketWidth(int idx) {
        return bucketRightExclusive(idx) - bucketLeft(idx);
    }

    private double greaterThanSelectivity(int v) {
        int idx = bucketIndex(v);
        double sel = 0.0;

        double left = bucketLeft(idx);
        double rightExclusive = bucketRightExclusive(idx);
        double inBucketFraction = (rightExclusive - (v + 1.0)) / (rightExclusive - left);
        if (inBucketFraction > 0) {
            sel += (buckets[idx] / (double) totalValues) * inBucketFraction;
        }

        for (int i = idx + 1; i < bucketCount; i++) {
            sel += buckets[i] / (double) totalValues;
        }
        return Math.max(0.0, Math.min(1.0, sel));
    }

    private double lessThanSelectivity(int v) {
        int idx = bucketIndex(v);
        double sel = 0.0;

        for (int i = 0; i < idx; i++) {
            sel += buckets[i] / (double) totalValues;
        }

        double left = bucketLeft(idx);
        double rightExclusive = bucketRightExclusive(idx);
        double inBucketFraction = ((double) v - left) / (rightExclusive - left);
        if (inBucketFraction > 0) {
            sel += (buckets[idx] / (double) totalValues) * inBucketFraction;
        }

        return Math.max(0.0, Math.min(1.0, sel));
    }
}
