# 6.830 Lab 2: SimpleDB Operators

**Assigned: Tue, Mar 9, 2021**
**Due: Fri, Mar 19, 2021 11:59 PM EDT**

在这个实验任务中，你将为SimpleDB编写一组操作符，以实现表的修改（如插入和删除记录）、选择、连接和聚合。这些将建立在你在实验1中编写的基础之上，为你提供一个可以对多个表进行简单查询的数据库系统。

此外，我们在实验室1中忽略了缓冲池管理的问题：我们没有处理当我们引用的页数超过了在数据库的生命周期内可以容纳的内存时出现的问题。在实验2中，你将设计一个驱逐策略，从缓冲池中冲走陈旧的页面。

你不需要在这个实验中实现事务或锁定。

本文件的其余部分给出了一些关于如何开始编码的建议，描述了一组练习来帮助你完成实验室的工作，并讨论了如何交出你的代码。这个实验室需要你写相当多的代码，所以我们鼓励你尽早开始工作

### 1.2. Implementation hints

下面是你可能进行SimpleDB实现的一个粗略的大纲

- 实现运算符Filter和Join，并验证其相应的测试是否有效。这些操作符的Javadoc注释包含了关于它们如何工作的细节。我们已经给了你Project和OrderBy的实现，这可能有助于你理解其他操作符的工作原理。


- 实现IntegerAggregator和StringAggregator。在这里，你将编写逻辑，实际计算输入图元序列中多组的特定字段的聚合。使用整数除法来计算平均值，因为SimpleDB只支持整数。StringAggegator只需要支持COUNT聚合，因为其他操作对字符串没有意义。


- 实现聚合运算符。和其他运算符一样，聚合运算实现了OpIterator接口，这样它们就可以被放在SimpleDB查询计划中。请注意，聚合运算符的输出是每次调用 next() 的整个组的聚合值，并且聚合构造器需要聚合和分组字段。


- 在BufferPool中实现与元组插入、删除和页面驱逐有关的方法。在这一点上，你不需要担心交易问题。

- 实现Insert和Delete操作符。像所有的操作符一样，Insert和Delete实现了OpIterator，接受一个要插入或删除的图元流，并输出一个带有整数字段的单一图元，表示插入或删除的图元的数量。这些操作符将需要调用BufferPool中的适当方法，这些方法实际上是对磁盘上的页面进行修改。检查插入和删除图元的测试是否正常工作。


请注意，SimpleDB没有实现任何类型的一致性或完整性检查，所以有可能在文件中插入重复的记录，也没有办法强制执行主键或外键约束。

在这一点上，你应该能够通过ant systemtest目标中的测试，这就是本实验室的目标。

你也将能够使用所提供的SQL解析器对你的数据库运行SQL查询!

最后，你可能会注意到，本实验室中的迭代器扩展了Operator类，而不是实现OpIterator接口。因为实现next/
hasNext的实现往往是重复的、烦人的和容易出错的，因此Operator通用地实现了这个逻辑，只要求你实现一个更简单的readNext。你可以自由地使用这种实现方式，或者如果你喜欢，就直接实现OpIterator接口。要实现OpIterator接口，请从迭代器类中删除extends Operator，并在其位置上加上实现OpIterator。

## 2. SimpleDB Architecture and Implementation Guide

### 2.1. Filter and Join

记得SimpleDB OpIterator类实现了关系代数的操作。现在你将实现两个运算符，使你能够执行比表扫描更有趣的查询。

- *Filter*: 这个操作符只返回满足作为其构造函数一部分的Predicate的图ples。因此，它过滤掉任何不符合谓词的图元。
- *Join*: 这个操作符根据作为其构造函数的一部分传入的JoinPredicate，将其两个子代的图元连接起来。我们只需要一个简单的嵌套循环连接，但你可以探索更有趣的连接实现。在你的实验报告中描述一下你的实现。

**Exercise 1.**

Implement the skeleton methods in:

------

- src/java/simpledb/execution/Predicate.java
- src/java/simpledb/execution/JoinPredicate.java
- src/java/simpledb/execution/Filter.java
- src/java/simpledb/execution/Join.java

------

