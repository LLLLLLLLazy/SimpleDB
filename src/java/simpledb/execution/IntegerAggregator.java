package simpledb.execution;

import simpledb.common.Type;
import simpledb.storage.Field;
import simpledb.storage.IntField;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;
import simpledb.storage.TupleIterator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private final int gbfield;
    private final Type gbfieldtype;
    private final int afield;
    private final Op what;
    private final Map<Field, AggState> groups;

    private static class AggState {
        int count = 0;
        int sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
    }

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        this.groups = new LinkedHashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        Field groupVal = gbfield == NO_GROUPING ? null : tup.getField(gbfield);
        int aVal = ((IntField) tup.getField(afield)).getValue();

        AggState state = groups.computeIfAbsent(groupVal, k -> new AggState());
        state.count++;
        state.sum += aVal;
        if (aVal < state.min) {
            state.min = aVal;
        }
        if (aVal > state.max) {
            state.max = aVal;
        }
    }

    /**
     * Create a OpIterator over group aggregate results.
     * 
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        List<Tuple> tuples = new ArrayList<>();
        TupleDesc td;
        if (gbfield == NO_GROUPING) {
            td = new TupleDesc(new Type[]{Type.INT_TYPE});
        } else {
            td = new TupleDesc(new Type[]{gbfieldtype, Type.INT_TYPE});
        }

        for (Map.Entry<Field, AggState> entry : groups.entrySet()) {
            Tuple t = new Tuple(td);
            int aggVal = computeAggregate(entry.getValue());
            if (gbfield == NO_GROUPING) {
                t.setField(0, new IntField(aggVal));
            } else {
                t.setField(0, entry.getKey());
                t.setField(1, new IntField(aggVal));
            }
            tuples.add(t);
        }

        return new TupleIterator(td, tuples);
    }

    private int computeAggregate(AggState state) {
        switch (what) {
            case MIN:
                return state.min;
            case MAX:
                return state.max;
            case SUM:
                return state.sum;
            case AVG:
                return state.sum / state.count;
            case COUNT:
                return state.count;
            default:
                throw new UnsupportedOperationException("unsupported op for lab2: " + what);
        }
    }

}
