# 6.830/6.814 Lab 1: SimpleDB

在6.830的实验作业中，你将编写一个名为SimpleDB的基本数据库管理系统。在这个实验室中，你将专注于实现访问磁盘上存储的数据所需的核心模块；在未来的实验室中，你将增加对各种查询处理操作符的支持，以及事务、锁定和并发查询。

SimpleDB是用Java编写的。我们已经为你提供了一组大部分未实现的类和接口。你将需要为这些类编写代码。我们将通过运行一组用JUnit编写的系统测试对你的代码进行评分。我们还提供了一些单元测试，我们不会在评分中使用这些测试，但你可能会发现这些测试对验证你的代码是否工作很有用。我们也鼓励你在我们的测试之外，开发你自己的测试套件。

本文档的其余部分描述了SimpleDB的基本架构，给出了一些关于如何开始编码的建议，并讨论了如何交出你的实验室。

我们强烈建议你尽可能早的开始这个实验室。它需要你写相当多的代码!

## 1. SimpleDB架构和实施指南

SimpleDB由以下部分组成：

- 表示字段（field）、图元（Tuples）和图元格式（TupleDesc）的类；
- 对图元应用谓词和条件的类；
- 一个或多个访问方法（例如，堆文件），在磁盘上存储关系，并提供一种方法来遍历这些关系的图元；
- 一个操作者类的集合（例如，选择、连接、插入、删除等），处理图元；
- 一个缓冲池，在内存中缓存活跃的图元和页面，并处理并发控制和事务（在本实验中你不需要担心这两点）；
- 一个目录，存储关于可用表和它们的模式的信息。

SimpleDB并不包括许多你可能认为是 "数据库 "一部分的东西。特别是，SimpleDB没有：

