package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Debug;
import simpledb.common.Permissions;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;
import simpledb.util.HeapFileIterator;

import javax.xml.crypto.Data;
import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @see HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private final File file;
    private final TupleDesc td;
    private RandomAccessFile randomAccessFile;

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        this.file = f;
        this.td = td;
        try {
            this.randomAccessFile = new RandomAccessFile(this.file, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return this.file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return this.file.getAbsolutePath().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return this.td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        final int pos = BufferPool.getPageSize() * pid.getPageNumber();
        byte[] pageData = new byte[BufferPool.getPageSize()];
        try{
            this.randomAccessFile.seek(pos);
            this.randomAccessFile.read(pageData,0,pageData.length);
            final HeapPage heapPage = new HeapPage((HeapPageId) pid,pageData);
            return heapPage;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        final int pos = BufferPool.getPageSize() * page.getId().getPageNumber();
        this.randomAccessFile.seek(pos);
        final byte[] pageData = page.getPageData();
        this.randomAccessFile.write(pageData);
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        return (int) (this.file.length() / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public List<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        //1.HeapFile的insertTuple遍历所有数据页（用BufferPool.getPage()获取，getPage会先从BufferPool再从磁盘获取），然后判断数据页是否有空slot，有的话调用对应有空slot的page的insertTuple方法去插入页面；
        //2.如果遍历完所有数据页，没有找到空的slot，这时应该在磁盘中创建一个空的数据页，并且先调用 writePage() 写入该数据页到磁盘, 并通过 bufferPool 来获取该数据也
        final ArrayList<Page> dirtPageList = new ArrayList<>();
        for(int i = 0;i < this.numPages();i ++){
            final HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid,new HeapPageId(getId(),i),Permissions.READ_WRITE);
            if(page != null && page.getNumEmptySlots() > 0){
                page.insertTuple(t);
                page.markDirty(true,tid);
                dirtPageList.add(page);
                break;
            }
        }
        if(dirtPageList.size() == 0){
            final HeapPageId heapPageId = new HeapPageId(getId(),this.numPages());
            HeapPage newPage = new HeapPage(heapPageId,HeapPage.createEmptyPageData());
            writePage(newPage);
            //through buffer poll to get newPage
            newPage = (HeapPage) Database.getBufferPool().getPage(tid,heapPageId,Permissions.READ_WRITE);
            newPage.insertTuple(t);
            newPage.markDirty(true,tid);
            dirtPageList.add(newPage);
        }
        return dirtPageList;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        //根据tuple得到对应的数据页HeapPage，然后调用数据页的deleteTuple即可
        final ArrayList<Page> dirtyPageList = new ArrayList<>();
        final RecordId recordId = t.getRecordId();
        final PageId pageId = recordId.getPageId();
        final HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid,pageId,Permissions.READ_WRITE);
        if(page != null && page.isSlotUsed(recordId.getTupleNumber())){
            page.deleteTuple(t);
            dirtyPageList.add(page);
        }
        return dirtyPageList;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new HeapFileIterator(numPages(),tid,this.getId());
    }

}