At this point, your code should pass the unit tests in PredicateTest, JoinPredicateTest, FilterTest, and JoinTest. Furthermore, you should be able to pass the system tests FilterTest and JoinTest.

### 2.2. Aggregates

一个额外的SimpleDB操作符实现了带有GROUP BY子句的基本SQL聚合。你应该实现五个SQL聚合（`COUNT, SUM, AVG, MIN, MAX`）并支持分组。你只需要支持单个字段的聚合，以及单个字段的分组。

为了计算聚合，我们使用一个聚合器接口，将一个新元组合并到聚合的现有计算中。Aggregator在构建过程中被告知它应该使用什么操作来进行聚合。随后，客户端代码应该为子迭代器中的每个图元调用`Aggregator.mergeTupleIntoGroup()`。在所有图元被合并后，客户端可以检索到一个聚合结果的OpIterator。结果中的每个元组都是一对形式为（`groupValue, aggregateValue`）的元组，除非group by字段的值是`Aggregator.NO_GROUPING`，在这种情况下，结果是一个形式为（`aggregateValue`）的单一元组。

请注意，这个实现需要的空间与不同组的数量成线性关系。就本实验而言，你不需要担心组的数量超过可用内存的情况。

**Exercise 2.**

Implement the skeleton methods in:

------

- src/java/simpledb/execution/IntegerAggregator.java
- src/java/simpledb/execution/StringAggregator.java
- src/java/simpledb/execution/Aggregate.java

------

At this point, your code should pass the unit tests IntegerAggregatorTest, StringAggregatorTest, and AggregateTest. Furthermore, you should be able to pass the AggregateTest system test.

### 2.3. HeapFile Mutability

现在，我们将开始实现支持修改表格的方法。我们从单个页面和文件的层面开始。有两组主要的操作：添加图元和删除图元。

**Removing tuples:** 要删除一个图元，你需要实现deleteTuple。图元包含RecordIDs，它允许你找到它们所在的页面，所以这应该很简单，只要找到图元所属的页面并适当地修改该页面的标题即可。

**Adding tuples:** `HeapFile.java`中的`insertTuple`方法负责将一个元组添加到堆文件中。要向HeapFile添加一个新的元组，你必须找到一个有空槽的页面。如果HeapFile中没有这样的页面存在，你需要创建一个新的页面，并将其追加到磁盘上的物理文件。你将需要确保元组中的RecordID被正确地更新。

**Exercise 3.**

Implement the remaining skeleton methods in:

------

- src/java/simpledb/storage/HeapPage.java
- src/java/simpledb/storage/HeapFile.java
  (Note that you do not necessarily need to implement writePage at this point).

------

为了实现HeapPage，你需要修改`insertTuple()`和`deleteTuple()`等方法的头部位图。`deleteTuple()`。你可能会发现，我们在实验1中要求你实现的`getNumEmptySlots()`和`isSlotUsed()`方法可以作为有用的抽象方法。请注意，有一个`markSlotUsed`方法作为抽象，用来修改页眉中元组的填充或清除状态。

请注意，HeapFile.insertTuple()和HeapFile.deleteTuple()方法使用BufferPool.getPage()方法访问页面是很重要的；否则，你在下一个实验中的事务实现将不能正常工作。

Implement the following skeleton methods in `src/simpledb/BufferPool.java`:

------

- insertTuple()
- deleteTuple()

------

这些方法应该调用HeapFile中属于被修改的表的适当方法（这个额外的间接层次是需要的，以便在未来支持其他类型的文件--比如索引--）。

在这一点上，你的代码应该通过HeapPageWriteTest和HeapFileWriteTest的单元测试，以及BufferPoolWriteTest。

### 2.4. Insertion and deletion

现在你已经写好了所有用于添加和删除图元的 HeapFile 机器，你将实现 Insert 和 Delete 操作符。

对于实现插入和删除查询的计划，最上面的操作符是一个特殊的插入或删除操作符，它修改了磁盘上的页面。这些操作符返回受影响图元的数量。这是通过返回一个带有一个整数字段的单一元组来实现的，其中包含计数。

