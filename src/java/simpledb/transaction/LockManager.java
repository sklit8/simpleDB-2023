package simpledb.transaction;

import simpledb.storage.Page;
import simpledb.storage.PageId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description
 * @Author luokunsong
 * @Version 1.0
 * @Data 2023/6/25 17:13
 */
public class LockManager {
    private final Map<PageId, List<Lock>> lockMap;
    public LockManager(){
        this.lockMap = new ConcurrentHashMap<>();
    }

    public boolean tryAcquireLock(final PageId pageId,final TransactionId tid,final int lockType,final int timeout){
        final long now = System.currentTimeMillis();
        while (true){
            if(System.currentTimeMillis() - now >= timeout){
                return false;
            }
            if(acquireLock(pageId, tid, lockType)){
                return true;
            }
        }
    }

    public synchronized boolean acquireLock(final PageId pageId,final TransactionId tid,final int lockType){
        //1.如果page没被锁，锁住返回true
        if(!this.lockMap.containsKey(pageId)){
            final Lock lock = new Lock(tid,lockType);
            final List<Lock> locks = new ArrayList<>();
            locks.add(lock);
            this.lockMap.put(pageId,locks);
            return true;
        }
        //被锁了，拿出锁
        final List<Lock> locks = this.lockMap.get(pageId);
        //2.检查是否当前事务的锁
        for(final Lock lock:locks){
            if(lock.getTid().equals(tid)){
                if(lock.getLockType() == lockType){
                    return true;
                }
                if(lock.getLockType() == 1){
                    return true;
                }
                if(lock.getLockType() == 0 && locks.size() == 1){
                    lock.setLockType(1);
                    return true;
                }
                return false;
            }
        }
        //3.检查是否是写锁
        if(locks.size() > 0 && locks.get(0).getLockType() == 1){
            return false;
        }

        //4.这里已经存在另一个锁，所以我们只能得到一个readLock
        if(lockType == 0){
            final Lock lock = new Lock(tid,lockType);
            locks.add(lock);
            return true;
        }
        return false;
    }

    public synchronized boolean releaseLock(final PageId pageId,final TransactionId tid){
        if(!this.lockMap.containsKey(pageId)){
            return false;
        }
        final List<Lock> locks = this.lockMap.get(pageId);
        for(int i = 0;i<locks.size();i++){
            final Lock lock = locks.get(i);
            if(lock.getTid().equals(tid)){
                locks.remove(lock);
                this.lockMap.put(pageId,locks);
                if(locks.size() == 0){
                    this.lockMap.remove(pageId);
                }
            }
        }
        return false;
    }

    public synchronized void releaseLockByTxn(final TransactionId tid){
        this.lockMap.forEach((pid,lokcs) -> {
            if(holds(pid,tid)){
                releaseLock(pid,tid);
            }
        });
    }

    public synchronized boolean holds(final PageId pageId,TransactionId tid){
        if(!this.lockMap.containsKey(pageId)){
            return false;
        }
        final List<Lock> locks = this.lockMap.get(pageId);
        for(int i = 0;i<locks.size();i++){
            final Lock lock = locks.get(i);
            if(lock.getTid().equals(tid)){
                return true;
            }
        }
        return false;
    }
}
