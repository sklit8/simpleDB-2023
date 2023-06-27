package simpledb.algorithm.Join;

import simpledb.common.DbException;
import simpledb.execution.JoinPredicate;
import simpledb.execution.OpIterator;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;
import simpledb.storage.TupleIterator;
import simpledb.transaction.TransactionAbortedException;

import java.util.Arrays;

/**
 * @Description
 * @Author luokunsong
 * @Version 1.0
 * @Data 2023/6/27 13:01
 */
public abstract class JoinStrategy {
    protected final OpIterator child1;
    protected final OpIterator child2;
    TupleDesc td;
    final JoinPredicate joinPredicate;

    public JoinStrategy(final OpIterator child1,final OpIterator child2,final TupleDesc td,final JoinPredicate joinPredicate){
        this.child1 = child1;
        this.child2 = child2;
        this.td = td;
        this.joinPredicate = joinPredicate;
    }

    //合并字段
    protected Tuple mergeTuple(final Tuple tuple1,final Tuple tuple2,final TupleDesc td){
        final Tuple tuple = new Tuple(td);
        int len1 = tuple1.getTupleDesc().numFields();
        for(int i = 0;i<len1;i++){
            tuple.setField(i,tuple1.getField(i));
        }
        for(int i = 0;i<tuple2.getTupleDesc().numFields();i++){
            tuple.setField(i+len1,tuple2.getField(i));
        }
        return tuple;
    }

    protected int fetchTuples(final OpIterator child,final Tuple[] tuples) throws TransactionAbortedException, DbException {
        int i = 0;
        Arrays.fill(tuples,null);
        while(child.hasNext() && i < tuples.length){
            final Tuple next = child.next();
            if(next != null){
                tuples[i++] = next;
            }
        }
        return i;
    }

    public abstract TupleIterator doJoin();

    public abstract void close();
}