- *Insert*: 这个操作符将它从其子操作符中读取的图元添加到其构造函数中指定的tableid中。它应该使用BufferPool.insertTuple()方法来做到这一点。
- *Delete*: 这个操作符将它从其子操作符中读取的图元从其构造函数中指定的表id中删除。它应该使用BufferPool.deleteTuple()方法来做这件事。

**Exercise 4.**

Implement the skeleton methods in:

------

- src/java/simpledb/execution/Insert.java
- src/java/simpledb/execution/Delete.java

------

At this point, your code should pass the unit tests in InsertTest. We have not provided unit tests for `Delete`. Furthermore, you should be able to pass the InsertTest and DeleteTest system tests.

### 2.5. Page eviction

在实验室1中，我们没有正确观察到由构造参数numPages定义的缓冲池中最大页数的限制。现在，你将选择一个页面驱逐策略，并对以前任何读取或创建页面的代码进行调试，以实现你的策略。

当缓冲池中的页数超过numPages时，在加载下一个页之前，应该从缓冲池中驱逐一个页。驱逐策略的选择是由你决定的；没有必要做一些复杂的事情。在实验报告中描述一下你的策略。

注意BufferPool要求你实现一个flushAllPages()方法。这不是你在实际实现缓冲池时需要的东西。然而，我们需要这个方法用于测试。你不应该从任何真正的代码中调用这个方法。

由于我们实现ScanTest.cacheTest的方式，你需要确保你的flushPage和flushAllPages方法不从缓冲池中驱逐页面，以正确通过这个测试。

flushAllPages应该对缓冲池中的所有页面调用flushPage，而flushPage应该将任何脏页写入磁盘并标记为不脏，同时将其留在缓冲池中。

唯一应该从缓冲池中移除页面的方法是evictPage，它应该对它驱逐的任何脏页调用flushPage。

**Exercise 5.**

Fill in the `flushPage()` method and additional helper methods to implement page eviction in:

------

- src/java/simpledb/storage/BufferPool.java

------

如果你没有在上面的HeapFile.java中实现writePage()，你也需要在这里实现它。最后，你还应该实现discardPage()来从缓冲池中删除一个页面，而不将其冲到磁盘上。我们不会在本实验中测试discardPage()，但它在未来的实验中是必要的。

在这一点上，你的代码应该通过EvictionTest系统测试。

由于我们不会检查任何特定的驱逐策略，这个测试通过创建一个有16页的BufferPool（注意：虽然DEFAULT_PAGES是50，但我们初始化的BufferPool更少！），扫描一个超过16页的文件，看看JVM的内存使用量是否增加了5MB以上。如果你没有正确地实施驱逐策略，你将无法驱逐足够多的页，并将超过大小限制，从而导致测试失败。

You have now completed this lab. Good work!



### 2.6. Query walkthrough

下面的代码在两个表之间实现了一个简单的连接查询，每个表由三列整数组成。( 文件some_data_file1.dat和some_data_file2.dat是这个文件的页面的二进制表示)。这段代码等同于SQL语句：

```
SELECT *
FROM some_data_file1,
     some_data_file2
WHERE some_data_file1.field1 = some_data_file2.field1
  AND some_data_file1.id > 1
```

对于更多的查询操作的例子，你可能会发现浏览连接、过滤器和聚合的单元测试是有帮助的。

```
package simpledb;

import java.io.*;

public class jointest {

    public static void main(String[] argv) {
        // construct a 3-column table schema
        Type types[] = new Type[]{Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE};
        String names[] = new String[]{"field0", "field1", "field2"};

        TupleDesc td = new TupleDesc(types, names);

        // create the tables, associate them with the data files
        // and tell the catalog about the schema  the tables.
        HeapFile table1 = new HeapFile(new File("some_data_file1.dat"), td);
        Database.getCatalog().addTable(table1, "t1");

        HeapFile table2 = new HeapFile(new File("some_data_file2.dat"), td);
        Database.getCatalog().addTable(table2, "t2");

        // construct the query: we use two SeqScans, which spoonfeed
        // tuples via iterators into join
        TransactionId tid = new TransactionId();

        SeqScan ss1 = new SeqScan(tid, table1.getId(), "t1");
        SeqScan ss2 = new SeqScan(tid, table2.getId(), "t2");

        // create a filter for the where condition
        Filter sf1 = new Filter(
                new Predicate(0,
                        Predicate.Op.GREATER_THAN, new IntField(1)), ss1);

        JoinPredicate p = new JoinPredicate(1, Predicate.Op.EQUALS, 1);
        Join j = new Join(p, sf1, ss2);

        // and run it
        try {
            j.open();
            while (j.hasNext()) {
                Tuple tup = j.next();
                System.out.println(tup);
            }
            j.close();
            Database.getBufferPool().transactionComplete(tid);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
```

