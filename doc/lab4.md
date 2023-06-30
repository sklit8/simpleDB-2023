# 6.830 Lab 4: SimpleDB Transactions

**Assigned: Monday, Apr 5, 2021**
**Due: Thursday, Apr 22, 2021 11:59 PM ET**

## 1. Information

You should begin with the code you su

在这个实验室中，你将在SimpleDB中实现一个简单的基于锁的事务系统。你将需要在代码中的适当位置添加锁和解锁调用，以及跟踪每个事务所持有的锁的代码，并在需要时授予事务锁。

本文档的其余部分描述了添加事务支持所涉及的内容，并提供了一个关于如何将这种支持添加到你的数据库的基本概要。

和前面的实验室一样，我们建议你尽可能早地开始。锁定和事务的调试是相当棘手的!

## 2. Transactions, Locking, and Concurrency Control

在开始之前，你应该确保你了解什么是事务，以及严格的两阶段锁定（你将使用它来确保事务的隔离性和原子性）是如何工作的。

在本节的剩余部分，我们将简要地概述这些概念，并讨论它们与SimpleDB的关系。

### 2.1. Transactions

事务是一组原子执行的数据库操作（例如插入、删除和读取）；也就是说，要么所有的操作都完成了，要么都没有完成，而且对于数据库的外部观察者来说，这些操作没有作为一个单一的、不可分割的操作的一部分完成，这一点是不明显的。

### 2.2. The ACID Properties

为了帮助你理解SimpleDB中的事务管理是如何工作的，我们简单回顾一下它是如何确保ACID属性得到满足的：

- 原子性：严格的两阶段锁定和谨慎的缓冲区管理确保了原子性。
- 一致性：由于原子性的存在，数据库是事务一致的。其他的一致性问题（例如，键约束）在SimpleDB中没有被解决。
- 隔离性：严格的两阶段锁定提供了隔离性。
- 持久性：一个FORCE缓冲区管理策略确保了耐久性（见下面2.3节）。

### 2.3. Recovery and Buffer Management

为了简化你的工作，我们建议你实施 "NO STEAL/FORCE  "的缓冲区管理政策。

正如我们在课堂上讨论的，这意味着：

- 如果脏页（更新的）被一个未提交的事务锁定，你不应该将它们从缓冲池中驱逐出去（这就是NO STEAL）。
- 在事务提交时，你应该把脏页强制放到磁盘上（例如，把这些页写出来）（这就是FORCE）。

为了进一步简化你的生活，你可以假设SimpleDB在处理transactionComplete命令时不会崩溃。注意这三点意味着你不需要在这个实验室中实现基于日志的恢复，因为你永远不需要撤销任何工作（你永远不会驱逐脏页），也不需要重做任何工作（你在提交时强制更新，不会在提交处理时崩溃）。

### 2.4. Granting Locks

你将需要增加对SimpleDB的调用（例如在BufferPool中），允许调用者代表特定事务请求或释放特定对象的（共享或独占）锁。

我们建议在页粒度上加锁；为了测试的简单性，请不要实现表级锁（即使它是可能的）。本文档的其余部分和我们的单元测试都假设是页级锁。

你将需要创建数据结构来跟踪每个事务所持有的锁，并在事务被请求时检查是否应该授予一个锁。

你将需要实现共享锁和独占锁；记得这些锁的工作原理如下：

- 在一个事务可以读取一个对象之前，它必须拥有一个共享锁。
- 在一个事务可以写一个对象之前，它必须有一个专属锁。
- 多个事务可以在一个对象上拥有一个共享锁。
- 只有一个事务可以在一个对象上拥有一个独占锁。
- 如果事务t是唯一在对象o上持有共享锁的事务，t可以将其对o的锁升级为独占锁。