- (在本实验室中），一个SQL前端或分析器，允许你直接向SimpleDB输入查询。取而代之的是，查询是通过将一组运算符串联到一个手工建立的查询计划中来建立的（见2.7节）。我们将提供一个简单的解析器，在后面的实验中使用。
- 视图。
- 除了整数和固定长度的字符串之外的数据类型。
- (在本实验室中) 查询优化器。
- (在本实验室中) 索引。

在本节的其余部分，我们将描述SimpleDB的每个主要组件，你将需要在本实验室中实现这些组件。你应该使用本讨论中的练习来指导你的实现。本文档绝不是SimpleDB的完整规范；你将需要对如何设计和实现系统的各个部分做出决定。注意，对于实验1，除了顺序扫描，你不需要实现任何操作符（例如，选择、连接、项目）。你将在未来的实验中增加对其他运算符的支持。

### 2.1. Database Class
数据库类提供了对作为数据库全局状态的静态对象集合的访问。特别是，这包括访问目录（数据库中所有表的列表）、缓冲池（当前驻留在内存中的数据库文件页的集合）和日志文件的方法。在本实验中，你不需要担心日志文件的问题。我们已经为你实现了数据库类。你应该看一下这个文件，因为你将需要访问这些对象。

### 2.2. Fields and Tuples
SimpleDB中的图元(Tuple)是非常基本的。它们由一个 "Field"对象的集合组成，在 "Tuple"中每个字段一个。Field "是一个接口，不同的数据类型（例如，整数，字符串）都可以实现。Tuple对象是由底层访问方法（例如，堆文件，或B-树）创建的，如下一节所述。元组也有一个类型，称为元组描述符，由`TupleDesc'对象表示。这个对象由 "Type"对象的集合组成，元组中的每个字段都有一个，每个对象都描述了相应字段的类型。

#### Exercise 1

Implement the skeleton methods in:

------

- src/java/simpledb/storage/TupleDesc.java
- src/java/simpledb/storage/Tuple.java

------

在这一点上，你的代码应该通过单元测试TupleTest和TupleDescTest。在这一点上，modifyRecordId()应该失败，因为你还没有实现它。

### 2.3. CataLog
目录（SimpleDB中的Catalog类）由当前数据库中的表和表的模式的列表组成。你需要支持添加一个新的表的能力，以及获得一个特定表的信息。与每个表相关的是一个TupleDesc对象，它允许操作者确定一个表的字段类型和数量。

全局目录是一个单一的Catalog实例，为整个SimpleDB进程分配。全局目录可以通过Database.getCatalog()方法进行检索，全局缓冲池也是如此（使用Database.getBufferPool()）。

#### Exercise 2

Implement the skeleton methods in:

------

- src/java/simpledb/common/Catalog.java

------

At this point, your code should pass the unit tests in CatalogTest.

### 2.4. BufferPool
缓冲池（SimpleDB中的BufferPool类）负责在内存中缓存最近从磁盘读取的页面。所有的操作者通过缓冲池从磁盘上的各种文件读写页面。它由固定数量的页面组成，由BufferPool构造函数的numPages参数定义。在后面的实验中，你将实现一个**驱逐策略**。对于这个实验室，你只需要实现构造函数和SeqScan操作者使用的BufferPool.getPage()方法。BufferPool应该最多存储numPages页。对于这个实验室，如果对不同页面的请求超过numPages`，那么你可以抛出一个DbException，而不是实施驱逐策略。在未来的实验中，你将被要求实现一个驱逐策略。

数据库类提供了一个静态方法，Database.getBufferPool()，它返回整个SimpleDB进程的一个BufferPool实例的引用。

### Exercise 3

Implement the getPage() method in:

------

- src/java/simpledb/storage/BufferPool.java

------

我们没有为 BufferPool 提供单元测试。你实现的功能将在下面的 HeapFile 的实现中得到测试。你应该使用DbFile.readPage方法来访问DbFile的页面。

### 2.5. HeapFile访问方法
访问方法提供了一种从磁盘读取或写入以特定方式排列的数据的方法。常见的访问方法包括堆文件（未经排序的图元文件）和B-树；对于这项作业，你将只实现一个堆文件访问方法，我们已经为你写了一些代码。

一个堆文件对象被安排成一组页面，每个页面由固定数量的字节组成，用于存储图元，（由常BufferPool.DEFAULT_PAGE_SIZE定义），包括一个头。在SimpleDB中，数据库中每个表都有一个HeapFile对象。HeapFile中的每个页面都被安排成一组槽，每个槽可以容纳一个元组（SimpleDB中给定表的元组都是相同大小的）。除了这些槽之外，每个页面都有一个头，由一个位图组成，每个元组槽有一个位。如果对应于某一元组的位是1，它表示该元组是有效的；如果是0，该元组是无效的（例如，已经被删除或从未被初始化）。 HeapFile对象的页是HeapPage类型，实现了页接口。页被存储在缓冲池中，但由HeapFile类读取和写入。

SimpleDB在磁盘上存储堆文件的格式或多或少与它们在内存中的存储格式相同。每个文件由磁盘上连续排列的页数据组成。每个页面由一个或多个代表页头的字节组成，然后是实际页面内容的页面大小字节。每个元组的内容需要元组大小*8位，页眉需要1位。因此，一个页面可以容纳的元组数量为：

```
tuples per page_ = floor((_page size_ * 8) / (_tuple size_ * 8 + 1))
```

其中图元大小是指页面中图元的大小，以字节为单位。这里的想法是，每个元组需要在头中增加一个比特的存储。我们计算一个页面中的比特数（通过将页面大小乘以8），然后用这个数量除以元组中的比特数（包括这个额外的头部比特），得到每页的元组数。下限操作四舍五入到最接近的图元数（我们不希望在一个页面上存储部分图元！）。

一旦我们知道了每页的图元数，存储页眉所需的字节数就简单了：

```
headerBytes = ceiling(tupsPerPage/8)
```

天花板操作四舍五入到最接近的整数字节数（我们永远不会存储少于一整字节的标题信息）。

每个字节的低位（最小有效位）代表文件中较早的槽的状态。因此，第一个字节的最低位代表页面中的第一个槽是否正在使用。第一个字节的第二个最低位代表页面中的第二个槽是否在使用中，以此类推。另外，请注意，最后一个字节的高阶位可能不对应于文件中实际存在的槽，因为槽的数量可能不是8的倍数。 还请注意，所有的Java虚拟机都是big-endian。

#### Exercise 4

Implement the skeleton methods in:

------

- src/java/simpledb/storage/HeapPageId.java
- src/java/simpledb/storage/RecordId.java
- src/java/simpledb/storage/HeapPage.java

------

尽管你不会在实验1中直接使用它们，但我们要求你在HeapPage中实现getNumEmptySlots()和isSlotUsed()。这些都需要在页头中推送一些位。你可能会发现查看HeapPage或src/simpledb/HeapFileEncoder.java中提供的其他方法对于理解页面的布局很有帮助。

你还需要在页面中的图元上实现一个Iterator，这可能涉及到一个辅助类或数据结构。

在这一点上，你的代码应该通过HeapPageIdTest、RecordIDTest和HeapPageReadTest的单元测试。

在你实现了HeapPage之后，你将在本实验室中为HeapFile编写方法，以计算文件中的页数，并从文件中读取一个页。然后你将能够从存储在磁盘上的文件中获取图元。

#### Exercise 5

Implement the skeleton methods in:

------

- src/java/simpledb/storage/HeapFile.java

  ------

要从磁盘上读取一个页面，你首先需要计算出文件中的正确偏移量。提示：你需要对文件进行随机访问，以便在任意的偏移量上读写页面。从磁盘读取页面时，你不应该调用BufferPool方法。

你还需要实现HeapFile.iterator()方法，它应该遍历HeapFile中每个页面的图元。迭代器必须使用BufferPool.getPage()方法来访问HeapFile中的页面。这个方法将页面加载到缓冲池中，最终将被用于（在后面的实验室中）实现基于锁的并发控制和恢复。不要在open()调用时将整个表加载到内存中 -- 这将导致非常大的表出现内存不足的错误。

在这一点上，你的代码应该通过HeapFileReadTest的单元测试。

### 2.6.操作符
操作员负责查询计划的实际执行。他们实现了关系代数的操作。在SimpleDB中，操作符是基于迭代器的；每个操作符都实现了DbIterator接口。

操作符通过将低级操作符传递到高级操作符的构造器中，也就是通过 "连锁 "将它们连接到一个计划中。在计划叶子上的特殊访问方法操作符负责从磁盘上读取数据（因此在它们下面没有任何操作符）。

在计划的顶部，与SimpleDB交互的程序只需调用根操作符的getNext；这个操作符然后调用其子操作符的getNext，以此类推，直到这些叶操作符被调用。他们从磁盘中获取图元，并将其传递到树上（作为getNext的返回参数）；图元以这种方式在计划中传播，直到它们在根部被输出或被计划中的另一个运算符合并或拒绝。

在本实验中，你只需要实现一个SimpleDB操作符。

#### Exercise 6.

Implement the skeleton methods in:

------

- src/java/simpledb/execution/SeqScan.java

------

这个操作符按顺序扫描构造函数中tableid指定的表的页面中的所有图元。这个操作符应该通过DbFile.iterator()方法访问图元。在这一点上，你应该能够完成ScanTest系统的测试。