两个表都有三个整数字段。为了表达这一点，我们创建了一个TupleDesc对象，并将一个表示字段类型的Type对象和表示字段名的String对象的数组传给它。一旦我们创建了这个TupleDesc，我们就初始化两个代表表的HeapFile对象。一旦我们创建了这些表，我们就把它们添加到目录中。(如果这是一个已经在运行的数据库服务器，我们会加载这个目录信息；我们需要加载这个信息只是为了这个测试的目的）。

一旦我们完成了数据库系统的初始化，我们就创建一个查询计划。我们的计划由两个SeqScan操作符组成，它们扫描磁盘上每个文件的图元，与第一个HeapFile上的Filter操作符相连，与Join操作符相连，后者根据JoinPredicate将表中的图元连接起来。一般来说，这些运算符被实例化为对适当的表（在SeqScan的情况下）或子运算符（在例如Join的情况下）的引用。然后，测试程序重复调用Join运算符的下一步，它又从其子运算符中提取图元。当图元从Join中输出时，它们会在命令行中打印出来。



### 2.7. Query Parser

我们已经为你提供了SimpleDB的查询分析器，一旦你完成了本实验室的练习，你可以用它来编写和运行针对你的数据库的SQL查询。

第一步是创建一些数据表和一个目录。假设你有一个文件data.txt，内容如下：

```
1,10
2,20
3,30
4,40
5,50
5,50
```

你可以使用转换命令将其转换为SimpleDB表（请确保先输入ant！）：

```
java -jar dist/simpledb.jar convert data.txt 2 "int,int"
```

这将创建一个文件data.dat。除了表的原始数据外，两个附加参数指定每条记录有两个字段，其类型为int和int。

接下来，创建一个目录文件 catalog.txt，其内容如下：

```
data (f1 int, f2 int)
```

这告诉SimpleDB有一个表，data（存储在data.dat中），有两个名为f1和f2的整数域。

最后，调用解析器。你必须从命令行运行java（ant在交互式目标下不能正常工作）。 从simpledb/目录中，键入

```
java -jar dist/simpledb.jar parser catalog.txt
```

You should see output like:

```
Added table : data with schema INT(f1), INT(f2), 
SimpleDB> 
```

Finally, you can run a query:

```
SimpleDB> select d.f1, d.f2 from data d;
Started a new transaction tid = 1221852405823
 ADDING TABLE d(data) TO tableMap
     TABLE HAS  tupleDesc INT(d.f1), INT(d.f2), 
1       10
2       20
3       30
4       40
5       50
5       50

 6 rows.
----------------
0.16 seconds

SimpleDB> 
```

该分析器的功能相对齐全（包括对SELECTs、INSERTs、DELETEs和事务的支持），但确实存在一些问题，不一定能报告出完全有参考价值的错误信息。这里有一些需要记住的限制：

- 你必须在每个字段名前加上其表名，即使字段名是唯一的（你可以使用表名别名，如上面的例子，但你不能使用AS关键字）。
- 在WHERE子句中支持嵌套查询，但在FROM子句中不支持。
- 不支持算术表达式（例如，你不能取两个字段的总和）。
- 最多允许一个GROUP BY和一个聚合列。
- 不允许使用面向集合的操作符，如IN、UNION和EXCEPT。
- 在WHERE子句中只允许使用AND表达式。
- 不支持UPDATE表达式。
- 允许使用字符串操作符LIKE，但是必须完全写出来（也就是说，不允许使用Postgres的tilde [~]缩写）。