package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.Permissions;
import simpledb.common.DbException;
import simpledb.common.DeadlockException;
import simpledb.transaction.LockManager;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;
import simpledb.util.LruCache;

import javax.xml.crypto.Data;
import java.io.*;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 * 
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;
    
    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    private final LruCache<PageId, Page> lruCache;

    private final LockManager lockManager;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.lruCache = new LruCache<>(numPages);
        this.lockManager = new LockManager();
    }
    
    public static int getPageSize() {
      return pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public  Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        // some code goes here
        final int lockType = perm == Permissions.READ_ONLY ? 0 : 1;
        final int timeout = new Random().nextInt(2000) + 1000;
        if(!this.lockManager.tryAcquireLock(pid,tid,lockType,timeout)){
            throw new TransactionAbortedException();
        }
        final Page page = this.lruCache.get(pid);
        if(page != null){
            return page;
        }
        return loadPageAndCache(pid);
    }

    private Page loadPageAndCache(final PageId pid) throws DbException {
        final DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
        final Page dbPage = dbFile.readPage(pid);
        if (dbPage != null) {
            this.lruCache.put(pid, dbPage);
            if (this.lruCache.getSize() == this.lruCache.getMaxSize()) {
                evictPage();
            }
        }
        return dbPage;
    }
    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void unsafeReleasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for lab1|lab2
        this.lockManager.releaseLock(pid,tid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        return this.lockManager.holds(p,tid);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit) {
        // some code goes here
        // not necessary for lab1|lab2
        try {
            if(commit){
                flushPages(tid);
            }else{
                reLoadPages(tid);
            }
            this.lockManager.releaseLockByTxn(tid);
        } catch (IOException | DbException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other 
     * pages that are updated (Lock acquisition is not needed for lab2). 
     * May block if the lock(s) cannot be acquired.
     * 
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * 代表事务 tid 将元组添加到指定的表中。将在元组添加到的页面上获取写锁定以及更新的任何其他页面（lab2 不需要锁定获取）。
     * 如果无法获取锁，则可能会阻止。通过调用其 markDirty 位，将任何作弄脏的页面标记为脏页，
     * 并将已脏的任何页的版本添加到缓存中（替换这些页的任何现有版本），以便将来的请求看到最新的页。
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        final DbFile table = Database.getCatalog().getDatabaseFile(tableId);
        final List<Page> dirtyPages = table.insertTuple(tid,t);
        for(final Page page : dirtyPages){
            page.markDirty(true,tid);
            this.lruCache.put(page.getId(), page);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * 从缓冲池中删除指定的元组。将在从中删除元组的页面上获取写锁定以及更新的任何其他页面。如果无法获取锁，则可能会阻止。
     * 通过调用其 markDirty 位，将任何作弄脏的页面标记为脏页，并将已脏的任何页的版本添加到缓存中（替换这些页的任何现有版本），
     * 以便将来的请求看到最新的页
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        int tableId = t.getRecordId().getPageId().getTableId();
        DbFile table = Database.getCatalog().getDatabaseFile(tableId);
        final List<Page> dirtPages= table.deleteTuple(tid,t);
        for(final Page page : dirtPages){
            page.markDirty(true,tid);
            this.lruCache.put(page.getId(), page);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // some code goes here
        // not necessary for lab1
        final Iterator<Page> pageIterator = this.lruCache.valueIterator();
        while(pageIterator.hasNext()){
            flushPage(pageIterator.next());
        }
    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.
        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
        从缓冲池中删除特定的页面 ID。恢复管理器需要它来确保缓冲池不会在其缓存中保留回滚的页面。
        也由 B+ 树文件用于确保从缓存中删除已删除的页面，以便可以安全地重复使用它们
    */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for lab1
        this.lruCache.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     * @param page the page that needed to be flush
     */
    public synchronized  void flushPage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        try{
            final DbFile tableFile = Database.getCatalog().getDatabaseFile(page.getId().getTableId());
            tableFile.writePage(page);
            page.markDirty(false,null);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error happen when flush page to disk:" + e.getMessage());
        }
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2

    }

    /**
     * @Description: 从磁盘重新加载指定事务的所有页面。
     * @Author: luokunsong
     * @Date: 2023/6/26
     */
    public synchronized void reLoadPages(TransactionId tid) throws DbException {
        final Iterator<Page> pageIterator = this.lruCache.valueIterator();
        while(pageIterator.hasNext()){
            final  Page page = pageIterator.next();
            if(page.isDirty() == tid){
                discardPage(page.getId());
                loadPageAndCache(page.getId());
            }
        }
    }
    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {
        // some code goes here
        // not necessary for lab1
        final Iterator<Page> pageIterator = this.lruCache.reverseIterator();
        while (pageIterator.hasNext()){
            final Page page = pageIterator.next();
            if(page.isDirty() == null){
                discardPage(page.getId());
                return;
            }
        }
        throw new DbException("All pages are dirty in buffer pool");
    }

}
