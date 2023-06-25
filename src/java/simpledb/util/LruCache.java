package simpledb.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @Description
 * @Author luokunsong
 * @Version 1.0
 * @Data 2023/6/25 16:31
 */
public class LruCache<K,V> {
    public class Node{
        public Node pre;
        public Node next;
        public K key;
        public V value;

        public Node(final K key,final V value){
            this.key = key;
            this.value = value;
        }
    }

    private final int maxSize;
    private final Map<K,Node> nodeMap;
    private final Node head;
    private final Node tail;

    public LruCache(int maxSize){
        this.maxSize = maxSize;
        this.head = new Node(null,null);
        this.tail = new Node(null,null);
        this.head.next = tail;
        this.tail.pre = head;
        this.nodeMap = new HashMap<>();
    }

    /**
    * @Description: 将节点插入为头结点
    * @Author: luokunsong
    * @Date: 2023/6/25
    */
    private void linkToHead(Node node){
        Node next = this.head.next;
        node.next = next;
        node.pre = this.head;
        this.head.next = node;
        next.pre = node;
    }

    /**
    * @Description: 移动到头结点
    * @Author: luokunsong
    * @Date: 2023/6/25
    */
    private void moveToHead(Node node){
        removeNode(node);
        linkToHead(node);
    }

    /**
    * @Description: 删除节点
    * @Author: luokunsong
    * @Date: 2023/6/25
    */
    public void removeNode(Node node){
        if(node.pre != null && node.next != null){
            node.pre.next = node.next;
            node.next.pre = node.pre;
        }
    }

    /**
    * @Description: 删除尾结点
    * @Author: luokunsong
    * @Date: 2023/6/25
    */
    public Node removeLast(){
        Node last = this.tail.pre;
        removeNode(last);
        return last;
    }

    /**
    * @Description: 删除节点
    * @Author: luokunsong
    * @Date: 2023/6/25
    */
    public synchronized void remove(K key){
        if(this.nodeMap.containsKey(key)){
            final Node node = this.nodeMap.get(key);
            removeNode(node);
            this.nodeMap.remove(key);
        }
    }

    /**
    * @Description: 获取节点
    * @Author: luokunsong
    * @Date: 2023/6/25
    */
    public synchronized V get(K key){
        if(this.nodeMap.containsKey(key)){
            Node node = this.nodeMap.get(key);
            moveToHead(node);
            return node.value;
        }
        return null;
    }

    public synchronized void put(K key,V value){
        if(this.nodeMap.containsKey(key)){
            Node node = this.nodeMap.get(key);
            node.value = value;
            moveToHead(node);
        }else{
            Node node = new Node(key,value);
            this.nodeMap.put(key,node);
            linkToHead(node);
        }
    }

    public synchronized Iterator<V> reverseIterator(){
        Node last = this.tail.pre;
        final ArrayList<V> list = new ArrayList<>();
        while(!list.equals(this.head)){
            list.add(last.value);
            last = last.pre;
        }
        return list.iterator();
    }
    public synchronized int getSize(){
        return this.nodeMap.size();
    }
    public int getMaxSize(){
        return maxSize;
    }
}