如果一个事务请求一个不能立即被授予的锁，你的代码应该阻塞，等待该锁变得可用（即，被另一个在不同线程中运行的事务释放）。在你的锁的实现中要小心竞赛条件------考虑对你的锁的并发调用会如何影响行为。(你可以阅读关于Java中的同步的文章）。

------

**Exercise 1.**

Write the methods that acquire and release locks in BufferPool. Assuming you are using page-level locking, you will need to complete the following:

- Modify `getPage()` to block and acquire the desired lock before returning a page.
- Implement `unsafeReleasePage()`. This method is primarily used for testing, and at the end of transactions.
- Implement `holdsLock()` so that logic in Exercise 2 can determine whether a page is already locked by a transaction.

你可能会发现定义一个LockManager类是有帮助的，它负责维护关于事务和锁的状态，但设计决定权在你。

在你的代码通过LockingTest的单元测试之前，你可能需要实现下一个练习。

------

### 2.5. Lock Lifetime

你将需要实现严格的两阶段锁。这意味着事务在访问任何对象之前应该获得适当类型的锁，并且在事务提交之前不应该释放任何锁。

幸运的是，SimpleDB的设计是这样的：在读取或修改页面之前，有可能在BufferPool.getPage()中获得页面的锁。因此，我们建议在getPage()中获取锁，而不是在你的每个操作中添加对锁程序的调用。根据你的实现，你有可能不需要在其他地方获取一个锁。这要靠你自己去验证!

你需要在读取任何页面（或元组）之前获得一个共享锁，你需要在写入任何页面（或元组）之前获得一个独占锁。你会注意到，我们已经在BufferPool中传递了许可对象；这些对象表明调用者希望对被访问对象拥有的锁的类型（我们已经给了你许可类的代码。）

请注意，你对HeapFile.insertTuple()和HeapFile.deleteTuple()的实现，以及HeapFile.iterator()返回的迭代器的实现应该使用BufferPool.getPage()来访问页面。仔细检查这些getPage()的不同用法是否传递了正确的权限对象（例如，Permissions.READ_WRITE 或 Permissions.READ_ONLY）。你也可以仔细检查你实现的BufferPool.insertTuple()和BufferPool.deleteTupe()是否在它们访问的任何页面上调用了markDirty()（你在实验2中实现这段代码时应该这样做，但我们没有对这种情况进行测试。）

在你获得了锁之后，你也需要考虑何时释放它们。很明显，你应该在一个事务提交或中止后释放与之相关的所有锁，以确保严格的2PL。然而，在其他情况下，在事务结束前释放锁可能是有用的。例如，你可以在扫描页面找到空槽后释放一个共享锁（如下所述）。

------

**Exercise 2.**

确保你在整个SimpleDB获得和释放锁。一些（但不一定是全部）你应该验证的操作正常工作：

- 在SeqScan过程中从页面上读取图元（如果你在BufferPool.getPage()中实现了锁，只要你的HeapFile.iterator()使用BufferPool.getPage()，这应该能正确工作。）
- 通过BufferPool和HeapFile方法插入和删除图元（如果你在BufferPool.getPage()中实现了锁定，只要HeapFile.insertTuple()和HeapFile.deleteTuple()使用BufferPool.getPage()，这应该能正确工作。）

你还需要特别考虑在以下情况下获取和释放锁的问题：

- 向HeapFile添加一个新的页面。你什么时候把这个页写到磁盘上？是否存在与其他事务（在其他线程上）的竞赛条件，可能需要在HeapFile级别上特别注意，而不考虑页级锁？
- 寻找一个可以插入图元的空槽。大多数实现都会扫描页面，寻找一个空槽，并且需要一个READ_ONLY锁来做这件事。然而，令人惊讶的是，如果一个事务t发现页面p上没有空槽，t可以立即释放p上的锁。虽然这显然与两阶段锁的规则相矛盾，但这是可以的，因为t没有使用页面上的任何数据，这样，一个更新p的并发事务t'不可能影响t的答案或结果。

At this point, your code should pass the unit tests in LockingTest.

------

### 2.6. Implementing NO STEAL

一个事务的修改只有在它提交之后才会被写入磁盘。这意味着我们可以通过丢弃脏页并从磁盘重读来中止一个事务。因此，我们必须不驱逐脏页。这个策略被称为NO STEAL。

你将需要修改BufferPool中的evictPage方法。特别是，它必须永远不驱逐一个脏页。如果你的驱逐策略倾向于驱逐一个脏页，你将不得不找到一种方法来驱逐一个替代页。在缓冲池中的所有页面都是脏的情况下，你应该抛出一个DbException。如果你的驱逐策略驱逐了一个干净的页面，要注意事务可能已经持有被驱逐的页面的任何锁，并在你的实现中适当地处理它们。

------

**Exercise 3.**

Implement the necessary logic for page eviction without evicting dirty pages in the `evictPage` method in `BufferPool`.

------

### 2.7. Transactions

在SimpleDB中，每次查询开始时都会创建一个TransactionId对象。这个对象被传递给每个参与查询的操作者。当查询完成后，BufferPool方法 transactionComplete被调用。

调用这个方法可以提交或中止事务，由参数标志commit指定。在执行过程中的任何时候，操作者都可以抛出一个TransactionAbortedException异常，这表明发生了内部错误或死锁。我们为你提供的测试案例创建了适当的TransactionId对象，以适当的方式将它们传递给你的操作者，并在查询完成后调用 transactionComplete。我们还实现了TransactionId。

------

**Exercise 4.**

在BufferPool中实现transactionComplete()方法。请注意，transactionComplete有两个版本，一个是接受额外的布尔提交参数，另一个是不接受。没有附加参数的版本应该总是提交，所以可以简单地通过调用transactionComplete(tid, true)来实现。

当你提交时，你应该将与事务相关的脏页刷入磁盘。当你放弃的时候，你应该通过恢复页面到磁盘上的状态来恢复事务所做的任何改变。

无论事务是提交还是中止，你都应该释放BufferPool保存的关于该事务的任何状态，包括释放该事务持有的任何锁。

在这一点上，你的代码应该通过TransactionTest单元测试和AbortEvictionTest系统测试。你可能会发现TransactionTest系统测试的说明性，但在你完成下一个练习之前，它可能会失败。

### 2.8. Deadlocks and Aborts

SimpleDB中的事务有可能出现死锁（如果你不明白为什么，我们建议阅读Ramakrishnan & Gehrke中关于死锁的内容）。你需要检测这种情况并抛出一个TransactionAbortedException。

有许多可能的方法来检测死锁。一个草根的例子是实现一个简单的超时策略，如果一个事务在给定的时间内还没有完成，就中止它。对于一个真正的解决方案，你可以在一个依赖图数据结构中实现周期检测，如讲座中所示。在这个方案中，你将定期检查依赖图中的周期，或者每当你试图授予一个新的锁时，如果存在一个周期，就中止某事。在你检测到死锁存在后，你必须决定如何改善这种情况。假设你在事务t等待锁的时候检测到了一个死锁。如果你有杀人的冲动，你可以中止t正在等待的所有事务；这可能会导致大量的工作被撤销，但你可以保证t会取得进展。另外，你也可以决定中止t，给其他事务一个取得进展的机会。这意味着终端用户将不得不重试事务t。

另一种方法是使用事务的全局排序来避免建立等待图。出于性能的考虑，这有时是首选，但是在这种方案下，本来可以成功的事务可能会被错误地中止。例子包括WAIT-DIE和WOUND-WAIT方案。

------

**Exercise 5.**

在 src/simpledb/BufferPool.java 中实现死锁检测或预防。对于你的死锁处理系统，你有很多设计决定，但没有必要做一些非常复杂的事情。我们希望你能做得比每个事务的简单超时更好。一个好的起点是在每个锁请求前的等待图中实现周期检测，这样的实现将得到全额奖励。请在实验报告中描述你的选择，并列出你的选择与其他选择相比的优点和缺点。

你应该通过抛出TransactionAbortedException异常来确保你的代码在发生死锁时正确中止事务。这个异常将被执行事务的代码捕获（例如，TransactionTest.java），它应该调用 transactionComplete() 来清理事务。我们不期望你自动重启一个因死锁而失败的事务--你可以假设更高级别的代码会处理这个问题。

我们在test/simpledb/DeadlockTest.java中提供了一些（不是单元的）测试。它们实际上有点复杂，所以它们可能需要超过几秒钟的时间来运行（取决于你的策略）。如果它们似乎无限期地挂起，那么你可能有一个未解决的死锁。这些测试构建了简单的死锁情况，你的代码应该能够逃脱。

请注意，在DeadLockTest.java的顶部有两个计时参数；它们决定了测试检查锁是否被获取的频率，以及中止的事务被重新启动前的等待时间。如果你使用基于超时的检测方法，你可以通过调整这些参数观察到不同的性能特征。测试将把解决死锁对应的TransactionAbortedExceptions输出到控制台。

你的代码现在应该通过TransactionTest系统测试（根据你的实现，它也可能运行相当长的时间）。

在这一点上，你应该有一个可恢复的数据库，也就是说，如果数据库系统崩溃（在 transactionComplete() 以外的地方），或者如果用户明确地中止一个事务，那么在系统重新启动（或事务中止）后，任何正在运行的事务的影响将不可见。 你可能希望通过运行一些事务和明确地杀死数据库服务器来验证这点。

------

### 2.9. Design alternatives

在这个实验室的过程中，我们已经确定了一些你必须做出的实质性设计选择：

- 锁定颗粒度：页级与元组级
- 死锁处理：检测与预防，终止自己与终止他人。

